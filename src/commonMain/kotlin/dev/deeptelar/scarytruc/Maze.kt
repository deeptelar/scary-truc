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

/**
 * What kills whoever walks in. The caller can't tell, but sees its [sign] from next to it; a note
 * left in the maze teaches its [lesson].
 */
enum class DoomKind(val word: String, val sign: String, val lesson: String) {
    PIT(
        "a pit with no bottom",
        "the floor turns black and wet, and it swallows the light of my phone",
        "Black floor that eats your light is not floor.",
    ),
    MOUTH(
        "a door that is a mouth",
        "a door, warm, and it moves a little, like it's breathing",
        "Doors don't breathe. If one does, don't go through it.",
    ),
    MIRROR(
        "a mirror that takes you in",
        "a tall mirror, and my reflection isn't holding a phone",
        "If your reflection doesn't copy you, it isn't a mirror. Don't walk into it.",
    ),
}

@Serializable
data class Doom(val at: Pos, val kind: DoomKind)

/**
 * A haunted maze, drawn by hand as text (see [parse]): the caller wakes up at [start] and is safe
 * at [gate]. A [Doom] waits in some corridors, but never on every way out. The Hollow sleeps at
 * [lair]. [seed] makes the chances of a call and the walls that move at midnight the same each time.
 */
class Maze private constructor(
    val seed: Long,
    val width: Int,
    val height: Int,
    // east[x][y]: the passage from (x, y) to (x + 1, y) is open. south: to (x, y + 1).
    private val east: Array<BooleanArray>,
    private val south: Array<BooleanArray>,
    val start: Pos,
    val gate: Pos,
    val lair: Pos,
    val dooms: List<Doom>,
) {
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

    /**
     * The same maze after midnight: some walls have moved. The dooms and the gate stay, every cell
     * can still be reached, and from the start there is still a way out without a doom.
     */
    fun shifted(): Maze {
        val random = Random(seed xor 0x5EED)
        val east = Array(width) { east[it].copyOf() }
        val south = Array(width) { south[it].copyOf() }
        val after = Maze(seed, width, height, east, south, start, gate, lair, dooms)
        val deadly = dooms.map { it.at }.toSet()
        var moved = 0
        repeat(400) {
            if (moved == width * height / 8) return after
            val pos = Pos(random.nextInt(width), random.nextInt(height))
            val dir = if (random.nextBoolean()) Dir.EAST else Dir.SOUTH
            if (pos + dir !in this) return@repeat
            val walls = if (dir == Dir.EAST) east else south
            walls[pos.x][pos.y] = !walls[pos.x][pos.y]
            val fine = after.distances(gate).size == width * height && gate in after.distances(start, deadly)
            if (fine) moved++ else walls[pos.x][pos.y] = !walls[pos.x][pos.y]
        }
        return after
    }

    companion object {
        /**
         * The maze drawn in [rows]: `#` is rock, and a cell is every other character of every other
         * row, with the gaps between them open or walled. In a cell, `S` is the start, `E` the way
         * out, `H` the Hollow's lair, and `P`, `M` and `R` a pit, a mouth and a mirror. What else a
         * cell holds is the [Haunt]'s, read by [Haunt.of].
         */
        fun parse(seed: Long, rows: List<String>): Maze {
            val width = (rows.first().length - 1) / 2
            val height = (rows.size - 1) / 2
            require(rows.all { it.length == width * 2 + 1 }) { "Every row of a maze has ${width * 2 + 1} characters." }
            fun cell(pos: Pos): Char = rows[pos.y * 2 + 1][pos.x * 2 + 1]
            val east = Array(width) { x -> BooleanArray(height) { y -> x < width - 1 && rows[y * 2 + 1][x * 2 + 2] != '#' } }
            val south = Array(width) { x -> BooleanArray(height) { y -> y < height - 1 && rows[y * 2 + 2][x * 2 + 1] != '#' } }
            val cells = (0 until height).flatMap { y -> (0 until width).map { x -> Pos(x, y) } }
            fun find(c: Char): Pos = cells.single { cell(it) == c }
            val kinds = mapOf('P' to DoomKind.PIT, 'M' to DoomKind.MOUTH, 'R' to DoomKind.MIRROR)
            val dooms = cells.mapNotNull { pos -> kinds[cell(pos)]?.let { Doom(pos, it) } }
            return Maze(seed, width, height, east, south, find('S'), find('E'), find('H'), dooms)
        }
    }
}
