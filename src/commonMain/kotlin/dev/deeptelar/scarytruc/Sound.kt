package dev.deeptelar.scarytruc

import dev.deeptelar.scarytruc.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** What the night sounds like at a moment of the game. */
enum class Cue {
    WOMAN_SCREAM_1, WOMAN_SCREAM_2, MAN_SCREAM_1, MAN_SCREAM_2,
    STATIC_1, STATIC_2, STATIC_3, STATIC_4,
    STINGER, WAKE, BELL, SIREN, WHISPER, DOOR, CHIME, RING, HANGUP, MESSAGE,
    STEP_1, STEP_2, STEP_3, STEP_4, RUN,
    SNIFF, FLARE, STONE,
    WOMAN_FALL, MAN_FALL, CRUNCH, SHATTER, ROAR, GRIND,
    PICKUP, LOW_BATTERY, POWER_OFF, BATTERY_IN,
    SCRATCH, PICK, WALL_1, WALL_2, RUSTLE_1, RUSTLE_2, HOWL, HEARTBEAT, WIND,
}

/** Sounds cut from a recording, read when the game starts: [cues] one after the other in [file], as long as each other. */
private class Recording(val file: String, val cues: List<Cue>) {
    constructor(file: String, cue: Cue) : this(file, listOf(cue))
}

/**
 * The drones that loop under the night: the dark one on the lock and the guide, one for each place, and after midnight one of [MIDNIGHT].
 * Each is read from [file] only once it is wanted, and played [gain] decibels under [Sound.DRONE]:
 * a chirp sounds louder than a hum as strong, so the crickets in the trees are turned down, and the voices
 * of the tape a little, to leave the caller room.
 */
enum class Drone(val file: String, val gain: Float = 0f) {
    MINE("files/mine.pcm"),
    WOODS("files/woods.pcm", gain = -10f),
    CUBE("files/cube.pcm"),
    TAPE("files/tape.pcm", gain = -4f),
    HEDGE("files/hedge.pcm", gain = -3f),
    SHIP("files/ship.pcm"),
    OTHERWORLD("files/otherworld.pcm"),
    DARK("files/ambient.pcm"),

    /** The dark loop under the screens around a call: to read over, not to listen to. */
    WAITING("files/ambient.pcm", gain = -8f),
    ;

    companion object {
        /** The drones that can play after midnight, one for each call. */
        val MIDNIGHT: List<Drone> = listOf(OTHERWORLD, DARK)
    }
}

/** Where the sounds are played: Java Sound on the desktop, Web Audio in a browser. */
interface SoundOutput {
    /** Plays [samples] once, [gain] decibels louder or quieter. */
    fun play(cue: Cue, gain: Float)

    /** Cuts [cue] short where it is playing. */
    fun stop(cue: Cue)

    /** Loops [drone], or stops every drone when it is `null`. */
    fun drone(drone: Drone?)
}

/**
 * The sounds of the night, recorded, and made from sine waves and noise until the recordings are
 * read. A low
 * drone plays all night, a heavier one after midnight, and radio static crackles when the Hollow
 * is near ([danger]), without saying where; now and then it crackles with nothing there. Just
 * before the Hollow steps close, everything can go quiet ([hushed]). Without an [output], or with
 * [on] false, it is silent.
 */
object Sound {
    const val RATE: Float = 22050f

    /** How many decibels under the other sounds a drone plays, before its own [Drone.gain]. */
    const val DRONE: Float = -8f

    var output: SoundOutput? = null

    var on: Boolean = true
        set(value) {
            field = value
            drones()
        }

    /** How close the Hollow is to the caller, from 0 (far or asleep) to 1 (next to them). */
    var danger: Float = 0f

    /** Told of what went wrong with the sound, which never stops the game. */
    var fault: (Throwable) -> Unit = {}

    /** The silence before the scare: no drone, no static. */
    var hushed: Boolean = false
        set(value) {
            field = value
            drones()
        }

    /** The phone is locked, or shows the guide or the options, over the dark ambient loop. */
    var waiting: Boolean = true
        set(value) {
            field = value
            drones()
        }

    /** The drone of the call's place, until midnight. */
    var place: Drone = Drone.MINE
        set(value) {
            field = value
            wanted.trySend(value)
            drones()
        }

