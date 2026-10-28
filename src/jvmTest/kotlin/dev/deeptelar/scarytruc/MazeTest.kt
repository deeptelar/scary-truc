package dev.deeptelar.scarytruc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MazeTest {
    private val maze =
        Maze.parse(
            1,
            listOf(
                "#######",
                "#S  #E#",
                "# # # #",
                "#P#H  #",
                "#######",
            ),
        )

    @Test
    fun aDrawingIsReadCellByCell() {
        assertEquals(3, maze.width)
        assertEquals(2, maze.height)
        assertEquals(Pos(0, 0), maze.start)
        assertEquals(Pos(2, 0), maze.gate)
        assertEquals(Pos(1, 1), maze.lair)
        assertEquals(listOf(Doom(Pos(0, 1), DoomKind.PIT)), maze.dooms)
        assertEquals(setOf(Dir.EAST, Dir.SOUTH), maze.exits(Pos(0, 0)).toSet())
        assertTrue(!maze.canGo(Pos(1, 0), Dir.EAST), "a wall")
    }

    @Test
    fun passagesGoBothWays() {
        for (call in Plan.entries) {
            val maze = call.maze()
            for (cell in maze.cells) {
                for (dir in maze.exits(cell)) assertTrue(maze.canGo(cell + dir, dir.opposite))
            }
        }
    }

    @Test
    fun aWayIsAShortestOne() {
        val (dir, steps) = maze.way(maze.start, maze.gate)!!
        assertEquals(maze.distances(maze.start).getValue(maze.gate), steps)
        assertEquals(steps - 1, maze.distances(maze.start + dir).getValue(maze.gate))
    }

    @Test
    fun theWallsMoveTheSameWayEachTime() {
        val a = Plan.HEDGE.maze().shifted()
        val b = Plan.HEDGE.maze().shifted()
        for (cell in a.cells) assertEquals(a.exits(cell), b.exits(cell))
        assertTrue(Plan.HEDGE.maze().cells.any { Plan.HEDGE.maze().exits(it) != a.exits(it) }, "some walls moved")
    }
}
