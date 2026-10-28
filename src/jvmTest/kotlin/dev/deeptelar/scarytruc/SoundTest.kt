package dev.deeptelar.scarytruc

import dev.deeptelar.scarytruc.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundTest {
    @Test
    fun everyCueIsAudibleAndNeverClips() {
        for (cue in Cue.entries) {
            val samples = Sound.samples(cue)
            assertTrue(samples.isNotEmpty(), "$cue")
            assertTrue(samples.maxOf { kotlin.math.abs(it) } in 0.05f..1f, "$cue peaks at ${samples.maxOf { kotlin.math.abs(it) }}")
        }
    }

    @Test
    fun theRecordingsHoldFourSoundsEach() = runBlocking {
        assertEquals(Sound.KEYS.size * 2646 * 2, Res.readBytes("files/keys.pcm").size)
        assertEquals(Sound.STEPS.size * 7717 * 2, Res.readBytes("files/steps.pcm").size)
        val scope = CoroutineScope(Dispatchers.Default)
        Sound.start(scope)
        withTimeout(5_000) { while (!Sound.ready(Cue.KEY_4) || !Sound.ready(Cue.STEP_4)) delay(10) }
        scope.cancel()
        assertEquals(7717, Sound.samples(Cue.STEP_2).size)
    }

    /** Writes every cue as a WAV, to listen to them: ./gradlew test --tests '*SoundTest*' -Pshots=DIR */
    @Test
    fun wavs() {
        val out = File(System.getProperty("shots") ?: return)
        val format = AudioFormat(22050f, 16, 1, true, false)
        for (cue in Cue.entries) {
            val samples = Sound.samples(cue)
            val bytes = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
                bytes[2 * i] = (v and 0xFF).toByte()
                bytes[2 * i + 1] = (v shr 8).toByte()
            }
            AudioSystem.write(AudioInputStream(ByteArrayInputStream(bytes), format, samples.size.toLong()), AudioFileFormat.Type.WAVE, File(out, "${cue.name.lowercase()}.wav"))
        }
        println("CLIP " + runCatching { AudioSystem.getClip().javaClass.simpleName }.getOrElse { "none: $it" })
    }
}