    /** The drones of the places picked and of their midnights, to read: only those, since each is a large recording. */
    private val wanted = Channel<Drone>(Channel.UNLIMITED)

    /** Past midnight: the [midnight] drone instead of the place's. */
    var otherworld: Boolean = false
        set(value) {
            field = value
            drones()
        }

    /** The drone after midnight, one of [Drone.MIDNIGHT]. */
    var midnight: Drone = Drone.OTHERWORLD
        set(value) {
            field = value
            wanted.trySend(value)
            drones()
        }

    private var started = false

    /** Footsteps, one played at random for each step the caller walks. */
    val STEPS: List<Cue> = listOf(Cue.STEP_1, Cue.STEP_2, Cue.STEP_3, Cue.STEP_4)

    /** Crackles of a radio, one at random each time. */
    val STATICS: List<Cue> = listOf(Cue.STATIC_1, Cue.STATIC_2, Cue.STATIC_3, Cue.STATIC_4)

    /** A caller walking into a wall, one at random. */
    val WALLS: List<Cue> = listOf(Cue.WALL_1, Cue.WALL_2)

    /** Clothes as a caller crouches, one at random. */
    val RUSTLES: List<Cue> = listOf(Cue.RUSTLE_1, Cue.RUSTLE_2)

    /** The last sound of a caller, in their voice. */
    fun screams(woman: Boolean): List<Cue> = if (woman) listOf(Cue.WOMAN_SCREAM_1, Cue.WOMAN_SCREAM_2) else listOf(Cue.MAN_SCREAM_1, Cue.MAN_SCREAM_2)

    /** A caller falling into a pit with no bottom, in their voice. */
    fun fall(woman: Boolean): Cue = if (woman) Cue.WOMAN_FALL else Cue.MAN_FALL

    /** How long [cue] lasts, in milliseconds. */
    fun millis(cue: Cue): Long = (samples(cue).size * 1000L / RATE).toLong()

    // Mono, 16 bits, 22050 Hz, cut from sound effects on Pixabay; the README names who made them.
    // Levelled by their loudest 400 ms: the scares near -8 dB and everything else at -18, so that
    // a scare is the loudest thing of the night; the drones at -14, and played 8 dB down.
    private val recordings =
        listOf(
            Recording("files/steps.pcm", STEPS),
            Recording("files/static.pcm", STATICS),
            Recording("files/woman-scream-1.pcm", Cue.WOMAN_SCREAM_1),
            Recording("files/woman-scream-2.pcm", Cue.WOMAN_SCREAM_2),
            Recording("files/man-scream-1.pcm", Cue.MAN_SCREAM_1),
            Recording("files/man-scream-2.pcm", Cue.MAN_SCREAM_2),
            Recording("files/stinger.pcm", Cue.STINGER),
            Recording("files/growl.pcm", Cue.WAKE),
            Recording("files/bell.pcm", Cue.BELL),
            Recording("files/siren.pcm", Cue.SIREN),
            Recording("files/breath.pcm", Cue.WHISPER),
            Recording("files/door.pcm", Cue.DOOR),
            Recording("files/success.pcm", Cue.CHIME),
            Recording("files/ring.pcm", Cue.RING),
            Recording("files/hangup.pcm", Cue.HANGUP),
            Recording("files/message.pcm", Cue.MESSAGE),
            Recording("files/sniff.pcm", Cue.SNIFF),
            Recording("files/flare.pcm", Cue.FLARE),
            Recording("files/stone.pcm", Cue.STONE),
            Recording("files/run.pcm", Cue.RUN),
            Recording("files/woman-fall.pcm", Cue.WOMAN_FALL),
            Recording("files/man-fall.pcm", Cue.MAN_FALL),
            Recording("files/crunch.pcm", Cue.CRUNCH),
            Recording("files/shatter.pcm", Cue.SHATTER),
            Recording("files/roar.pcm", Cue.ROAR),
            Recording("files/grind.pcm", Cue.GRIND),
            Recording("files/pickup.pcm", Cue.PICKUP),
            Recording("files/low-battery.pcm", Cue.LOW_BATTERY),
            Recording("files/power-off.pcm", Cue.POWER_OFF),
            Recording("files/battery-in.pcm", Cue.BATTERY_IN),
            Recording("files/scratch.pcm", Cue.SCRATCH),
            Recording("files/pick.pcm", Cue.PICK),
            Recording("files/wall-1.pcm", Cue.WALL_1),
            Recording("files/wall-2.pcm", Cue.WALL_2),
            Recording("files/rustle-1.pcm", Cue.RUSTLE_1),
            Recording("files/rustle-2.pcm", Cue.RUSTLE_2),
            Recording("files/howl.pcm", Cue.HOWL),
            Recording("files/heartbeat.pcm", Cue.HEARTBEAT),
            Recording("files/wind.pcm", Cue.WIND),
        )

