package dev.deeptelar.scarytruc

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl

/** The sounds on the desktop, through Java Sound: a clip per cue and per drone, made at the start. */
class JavaSound : SoundOutput {
    private val format = AudioFormat(Sound.RATE, 16, 1, true, false)
    /** A clip per cue, made when it is first played: the keys are read from a file after the start. */
    private val clips = mutableMapOf<Cue, Clip>()
    /** A loop per drone, with the samples it was made of: made again when they change. */
    private val drones = mutableMapOf<Drone, Pair<FloatArray, Clip>>()

    @Synchronized
    override fun play(cue: Cue, gain: Float) {
        val clip = clips[cue] ?: runCatching { clip(Sound.samples(cue), loop = false) }.getOrNull()?.also { clips[cue] = it } ?: return
        clip.stop()
        (clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl)?.let { it.value = gain.coerceIn(it.minimum, it.maximum) }
        clip.framePosition = 0
        clip.start()
    }

    @Synchronized
    override fun stop(cue: Cue) {
        clips[cue]?.stop()
    }

    @Synchronized
    override fun drone(drone: Drone?) {
        val playing =
            drone?.let {
                val samples = Sound.samples(it)
                drones[it]?.takeIf { made -> made.first === samples }?.second
                    ?: runCatching { clip(samples, loop = true, gain = Sound.DRONE + it.gain) }.getOrNull()?.also { clip -> drones.put(it, samples to clip)?.second?.close() }
            }
        for ((_, clip) in drones.values) {
            if (clip == playing && !clip.isRunning) clip.loop(Clip.LOOP_CONTINUOUSLY)
            if (clip != playing && clip.isRunning) clip.stop()
        }
    }

    private fun clip(samples: FloatArray, loop: Boolean, gain: Float = 0f): Clip {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            bytes[2 * i] = (v and 0xFF).toByte()
            bytes[2 * i + 1] = (v shr 8).toByte()
        }
        return AudioSystem.getClip().apply {
            open(format, bytes, 0, bytes.size)
            if (loop) (getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl)?.let { it.value = gain.coerceIn(it.minimum, it.maximum) }
        }
    }
}
