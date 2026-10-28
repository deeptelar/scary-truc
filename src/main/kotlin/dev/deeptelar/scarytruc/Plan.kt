package dev.deeptelar.scarytruc

import kotlin.random.Random

/**
 * The nights of the game: each is a call from another of the crew, and each brings one new thing.
 * [ENDLESS] is the fourth on a new maze, with anyone on the phone. A night with a [seed] is always
 * the same maze. The battery is the time the night gives: it drains a little every turn and with
 * every step. [midnight] is the turn the Hollow wakes up and, when the night [shifts], the walls move.
 */
enum class Plan(
    val title: String,
    val role: Role?,
    val width: Int,
    val height: Int,
    val dooms: Int,
    val hollow: Boolean,
    val shifts: Boolean,
    val battery: Int,
    val midnight: Int,
    val arrows: Int,
    val oddities: Int,
    val seed: Long?,
    /** The first note, by the start: what the one before learned. The lessons of the dooms follow it. */
    val notes: List<String>,
    val hint: String,
) {
    FIRST(
        "Night 1: the Scout",
        Role.SCOUT, 6, 5, dooms = 2, hollow = false, shifts = false, battery = 70, midnight = 99, arrows = 1, oddities = 1, seed = 11,
        notes = listOf("Whoever finds this: the way out is where the air turns cold. Scratch marks on the walls, or you'll walk in circles forever."),
        hint = "Tell them which way to go, from where they face: left, right, ahead, back. Ask them to mark the walls. Your notebook draws what they tell you.",
    ),
    SECOND(
        "Night 2: the Coder",
        Role.CODER, 7, 5, dooms = 2, hollow = true, shifts = false, battery = 80, midnight = 5, arrows = 2, oddities = 2, seed = 22,
        notes = listOf("Something sleeps here until midnight. Then it hunts by sound. When you hear it, hide. Breathe slow. Don't talk."),
        hint = "Something sleeps in this maze until midnight. When they hear it, tell them to hide, and keep them calm: fear is loud.",
    ),
    THIRD(
        "Night 3: the Planner",
        Role.PLANNER, 8, 6, dooms = 3, hollow = true, shifts = true, battery = 90, midnight = 6, arrows = 2, oddities = 3, seed = 33,
        notes = listOf("At midnight the walls move. Your marks don't."),
        hint = "At midnight the walls move, and your notebook will lie. The marks on the walls stay true.",
    ),
    FOURTH(
        "Night 4: the Critic",
        Role.CRITIC, 9, 6, dooms = 3, hollow = true, shifts = true, battery = 100, midnight = 7, arrows = 2, oddities = 3, seed = 44,
        notes = listOf("Don't trust the voice on the phone.", "This is my handwriting. I wrote these. When did I write these?"),
        hint = "The Critic doesn't trust you. Orders won't do it: kindness and being right will.",
    ),
    ENDLESS(
        "Endless calls",
        null, 9, 6, dooms = 3, hollow = true, shifts = true, battery = 100, midnight = 7, arrows = 2, oddities = 3, seed = null,
        notes = listOf("The way out is where the air turns cold. It hunts by sound. At midnight the walls move."),
        hint = "A new maze every call, and anyone on the phone. Its number is its seed: share it.",
    ),
    ;

    val next: Plan? get() = entries.getOrNull(ordinal + 1)

    fun maze(seed: Long = this.seed ?: 1): Maze = Maze.of(seed, width, height, dooms)

    /** The maze after midnight: the same one when the night doesn't shift. */
    fun shifted(maze: Maze): Maze = if (shifts) maze.shifted() else maze

    /**
     * The clues of [maze], the same for the same seed. The notes lie along a safe way out, in the
     * first part of it, so their lessons come before the dooms; the arrows point along it from
     * crossings; oddities sit where they can be mistaken for a doom.
     */
    fun haunt(maze: Maze): Haunt {
        val random = Random(maze.seed * 7 + 3)
        val deadly = maze.dooms.map { it.at }.toSet()
        val path = mutableListOf(maze.start)
        while (path.last() != maze.gate) path += path.last() + (maze.way(path.last(), maze.gate, deadly) ?: break).first
        val lessons = maze.dooms.map { it.kind }.distinct().map { it.lesson }
        val texts = notes.take(1) + lessons + notes.drop(1)
        val early = path.dropLast(1).take((path.size * 2 / 3).coerceAtLeast(texts.size))
        val notes = texts.mapIndexed { i, text -> early[i * early.size / texts.size] to text }.toMap()
        val arrows =
            path.zipWithNext().filter { (cell, _) -> cell !in notes && maze.exits(cell).size >= 3 }
                .shuffled(random).take(this.arrows)
                .associate { (cell, next) -> cell to Dir.entries.first { cell + it == next } }
        val taken = notes.keys + arrows.keys + deadly + maze.start + maze.gate
        val oddities =
            maze.cells.filter { it !in taken && maze.exits(it).size == 2 }.shuffled(random).take(oddities)
                .mapIndexed { i, cell -> cell to ODDITIES[(i + maze.seed.toInt()).mod(ODDITIES.size)] }.toMap()
        // The Hollow sleeps near the middle, two steps or more from the safe way out.
        val fromPath = path.map { maze.distances(it) }
        val center = Pos(maze.width / 2, maze.height / 2)
        val lair =
            maze.cells.filter { cell -> cell !in taken && cell !in oddities && fromPath.all { (it[cell] ?: 99) >= 2 } }
                .minByOrNull { kotlin.math.abs(it.x - center.x) + kotlin.math.abs(it.y - center.y) }
        return Haunt(arrows, notes, oddities, lair)
    }
}

/** Things in the maze that look as bad as a doom and do nothing. */
private val ODDITIES =
    listOf(
        "a child's shoe, still wet",
        "scratches on the wall, like someone counting, hundreds of them",
        "a phone, face down, still warm",
        "my own name, written in the dust",
        "a dead lantern with a fingernail inside",
        "a chair facing the wall",
        "a music box, open, not playing",
    )