    /** The cues read before the rest: what is heard before the call starts. */
    private val FIRST = setOf(Cue.RING, Cue.PICKUP)

    /** The recorded cues read so far. */
    @Volatile
    private var recorded: Map<Cue, FloatArray> = emptyMap()

    /** Whether [cue] can be played: a recorded one only once its recording is read. */
    fun ready(cue: Cue): Boolean = cue in recorded || recordings.none { cue in it.cues }

    /** Starts the drone and the static, in [scope]. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        drones()
        // One after the other, so that each adds to what the last one read: the ring and the pickup,
        // the drone heard first, the other cues, the drones of this place and of its midnight, and each drone wanted later.
        scope.launch {
            val loaded = mutableSetOf<Drone>()

            suspend fun load(drone: Drone) {
                if (!loaded.add(drone)) return
                readDrones = readDrones + (drone to (read(drone.file) ?: return))
                drones()
            }
            suspend fun read(recording: Recording) {
                val samples = read(recording.file) ?: return
                val length = samples.size / recording.cues.size
                recorded = recorded + recording.cues.withIndex().associate { (i, cue) -> cue to samples.copyOfRange(i * length, (i + 1) * length) }
            }
            // The ring before anything else: the phone rings as soon as the page opens, and a large
            // drone read first kept it silent for as long as a phone took to download it.
            val first = recordings.filter { it.cues.any { cue -> cue in FIRST } }
            for (recording in first) read(recording)
            load(if (waiting) Drone.WAITING else place)
            for (recording in recordings - first.toSet()) read(recording)
            load(place)
            load(midnight)
            for (drone in wanted) load(drone)
        }
        scope.launch {
            val random = Random(7)
            while (true) {
                val d = danger
                when {
                    !on || hushed -> Unit
                    d > 0.05f -> play(STATICS.random(random), gain = -16f + 16f * d)
                    // Static with nothing there, now and then: the line can't be trusted.
                    random.nextDouble() < 0.012 -> play(STATICS.random(random), gain = -18f)
                }
                delay(if (d > 0.05f) (700 - 520 * d).toLong() + random.nextLong(120) else 400)
            }
        }
        scope.launch {
            // The caller's heart, faster and louder as the Hollow comes; it beats on in the silence before the scare.
            while (true) {
                val d = danger
                if (on && d > 0.2f) {
                    play(Cue.HEARTBEAT, gain = -6f + 12f * d)
                    delay((1100 - 500 * d).toLong())
                } else {
                    delay(300)
                }
            }
        }
    }

    /** The output can play now, as a browser's after a tap: the drone that should be playing starts. */
    fun opened() = drones()

    private fun drones() {
        if (!started) return
        runCatching { output?.drone(if (!on || hushed) null else if (waiting) Drone.WAITING else if (otherworld) midnight else place) }.onFailure(fault)
    }

    fun play(cue: Cue, gain: Float = 0f) {
        if (!on || !ready(cue)) return
        runCatching { output?.play(cue, gain) }.onFailure(fault)
    }

    fun stop(cue: Cue) {
        runCatching { output?.stop(cue) }.onFailure(fault)
    }

