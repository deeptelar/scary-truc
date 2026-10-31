package dev.deeptelar.scarytruc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClockTest {
    private val plan = Plan.CUBE
    private val maze = plan.maze()
    private val empty = Haunt(emptyMap(), emptyMap(), emptyMap())

    /** A corridor cell with nothing near it. */
    private val here = maze.cells.first { cell -> maze.exits(cell).size == 2 && (maze.exits(cell).map { cell + it } + cell).all { maze.doomAt(it) == null && it != maze.gate && it != maze.start } }

    /** A cell [steps] from here. */
    private fun away(steps: Int) = maze.distances(here).entries.first { it.value == steps }.key

    private fun call(heard: Heard, hollow: Hollow? = null) =
        Call.begin(plan, maze, empty).copy(at = here, facing = maze.exits(here).first(), turn = 2, hollow = hollow, said = "go ahead", heard = heard)

    @Test
    fun theFirstSecondsAreFreeThenThinkingCosts() {
        val quick = call(Heard(Order.STAY, 0)).copy(hesitated = 5).walk(maze, empty)
        val slow = call(Heard(Order.STAY, 0)).copy(hesitated = 15).walk(maze, empty)
        assertEquals(quick.battery - 2, slow.battery)
        assertEquals(quick.panic + 4, slow.panic)
        assertTrue(slow.facts.any { "took so long" in it }, slow.facts.toString())
        assertFalse(quick.facts.any { "took so long" in it })
        val hollow = Hollow(away(4), awake = true)
        val walked = call(Heard(Order.STAY, 0), hollow = hollow).copy(hesitated = 15).walk(maze, empty)
        val waited = call(Heard(Order.STAY, 0), hollow = hollow).copy(hesitated = 0).walk(maze, empty)
        assertTrue(maze.distances(here)[walked.hollow!!.at]!! < maze.distances(here)[waited.hollow!!.at]!!, "it kept walking")
    }

    @Test
    fun theyCanBeTakenWhileYouThink() {
        val slow = call(Heard(Order.AHEAD, 0), hollow = Hollow(away(1), awake = true)).copy(hesitated = 20, panic = 50).walk(maze, empty)
        assertEquals(End.TAKEN, slow.end)
    }

    @Test
    fun theFirstAnswerIsFreeAndPatienceIsShortWithSomethingNear() {
        val first = Call.begin(plan, maze, empty).copy(said = "go", heard = Heard(Order.NONE, 0), hesitated = 40)
        assertEquals(first.battery - 1, first.walk(maze, empty).battery)
        assertEquals(1f, first.patience(maze))
        val later = call(Heard(Order.NONE, 0))
        val near = call(Heard(Order.NONE, 0), hollow = Hollow(away(2), awake = true))
        assertTrue(near.patience(maze) < later.patience(maze) && later.patience(maze) < 1f)
    }
}
