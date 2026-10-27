package dev.deeptelar.scarytruc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightTest {
    private val maze = Maze.of(11)

    /** A night with the Hollow far away, asleep over a treat, so it changes nothing. */
    private fun quiet(vararg crew: Agent, marks: List<Mark> = emptyList()) =
        Night(maze.seed, crew.toList(), Hollow(maze.lair, eating = 99), marks = marks)

    private fun neighbour(of: Pos) = maze.exits(of).first().let { it to of + it }

    @Test
    fun aDoomKillsWhoeverWalksIn() {
        val doom = maze.dooms.first()
        val dir = maze.exits(doom.at).first()
        val from = doom.at + dir
        val night = quiet(Agent(Role.CODER, from)).copy(moves = listOf(Move(Role.CODER, dir.opposite, "Just one bite.")))
        val agent = night.step(maze).crew.single()
        assertEquals(Fate.DEAD, agent.fate)
        assertEquals(doom.kind.word, agent.death)
        assertEquals("Just one bite.", agent.words)
    }

    @Test
    fun aTreatIsEatenAndATrickFades() {
        val (dir, to) = neighbour(maze.start)
        val night =
            quiet(Agent(Role.CODER, maze.start), marks = listOf(Mark(to, MarkKind.TREAT), Mark(Pos(5, 0), MarkKind.TRICK, age = Night.TRICK_TURNS - 1)))
                .copy(moves = listOf(Move(Role.CODER, dir, "Caramel!")))
        val after = night.step(maze)
        assertTrue(after.marks.isEmpty())
        assertEquals(1, after.turn)
        assertTrue(after.log.last().contains("ate a treat"))
    }

    @Test
    fun theGateSavesAndTheNightEndsWhenNobodyIsLost() {
        val dir = maze.exits(maze.gate).first()
        val night = quiet(Agent(Role.PLANNER, maze.gate + dir)).copy(moves = listOf(Move(Role.PLANNER, dir.opposite, "Out!")))
        val after = night.step(maze)
        assertEquals(Fate.SAVED, after.crew.single().fate)
        assertTrue(after.over)
    }

    @Test
    fun theHollowHuntsTheNearestAgentAndTakesIt() {
        val (_, next) = neighbour(maze.lair)
        val night = Night(maze.seed, listOf(Agent(Role.SCOUT, next)), Hollow(maze.lair))
        val after = night.step(maze)
        assertEquals(Fate.DEAD, after.crew.single().fate)
        assertEquals("the Hollow", after.crew.single().death)
    }

    @Test
    fun aTreatLuresTheHollowAndKeepsItBusy() {
        val (_, next) = neighbour(maze.lair)
        val night = Night(maze.seed, listOf(Agent(Role.SCOUT, maze.start)), Hollow(maze.lair), marks = listOf(Mark(next, MarkKind.TREAT)))
        val after = night.step(maze)
        assertEquals(next, after.hollow.at)
        assertTrue(after.hollow.eating > 0)
        assertTrue(after.marks.isEmpty())
        assertEquals(next, after.step(maze).hollow.at)
    }

    @Test
    fun placingUsesTheSupplyAndNotTheGateOrATakenCell() {
        val night = Night.begin(maze)
        val placed = night.place(maze, Pos(3, 3), MarkKind.TRICK)!!
        assertEquals(Night.TRICKS - 1, placed.tricks)
        assertNull(placed.place(maze, Pos(3, 3), MarkKind.TREAT))
        assertNull(night.place(maze, maze.gate, MarkKind.TREAT))
        assertNull(night.place(maze, maze.start, MarkKind.TREAT), "the crew stands there")
        assertNull(night.copy(treats = 0).place(maze, Pos(2, 2), MarkKind.TREAT))
    }

    @Test
    fun theSensesTellATrickAndATreatApart() {
        val (dir, to) = neighbour(maze.start)
        val night = quiet(Agent(Role.SCOUT, maze.start), marks = listOf(Mark(to, MarkKind.TRICK)))
        val way = senses(night, maze, night.crew.single()).single { it.dir == dir }
        assertEquals(1, way.trick)
        assertNull(way.treat)
        assertTrue(describe(listOf(way), night, night.crew.single()).contains("a scream"))
    }

    @Test
    fun aScaredScoutRunsFromATrickAndAHungryCoderFollowsATreat() {
        // A cell with two ways, so there is somewhere else to go.
        val cell = maze.cells.first { maze.exits(it).size >= 2 && maze.doomAt(it) == null && it != maze.lair }
        val (toward, away) = maze.exits(cell)
        val mark = cell + toward
        for (seed in 0 until 5) {
            val scared = quiet(Agent(Role.SCOUT, cell), marks = listOf(Mark(mark, MarkKind.TRICK))).copy(turn = seed)
            val hungry = quiet(Agent(Role.CODER, cell), marks = listOf(Mark(mark, MarkKind.TREAT))).copy(turn = seed)
            kotlinx.coroutines.runBlocking {
                assertTrue(Instinct.decide(scared, maze, scared.crew.single()).go != toward)
                assertEquals(toward, Instinct.decide(hungry, maze, hungry.crew.single()).go)
            }
        }
        check(away != toward)
    }
}
