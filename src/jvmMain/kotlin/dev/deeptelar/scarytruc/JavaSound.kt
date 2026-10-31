package dev.deeptelar.scarytruc

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread
import kotlin.math.pow

/**
 * What is sounding at a moment, added up here: a Java Sound clip for each sound came out silent for
 * 24 of the 45 on a Linux desktop, with no error. A cue played again starts again; one drone loops
 * under them, and comes back where it left off.
 */
internal class Mix {
    private class Voice(val samples: FloatArray, val volume: Float, val loop: Boolean, var at: Int = 0)

    private val cues = mutableMapOf<Cue, Voice>()
    private var loop: Pair<Drone, Voice>? = null
    private val stopped = mutableMapOf<Drone, Int>()

    @Synchronized
    fun play(cue: Cue, samples: FloatArray, gain: Float) {
        cues[cue] = Voice(samples, volume(gain), loop = false)
    }

    @Synchronized
    fun stop(cue: Cue) {
        cues.remove(cue)
    }

    /** Loops [drone], made of [samples], or nothing when it is `null`. */
    @Synchronized
    fun loop(drone: Drone?, samples: FloatArray?, gain: Float) {
        val playing = loop
        if (playing?.first == drone && playing?.second?.samples === samples) return
        playing?.let { (old, voice) -> stopped[old] = voice.at }
        loop =
            if (drone == null || samples == null) {
                null
            } else {
                drone to Voice(samples, volume(gain), loop = true, at = (stopped[drone] ?: 0).takeIf { it < samples.size } ?: 0)
            }
    }

    /** Fills [out] with the next samples of what is sounding, from -1 to 1. */
    @Synchronized
    fun read(out: FloatArray) {
        out.fill(0f)
        for (voice in cues.values + listOfNotNull(loop?.second)) {
            for (i in out.indices) {
                if (voice.at >= voice.samples.size) {
                    if (!voice.loop || voice.samples.isEmpty()) break
                    voice.at = 0
                }
                out[i] += voice.samples[voice.at++] * voice.volume
            }
        }
        cues.values.removeAll { it.at >= it.samples.size }
        for (i in out.indices) out[i] = out[i].coerceIn(-1f, 1f)
    }

    private fun volume(gain: Float): Float = 10f.pow(gain / 20f)
}

/** The sounds on the desktop, through Java Sound: one line, and everything mixed by [Mix]. */
class JavaSound : SoundOutput {
    private val mix = Mix()

    private val line by lazy {
        thread(isDaemon = true, name = "sound") {
            val format = AudioFormat(Sound.RATE, 16, 1, true, false)
            val line = AudioSystem.getSourceDataLine(format)
            line.open(format, BUFFER * 2 * 4)
            line.start()
            val samples = FloatArray(BUFFER)
            val bytes = ByteArray(BUFFER * 2)
            while (true) {
                mix.read(samples)
                for (i in samples.indices) {
                    val value = (samples[i] * 32767).toInt()
                    bytes[2 * i] = (value and 0xFF).toByte()
                    bytes[2 * i + 1] = (value shr 8).toByte()
                }
                // It waits here until the sound card has room: this is what paces the mix.
                line.write(bytes, 0, bytes.size)
            }
        }
    }

    override fun play(cue: Cue, gain: Float) {
        line
        mix.play(cue, Sound.samples(cue), gain)
    }

    override fun stop(cue: Cue) = mix.stop(cue)

    override fun drone(drone: Drone?) {
        line
        mix.loop(drone, drone?.let(Sound::samples), Sound.DRONE + (drone?.gain ?: 0f))
    }

    private companion object {
        /** The samples mixed at a time: a twentieth of a second of sound goes by before a change is heard. */
        const val BUFFER = 256
    }
}
