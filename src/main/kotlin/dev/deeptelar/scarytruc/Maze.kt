package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class Pos(val x: Int, val y: Int) {
    operator fun plus(dir: Dir): Pos = Pos(x + dir.dx, y + dir.dy)
}

enum class Dir(val dx: Int, val dy: Int, val word: String) {
    NORTH(0, -1, "north"),
    EAST(1, 0, "east"),
    SOUTH(0, 1, "south"),
    WEST(-1, 0, "west"),
    ;

    val opposite: Dir get() = entries[(ordinal + 2) % 4]

    companion object {
        fun of(word: String): Dir? = entries.firstOrNull { it.word == word.trim().lowercase() }
    }
}

/** What kills whoever walks in. The player sees them; the crew does not. */
enum class DoomKind(val word: String) {
    PIT("a pit with no bottom"),
    MOUTH("a door that is a mouth"),
    MIRROR("a mirror that takes you in"),
}

@Serializable
data class Doom(val at: Pos, val kind: DoomKind)

/**
 * The haunted maze of a night: the same [seed] always builds the same maze, so a night is saved by
 * its seed and a fork of it walks the same corridors.
 *
 * The crew starts at [start], top left, and is safe at [gate], bottom right. A [Doom] waits in some
 * corridors, but never on all the ways out: there is always a path from [start] to [gate] without one.
 */
class Maze private constructor(
    val seed: Long,
    val width: Int,
    val height: Int,
    // east[x][y]: the passage from (x, y) to (x + 1, y) is open. south: to (x, y + 1).
    private val east: Array<BooleanArray>,
    private val south: Array<BooleanArray>,
) {
    val start: Pos = Pos(0, 0)
    val gate: Pos = Pos(width - 1, height - 1)

    /** Where the Hollow wakes up: the middle, far from the crew. */
    val lair: Pos = Pos(width / 2, height / 2)

    lateinit var dooms: List<Doom>
        private set

    /** Where lost candy lies at nightfall: dead ends, so that a detour pays. */
    lateinit var candy: List<Pos>
        private set

    val cells: List<Pos> get() = (0 until height).flatMap { y -> (0 until width).map { x -> Pos(x, y) } }

    operator fun contains(pos: Pos): Boolean = pos.x in 0 until width && pos.y in 0 until height

    fun canGo(from: Pos, dir: Dir): Boolean {
        val to = from + dir
        if (from !in this || to !in this) return false
        return when (dir) {
            Dir.EAST -> east[from.x][from.y]
            Dir.WEST -> east[to.x][to.y]
            Dir.SOUTH -> south[from.x][from.y]
            Dir.NORTH -> south[to.x][to.y]
        }
    }

    fun exits(from: Pos): List<Dir> = Dir.entries.filter { canGo(from, it) }

    fun doomAt(pos: Pos): Doom? = dooms.firstOrNull { it.at == pos }

    /** Steps from [from] to every cell it can reach, not walking through [avoid]. */
    fun distances(from: Pos, avoid: Set<Pos> = emptySet()): Map<Pos, Int> {
        val seen = mutableMapOf(from to 0)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val here = queue.removeFirst()
            for (dir in exits(here)) {
                val next = here + dir
                if (next in seen || next in avoid) continue
                seen[next] = seen.getValue(here) + 1
                queue.addLast(next)
            }
        }
        return seen
    }

    /** The first step of a shortest way from [from] to [to], and how long that way is; `null` when there is none. */
    fun way(from: Pos, to: Pos, avoid: Set<Pos> = emptySet()): Pair<Dir, Int>? {
        if (from == to) return null
        val back = distances(to, avoid)
        val total = back[from] ?: return null
        val dir = exits(from).filter { (from + it) in back }.minBy { back.getValue(from + it) }
        return dir to total
    }

    companion object {
        const val WIDTH: Int = 11
        const val HEIGHT: Int = 8

        fun of(seed: Long, width: Int = WIDTH, height: Int = HEIGHT, dooms: Int = 4, candy: Int = 0): Maze {
            val random = Random(seed)
            val east = Array(width) { BooleanArray(height) }
            val south = Array(width) { BooleanArray(height) }
            fun open(from: Pos, dir: Dir) {
                when (dir) {
                    Dir.EAST -> east[from.x][from.y] = true
                    Dir.WEST -> east[from.x - 1][from.y] = true
                    Dir.SOUTH -> south[from.x][from.y] = true
                    Dir.NORTH -> south[from.x][from.y - 1] = true
                }
            }
            fun inside(pos: Pos) = pos.x in 0 until width && pos.y in 0 until height

            // A perfect maze by a random walk that backtracks, then some walls knocked down, so that
            // there are loops: a way around a doom, and choices worth a treat or a trick.
            val visited = mutableSetOf(Pos(0, 0))
            val stack = ArrayDeque(listOf(Pos(0, 0)))
            while (stack.isNotEmpty()) {
                val here = stack.last()
                val next = Dir.entries.shuffled(random).firstOrNull { inside(here + it) && (here + it) !in visited }
                if (next == null) {
                    stack.removeLast()
                } else {
                    open(here, next)
                    visited += here + next
                    stack.addLast(here + next)
                }
            }
            val maze = Maze(seed, width, height, east, south)
            repeat(width * height / 7) {
                val pos = Pos(random.nextInt(width), random.nextInt(height))
                val dir = if (random.nextBoolean()) Dir.EAST else Dir.SOUTH
                if (inside(pos + dir)) open(pos, dir)
            }

            // Dooms in corridors away from the start, the gate and the lair. The first one on the
            // shortest way out, because that is where the crew is most likely to walk.
            val placed = mutableListOf<Doom>()
            val fromStart = maze.distances(maze.start)
            val candidates =
                maze.cells.filter {
                    it != maze.gate && it != maze.lair && fromStart.getValue(it) >= 3 && maze.exits(it).size == 2
                }.shuffled(random)
            val shortest = shortestPath(maze, maze.start, maze.gate)
            for (cell in listOfNotNull(candidates.firstOrNull { it in shortest }) + candidates) {
                if (placed.size == dooms) break
                if (placed.any { it.at == cell || distance(it.at, cell) < 3 }) continue
                val avoid = placed.map { it.at }.toSet() + cell
                if (maze.gate !in maze.distances(maze.start, avoid)) continue
                if (maze.lair !in maze.distances(maze.start, avoid)) continue
                placed += Doom(cell, DoomKind.entries[placed.size % DoomKind.entries.size])
            }
            maze.dooms = placed
            maze.candy =
                maze.cells.filter { maze.exits(it).size == 1 && it != maze.start && it != maze.gate && maze.doomAt(it) == null }
                    .sortedByDescending { fromStart.getValue(it) }.take(candy * 2).shuffled(random).take(candy)
            return maze
        }

        private fun distance(a: Pos, b: Pos) = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)

        private fun shortestPath(maze: Maze, from: Pos, to: Pos): Set<Pos> {
            val path = mutableSetOf(from)
            var here = from
            while (here != to) {
                here += maze.way(here, to)?.first ?: break
                path += here
            }
            return path
        }
    }
}
