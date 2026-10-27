package dev.deeptelar.scarytruc

import dev.deeptelar.telar.test.ScriptedChatModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {
    @Test
    fun aNightPausesBeforeEveryTurnAndEndsByDawn() =
        runTest {
            for (seed in 1L..30L) {
                val run = NightRun(Maze.of(seed), Instinct)
                assertEquals(0, run.begin().turn)
                var turns = 0
                while (!run.night.over) {
                    run.next()
                    turns++
                }
                assertTrue(turns <= Night.DAWN)
                assertTrue(run.night.crew.none { it.fate == Fate.LOST } || run.night.turn == Night.DAWN, "seed $seed")
                assertEquals(turns, run.past.size, "a pause for every turn that was not the last")
            }
        }

    @Test
    fun marksPlacedBetweenTurnsReachTheNight() =
        runTest {
            val maze = Maze.of(5)
            val run = NightRun(maze, Instinct)
            run.begin()
            val cell = maze.cells.first { it != maze.start && it != maze.lair && it != maze.gate }
            assertTrue(run.place(cell, MarkKind.TREAT))
            assertTrue(run.takeBack(cell))
            assertTrue(run.place(cell, MarkKind.TREAT))
            val after = run.next()
            assertEquals(Night.TREATS - 1, after.treats)
            assertFalse(run.takeBack(cell), "a mark of an earlier turn stays")
        }

    @Test
    fun aMarigoldTakesTheNightBackAndReplaysTheSameWay() =
        runTest {
            val maze = Maze.of(9)
            val run = NightRun(maze, Instinct)
            run.begin()
            repeat(6) { run.next() }
            val atSix = run.night
            val back = run.remember(2)
            assertEquals(2, back.turn)
            assertEquals(Night.MARIGOLDS - 1, back.marigolds)
            assertEquals(3, run.past.size)
            repeat(4) { run.next() }
            // Instinct decides the same on the same turn, so the same crew walks the same way.
            assertEquals(atSix.crew, run.night.crew)
            assertEquals(Night.MARIGOLDS - 1, run.night.marigolds)
        }

    @Test
    fun gemmaPlaysAnAgentAndItsWallsAndFailuresFallBackToInstinct() =
        runTest {
            val maze = Maze.of(4)
            val night = Night.begin(maze)
            val agent = night.crew.first()
            val open = maze.exits(maze.start).first()
            val wall = Dir.entries.first { it !in maze.exits(maze.start) }
            var trouble: String? = "none yet"
            val model =
                ScriptedChatModel {
                    says("""{"go": "${open.word}", "says": "Fresh air, I think."}""")
                    says("""{"go": "${wall.word}", "says": "Through the wall!"}""")
                    fails("Ollama is not running")
                }
            val gemma = Gemma(model) { trouble = it }
            val first = gemma.decide(night, maze, agent)
            assertEquals(Move(agent.role, open, "Fresh air, I think.", gemma = true), first)
            assertNull(trouble)
            assertFalse(gemma.decide(night, maze, agent).gemma)
            assertFalse(gemma.decide(night, maze, agent).gemma)
            assertNotNull(trouble)
            val prompt = model.requests.first()
            assertTrue(prompt.system!!.contains(agent.role.nature))
        }
}
