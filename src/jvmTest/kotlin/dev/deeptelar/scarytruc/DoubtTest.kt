package dev.deeptelar.scarytruc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoubtTest {
    private val plan = Plan.CUBE
    private val maze = plan.maze()
    private val empty = Haunt(emptyMap(), emptyMap(), emptyMap())
    private val mazeOf = { _: Call -> maze }

    /** A corridor cell with nothing near it. */
    private val here = maze.cells.first { cell -> maze.exits(cell).size == 2 && (maze.exits(cell).map { cell + it } + cell).all { maze.doomAt(it) == null && it != maze.gate && it != maze.start } }
    private val way = maze.exits(here).first()

    /** A cell [steps] from here. */
    private fun away(steps: Int) = maze.distances(here).entries.first { it.value == steps }.key

    private fun call(heard: Heard, said: String = "go ahead", doubt: Doubt? = Doubt.NUMBER, hollow: Hollow? = null, trust: Int = 50) =
        Call.begin(plan, maze, empty).copy(at = here, facing = way, turn = 2, hollow = hollow, said = said, heard = heard, doubt = doubt, doubts = setOfNotNull(doubt), doubtAt = 2, trust = trust)

    @Test
    fun anOrderIsNoAnswerAndTheyWontMoveUntilYouGiveOne() {
        val dodged = call(Heard(Order.AHEAD, 0, convinced = -1)).act(mazeOf, empty)
        assertEquals(here, dodged.at, "they stay")
        assertEquals(Doubt.NUMBER, dodged.doubt, "and they keep asking")
        assertEquals(35, dodged.trust)
        assertTrue(dodged.facts.none { it.startsWith("Which way") }, dodged.facts.toString())
    }

    @Test
    fun anAnswerGivesTheCallerBackAndTheyDoWhatYouSaid() {
        val answered = call(Heard(Order.AHEAD, 0, convinced = 2)).act(mazeOf, empty)
        assertNotEquals(here, answered.at)
        assertNull(answered.doubt)
        assertEquals(62, answered.trust.coerceAtMost(62))
        assertEquals(listOf("go ahead"), answered.answers)
    }

    @Test
    fun anAnswerWithNoOrderStillLeavesThemToldWhereTheyAre() {
        val asking = call(Heard(Order.NONE, 0, convinced = 2), "I swear I don't know")
        val answered = asking.act(mazeOf, empty)
        assertEquals(here, answered.at)
        assertTrue(answered.facts.any { it.startsWith("Which way") }, answered.facts.toString())
    }

    @Test
    fun withItOnThemFearBeatsDoubtButItStillCosts() {
        val close = Hollow(away(2), awake = true)
        val dodged = call(Heard(Order.AHEAD, 0, convinced = -1), hollow = close).act(mazeOf, empty)
        assertNotEquals(here, dodged.at, "they run")
        assertEquals(35, dodged.trust)
        assertNull(dodged.doubt)
    }

    @Test
    fun silenceIsAnAnswerToo() {
        val left = call(Heard(Order.NONE, 0), said = "").act(mazeOf, empty)
        assertNull(left.doubt)
        assertTrue(left.trust < 25, "${left.trust}")
    }

    @Test
    fun whenThereIsNoTrustLeftTheyHangUp() {
        val gone = call(Heard(Order.AHEAD, 0, convinced = -2), trust = 20).act(mazeOf, empty)
        assertEquals(End.TRUST, gone.end)
        assertEquals(here, gone.at)
        assertNotNull(gone.away)
    }

    @Test
    fun theyDontHangUpOverAVagueAnswer() {
        val vague = call(Heard(Order.AHEAD, 0, convinced = 0), trust = 3).act(mazeOf, empty)
        assertEquals(null, vague.end)
    }

    @Test
    fun aDoubtComesWithinTheFirstTurnsAndNeverPilesUp() = runBlocking {
        for (seed in 1L..20L) {
            val run = CallRun(Plan.CAVE, seed, Keywords, Script)
            var call = run.begin()
            val asked = mutableListOf<Int>()
            while (!call.over && call.turn < 30) {
                if (call.doubt != null) asked += call.turn
                val turns = listOf("ahead", "right", "back", "left")
                call = run.say("I'm here with you. Go " + turns[call.facing.side(run.maze.exits(call.at).first())])
            }
            assertTrue(call.over || asked.isNotEmpty(), "seed $seed never doubted")
            if (call.turn >= 5) assertTrue(asked.isNotEmpty() && asked.first() <= 5, "seed $seed first doubt: $asked")
            assertTrue(asked.size <= 3, "$asked")
            assertTrue(asked.zipWithNext().all { (a, b) -> b - a >= 4 }, "$asked")
        }
    }

    @Test
    fun theHollowOnTheLineIsADoubt() {
        val heard = Call.begin(plan, maze, empty).copy(at = here, facing = way, turn = 5, hollow = null, lines = listOf(Line(Who.HOLLOW, "Keep talking."), Line(Who.CALLER, "Who is that?"), Line(Who.YOU, "go")), heard = Heard(Order.AHEAD, 0), said = "go")
        assertEquals(Doubt.VOICE, heard.act(mazeOf, empty).doubt)
    }

    @Test
    fun whileTheyWaitForAnAnswerTheyAskForNothingElse() {
        val waiting = call(Heard(Order.NONE, 0))
        assertTrue(waiting.nudge(maze).startsWith("Answer me"))
    }

    @Test
    fun anOrderAloneDoesntConvinceAndNeitherDoesAWord() = runBlocking {
        val asking = call(Heard(Order.NONE, 0))
        assertEquals(-1, Keywords.listen(asking.copy(said = "go left")).convinced)
        assertEquals(1, Keywords.listen(asking.copy(said = "I'm here with you, go left")).convinced)
        assertEquals(0, Keywords.listen(asking.copy(said = "I just picked up the phone")).convinced)
        assertEquals(0, Keywords.listen(asking.copy(said = "I'm here with you", doubt = null)).convinced)
    }
}
