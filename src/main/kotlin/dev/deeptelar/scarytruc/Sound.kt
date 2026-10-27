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
enum class Cue { SCREAM, HEART, STINGER, WAKE, BELL, CHIME, DEATH, TREAT, TICK }

/**
 * The sounds of the night, made from sine waves and noise when the game starts: no files. A low
 * drone plays all night, and a heartbeat that quickens as the Hollow nears ([danger]). Without a
 * sound device, or with [on] false, it is silent.
 */
object Sound {
    private const val RATE = 22050f
    private val format = AudioFormat(RATE, 16, 1, true, false)

    @Volatile
    var on: Boolean = true

    /** How close the Hollow is to the crew, from 0 (far or asleep) to 1 (next to someone). */
    @Volatile
    var danger: Float = 0f

    private val clips: Map<Cue, Clip> by lazy {
        runCatching { Cue.entries.associateWith { clip(samples(it), loop = false) } }.getOrDefault(emptyMap())
    }
    private var drone: Clip? = null
    private var heart: Thread? = null

    /** Starts the drone and the heartbeat. */
    fun start() {
        if (!on || drone != null) return
        runCatching {
            drone = clip(droneSamples(), loop = true).also { it.loop(Clip.LOOP_CONTINUOUSLY) }
            clips.size
        }
        heart =
            Thread {
                while (true) {
                    val d = danger
                    if (d > 0.05f && on) play(Cue.HEART)
                    Thread.sleep(if (d > 0.05f) (1400 - 900 * d).toLong() else 400)
                }
            }.apply {
                isDaemon = true
                name = "heartbeat"
                start()
            }
    }

    fun play(cue: Cue) {
        if (!on) return
        val clip = clips[cue] ?: return
        runCatching {
            clip.stop()
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
            // Lub-dub: two low thumps.
            Cue.HEART ->
                wave(0.45) { t, _ ->
                    val lub = if (t < 0.14) sin(2 * PI * 52 * t) * exp(-t * 28) else 0.0
                    val dub = if (t > 0.2 && t < 0.32) sin(2 * PI * 46 * (t - 0.2)) * exp(-(t - 0.2) * 32) * 0.8 else 0.0
                    (lub + dub) * 0.9
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
        if (!before.afterMidnight && after.afterMidnight) add(Cue.BELL)
    }

/** How close the awake Hollow is to the nearest of the crew, from 0 to 1, for the heartbeat. */
fun danger(night: Night, maze: Maze): Float {
    val hollow = night.hollow?.takeIf { it.awake } ?: return 0f
    val steps = maze.distances(hollow.at)
    val nearest = night.lost.mapNotNull { steps[it.at] }.minOrNull() ?: return 0f
    return ((7 - nearest) / 6f).coerceIn(0f, 1f)
}
