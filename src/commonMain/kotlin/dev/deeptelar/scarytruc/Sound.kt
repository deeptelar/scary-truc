package dev.deeptelar.scarytruc

import dev.deeptelar.scarytruc.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** What the night sounds like at a moment of the game. */
enum class Cue {
    WOMAN_SCREAM_1, WOMAN_SCREAM_2, MAN_SCREAM_1, MAN_SCREAM_2, SOB,
    STATIC_1, STATIC_2, STATIC_3, STATIC_4,
    STINGER, WAKE, BELL, SIREN, WHISPER, DOOR, CHIME, RING, HANGUP, MESSAGE,
    STEP_1, STEP_2, STEP_3, STEP_4,
}

/** Sounds cut from a recording, read when the game starts: [cues] one after the other in [file], as long as each other. */
private class Recording(val file: String, val cues: List<Cue>) {
    constructor(file: String, cue: Cue) : this(file, listOf(cue))
}

/** The drones that loop under the night. */
enum class Drone { NIGHT, OTHERWORLD }

/** Where the sounds are played: Java Sound on the desktop, Web Audio in a browser. */
interface SoundOutput {
    /** Plays [samples] once, [gain] decibels louder or quieter. */
    fun play(cue: Cue, gain: Float)

    /** Loops [drone], or stops every drone when it is `null`. */
    fun drone(drone: Drone?)
}

/**
 * The sounds of the night, recorded, and made from sine waves and noise until the recordings are
 * read; the drones are only made. A low
 * drone plays all night, a heavier one after midnight, and radio static crackles when the Hollow
 * is near ([danger]), without saying where; now and then it crackles with nothing there. Just
 * before the Hollow steps close, everything can go quiet ([hushed]). Without an [output], or with
 * [on] false, it is silent.
 */
object Sound {
    const val RATE: Float = 22050f

    var output: SoundOutput? = null

    var on: Boolean = true
        set(value) {
            field = value
            drones()
        }

    /** How close the Hollow is to the caller, from 0 (far or asleep) to 1 (next to them). */
    var danger: Float = 0f

    /** The silence before the scare: no drone, no static. */
    var hushed: Boolean = false
        set(value) {
            field = value
            drones()
        }

    /** Past midnight: the Otherworld's drone. */
    var otherworld: Boolean = false
        set(value) {
            field = value
            drones()
        }

    private var started = false

    /** Footsteps, one played at random for each step the caller walks. */
    val STEPS: List<Cue> = listOf(Cue.STEP_1, Cue.STEP_2, Cue.STEP_3, Cue.STEP_4)

    /** Crackles of a radio, one at random each time. */
    val STATICS: List<Cue> = listOf(Cue.STATIC_1, Cue.STATIC_2, Cue.STATIC_3, Cue.STATIC_4)

    /** The last sound of a caller, in their voice. */
    fun screams(woman: Boolean): List<Cue> = if (woman) listOf(Cue.WOMAN_SCREAM_1, Cue.WOMAN_SCREAM_2) else listOf(Cue.MAN_SCREAM_1, Cue.MAN_SCREAM_2)

    // Mono, 16 bits, 22050 Hz, cut from sound effects on Pixabay; the README names who made them.
    private val recordings =
        listOf(
            Recording("files/steps.pcm", STEPS),
            Recording("files/static.pcm", STATICS),
            Recording("files/woman-scream-1.pcm", Cue.WOMAN_SCREAM_1),
            Recording("files/woman-scream-2.pcm", Cue.WOMAN_SCREAM_2),
            Recording("files/man-scream-1.pcm", Cue.MAN_SCREAM_1),
            Recording("files/man-scream-2.pcm", Cue.MAN_SCREAM_2),
            Recording("files/sob.pcm", Cue.SOB),
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
        )

    /** The recorded cues read so far. */
    private var recorded: Map<Cue, FloatArray> = emptyMap()

    /** Whether [cue] can be played: a recorded one only once its recording is read. */
    fun ready(cue: Cue): Boolean = cue in recorded || recordings.none { cue in it.cues }

    /** Starts the drone and the static, in [scope]. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        drones()
        // One after the other, so that each adds to what the last one read.
        scope.launch {
            for (recording in recordings) {
                val bytes = runCatching { Res.readBytes(recording.file) }.getOrNull() ?: continue
                val samples = FloatArray(bytes.size / 2) { i -> ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f }
                val length = samples.size / recording.cues.size
                recorded = recorded + recording.cues.withIndex().associate { (i, cue) -> cue to samples.copyOfRange(i * length, (i + 1) * length) }
            }
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
    }

    private fun drones() {
        if (!started) return
        runCatching { output?.drone(if (!on || hushed) null else if (otherworld) Drone.OTHERWORLD else Drone.NIGHT) }
    }

    fun play(cue: Cue, gain: Float = 0f) {
        if (!on || !ready(cue)) return
        runCatching { output?.play(cue, gain) }
    }

    /** The samples of [cue], from -1 to 1: its recording, or until it is read, a sound made like it. */
    fun samples(cue: Cue): FloatArray {
        recorded[cue]?.let { return it }
        val noise = Random(cue.ordinal)
        return when (cue) {
            // A thin, wavering scream that rises and breaks.
            Cue.WOMAN_SCREAM_1, Cue.WOMAN_SCREAM_2, Cue.MAN_SCREAM_1, Cue.MAN_SCREAM_2, Cue.SOB ->
                wave(0.9) { t, _ ->
                    val pitch = 700 + 900 * t / 0.9 * (1 - t / 0.9) * 4 + 40 * sin(2 * PI * 9 * t)
                    (saw(pitch * t) * 0.45 + (noise.nextDouble() - 0.5) * 0.3) * envelope(t, 0.03, 0.9)
                }
            // Radio static: crackles of noise that come and go.
            Cue.STATIC_1, Cue.STATIC_2, Cue.STATIC_3, Cue.STATIC_4 -> {
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
            // Something waking: a low, rough growl.
            Cue.WAKE ->
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
            // A breath that almost says something.
            Cue.WHISPER -> {
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
            // The line goes dead: three short, high beeps.
            Cue.HANGUP ->
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
            // A footstep: a dull knock and a splash.
            Cue.STEP_1, Cue.STEP_2, Cue.STEP_3, Cue.STEP_4 ->
                wave(0.22) { t, _ -> (sin(2 * PI * 70 * t) * 0.8 + (noise.nextDouble() - 0.5) * 0.4) * exp(-t * 28) }
        }
    }

    /** The samples of [drone], which loop without a seam. */
    fun samples(drone: Drone): FloatArray = if (drone == Drone.NIGHT) droneSamples() else otherworldSamples()

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
