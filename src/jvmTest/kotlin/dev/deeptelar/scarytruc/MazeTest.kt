package dev.deeptelar.scarytruc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MazeTest {
    @Test
    fun theSameSeedBuildsTheSameMaze() {
        val a = Maze.of(42)
        val b = Maze.of(42)
        for (cell in a.cells) assertEquals(a.exits(cell), b.exits(cell))
        assertEquals(a.dooms, b.dooms)
    }

    @Test
    fun everyCellIsReachableAndTheGateHasAWayWithoutDooms() {
        for (seed in 1L..200L) {
            val maze = Maze.of(seed)
            assertEquals(maze.cells.size, maze.distances(maze.start).size, "seed $seed")
            val dooms = maze.dooms.map { it.at }.toSet()
            assertTrue(maze.gate in maze.distances(maze.start, dooms), "seed $seed")
            assertTrue(maze.dooms.size >= 2, "seed $seed has ${maze.dooms.size} dooms")
            assertTrue(maze.dooms.none { it.at == maze.start || it.at == maze.gate || it.at == maze.lair })
        }
    }

    @Test
    fun passagesGoBothWays() {
        val maze = Maze.of(7)
        for (cell in maze.cells) {
            for (dir in maze.exits(cell)) assertTrue(maze.canGo(cell + dir, dir.opposite))
        }
    }

    @Test
    fun aWayIsAShortestOne() {
        val maze = Maze.of(3)
        val (dir, steps) = maze.way(maze.start, maze.gate)!!
        assertEquals(maze.distances(maze.start).getValue(maze.gate), steps)
        assertEquals(steps - 1, maze.distances(maze.start + dir).getValue(maze.gate))
    }
}
