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
            val cell = run.night.seen.first { it !in run.maze.starts && it != run.maze.gate }
            assertTrue(run.place(cell, MarkKind.TREAT))
            assertFalse(run.place(run.night.seen.last { it != cell && it !in run.maze.starts }, MarkKind.TREAT), "one a turn")
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
            val run = NightRun(Plan.FIFTH, 4248, Instinct)
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
            val run = NightRun(Plan.FOURTH, 3292, Instinct) { seen += it }
            run.begin()
            assertEquals((1..4).toList(), seen.map { it.moves.size }.filter { it > 0 }.take(4))
        }

    @Test
    fun aTrickMakesThoseWhoHearItDecideAgainWithGemma() =
        runTest {
            val model =
                ScriptedChatModel {
                    says("""{"go": "stay", "says": "Waiting."}""")
                    says("""{"go": "stay", "says": "What was THAT?"}""")
                    says("""{"go": "stay", "says": "Still here."}""")
                }
            val run = NightRun(Plan.FIRST, 2, Gemma(model))
            run.begin()
            val scout = run.night.crew.single()
            val cell = run.night.seen.first { it != scout.at }
            assertTrue(run.place(cell, MarkKind.TRICK))
            val after = run.rethink(cell)
            assertEquals("What was THAT?", after.moves.single().says)
            assertTrue(model.requests.last().messages.last().toString().lowercase().contains("scream"))
            assertEquals("What was THAT?", run.next().crew.single().words, "the walk uses the new decision")
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

class WhisperTest {
    private val plan = Plan.FIFTH
    private val maze = plan.maze()

    private fun nearHollow(awake: Boolean): Night {
        val next = maze.exits(maze.lair).map { maze.lair + it }.first { maze.doomAt(it) == null }
        val far = maze.cells.maxBy { maze.distances(maze.lair)[it] ?: 0 }
        return Night.begin(plan, maze).copy(
            crew = listOf(Agent(Role.CODER, maze.start.takeIf { it != next } ?: far), Agent(Role.CRITIC, next)),
            hollow = Hollow(maze.lair, awake = awake),
            turn = 5,
        )
    }

    @Test
    fun theAwakeHollowWhispersToTheNearestNowAndThen() {
        assertNull(nearHollow(awake = false).whisperTarget(maze))
        val night = nearHollow(awake = true)
        assertEquals(Role.CRITIC, night.whisperTarget(maze)?.role)
        val heard = night.heard(Role.CRITIC, "Who reviews the reviewer?")
        assertEquals(Role.CRITIC.nerve - WHISPER_HURTS, heard.crew.first { it.role == Role.CRITIC }.nerve)
        assertNull(heard.copy(turn = 6).whisperTarget(maze), "quiet for a while after")
        assertEquals(Role.CRITIC, heard.copy(turn = 5 + WHISPER_EVERY).whisperTarget(maze)?.role)
        val critic = heard.crew.first { it.role == Role.CRITIC }
        assertTrue("Who reviews the reviewer?" in describe(senses(heard, maze, critic), heard, critic))
    }

    @Test
    fun gemmaWritesTheWhisperFromTheFearAndTheEchoesSpeakWhenItCannot() =
        runTest {
            val night = nearHollow(awake = true)
            val critic = night.crew.first { it.role == Role.CRITIC }
            val model =
                ScriptedChatModel {
                    says("\"Your red ink still bleeds, reviewer.\"")
                    fails("Ollama is not running")
                }
            val whispers = GemmaWhispers(model)
            assertEquals("Your red ink still bleeds, reviewer.", whispers.whisper(night, critic))
            assertTrue(model.requests.first().messages.first().toString().contains(Role.CRITIC.fear))
            assertTrue(whispers.whisper(night, critic).isNotBlank())
        }

    @Test
    fun theWhisperNodeRunsBetweenTheWalkAndTheNextDecision() =
        runTest {
            val run = NightRun(Plan.FIFTH, 4158, Instinct, Whisperer { _, agent -> "I see you, ${agent.role.title}." })
            run.begin()
            while (!run.night.over && run.night.whisper == null) run.next()
            val whisper = assertNotNull(run.night.whisper)
            assertEquals("I see you, ${whisper.role.title}.", whisper.text)
            assertTrue(run.night.log.any { "whispers" in it })
        }

    @Test
    fun midnightSoundsTheSirenAndAWhisperIsHeard() {
        val night = Night.begin(plan, maze)
        val cues = cues(night.copy(turn = night.midnight - 1), night.copy(turn = night.midnight, whisper = Whisper(Role.SCOUT, "Alone.", night.midnight)), maze)
        assertTrue(Cue.SIREN in cues && Cue.BELL in cues && Cue.WHISPER in cues)
    }
}
