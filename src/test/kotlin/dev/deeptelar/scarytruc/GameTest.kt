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
    fun aNightPausesWithTheCrewsIntentsAndEndsByDawn() =
        runTest {
            for (plan in Plan.entries) {
                for (seed in 1L..10L) {
                    val run = NightRun(plan, plan.seed ?: seed, Instinct)
                    val first = run.begin()
                    assertEquals(0, first.turn)
                    assertEquals(plan.crew.size, first.moves.size, "the crew decided before the pause")
                    var turns = 0
                    while (!run.night.over) {
                        run.next()
                        turns++
                    }
                    assertTrue(turns <= plan.dawn)
                    assertEquals(turns, run.past.size, "a pause for every turn but the last")
                }
            }
        }

    @Test
    fun theTreatOrTrickOfATurnReachesTheWalk() =
        runTest {
            val run = NightRun(Plan.FIRST, 2, Instinct)
            run.begin()
            val cell = run.night.seen.first { it != run.maze.start && it != run.maze.gate }
            assertTrue(run.place(cell, MarkKind.TREAT))
            assertFalse(run.place(run.night.seen.last { it != cell && it != run.maze.start }, MarkKind.TREAT), "one a turn")
            assertTrue(run.takeBack(cell))
            assertTrue(run.place(cell, MarkKind.TRICK))
            val after = run.next()
            assertEquals(Plan.FIRST.candy - 2, after.candy)
            assertFalse(after.played)
            assertFalse(run.takeBack(cell), "a mark of an earlier turn stays")
        }

    @Test
    fun aMarigoldTakesTheNightBackToTheSameDecisions() =
        runTest {
            val run = NightRun(Plan.FIFTH, 4197, Instinct)
            run.begin()
            repeat(6) { run.next() }
            val atSix = run.night
            val two = run.past.first { it.state.turn == 2 }.state
            val back = run.remember(2)
            assertEquals(2, back.turn)
            assertEquals(two.moves, back.moves, "paused before the walk, with the crew's decisions of then")
            assertEquals(0, back.marigolds)
            assertEquals(3, run.past.size)
            repeat(4) { run.next() }
            // Instinct decides the same on the same turn, so the same crew walks the same way.
            assertEquals(atSix.crew, run.night.crew)
            val now = run.night
            assertEquals(now, run.remember(1), "no marigold left")
        }

    @Test
    fun intentsArriveOneByOneWhileTheCrewDecides() =
        runTest {
            val seen = mutableListOf<Night>()
            val run = NightRun(Plan.FOURTH, 3298, Instinct) { seen += it }
            run.begin()
            assertEquals((1..4).toList(), seen.map { it.moves.size }.filter { it > 0 }.take(4))
        }

    @Test
    fun gemmaPlaysAnAgentAndItsWallsAndFailuresFallBackToInstinct() =
        runTest {
            val maze = Plan.FIRST.maze()
            val night = Night.begin(Plan.FIRST, maze)
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
            assertEquals(Move(agent.role, open, "Fresh air, I think.", gemma = true), gemma.decide(night, maze, agent))
            assertNull(trouble)
            assertFalse(gemma.decide(night, maze, agent).gemma)
            assertFalse(gemma.decide(night, maze, agent).gemma)
            assertNotNull(trouble)
            val system = model.requests.first().system!!
            assertTrue(system.contains(agent.role.nature))
            assertFalse(system.contains("Hollow"), "the first night has no Hollow")
        }
}
