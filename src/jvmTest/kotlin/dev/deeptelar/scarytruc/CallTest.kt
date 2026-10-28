package dev.deeptelar.scarytruc

import dev.deeptelar.telar.test.ScriptedChatModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallTest {
    private val plan = Plan.THIRD
    private val maze = plan.maze()
    private val haunt = plan.haunt(maze)
    private val empty = Haunt(emptyMap(), emptyMap(), emptyMap())

    /** A quiet call at [at], facing [facing], with nothing in the maze but the dooms. */
    private fun quiet(at: Pos, facing: Dir, order: Order, calm: Int = 0) =
        Call.begin(plan, maze, empty).copy(at = at, facing = facing, hollow = null, said = "go", heard = Heard(order, calm), facts = emptyList())

    /** A corridor cell, its two ways, and no doom, gate or start next to it. */
    private val corridor: Pair<Pos, Dir> =
        maze.cells.firstNotNullOf { here ->
            val exits = maze.exits(here)
            val safe = (exits.map { here + it } + here).all { maze.doomAt(it) == null && it != maze.gate && it != maze.start }
            if (exits.size == 2 && safe) here to exits.first() else null
        }

    @Test
    fun theStrangersWordsAreReadForTheFirstWayAndTheTone() = runBlocking {
        suspend fun said(text: String) = Keywords.listen(Call.begin(plan, maze, empty).copy(said = text))
        assertEquals(Heard(Order.LEFT, 0), said("Go left, then right"))
        assertEquals(Order.AHEAD, said("keep going straight").order)
        assertEquals(Order.HIDE, said("Shh. Hide.").order)
        assertEquals(Order.NONE, said("Who are you?").order)
        assertEquals(2, said("Breathe. Slow. I'm here with you, okay?").calm)
        assertTrue(said("GO LEFT NOW HURRY").calm < 0)
        assertEquals(Order.NONE, said("I'm leftover").order, "a whole word only")
    }

    @Test
    fun aWayIsFromWhereTheCallerFaces() {
        val (here, dir) = corridor
        val call = quiet(here, dir.turned(1), Order.LEFT).walk(maze, empty)
        assertTrue(call.walked >= 1)
        assertTrue(call.facts.first().startsWith("I went left"), call.facts.first())
    }

    @Test
    fun aWallCostsTrustAndNobodyMoves() {
        val (here, _) = corridor
        val wall = Dir.entries.first { !maze.canGo(here, it) }
        val call = quiet(here, wall, Order.AHEAD).walk(maze, empty)
        assertEquals(here, call.at)
        assertTrue(call.trust < plan.role!!.trust)
        assertEquals(0, call.walked)
    }

    @Test
    fun aWalkGoesOnAlongACorridorAndStopsWhereTheWaysSplit() {
        // From the start, follow the first way out until it stops.
        val call = quiet(maze.start, maze.exits(maze.start).first(), Order.AHEAD).walk(maze, empty)
        val exits = maze.exits(call.at)
        val ahead = exits.singleOrNull { it != call.facing.opposite }
        assertTrue(call.over || exits.size != 2 || ahead == null || maze.doomAt(call.at + ahead) != null || call.walked == 6, "stopped at ${call.at}")
        assertTrue(call.at in call.mapped)
    }

    @Test
    fun aDoomTakesWhoeverWalksInAndIsSeenFromNextToIt() {
        val doom = maze.dooms.first()
        val dir = maze.exits(doom.at).first()
        val next = quiet(doom.at + dir, dir.opposite, Order.STAY).walk(maze, empty)
        assertTrue(next.facts.any { doom.kind.sign in it }, next.facts.toString())
        assertEquals(Glimpse.STRANGE, next.glimpsed[doom.at])
        val gone = next.copy(heard = Heard(Order.AHEAD, 0), said = "go").walk(maze, empty)
        assertEquals(End.DOOM, gone.end)
        assertEquals(doom.kind.word, gone.death)
    }

    @Test
    fun theGateEndsTheCall() {
        val dir = maze.exits(maze.gate).first()
        val call = quiet(maze.gate + dir, dir.opposite, Order.AHEAD).walk(maze, empty)
        assertEquals(End.OUT, call.end)
    }

    @Test
    fun aMarkIsScratchedOnceAndRemembered() {
        val (here, dir) = corridor
        val marked = quiet(here, dir, Order.MARK).walk(maze, empty)
        assertEquals(listOf(Mark(here, 1)), marked.marks)
        val again = marked.copy(heard = Heard(Order.MARK, 0), said = "mark").walk(maze, empty)
        assertEquals(1, again.marks.size)
        assertTrue(again.facts.any { "number 1" in it })
    }

    @Test
    fun silenceScaresAndKindWordsCalm() {
        val (here, dir) = corridor
        val start = quiet(here, dir, Order.NONE)
        assertTrue(start.copy(said = "").walk(maze, empty).panic > start.panic)
        assertTrue(start.copy(heard = Heard(Order.NONE, 2)).walk(maze, empty).panic < start.panic)
    }

    @Test
    fun theHollowTakesWhomItMeetsUnlessTheyHideCalm() {
        val (here, dir) = corridor
        val awake = Hollow(here + dir, awake = true)
        val caught = quiet(here, dir, Order.STAY).copy(hollow = awake).walk(maze, empty)
        assertEquals(End.TAKEN, caught.end)
        val hidden = quiet(here, dir, Order.HIDE).copy(hollow = awake, panic = 20).walk(maze, empty)
        assertNull(hidden.end)
        assertTrue(hidden.panic > 20, "it was right there")
    }

    @Test
    fun aPanickedRunLosesTheNotebookUntilAKnownPlace() {
        val (here, dir) = corridor
        val panicked = (0..40).map { quiet(here, dir, Order.STAY).copy(panic = 100, turn = it, mapped = emptyMap()).walk(maze, empty) }
        val ran = panicked.first { it.lostTrack && !it.over }
        assertTrue(ran.mapped.isEmpty(), "nothing is drawn while lost")
        val known = ran.copy(mapped = mapOf(ran.at to emptySet()), marks = listOf(Mark(ran.at, 1)), heard = Heard(Order.MARK, 0), panic = 0)
        assertFalse(known.walk(maze, empty).lostTrack)
    }

    @Test
    fun theBatteryEndsTheCall() {
        val (here, dir) = corridor
        assertEquals(End.BATTERY, quiet(here, dir, Order.STAY).copy(battery = 1).walk(maze, empty).end)
    }

    @Test
    fun theNotesLieOnASafeWayOutAndTheFirstIsAtTheStart() {
        for (night in Plan.entries) {
            val maze = night.maze()
            val haunt = night.haunt(maze)
            assertNotNull(haunt.notes[maze.start], "${night.name}")
            assertEquals(night.notes.size + maze.dooms.map { it.kind }.distinct().size, haunt.notes.size, night.name)
            assertTrue(haunt.notes.keys.none { maze.doomAt(it) != null })
            for ((cell, dir) in haunt.arrows) assertTrue(maze.canGo(cell, dir))
        }
    }

    /** The way a perfect guide would go: along a safe way out, from where the caller faces. */
    private fun guide(call: Call, maze: Maze): String {
        val near = call.lines.lastOrNull { it.who == Who.CALLER }?.text.orEmpty()
        if ("I can hear it" in near && ("close" in near || "corner" in near)) return "Shh. Hide. Breathe slowly, I'm here with you."
        val deadly = maze.dooms.map { it.at }.toSet()
        val (dir, _) = maze.way(call.at, maze.gate, deadly) ?: return "wait"
        return "Breathe, you're doing well. Go " + listOf("ahead", "right", "back", "left")[call.facing.side(dir)]
    }

    @Test
    fun aKindGuideWhoKnowsTheWayGetsEveryCallerOut() = runBlocking {
        for (night in Plan.entries.filter { it.seed != null }) {
            val run = CallRun(night, night.seed!!, Keywords, Script)
            var call = run.begin()
            while (!call.over) call = run.say(guide(call, run.maze))
            if (call.end != End.OUT) call.lines.forEach { println("LINE ${it.who}: ${it.text}") }
            assertEquals(End.OUT, call.end, "${night.name}: ${call.death}, battery ${call.battery}, turn ${call.turn}")
            println("BALANCE ${night.name}: out on turn ${call.turn} with ${call.battery}% left")
        }
    }

    @Test
    fun silenceLetsTheCallDie() = runBlocking {
        val run = CallRun(Plan.FIRST, 11, Keywords, Script)
        var call = run.begin()
        while (!call.over) call = run.say("")
        assertTrue(call.end != End.OUT)
        assertEquals("Call ended", call.lines.last().text)
    }

    @Test
    fun gemmaHearsTheWordsAndVoicesTheFacts() = runBlocking {
        val model =
            ScriptedChatModel {
                says("Hello? Is someone there? I'm so cold.")
                answers(Understood("left", 2))
                says("Okay, okay. I went left.")
            }
        val run = CallRun(Plan.FIRST, 11, GemmaEars(model), GemmaVoice(model))
        run.begin()
        val call = run.say("Hey. Breathe. Go left, slowly.")
        assertEquals(listOf(Who.CALLER, Who.YOU, Who.CALLER), call.lines.map { it.who })
        assertEquals("Okay, okay. I went left.", call.lines.last().text)
        assertTrue(model.requests.last().messages.last().text.contains("Hey. Breathe."), "the voice hears the stranger")
    }
}
