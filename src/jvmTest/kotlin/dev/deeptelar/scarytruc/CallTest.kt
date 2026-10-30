package dev.deeptelar.scarytruc

import dev.deeptelar.telar.test.ScriptedChatModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallTest {
    private val plan = Plan.CUBE
    private val maze = plan.maze()
    private val empty = Haunt(emptyMap(), emptyMap(), emptyMap())

    /** A quiet call at [at], facing [facing], with nothing in the maze but the dooms. */
    private fun quiet(at: Pos, facing: Dir, order: Order, calm: Int = 0) =
        Call.begin(plan, maze, empty).copy(at = at, facing = facing, hollow = null, said = "go", heard = Heard(order, calm), facts = emptyList(), told = emptySet())

    /** A corridor cell, its two ways, and no doom, gate or start next to it. */
    private val corridor: Pair<Pos, Dir> =
        maze.cells.firstNotNullOf { here ->
            val exits = maze.exits(here)
            val safe = (exits.map { here + it } + here).all { maze.doomAt(it) == null && it != maze.gate && it != maze.start }
            if (exits.size == 2 && safe) here to exits.first() else null
        }

    @Test
    fun hangingUpLeavesTheCallerAloneAndCountsForNothing() = runBlocking {
        val model = CallModel(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default), Minds.none, plan = plan, seed = 1, clock = false)
        model.answer()
        while (model.thinking) kotlinx.coroutines.delay(10)
        model.hangUp()
        assertEquals(End.ALONE, model.call.end)
        assertEquals(Who.LINE, model.call.lines.last().who)
        assertTrue(plan !in model.best)
    }

    @Test
    fun aBareOrderIsAShortcutThatNeedsNoModel() = runBlocking {
        assertEquals(Order.LEFT, Keywords.bare("left"))
        assertEquals(Order.LEFT, Keywords.bare("Go left!"))
        assertEquals(Order.HIDE, Keywords.bare("hide"))
        assertEquals(Order.AHEAD, Keywords.bare("keep going"))
        assertNull(Keywords.bare("don't go left"))
        assertNull(Keywords.bare("left, it's safe there"))
        // The model is never asked: a script with no answers would fail.
        val ears = GemmaEars(ScriptedChatModel {})
        assertEquals(Order.RIGHT, ears.listen(Call.begin(plan, maze, empty).copy(said = "right")).order)
    }

    @Test
    fun inSpanishTheCallerUnderstandsSpanishAndNotEnglish() = runBlocking {
        val spanish = Call.begin(plan, maze, plan.haunt(Lang.ES), lang = Lang.ES)
        assertTrue(spanish.facts.first().startsWith("Me acabo de despertar"), spanish.facts.first())
        suspend fun said(text: String) = Keywords.listen(spanish.copy(said = text))
        assertEquals(Order.LEFT, said("ve a la izquierda, tranquila").order)
        assertEquals(Order.HIDE, said("¡Escóndete!").order)
        assertEquals(Heard(Order.USE, 0, Item.STONE, Order.BACK), said("tira la piedra atrás"))
        assertEquals(Order.LEFT, Keywords.bare("Ve a la izquierda", Lang.ES))
        assertTrue(said("go left").foreign, "English, to a caller who speaks Spanish")
        assertFalse(said("ok").foreign, "ok is every language's")
        assertTrue(Keywords.listen(Call.begin(plan, maze, empty).copy(said = "ve a la izquierda")).foreign)
        // Not understood, nothing happens, and they say it in their language.
        val lost = spanish.copy(said = "go left", heard = Heard(Order.NONE, 0, foreign = true)).walk(maze, plan.haunt(Lang.ES))
        assertEquals(listOf("No entiendo lo que dices."), lost.facts)
        assertEquals(spanish.at, lost.at)
        assertEquals(spanish.turn, lost.turn)
    }

    @Test
    fun aThoughtNamesWhatCouldHelpWithoutSayingWhatToDo() {
        val (here, dir) = corridor
        val call = quiet(here, dir, Order.STAY).copy(turn = 2)
        val hunted = call.copy(hollow = Hollow(here + maze.exits(here).first(), awake = true))
        assertEquals(Thought.LISTENS, Thought.of(hunted, maze, empty))
        assertEquals(Thought.NOISE, Thought.of(hunted.copy(carried = listOf(Item.STONE)), maze, empty))
        assertEquals(Thought.FIRE, Thought.of(hunted.copy(carried = listOf(Item.STONE, Item.FLARE)), maze, empty))
        // Never an order: no thought starts with one of the caller's words.
        val orders = Order.entries.map { it.word } + listOf("go", "hide", "throw", "light", "use")
        for (thought in Thought.entries) assertTrue(orders.none { thought.en.lowercase().startsWith(it) }, thought.en)
    }

    @Test
    fun aThoughtIsSaidAloudOnceAndNotOnEveryTurn() {
        val (here, _) = corridor
        val rock = Haunt(emptyMap(), emptyMap(), emptyMap(), items = mapOf(here to Item.STONE))
        val waiting = quiet(here, maze.exits(here).first(), Order.STAY).copy(turn = 3)

        val first = waiting.walk(maze, rock)
        assertEquals(Thought.STONE_HERE.en, first.hunch)
        assertEquals("Fine. ${Thought.STONE_HERE.en}", first.spoke("Fine.").lines.last().text)
        assertEquals(null, waiting.copy(thoughts = setOf(Thought.STONE_HERE)).walk(maze, rock).hunch, "once in a call")
        assertEquals(null, waiting.copy(thoughtAt = 3).walk(maze, rock).hunch, "not two turns running")
        assertEquals(null, Call.begin(plan, maze, rock).hunch, "not before they have said anything")
        assertEquals("Esa piedra podría venirnos bien.", waiting.copy(lang = Lang.ES).walk(maze, rock).hunch)
    }

    @Test
    fun wordsThatHaveNothingToDoWithGettingOutCostMoreTrustEachTimeInARow() {
        val (here, dir) = corridor
        val stray = quiet(here, dir, Order.NONE).copy(said = "tell me a joke", heard = Heard(Order.NONE, 0, offTopic = true))
        val once = stray.walk(maze, empty)
        val twice = once.copy(said = "what's the capital of France?", heard = Heard(Order.NONE, 0, offTopic = true)).walk(maze, empty)
        assertEquals(2, twice.strays)
        assertTrue(plan.role.trust - once.trust < once.trust - twice.trust, "the second costs more")
        assertEquals(0, twice.copy(said = "left", heard = Heard(Order.LEFT, 0)).walk(maze, empty).strays)
    }

    @Test
    fun withOneWayOnACallerStopsOnlyForWhatIsNew() {
        // A corridor that goes on past the next cell, with nothing to stop for beyond it.
        val (here, dir) =
            maze.cells.firstNotNullOf { from ->
                maze.exits(from).firstNotNullOfOrNull { way ->
                    val next = from + way
                    val on = maze.exits(next).singleOrNull { it != way.opposite }
                    val clear = on != null && maze.exits(next).size == 2 && maze.doomAt(next) == null && next != maze.gate && maze.doomAt(next + on) == null && next + on != maze.gate
                    if (clear) from to way else null
                }
            }
        val next = here + dir
        val walking = quiet(here, dir, Order.AHEAD)

        val prop = Haunt(emptyMap(), emptyMap(), mapOf(next to "a boot"))
        assertEquals(next, walking.copy(been = setOf(here)).walk(maze, prop).at, "the first time, they stop to look")
        assertNotEquals(next, walking.copy(been = setOf(here, next)).walk(maze, prop).at, "a boot they saw is nothing to stop for")

        val note = Haunt(emptyMap(), mapOf(next to "Run."), emptyMap())
        assertEquals(next, walking.walk(maze, note).at, "a note not read yet")
        assertNotEquals(next, walking.copy(read = setOf(next)).walk(maze, note).at, "a note already read")

        val item = Haunt(emptyMap(), emptyMap(), emptyMap(), items = mapOf(next to Item.STONE))
        assertEquals(next, walking.copy(been = setOf(here, next)).walk(maze, item).at, "something still to take")
    }

    @Test
    fun aCallerGoingInCirclesSaysSoAndNamesAWayNotTried() {
        val (here, way) = corridor
        val other = maze.exits(here).first { it != way }
        val untried = here + way
        // They walk off down the corridor, away from the one place they haven't been.
        val walking = quiet(here, other, Order.AHEAD).copy(been = maze.cells.toSet() - untried)

        val circling = walking.copy(stale = 2).walk(maze, empty)
        assertEquals(3, circling.stale)
        assertTrue(circling.facts.any { "going in circles" in it }, "${circling.facts}")
        val back = maze.way(circling.at, untried, maze.dooms.map { it.at }.toSet())!!.first
        val word = Order.entries.first { it.turn == circling.facing.side(back) }.word
        assertTrue(circling.hunch!!.startsWith("Maybe we should go $word:"), "${circling.hunch}")
        val said = circling.spoke("I think we're lost.")
        assertEquals("I think we're lost. ${circling.hunch}", said.lines.last().text)
        assertEquals(null, said.hunch, "said once")
        val still = walking.copy(stale = 3).walk(maze, empty)
        assertTrue(still.facts.none { "circles" in it }, "they said so once")
        assertTrue(still.hunch!!.startsWith("The way I haven't tried is"), "they keep pointing it out: ${still.hunch}")

        assertTrue(walking.walk(maze, empty).facts.none { "circles" in it }, "not on the first walk back")
        val stays = walking.copy(stale = 2, heard = Heard(Order.STAY, 0)).walk(maze, empty)
        assertEquals(2, stays.stale, "standing still is not walking in circles")

        val found = walking.copy(stale = 5, been = setOf(here)).walk(maze, empty)
        assertEquals(0, found.stale, "somewhere new starts it over")
        assertTrue(found.facts.none { "circles" in it })

        val spanish = walking.copy(stale = 2, lang = Lang.ES).walk(maze, empty)
        assertTrue(spanish.facts.any { "Estamos dando vueltas" in it }, "${spanish.facts}")
    }

    @Test
    fun aCallerGoingInCirclesNeverSuggestsADoom() {
        val (here, way) = corridor
        val other = maze.exits(here).first { it != way }
        // All that is left to try is what kills.
        val been = maze.cells.toSet() - maze.dooms.map { it.at }.toSet()
        val circling = quiet(here, other, Order.AHEAD).copy(been = been, stale = 2).walk(maze, empty)
        assertTrue(circling.facts.any { "going in circles" in it }, "${circling.facts}")
        assertEquals(null, circling.hunch, "${circling.facts}")
    }

    @Test
    fun aModelsTextIsMadeIntoWordsSaidOnAPhone() {
        assertEquals("I'm here. Help me.", spoken("\"*shaking* I'm **here**. Help me. 😱\""))
        assertEquals("Okay. Left.", spoken("- Okay.\n- Left."))
        assertEquals("One. Two.", whole("One. Two. Three is too long", limit = 20))
        assertEquals("Short enough", whole("Short enough", limit = 20))
    }

    @Test
    fun theStrangersWordsAreReadForTheFirstWayAndTheTone() = runBlocking {
        suspend fun said(text: String) = Keywords.listen(Call.begin(plan, maze, empty).copy(said = text))
        assertEquals(Heard(Order.LEFT, 0), said("Go left, then right"))
        assertEquals(Order.AHEAD, said("keep going straight").order)
        assertEquals(Order.AHEAD, said("Don't stop").order)
        assertEquals(Order.HIDE, said("Shh. Hide.").order)
        assertEquals(Order.NONE, said("Who are you?").order)
        assertEquals(2, said("Breathe. Slow. I'm here with you, okay?").calm)
        assertTrue(said("GO LEFT NOW HURRY").calm < 0)
        assertEquals(Order.NONE, said("I'm leftover").order, "a whole word only")
    }

    @Test
    fun theWordsForThingsAreHeard() = runBlocking {
        suspend fun said(text: String) = Keywords.listen(Call.begin(plan, maze, empty).copy(said = text))
        assertEquals(Order.TAKE, said("Grab it, quick").order)
        assertEquals(Heard(Order.USE, 0, Item.FLARE, null), said("Light the flare"))
        assertEquals(Heard(Order.USE, 0, Item.STONE, Order.LEFT), said("Throw the stone left"))
        assertEquals(Order.AHEAD, said("Go ahead, toward the light").order, "the light is not the flare")
    }

    @Test
    fun aThingIsTakenOnceAndUsedOnce() {
        val (here, dir) = corridor
        val haunt = Haunt(emptyMap(), emptyMap(), emptyMap(), mapOf(here to Item.BATTERY))
        val took = quiet(here, dir, Order.TAKE).walk(maze, haunt)
        assertEquals(listOf(Item.BATTERY), took.carried)
        assertEquals(emptyList(), took.copy(heard = Heard(Order.TAKE, 0), said = "take").walk(maze, haunt).carried.drop(1), "nothing more here")
        val low = took.copy(battery = 20, heard = Heard(Order.USE, 0, Item.BATTERY), said = "swap the battery").walk(maze, haunt)
        assertTrue(low.battery > 40, "charged: ${low.battery}")
        assertTrue(low.carried.isEmpty())
    }

    @Test
    fun aFlareSendsTheHollowAwayAndAStoneDrawsIt() {
        val (here, dir) = corridor
        val close = Hollow(here + dir, awake = true)
        val lit = quiet(here, dir, Order.USE).copy(hollow = close, carried = listOf(Item.FLARE), heard = Heard(Order.USE, 0, Item.FLARE)).walk(maze, empty)
        assertNull(lit.end)
        assertTrue(lit.hollow!!.deaf > 0)
        assertTrue((maze.distances(lit.at)[lit.hollow!!.at] ?: 0) >= 2, "it fled")
        val thrown = quiet(here, dir, Order.USE).copy(hollow = Hollow(here + dir + dir, awake = false), carried = listOf(Item.STONE), heard = Heard(Order.USE, 0, Item.STONE, Order.AHEAD)).walk(maze, empty)
        assertTrue(thrown.hollow!!.awake, "the noise woke it")
        assertTrue(thrown.carried.isEmpty())
    }

    @Test
    fun leftWithoutAnAnswerTheyChooseSomewhereNewAndAskWhichWay() {
        val (here, dir) = corridor
        val alone = quiet(here, dir, Order.NONE).copy(said = "", mapped = mapOf(here to emptySet(), here + dir.opposite to emptySet())).walk(maze, empty)
        assertTrue(alone.walked > 0, "they went somewhere")
        assertTrue(alone.facts.any { it.startsWith("So I chose myself") })
        val call = quiet(here, dir, Order.STAY).walk(maze, empty)
        assertTrue(call.facts.last().startsWith("Which way do I go:"), call.facts.last())
    }

    @Test
    fun whatIsAroundIsSaidOnceUnlessTheStrangerAsksAgain() {
        val (here, dir) = corridor
        val first = quiet(here, dir, Order.STAY).walk(maze, empty)
        assertTrue(first.facts.any { it.startsWith("Which way do I go:") })
        val second = first.copy(heard = Heard(Order.STAY, 0), said = "stay").walk(maze, empty)
        assertEquals(listOf("I stayed where I was."), second.facts)
        val asked = second.copy(heard = Heard(Order.NONE, 0, again = true), said = "what do you see?").walk(maze, empty)
        assertTrue(asked.facts.any { it.startsWith("Which way do I go:") }, asked.facts.toString())
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
        assertTrue(call.bumped, "they walked into it")
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
        assertTrue(hidden.sniffed, "it sniffed them")
        assertFalse(caught.sniffed)
    }

    @Test
    fun aPanickedRunLosesTheNotebookUntilAKnownPlace() {
        val (here, dir) = corridor
        val panicked = (0..40).map { quiet(here, dir, Order.STAY).copy(panic = 100, turn = it, mapped = emptyMap()).walk(maze, empty) }
        val ran = panicked.first { it.lostTrack && !it.over }
        assertTrue(ran.mapped.isEmpty(), "nothing is drawn while lost")
        assertTrue(ran.ran, "they ran")
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
