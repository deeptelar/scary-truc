package dev.deeptelar.scarytruc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NightTest {
    private val plan = Plan.FIFTH
    private val maze = plan.maze()

    /** A night without the Hollow, so it changes nothing. */
    private fun quiet(vararg crew: Agent, marks: List<Mark> = emptyList(), moves: List<Move> = emptyList()) =
        Night.begin(plan, maze).copy(crew = crew.toList(), hollow = null, marks = marks, moves = moves, lostCandy = emptyList())

    /** A corridor of three cells walked in a row: where an agent came from, where it is, and where it means to go. */
    private val corridor: Triple<Pos, Pos, Dir> =
        maze.cells.firstNotNullOf { here ->
            val exits = maze.exits(here)
            if (exits.size < 2 || maze.doomAt(here) != null) return@firstNotNullOf null
            val (from, to) = exits
            val ahead = here + to
            if (maze.doomAt(here + from) != null || maze.doomAt(ahead) != null || ahead == maze.gate || here + from == maze.gate) null else Triple(here + from, here, to)
        }

    private fun walker(role: Role): Agent {
        val (from, here, _) = corridor
        return Agent(role, here, trail = listOf(from, here))
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
    fun aTrickWhereAnAgentMeansToGoSendsItBackTheWayItCame() {
        val (from, here, go) = corridor
        val trick = listOf(Mark(here + go, MarkKind.TRICK))
        val planner = quiet(walker(Role.PLANNER), marks = trick, moves = listOf(Move(Role.PLANNER, go, "Onwards."))).act(maze).crew.single()
        assertEquals(from, planner.at)
        assertEquals("What was that?! No way.", planner.words)
    }

    @Test
    fun theScoutRunsBackTwoStepsAndTheCriticWalksIntoTheScream() {
        val (from, here, go) = corridor
        val back = maze.exits(from).map { from + it }.first { it != here }
        val scout = Agent(Role.SCOUT, here, trail = listOf(back, from, here))
        val trick = listOf(Mark(here + go, MarkKind.TRICK))
        val ran = quiet(scout, marks = trick, moves = listOf(Move(Role.SCOUT, go, "Onwards."))).act(maze).crew.single()
        assertEquals(back, ran.at)

        val critic = quiet(walker(Role.CRITIC), marks = listOf(Mark(from, MarkKind.TRICK)), moves = listOf(Move(Role.CRITIC, go, "Onwards."))).act(maze).crew.single()
        assertEquals(from, critic.at, "the Critic goes to see the trick instead")
    }

    @Test
    fun aTreatNextToAnAgentPullsItAndIsEaten() {
        val (from, here, go) = corridor
        val night = quiet(walker(Role.PLANNER), marks = listOf(Mark(from, MarkKind.TREAT)), moves = listOf(Move(Role.PLANNER, go, "Onwards.")))
        val after = night.act(maze)
        assertEquals(from, after.crew.single().at)
        assertTrue(after.marks.isEmpty())
        assertEquals(1, after.turn)
    }

    @Test
    fun theCriticRunsBackFromATreat() {
        val (from, here, go) = corridor
        val after = quiet(walker(Role.CRITIC), marks = listOf(Mark(here + go, MarkKind.TREAT)), moves = listOf(Move(Role.CRITIC, go, "Hm."))).act(maze)
        assertEquals(from, after.crew.single().at)
        assertEquals(1, after.marks.size)
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
    fun theHollowHuntsTheNearestAgentAndATreatKeepsItBusy() {
        val next = maze.lair + maze.exits(maze.lair).first()
        val hunted = Night.begin(plan, maze).copy(crew = listOf(Agent(Role.SCOUT, next)))
        assertEquals(Fate.DEAD, hunted.act(maze).crew.single().fate)

        val lured = Night.begin(plan, maze).copy(crew = listOf(Agent(Role.SCOUT, maze.start)), marks = listOf(Mark(next, MarkKind.TREAT)))
        val after = lured.act(maze)
        assertEquals(next, after.hollow!!.at)
        assertTrue(after.hollow!!.eating > 0)
        assertEquals(next, after.act(maze).hollow!!.at)
    }

    @Test
    fun onePlayATurnPaidInCandyOnlyWhereALanternShone() {
        val night = Night.begin(plan, maze)
        val cell = night.seen.first { it != maze.start }
        val placed = night.place(maze, cell, MarkKind.TRICK)!!
        assertEquals(plan.candy - 2, placed.candy)
        assertNull(placed.place(maze, night.seen.last { it != cell && it != maze.start }, MarkKind.TREAT), "one a turn")
        assertNull(night.place(maze, maze.cells.first { it !in night.seen }, MarkKind.TREAT), "in the dark")
        assertNull(night.place(maze, maze.start, MarkKind.TREAT), "the crew stands there")
        assertNull(night.copy(candy = 1).place(maze, cell, MarkKind.TRICK), "too few candies")
    }

    @Test
    fun theSensesTellATrickAndATreatApart() {
        val (_, here, go) = corridor
        val night = quiet(walker(Role.SCOUT), marks = listOf(Mark(here + go, MarkKind.TRICK)))
        val way = senses(night, maze, night.crew.single()).single { it.dir == go }
        assertEquals(1, way.trick)
        assertNull(way.treat)
        assertTrue(describe(listOf(way), night, night.crew.single()).contains("a scream"))
    }
}