    /** The samples of [cue], from -1 to 1: its recording, or until it is read, a sound made like it. */
    fun samples(cue: Cue): FloatArray {
        recorded[cue]?.let { return it }
        val noise = Random(cue.ordinal)
        return when (cue) {
            // A thin, wavering scream that rises and breaks.
            Cue.WOMAN_SCREAM_1, Cue.WOMAN_SCREAM_2, Cue.MAN_SCREAM_1, Cue.MAN_SCREAM_2, Cue.WOMAN_FALL, Cue.MAN_FALL ->
                wave(0.9) { t, _ ->
                    val pitch = 700 + 900 * t / 0.9 * (1 - t / 0.9) * 4 + 40 * sin(2 * PI * 9 * t)
                    (saw(pitch * t) * 0.45 + (noise.nextDouble() - 0.5) * 0.3) * envelope(t, 0.03, 0.9)
                }
            // Radio static: crackles of noise that come and go; something that cracks or breaks.
            Cue.STATIC_1, Cue.STATIC_2, Cue.STATIC_3, Cue.STATIC_4, Cue.CRUNCH, Cue.SHATTER, Cue.SCRATCH, Cue.RUSTLE_1, Cue.RUSTLE_2 -> {
                var gate = 0.0
                var last = 0.0
                wave(0.35) { t, i ->
                    if (i % 220 == 0) gate = if (noise.nextDouble() < 0.6) noise.nextDouble() else 0.0
                    val white = noise.nextDouble() - 0.5
                    // Only the hiss: the difference of two samples drops the low end.
                    val hiss = white - last
                    last = white
                    hiss * gate * 0.7 * envelope(t, 0.01, 0.35)
                }
            }
            // The jump scare: a dissonant cluster that hits at once and dies slowly.
            Cue.STINGER ->
                wave(1.6) { t, _ ->
                    val cluster = listOf(220.0, 233.1, 311.1, 466.2, 622.3).sumOf { sin(2 * PI * it * t) } / 5
                    (cluster * 0.8 + (noise.nextDouble() - 0.5) * 0.5 * exp(-t * 12)) * exp(-t * 2.2)
                }
            // Something waking: a low, rough growl. Its roar, and the walls that move, sound the same.
            Cue.WAKE, Cue.HOWL, Cue.ROAR, Cue.GRIND ->
                wave(1.8) { t, _ ->
                    val rumble = sin(2 * PI * 38 * t) * (0.6 + 0.4 * sin(2 * PI * 7 * t))
                    (rumble * 0.7 + (noise.nextDouble() - 0.5) * 0.35) * envelope(t, 0.4, 1.8)
                }
            // A church bell for midnight.
            Cue.BELL ->
                wave(2.6) { t, _ ->
                    (sin(2 * PI * 196 * t) + 0.5 * sin(2 * PI * 392.7 * t) + 0.3 * sin(2 * PI * 587 * t) + 0.2 * sin(2 * PI * 466 * t)) / 2 * exp(-t * 1.4)
                }
            // The siren of midnight: a slow wail up and down, twice. Its pitch, 540 - 210 cos(πt)
            // hertz, sweeps from 330 to 750; the phase is the integral of the pitch.
            Cue.SIREN ->
                wave(4.0) { t, _ ->
                    val phase = 2 * PI * (540 * t - 210 * sin(PI * t) / PI)
                    (sin(phase) + 0.3 * sin(3 * phase)) * 0.45 * envelope(t, 0.3, 4.0)
                }
            // A breath that almost says something; a caller's breath, a sniff, a flare's hiss.
            Cue.WHISPER, Cue.SNIFF, Cue.FLARE, Cue.WIND -> {
                var low = 0.0
                wave(1.5) { t, _ ->
                    low += (noise.nextDouble() - 0.5 - low) * 0.18
                    low * 2.2 * (0.55 + 0.45 * sin(2 * PI * 4.5 * t)) * envelope(t, 0.15, 1.5)
                }
            }
            Cue.DOOR, Cue.CHIME -> wave(0.7) { t, _ -> (sin(2 * PI * 880 * t) + sin(2 * PI * 1318.5 * t)) / 2 * exp(-t * 5) * 0.6 }
            // An old phone ringing: two bursts of a warbling double tone.
            Cue.RING ->
                wave(2.0) { t, _ ->
                    val on = (t % 0.6) < 0.4 && t < 1.2
                    if (!on) 0.0 else (sin(2 * PI * 440 * t) + sin(2 * PI * 480 * t)) * 0.3 * (0.6 + 0.4 * sin(2 * PI * 20 * t))
                }
            // The line goes dead: three short, high beeps. A phone's battery beeps like it.
            Cue.HANGUP, Cue.LOW_BATTERY, Cue.POWER_OFF ->
                wave(1.2) { t, _ ->
                    if ((t % 0.4) < 0.18) sin(2 * PI * 620 * t) * 0.35 * envelope(t % 0.4, 0.005, 0.18) else 0.0
                }
            // A message on a phone of 2003: "SMS" in Morse, · · ·  − −  · · ·, on its buzzer.
            Cue.MESSAGE -> {
                val dot = 0.065
                val beeps = mutableListOf<Pair<Double, Double>>()
                var at = 0.0
                for ((i, letter) in listOf("...", "--", "...").withIndex()) {
                    if (i > 0) at += 2 * dot
                    for (sign in letter) {
                        val length = if (sign == '.') dot else 3 * dot
                        beeps += at to length
                        at += length + dot
                    }
                }
                wave(at) { t, _ ->
                    val beep = beeps.firstOrNull { (start, length) -> t >= start && t < start + length } ?: return@wave 0.0
                    // A square wave, its edges eased so that each beep starts and stops without a click.
                    val edge = minOf(t - beep.first, beep.first + beep.second - t).coerceAtMost(0.004) / 0.004
                    (if (sin(2 * PI * 1760 * t) >= 0) 0.22 else -0.22) * edge
                }
            }
            // A footstep: a dull knock and a splash. Running, a stone that falls or a click sound the same.
            Cue.STEP_1, Cue.STEP_2, Cue.STEP_3, Cue.STEP_4, Cue.RUN, Cue.STONE, Cue.PICKUP, Cue.BATTERY_IN, Cue.PICK, Cue.WALL_1, Cue.WALL_2, Cue.HEARTBEAT ->
                wave(0.22) { t, _ -> (sin(2 * PI * 70 * t) * 0.8 + (noise.nextDouble() - 0.5) * 0.4) * exp(-t * 28) }
        }
    }

