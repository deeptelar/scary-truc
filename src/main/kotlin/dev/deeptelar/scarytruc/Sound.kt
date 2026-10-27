package dev.deeptelar.scarytruc

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** What the night sounds like at a moment of the game. */
enum class Cue { SCREAM, STATIC, STINGER, WAKE, BELL, SIREN, WHISPER, CHIME, DEATH, TREAT, TICK }

/**
 * The sounds of the night, made from sine waves and noise when the game starts: no files. A low
 * drone plays all night, a heavier one after midnight, and radio static crackles when the Hollow
 * is near ([danger]), without saying where; now and then it crackles with nothing there. Just
 * before the Hollow steps into the light, everything goes quiet ([hushed]). Without a sound
 * device, or with [on] false, it is silent.
 */
object Sound {
    private const val RATE = 22050f
    private val format = AudioFormat(RATE, 16, 1, true, false)

    @Volatile
    var on: Boolean = true
        set(value) {
            field = value
            drones()
        }

    /** How close the Hollow is to the crew, from 0 (far or asleep) to 1 (next to someone). */
    @Volatile
    var danger: Float = 0f

    /** The silence before the scare: no drone, no static. */
    @Volatile
    var hushed: Boolean = false
        set(value) {
            field = value
            drones()
        }

    /** Past midnight: the Otherworld's drone. */
    @Volatile
    var otherworld: Boolean = false
        set(value) {
            field = value
            drones()
        }

    private val clips: Map<Cue, Clip> by lazy {
        runCatching { Cue.entries.associateWith { clip(samples(it), loop = false) } }.getOrDefault(emptyMap())
    }
    private var drone: Clip? = null
    private var otherDrone: Clip? = null
    private var started = false

    /** Starts the drone and the static. */
    fun start() {
        if (started) return
        started = true
        runCatching {
            drone = clip(droneSamples(), loop = true)
            otherDrone = clip(otherworldSamples(), loop = true)
            clips.size
        }
        drones()
        Thread {
            val random = Random(7)
            while (true) {
                val d = danger
                when {
                    !on || hushed -> Unit
                    d > 0.05f -> play(Cue.STATIC, gain = -16f + 16f * d)
                    // Static with nothing there, now and then: the radio can't be trusted.
                    random.nextDouble() < 0.012 -> play(Cue.STATIC, gain = -18f)
                }
                Thread.sleep(if (d > 0.05f) (700 - 520 * d).toLong() + random.nextLong(120) else 400)
            }
        }.apply {
            isDaemon = true
            name = "static"
            start()
        }
    }

    /** The drone that should play now, looping, and the other one stopped. */
    @Synchronized
    private fun drones() {
        runCatching {
            val playing = if (!on || hushed) null else if (otherworld) otherDrone else drone
            for (clip in listOfNotNull(drone, otherDrone)) {
                if (clip == playing && !clip.isRunning) clip.loop(Clip.LOOP_CONTINUOUSLY)
                if (clip != playing && clip.isRunning) clip.stop()
            }
        }
    }

    fun play(cue: Cue, gain: Float = 0f) {
        if (!on) return
        val clip = clips[cue] ?: return
        runCatching {
            clip.stop()
            (clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl)?.let { it.value = gain.coerceIn(it.minimum, it.maximum) }
            clip.framePosition = 0
            clip.start()
        }
    }

    private fun clip(samples: FloatArray, loop: Boolean): Clip {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            bytes[2 * i] = (v and 0xFF).toByte()
            bytes[2 * i + 1] = (v shr 8).toByte()
        }
        return AudioSystem.getClip().apply {
            open(format, bytes, 0, bytes.size)
            if (loop) (getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl)?.value = -14f
        }
    }

    /** The samples of [cue], from -1 to 1. */
    fun samples(cue: Cue): FloatArray {
        val noise = Random(cue.ordinal)
        return when (cue) {
            // A thin, wavering scream that rises and breaks.
            Cue.SCREAM ->
                wave(0.9) { t, _ ->
                    val pitch = 700 + 900 * t / 0.9 * (1 - t / 0.9) * 4 + 40 * sin(2 * PI * 9 * t)
                    (saw(pitch * t) * 0.45 + (noise.nextDouble() - 0.5) * 0.3) * envelope(t, 0.03, 0.9)
                }
            // Radio static: crackles of noise that come and go.
            Cue.STATIC -> {
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
            Cue.CHIME -> wave(0.7) { t, _ -> (sin(2 * PI * 880 * t) + sin(2 * PI * 1318.5 * t)) / 2 * exp(-t * 5) * 0.6 }
            Cue.TREAT -> wave(0.25) { t, _ -> sin(2 * PI * (1200 + 800 * t) * t) * exp(-t * 14) * 0.4 }
            // A long fall.
            Cue.DEATH ->
                wave(1.4) { t, _ ->
                    val pitch = 420 * exp(-t * 1.6)
                    (saw(pitch * t) * 0.5 + (noise.nextDouble() - 0.5) * 0.2) * envelope(t, 0.02, 1.4)
                }
            Cue.TICK -> wave(0.05) { t, _ -> sin(2 * PI * 1800 * t) * exp(-t * 90) * 0.35 }
        }
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

/** The sounds a turn calls for, from the night [before] it to the night [after] it. */
fun cues(before: Night, after: Night, maze: Maze): List<Cue> =
    buildList {
        val deadBefore = before.crew.count { it.fate == Fate.DEAD }
        if (after.crew.count { it.fate == Fate.DEAD } > deadBefore) add(Cue.DEATH)
        if (after.saved > before.saved) add(Cue.CHIME)
        if (after.candy > before.candy && after.turn > before.turn) add(Cue.CHIME)
        if (before.hollow?.awake == false && after.hollow?.awake == true) add(Cue.WAKE)
        val seen = after.hollow?.let { it.at in after.lit(maze) } == true
        val seenBefore = before.hollow?.let { it.at in before.lit(maze) } == true
        if (seen && !seenBefore) add(Cue.STINGER)
        if (!before.afterMidnight && after.afterMidnight) {
            add(Cue.BELL)
            add(Cue.SIREN)
        }
        if (after.whisper != null && after.whisper != before.whisper) add(Cue.WHISPER)
    }

/** How close the awake Hollow is to the nearest of the crew, from 0 to 1, for the heartbeat. */
fun danger(night: Night, maze: Maze): Float {
    val hollow = night.hollow?.takeIf { it.awake } ?: return 0f
    val steps = maze.distances(hollow.at)
    val nearest = night.lost.mapNotNull { steps[it.at] }.minOrNull() ?: return 0f
    return ((7 - nearest) / 6f).coerceIn(0f, 1f)
}

/**
 * Whether the Hollow is about to step into a lantern's light: awake, still in the dark, and a step
 * or two from the light. The moment for silence.
 */
fun imminent(night: Night, maze: Maze): Boolean {
    val hollow = night.hollow?.takeIf { it.awake } ?: return false
    val lit = night.lit(maze)
    if (hollow.at in lit) return false
    val steps = maze.distances(hollow.at)
    return lit.any { (steps[it] ?: 99) <= 2 }
}
