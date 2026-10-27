package dev.deeptelar.scarytruc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightTest {
    private val plan = Plan.FIFTH
    private val maze = plan.maze()

    /** A night without the Hollow, so it changes nothing. */
    private fun quiet(vararg crew: Agent, marks: List<Mark> = emptyList(), moves: List<Move> = emptyList()) =
        Night.begin(plan, maze).copy(crew = crew.toList(), hollow = null, marks = marks, moves = moves, lostCandy = emptyList())

    /** A cell with two ways out and no doom on it or next to it, and one of its ways. */
    private val corridor: Pair<Pos, Dir> =
        maze.cells.firstNotNullOf { here ->
            val exits = maze.exits(here)
            val safe = exits.all { maze.doomAt(here + it) == null && here + it != maze.gate }
            if (exits.size >= 2 && safe && maze.doomAt(here) == null && here != maze.lair) here to exits.first() else null
        }

    @Test
    fun aDoomKillsWhoeverWalksIn() {
        val doom = maze.dooms.first()
        val dir = maze.exits(doom.at).first()
        val night = quiet(Agent(Role.CODER, doom.at + dir), moves = listOf(Move(Role.CODER, dir.opposite, "Just one bite.")))
        val agent = night.act(maze).crew.single()
        assertEquals(Fate.DEAD, agent.fate)
        assertEquals(doom.kind.word, agent.death)
        assertEquals("Just one bite.", agent.words)
    }

    @Test
    fun theCrewWalksWhereItChoseAndATreatOnTheWayIsEaten() {
        val (here, go) = corridor
        val night = quiet(Agent(Role.PLANNER, here), marks = listOf(Mark(here + go, MarkKind.TREAT)), moves = listOf(Move(Role.PLANNER, go, "Onwards.")))
        val after = night.act(maze)
        assertEquals(here + go, after.crew.single().at)
        assertTrue(after.marks.isEmpty())
        assertTrue(after.crew.single().nerve > Role.PLANNER.nerve, "a sweet calms")
    }

    @Test
    fun screamsFrayNervesLessEachTimeAndQuietTurnsMendThem() {
        val (here, go) = corridor
        val scream = listOf(Mark(here + go, MarkKind.TRICK))
        val first = quiet(Agent(Role.SCOUT, here), marks = scream).act(maze).crew.single()
        assertEquals(Role.SCOUT.nerve - 16, first.nerve)
        assertEquals(1, first.screams)
        val second = quiet(first, marks = scream).act(maze).crew.single()
        assertEquals(first.nerve - 8, second.nerve, "the second scream scares half as much")
        assertEquals(second.nerve + 4, quiet(second).act(maze).crew.single().nerve)
    }

    @Test
    fun outOfNerveAnAgentMayBoltWhereverItMeantToGo() {
        val (here, go) = corridor
        val bolts =
            (0 until 40).count { turn ->
                val night = quiet(Agent(Role.SCOUT, here, nerve = 5), moves = listOf(Move(Role.SCOUT, go, "Steady."))).copy(turn = turn)
                "panics" in night.act(maze).log.last()
            }
        assertTrue(bolts in 10..30, "about half the turns, was $bolts of 40")
    }

    @Test
    fun theGateSavesAndTheNightEndsWhenNobodyIsLost() {
        val dir = maze.exits(maze.gate).first()
        val after = quiet(Agent(Role.PLANNER, maze.gate + dir), moves = listOf(Move(Role.PLANNER, dir.opposite, "Out!"))).act(maze)
        assertEquals(Fate.SAVED, after.crew.single().fate)
        assertTrue(after.over)
    }

    @Test
    fun lostCandyRefillsTheBag() {
        val candy = maze.candy.first()
        val dir = maze.exits(candy).single()
        val night = quiet(Agent(Role.SCOUT, candy + dir), moves = listOf(Move(Role.SCOUT, dir.opposite, "Shiny."))).copy(lostCandy = listOf(candy))
        val after = night.act(maze)
        assertEquals(night.candy + 1, after.candy)
        assertTrue(after.lostCandy.isEmpty())
    }

    @Test
    fun theHollowSleepsUntilAScreamWakesItAndThenGoesToTheScream() {
        val far = maze.cells.maxBy { maze.distances(maze.lair)[it] ?: 0 }
        val asleep = Night.begin(plan, maze).copy(crew = listOf(Agent(Role.SCOUT, far)))
        assertFalse(asleep.act(maze).hollow!!.awake, "nothing woke it")
        val scream = maze.distances(maze.lair).entries.first { it.value == 4 }.key
        val woken = asleep.copy(marks = listOf(Mark(scream, MarkKind.TRICK))).act(maze)
        assertTrue(woken.hollow!!.awake)
        assertEquals(maze.lair, woken.hollow!!.at, "it wakes this turn and hunts the next")
        // Heard from afar, a fresh scream draws it.
        val drawn = woken.copy(marks = listOf(Mark(scream, MarkKind.TRICK)), turn = 0).act(maze)
        assertEquals(3, maze.distances(drawn.hollow!!.at).getValue(scream))
    }

    @Test
    fun theAwakeHollowTakesOneAndATreatKeepsItBusy() {
        val next = maze.exits(maze.lair).map { maze.lair + it }.first { maze.doomAt(it) == null }
        val hunted = Night.begin(plan, maze).copy(crew = listOf(Agent(Role.SCOUT, next), Agent(Role.CODER, next)), hollow = Hollow(maze.lair, awake = true))
        val after = hunted.act(maze)
        assertEquals(1, after.crew.count { it.fate == Fate.DEAD }, "one, and it feeds")
        assertTrue(after.hollow!!.eating > 0)

        val lured = Night.begin(plan, maze).copy(crew = listOf(Agent(Role.SCOUT, maze.start)), marks = listOf(Mark(next, MarkKind.TREAT)), hollow = Hollow(maze.lair, awake = true))
        assertEquals(next, lured.act(maze).hollow!!.at)
    }

    @Test
    fun aDoomShowsOnlyWhereALanternShoneFromNextToIt() {
        val night = Night.begin(plan, maze)
        assertTrue(night.near.all { cell -> night.lost.any { (maze.distances(it.at)[cell] ?: 9) <= 1 } })
        assertTrue(night.seen.size > night.near.size)
    }

    @Test
    fun atMidnightTheWallsMoveButEveryoneCanStillGetOut() {
        for (seed in 1L..40L) {
            val before = Plan.FIFTH.maze(seed)
            val after = before.shifted()
            val changed = before.cells.count { cell -> before.exits(cell) != after.exits(cell) }
            assertTrue(changed > 0, "seed $seed")
            assertEquals(after.cells.size, after.distances(after.gate).size)
            val dooms = after.dooms.map { it.at }.toSet()
            assertTrue(after.starts.all { after.gate in after.distances(it, dooms) })
        }
    }

    @Test
    fun onePlayATurnPaidInCandyOnlyWhereALanternShone() {
        val night = Night.begin(plan, maze)
        val cell = night.seen.first { it !in maze.starts }
        val placed = night.place(maze, cell, MarkKind.TRICK)!!
        assertEquals(plan.candy - 2, placed.candy)
        assertNull(placed.place(maze, night.seen.last { it != cell && it !in maze.starts }, MarkKind.TREAT), "one a turn")
        assertNull(night.place(maze, maze.cells.first { it !in night.seen }, MarkKind.TREAT), "in the dark")
        assertNull(night.place(maze, maze.start, MarkKind.TREAT), "the crew stands there")
        assertNull(night.copy(candy = 1).place(maze, cell, MarkKind.TRICK), "too few candies")
    }

    @Test
    fun theSensesTellNerveDarknessAndAScreamRightThere() {
        val (here, go) = corridor
        val night = quiet(Agent(Role.SCOUT, here, nerve = 10, dark = 2), marks = listOf(Mark(here + go, MarkKind.TRICK)))
        val agent = night.crew.single()
        val text = describe(senses(night, maze, agent), night, agent)
        assertTrue("A SCREAM right there" in text)
        assertTrue("breaking" in text)
        assertTrue("lantern has gone out" in text)
    }

    @Test
    fun scaredInstinctAvoidsTheScreamMoreThanCalmInstinct() =
        runBlocking {
            val (here, go) = corridor
            val scream = listOf(Mark(here + go, MarkKind.TRICK))
            val avoided =
                listOf(90, 10).map { nerve ->
                    (0 until 30).count { turn ->
                        val night = quiet(Agent(Role.CODER, here, nerve = nerve), marks = scream).copy(turn = turn)
                        Instinct.decide(night, maze, night.crew.single()).go != go
                    }
                }
            assertTrue(avoided[1] >= avoided[0], "scared $avoided")
            assertNotEquals(0, avoided[1])
        }
}
