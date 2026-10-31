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

class NudgeTest {
    private val plan = Plan.CUBE
    private val maze = plan.maze()
    private val empty = Haunt(emptyMap(), emptyMap(), emptyMap())
    private val fork = maze.cells.first { maze.exits(it).size >= 3 }

    private fun call(lang: Lang, facing: Dir = Dir.NORTH) = Call.begin(plan, maze, empty, lang = lang).copy(at = fork, facing = facing, hollow = null)

    @Test
    fun theCallerNamesOnlyTheWaysThatAreOpen() {
        for (lang in Lang.entries) {
            for (facing in Dir.entries) {
                val here = call(lang, facing)
                assertEquals(maze.exits(fork).size, here.ways(maze).size)
                val words = here.nudge(maze).lowercase()
                for (way in here.ways(maze)) assertTrue(lang.t(way.word, listOf("delante", "derecha", "atrás", "izquierda")[way.turn!!]) in words, words)
            }
        }
    }

    @Test
    fun aThingUsedMeansWhatItSaysInBothLanguages() = runBlocking {
        for (lang in Lang.entries) {
            for (item in Item.entries) {
                val heard = Keywords.listen(call(lang).copy(said = item.use(lang)))
                assertEquals(Order.USE, heard.order, item.use(lang))
                assertEquals(item, heard.item, item.use(lang))
            }
            for (way in listOf(Order.AHEAD, Order.LEFT, Order.RIGHT, Order.BACK)) {
                assertEquals(way, Keywords.listen(call(lang).copy(said = Item.STONE.use(lang, way))).toward)
            }
        }
    }

    @Test
    fun theCallerBreaksTheSilenceOnceAndStaysOnTheRecord() = runBlocking {
        Sound.on = false
        val model = CallModel(CoroutineScope(Dispatchers.Default), Minds.none, plan = plan, seed = 1)
        model.answer()
        while (model.thinking) delay(10)
        val since = model.spokeAt + model.speaking()
        model.hurry(since + 1000)
        assertNull(model.nudge, "not yet")
        val before = model.call
        model.hurry(since + (CallModel.SILENCE * CallModel.NUDGE_AT).toLong() + 1000)
        val nudge = assertNotNull(model.nudge)
        assertEquals(before, model.call, "it moves nothing")
        model.type("left")
        model.send()
        while (model.thinking) delay(10)
        assertNull(model.nudge)
        assertTrue(model.call.lines.any { it.who == Who.CALLER && it.text == nudge })
        assertFalse(model.call.lines.last().text == nudge)
    }
}
