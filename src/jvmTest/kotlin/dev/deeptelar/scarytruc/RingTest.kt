package dev.deeptelar.scarytruc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** The phone in a browser opens locked; once unlocked and past the guide it rings, and it was silent until now. */
class RingTest {
    @Test
    fun aLockedPhoneRingsOnceUnlocked() = runBlocking {
        val played = mutableListOf<Cue>()
        val output =
            object : SoundOutput {
                override fun play(cue: Cue, gain: Float) {
                    synchronized(played) { played += cue }
                }

                override fun stop(cue: Cue) = Unit

                override fun drone(drone: Drone?) = Unit
            }
        val scope = CoroutineScope(Dispatchers.Default)
        Sound.on = true
        Sound.output = output
        Sound.start(scope)
        val model = CallModel(scope, Minds.none, clock = false, locked = true)
        delay(1500)
        assertTrue(Cue.RING !in played, "locked, it doesn't ring")
        // Unlocked, the phone shows how to save them first, and holds its ring while it is read.
        model.reading = true
        model.unlock()
        delay(1000)
        assertTrue(Cue.RING !in played, "while the guide is read, it doesn't ring")
        model.reading = false
        delay(1000)
        Sound.output = null
        assertTrue(Cue.RING in played)
    }
}
