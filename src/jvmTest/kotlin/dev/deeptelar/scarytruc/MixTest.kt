package dev.deeptelar.scarytruc

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MixTest {
    private fun Mix.next(size: Int) = FloatArray(size).also(::read).toList()

    @Test
    fun soundsAddUpAndEnd() {
        val mix = Mix()
        mix.play(Cue.RING, floatArrayOf(0.1f, 0.1f), 0f)
        mix.play(Cue.MESSAGE, floatArrayOf(0.2f, 0.2f, 0.2f), 0f)
        assertContentEquals(listOf(0.3f, 0.3f, 0.2f, 0f), mix.next(4).map { (it * 1000).toInt() / 1000f + 0f })
        assertEquals(listOf(0f, 0f), mix.next(2))
    }

    @Test
    fun aCueThatEndedCanBePlayedAgainAndAgain() {
        val mix = Mix()
        repeat(3) {
            mix.play(Cue.STEP_1, floatArrayOf(0.5f), 0f)
            assertEquals(listOf(0.5f, 0f), mix.next(2))
        }
    }

    @Test
    fun playedAgainItStartsAgainAndStopCutsItShort() {
        val mix = Mix()
        mix.play(Cue.RING, floatArrayOf(0.1f, 0.2f, 0.3f), 0f)
        mix.next(2)
        mix.play(Cue.RING, floatArrayOf(0.1f, 0.2f, 0.3f), 0f)
        assertEquals(listOf(0.1f), mix.next(1))
        mix.stop(Cue.RING)
        assertEquals(listOf(0f), mix.next(1))
    }

    @Test
    fun twentyDecibelsDownIsATenth() {
        val mix = Mix()
        mix.play(Cue.RING, floatArrayOf(1f), -20f)
        assertEquals(0.1f, mix.next(1).single(), 0.001f)
    }

    @Test
    fun whatIsTooLoudIsClippedNotWrapped() {
        val mix = Mix()
        mix.play(Cue.RING, floatArrayOf(0.8f), 0f)
        mix.play(Cue.MESSAGE, floatArrayOf(0.8f), 0f)
        assertEquals(listOf(1f), mix.next(1))
    }

    @Test
    fun aDroneLoopsUnderTheCuesAndComesBackWhereItLeftOff() {
        val mix = Mix()
        val drone = floatArrayOf(0.1f, 0.2f, 0.3f)
        mix.loop(Drone.MINE, drone, 0f)
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.1f), mix.next(4))
        mix.loop(Drone.MINE, drone, 0f)
        assertEquals(listOf(0.2f), mix.next(1), "the same drone goes on")
        mix.loop(null, null, 0f)
        assertEquals(listOf(0f), mix.next(1))
        mix.loop(Drone.MINE, drone, 0f)
        assertEquals(listOf(0.3f), mix.next(1))
    }

    @Test
    fun anotherRecordingOfTheDroneTakesOver() {
        val mix = Mix()
        mix.loop(Drone.MINE, floatArrayOf(0.1f), 0f)
        mix.loop(Drone.MINE, floatArrayOf(0.9f), 0f)
        assertEquals(listOf(0.9f), mix.next(1))
    }
}
