package dev.deeptelar.scarytruc

import dev.deeptelar.telar.agent.ChatMessage
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.ChatResponse
import dev.deeptelar.telar.agent.TokenUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayLogTest {
    private class Memory : Store {
        val values = mutableMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun set(key: String, value: String) {
            values[key] = value
        }
    }

    @Test
    fun aFaultComesOnceWithItsStackAndNeverWithTheKey() {
        val log = PlayLog(Memory())
        log.secret = "sk-secret"
        val failure = IllegalStateException("401 for sk-secret")
        repeat(3) { log.error("turn 1", failure) }
        val text = log.exported()
        assertEquals(1, Regex("! turn 1").findAll(text).count())
        assertTrue("IllegalStateException: 401 for ***" in text)
        assertTrue("PlayLogTest" in text, "the stack")
        assertFalse("sk-secret" in text)
    }

    @Test
    fun aBrokenFrameDoesNotFillTheLog() {
        val log = PlayLog(Memory())
        repeat(500) { log.fault("page", "fault $it") }
        assertEquals(50, Regex("! page").findAll(log.exported()).count())
        assertEquals(1, Regex("! more faults").findAll(log.exported()).count())
    }

    @Test
    fun aCrashTheGameCouldNotLogIsFoundOnTheNextVisit() {
        val store = Memory()
        store.values["crash"] = "could not load scaryTruc.js\n"
        val log = PlayLog(store)
        log.recover()
        assertTrue("could not load scaryTruc.js" in log.exported())
        assertEquals("", store.values["crash"])
    }

    @Test
    fun aTurnThatThrowsIsLogged() = runBlocking {
        val minds = Minds("broken", { Ears { throw AssertionError("it broke") } }, { Script }, { Echoes })
        val model = CallModel(CoroutineScope(Dispatchers.Default), minds, plan = Plan.CAVE, seed = 1, clock = false, store = Memory())
        model.answer()
        while (model.thinking) delay(10)
        model.type("hide")
        model.send()
        while (model.thinking) delay(10)
        assertTrue("! turn" in model.log.exported() && "AssertionError: it broke" in model.log.exported())
    }

    @Test
    fun everyCallToTheModelIsTraced() = runBlocking {
        val chat = ChatModel { ChatResponse(ChatMessage.Assistant("I hear it."), TokenUsage(120, 7)) }
        val model = CallModel(CoroutineScope(Dispatchers.Default), Minds.of(chat, "gemma"), plan = Plan.CAVE, seed = 1, clock = false, store = Memory())
        model.answer()
        while (model.thinking) delay(10)
        assertTrue(Regex("~ voice \\d+ ms, 10 letters, 120 tokens in, 7 out").containsMatchIn(model.log.exported()), model.log.exported())

        val down = ChatModel { throw IllegalStateException("no signal") }
        val failing = CallModel(CoroutineScope(Dispatchers.Default), Minds.of(down, "gemma"), plan = Plan.CAVE, seed = 1, clock = false, store = Memory())
        failing.answer()
        while (failing.thinking) delay(10)
        assertTrue(Regex("~ voice failed after \\d+ ms: no signal").containsMatchIn(failing.log.exported()), failing.log.exported())
    }

    @Test
    fun aModelThatDoesNotSpeakIsInTheLog() = runBlocking {
        val minds = Minds("broken", { Keywords }, { trouble -> Voice { trouble("the caller: it broke"); Script.speak(it) } }, { Echoes })
        val model = CallModel(CoroutineScope(Dispatchers.Default), minds, plan = Plan.CAVE, seed = 1, clock = false, store = Memory())
        model.answer()
        while (model.thinking) delay(10)
        assertTrue("trouble: the caller: it broke" in model.log.exported())
    }

    @Test
    fun aCallIsLoggedTurnByTurnAndKeptBetweenVisits() = runBlocking {
        val store = Memory()
        val model = CallModel(CoroutineScope(Dispatchers.Default), Minds.none, plan = Plan.CAVE, seed = 1, clock = false, store = store)
        model.answer()
        while (model.thinking) delay(10)
        model.type("hide")
        model.send()
        delay(20)
        while (model.thinking) delay(10)
        model.toggleMute()
        model.hangUp()

        val log = model.log.exported()
        assertTrue("=== Call" in log && "The Cave" in log && "model none" in log, log)
        assertTrue("YOU after" in log && ": hide\n" in log, log)
        assertTrue("understood: hide" in log, log)
        assertTrue("CALLER after" in log, log)
        assertTrue("battery" in log && "panic" in log && "trust" in log, log)
        assertTrue("* sound off" in log, log)
        assertTrue("* hung up" in log, log)
        assertEquals(1, model.log.calls)

        // Another visit to the page finds it.
        val again = PlayLog(store)
        assertEquals(1, again.calls)
        assertTrue(": hide\n" in again.exported())
        Sound.on = true
    }

    @Test
    fun aKeyNeverGoesInTheLog() = runBlocking {
        val model = CallModel(CoroutineScope(Dispatchers.Default), Minds.none, plan = Plan.CAVE, seed = 1, clock = false, store = Memory())
        model.connect(Server("http://localhost:1/v1", "some-model", "SECRET-KEY"))
        val log = model.log.exported()
        assertTrue("some-model" in log, log)
        assertFalse("SECRET-KEY" in log, log)
    }

    @Test
    fun theOldestCallsGoFirstWhenItGrowsTooLong() {
        val store = Memory()
        val log = PlayLog(store)
        val call = Call.begin(Plan.CAVE, Plan.CAVE.maze(), Haunt(emptyMap(), emptyMap(), emptyMap()))
        repeat(40) { i ->
            log.call(call, "call-$i-")
            repeat(100) { log.note("x".repeat(100)) }
        }
        assertTrue(store.values.getValue("log").length <= 200_000)
        assertTrue("call-39-" in log.exported())
        assertFalse("call-0-" in log.exported())
        assertEquals(store.values.getValue("log").split("=== Call").size - 1, log.calls)
    }
}