    /** The drones read so far. */
    @Volatile
    private var readDrones: Map<Drone, FloatArray> = emptyMap()

    /** The drones made from sine waves, for until their recording is read. */
    private var madeDrones: Map<Drone, FloatArray> = emptyMap()

    /**
     * The samples of [drone], which loop without a seam: the same array until the recording is read,
     * so that an output can tell when to make its loop again.
     */
    fun samples(drone: Drone): FloatArray =
        readDrones[drone] ?: madeDrones[drone] ?: (if (drone == Drone.OTHERWORLD) otherworldSamples() else droneSamples()).also { madeDrones = madeDrones + (drone to it) }

    /** A recording in [file]: mono, 16 bits, at [RATE]. */
    private suspend fun read(file: String): FloatArray? {
        val bytes = runCatching { Res.readBytes(file) }.onFailure(fault).getOrNull() ?: return null
        return FloatArray(bytes.size / 2) { i -> ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f }
    }

    /** Two low tones that beat against each other, and a breath of noise: loops without a seam. */
    private fun droneSamples(): FloatArray {
        val noise = Random(99)
        // Whole cycles of 55 Hz and 58 Hz fit in 4 seconds.
        return wave(4.0) { t, _ -> (sin(2 * PI * 55 * t) + sin(2 * PI * 58 * t)) * 0.25 + (noise.nextDouble() - 0.5) * 0.04 }
    }

    /** The Otherworld: lower, out of tune, with a slow metal grind; whole cycles in 4 seconds. */
    private fun otherworldSamples(): FloatArray {
        val noise = Random(66)
        var grind = 0.0
        return wave(4.0) { t, _ ->
            grind += (noise.nextDouble() - 0.5 - grind) * 0.02
            (sin(2 * PI * 41.25 * t) + sin(2 * PI * 44 * t) + 0.25 * sin(2 * PI * 165.25 * t) * sin(2 * PI * 0.25 * t)) * 0.24 + grind * 1.4
        }
    }

    private fun wave(seconds: Double, at: (t: Double, i: Int) -> Double): FloatArray =
        FloatArray((seconds * RATE).toInt()) { i -> at(i / RATE.toDouble(), i).toFloat() }

    private fun saw(phase: Double): Double = 2 * (phase - kotlin.math.floor(phase + 0.5))

    /** Rises over [attack] seconds and falls to nothing at [length]. */
    private fun envelope(t: Double, attack: Double, length: Double): Double = if (t < attack) t / attack else (1 - (t - attack) / (length - attack)).coerceAtLeast(0.0)
}
