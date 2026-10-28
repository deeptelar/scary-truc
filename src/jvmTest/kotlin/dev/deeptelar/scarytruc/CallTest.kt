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
    private val plan = Plan.CUBE
    private val maze = plan.maze()
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
        assertTrue(call.trust < plan.role.trust)
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
    fun everyCallIsDrawnSoThatItCanBeWon() {
        for (call in Plan.entries) {
            val maze = call.maze()
            val haunt = call.haunt()
            val deadly = maze.dooms.map { it.at }.toSet()
            assertEquals(maze.cells.size, maze.distances(maze.start).size, "${call.name}: every cell can be reached")
            assertTrue(maze.gate in maze.distances(maze.start, deadly), "${call.name}: a way out without a doom")
            assertTrue(maze.lair !in deadly && maze.lair != maze.start && maze.lair != maze.gate, call.name)
            assertEquals(call.notes.first(), haunt.notes[maze.start], "${call.name}: the first note is at the start")
            assertEquals(call.notes.size, haunt.notes.size, "${call.name}: every note is in the maze")
            assertTrue(haunt.notes.keys.none { maze.doomAt(it) != null })
            for ((cell, dir) in haunt.arrows) assertTrue(maze.canGo(cell, dir), "${call.name}: an arrow at $cell points at a wall")
            // Each doom's lesson is in a note, and a note can be read before the doom is reached.
            for (doom in maze.dooms) assertTrue(doom.kind.lesson in call.notes, "${call.name}: no note teaches ${doom.kind}")
            val shifted = maze.shifted()
            assertTrue(maze.gate in shifted.distances(maze.start, deadly), "${call.name}: still a way out after midnight")
        }
    }

    /** The way a perfect guide would go: along a safe way out, from where the caller faces. */
    private fun guide(call: Call, maze: Maze): String {
        val near = call.lines.lastOrNull { it.who == Who.CALLER }?.text.orEmpty()
        // A few steps from the way out, run for it, whatever is behind.
        maze.way(call.at, maze.gate, maze.dooms.map { it.at }.toSet())?.takeIf { it.second <= 3 && "I can hear it" !in near }?.let { (dir, _) ->
            return "Go " + listOf("ahead", "right", "back", "left")[call.facing.side(dir)] + ", now, you can do it"
        }
        if ("I can hear it" in near && ("close" in near || "corner" in near)) return "Shh. Hide. Breathe slowly, I'm here with you."
        // Too scared to walk: they would bolt anywhere. Calm them first.
        if (call.panic >= BOLT - 10) return "Breathe. Slowly. I'm here with you, okay? You're safe."
        val deadly = maze.dooms.map { it.at }.toSet()
        val (dir, _) = maze.way(call.at, maze.gate, deadly) ?: return "wait"
        return "Breathe, you're doing well. Go " + listOf("ahead", "right", "back", "left")[call.facing.side(dir)]
    }

    @Test
    fun aKindGuideWhoKnowsTheWayGetsMostCallersOut() = runBlocking {
        val outs =
            Plan.entries.associateWith { night ->
            val ends =
                (1L..12L).map { seed ->
                    val run = CallRun(night, seed, Keywords, Script)
                    var call = run.begin()
                    while (!call.over) call = run.say(guide(call, run.maze))
                    call
                }
            val out = ends.count { it.end == End.OUT }
            println("BALANCE ${night.name}: $out of 12 out; ${ends.filter { it.end != End.OUT }.map { "${it.end} ${it.away} away" }}")
            out
            }
        for ((night, out) in outs) assertTrue(out >= 8, "${night.name}: only $out of 12 out")
    }

    @Test
    fun silenceLetsTheCallDie() = runBlocking {
        val run = CallRun(Plan.CAVE, ears = Keywords, voice = Script)
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
        val run = CallRun(Plan.CAVE, ears = GemmaEars(model), voice = GemmaVoice(model))
        run.begin()
        val call = run.say("Hey. Breathe. Go left, slowly.")
        assertEquals(listOf(Who.CALLER, Who.YOU, Who.CALLER), call.lines.map { it.who })
        assertEquals("Okay, okay. I went left.", call.lines.last().text)
        assertTrue(model.requests.last().messages.last().text.contains("Hey. Breathe."), "the voice hears the stranger")
    }

    @Test
    fun theNotebookFadesExceptWhereMarked() {
        val (here, dir) = corridor
        val call = quiet(here, dir, Order.MARK).copy(drawn = mapOf(here to 0, here + dir to 0), turn = FADE)
        assertEquals(1f, call.walk(maze, empty).ink(here), "marked")
        assertEquals(0f, call.copy(marks = emptyList()).ink(here + dir))
        assertEquals(0.5f, call.copy(turn = FADE / 2).ink(here + dir))
    }
}
