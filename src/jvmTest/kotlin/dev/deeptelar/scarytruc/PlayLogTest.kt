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

    @Test
    fun eachCallThatEndsIsKeptForTheStatisticsAcrossVisits() {
        val store = Memory()
        val log = PlayLog(store)
        val maze = Plan.CAVE.maze()
        val call = Call.begin(Plan.CAVE, maze, Haunt(emptyMap(), emptyMap(), emptyMap()))
        log.call(call, null)
        val scared = call.copy(turn = 1, panic = 90, ran = true)
        log.turn(call, scared, maze, 0, 0, null)
        log.turn(scared, scared.copy(turn = 2, panic = 70, ran = false, end = End.TAKEN, death = "the Hollow", away = 4), maze, 0, 0, null)
        log.call(call, null)
        log.hungUp(call.hungUp(maze))
        val again = PlayLog(store).plays
        assertEquals(listOf(End.TAKEN, End.ALONE), again.map { it.end })
        val taken = again.first()
        assertEquals(4, taken.away)
        assertEquals(90, taken.worst)
        assertEquals(1, taken.bolts)
        assertTrue(taken.panicked, "the Hollow heard their fear")
        assertFalse(again.last().panicked)
    }

    @Test
    fun theCallsInAnOldLogAreReadIntoTheStatistics() {
        val old =
            """
            === Call 2026-10-01T20:00:00Z The Cave | Ana | SCOUT | en | model none | seed 1
            20:00:05 YOU after 3.0s: left
              turn 1: at 1,1 facing north, walked 2, battery 99, panic 88, trust 50, walks with nothing new 0, bolted
              END DOOM (a pit with no bottom), 5 steps from the way out, after 1 turns
            === Call 2026-10-01T20:10:00Z The Cave | Ana | SCOUT | en | model none | seed 2
              turn 1: at 1,1 facing north, walked 1, battery 99, panic 30, trust 50, walks with nothing new 0
            20:11:00 * hung up, 7 steps from the way out, after 1 turns
            === Call 2026-10-01T20:20:00Z The Cave | Ana | SCOUT | en | model none | seed 3
              turn 9: at 1,1 facing north, walked 1, battery 80, panic 10, trust 60, walks with nothing new 0
              END OUT, 0 steps from the way out, after 9 turns
            """.trimIndent()
        val store = Memory()
        store.values["log"] = old
        val plays = PlayLog(store).plays
        assertEquals(listOf(End.DOOM, End.ALONE, End.OUT), plays.map { it.end })
        assertTrue(plays[0].panicked, "they ran into it")
        assertEquals("a pit with no bottom", plays[0].death)
        assertEquals(listOf(5, 7, 0), plays.map { it.away })
        assertTrue(plays.all { it.plan == Plan.CAVE })
        assertTrue("plays" in store.values, "read once, then kept")
    }
}
