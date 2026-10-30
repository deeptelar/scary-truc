package dev.deeptelar.scarytruc

import kotlin.test.Test
import kotlin.test.assertTrue

class StaticTest {
    private val line = "I can hear it behind me, there is a way to the left and a door ahead of me. ".repeat(20)
    private val frames = (0L until 20_000L step 50).toList()

    @Test
    fun nothingIsLostWhileTheHollowIsFar() {
        assertTrue(frames.all { static(line, 0.25f, it) == line })
    }

    @Test
    fun theWordsCanBeReadMostOfTheTimeWithTheHollowOnTop() {
        val clean = frames.count { static(line, 1f, it) == line }
        assertTrue(clean >= frames.size * 2 / 3, "clean in $clean of ${frames.size} frames")
    }

    @Test
    fun aBurstTakesFewLetters() {
        val lost = frames.map { now -> static(line, 1f, now).zip(line).count { (a, b) -> a != b } }
        assertTrue(lost.any { it > 0 }, "it still interferes")
        assertTrue(lost.max() <= line.count { it != ' ' } / 10, "at most a tenth: ${lost.max()} of ${line.length}")
    }
}
