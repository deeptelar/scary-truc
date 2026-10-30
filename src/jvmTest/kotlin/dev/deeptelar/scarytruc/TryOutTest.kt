package dev.deeptelar.scarytruc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TryOutTest {
    private class Memory : Store {
        val values = mutableMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun set(key: String, value: String) {
            values[key] = value
        }
    }

    /** A lock that keeps the text next to its word, so that these tests read what the game does around it. */
    private class Fake : Links {
        override suspend fun lock(text: String, word: String) = "$word|$text"

        override suspend fun unlock(code: String, word: String) = code.takeIf { it.startsWith("$word|") }?.removePrefix("$word|")

        override fun link(code: String) = "https://page/#try=$code"

        override fun copy(text: String) = Unit

        override fun send(link: String, message: String) = Unit
    }

    private val day = 24 * 60 * 60 * 1000L
    private val mine = Server("https://openrouter.ai/api/v1", "google/gemma-4-31b-it", "sk-or-mine")

    private fun game(store: Store, now: () -> Long) =
        CallModel(CoroutineScope(Dispatchers.Default), plan = Plan.CAVE, seed = 1, clock = false, store = store, locked = true, date = now).also { it.links = Fake() }

    private suspend fun link(word: String, days: Int, now: Long = 1000L): String {
        val friend = game(Memory()) { now }
        friend.connect(mine)
        friend.shareTryOut(word, days)
        while (friend.shared == null) delay(5)
        return friend.shared!!.substringAfter("#try=")
    }

    @Test
    fun aWordIsTheSameWordHoweverItIsTyped() {
        assertEquals(TryOut.secret("Bruja de  Blair "), TryOut.secret("bruja de blair"))
        assertEquals(TryOut.secret("Niño"), TryOut.secret("nino"))
    }

    @Test
    fun aLinkOpensWithItsWordAndNoOther() = runBlocking {
        val game = game(Memory()) { 2000L }
        game.tryCode = link("Hollow", days = 3)

        game.openTryOut("wrong")
        while (game.tryOpening) delay(5)
        assertNotNull(game.tryFailure)
        assertFalse(game.gemma)

        game.openTryOut(" hollow ")
        while (game.tryOpening) delay(5)
        assertNull(game.tryCode)
        assertEquals(mine, game.server)
        assertEquals(1000L + 3 * day, game.borrowedUntil)
        assertTrue(game.gemma)
        assertFalse("sk-or-mine" in game.log.exported())
    }

    @Test
    fun aLinkPastItsTimeDoesNotOpen() = runBlocking {
        val game = game(Memory()) { 1000L + 4 * day }
        game.tryCode = link("hollow", days = 3)
        game.openTryOut("hollow")
        while (game.tryOpening) delay(5)
        assertNotNull(game.tryFailure)
        assertNull(game.server)
    }

    @Test
    fun thePageForgetsTheKeyWhenTheTryOutEndsAndWhatWasThereComesBack() = runBlocking {
        var now = 2000L
        val store = Memory()
        val own = Server("http://localhost:11434/v1", "gemma4:12b")
        val game = game(store) { now }
        game.connect(own)
        game.tryCode = link("hollow", days = 1)
        game.openTryOut("hollow")
        while (game.tryOpening) delay(5)
        assertEquals(mine, game.server)

        now += 2 * day
        game.play(Plan.CAVE)
        assertEquals(own, game.server)
        assertNull(game.borrowedUntil)
        assertTrue(game.tryEnded)
        assertFalse(store.values.values.any { "sk-or-mine" in it }, "the key is gone from the store")
    }

    @Test
    fun aKeyThatEndedWhileThePageWasClosedIsGoneWhenItOpens() = runBlocking {
        val store = Memory()
        val first = game(store) { 2000L }
        first.tryCode = link("hollow", days = 1)
        first.openTryOut("hollow")
        while (first.tryOpening) delay(5)

        val later = game(store) { 2000L + 2 * day }
        assertNull(later.server)
        assertNull(later.saved())
        assertTrue(later.tryEnded)
    }
}
