package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The four agents of the crew, each with a nature that Gemma plays and that [Instinct] follows
 * without it. The weights say how a direction pulls (positive) or pushes (negative) each of them.
 */
enum class Role(
    val title: String,
    val nature: String,
    val treat: Double,
    val trick: Double,
    val hollow: Double,
    val novelty: Double,
    val air: Double,
) {
    PLANNER(
        "Planner",
        "You are methodical. You trust fresh air and hate walking where you have already been. Sweets tempt you, but a plan is a plan.",
        treat = 1.5, trick = -4.0, hollow = -4.0, novelty = 2.0, air = 4.0,
    ),
    SCOUT(
        "Scout",
        "You are curious and jumpy. You love corridors nobody has seen. A scream makes you run the other way at once.",
        treat = 1.5, trick = -8.0, hollow = -3.0, novelty = 4.0, air = 1.0,
    ),
    CODER(
        "Coder",
        "You are always hungry, above all for sweets, and a little reckless. You would follow candy into any dark corridor.",
        treat = 5.0, trick = -3.0, hollow = -2.0, novelty = 1.0, air = 1.0,
    ),
    CRITIC(
        "Critic",
        "You are brave and contrary. Sweets left in a haunted maze are obviously a trap, so you go the other way. Screams are cheap tricks that make you curious. Only the Hollow scares you.",
        treat = -3.0, trick = 1.5, hollow = -4.0, novelty = 2.0, air = 2.0,
    ),
}

enum class Fate { LOST, SAVED, DEAD }

@Serializable
data class Agent(
    val role: Role,
    val at: Pos,
    val fate: Fate = Fate.LOST,
    /** The cells it walked, oldest first, which it remembers. */
    val trail: List<Pos> = listOf(at),
    /** What it said last: on the ofrenda, its last words. */
    val words: String = "",
    /** What killed it. */
    val death: String? = null,
)

enum class MarkKind { TREAT, TRICK }

/** A treat waits until someone eats it. A trick screams for [TRICK_TURNS] turns, then is gone. */
@Serializable
data class Mark(val at: Pos, val kind: MarkKind, val age: Int = 0)

@Serializable
data class Hollow(val at: Pos, val eating: Int = 0)

/** Where an agent goes this turn, or stays when [go] is `null`, and what it says. */
@Serializable
data class Move(val role: Role, val go: Dir?, val says: String, val gemma: Boolean = false)

/**
 * A night in the maze of [seed], the state of the Telar graph: one turn is the crew deciding, then
 * [step]. The player edits only [marks] and the supplies, between turns.
 */
@Serializable
data class Night(
    val seed: Long,
    val crew: List<Agent>,
    val hollow: Hollow,
    val turn: Int = 0,
    val dawn: Int = DAWN,
    val marks: List<Mark> = emptyList(),
    val treats: Int = TREATS,
    val tricks: Int = TRICKS,
    val marigolds: Int = MARIGOLDS,
    val moves: List<Move> = emptyList(),
    val log: List<String> = emptyList(),
) {
    val lost: List<Agent> get() = crew.filter { it.fate == Fate.LOST }
    val over: Boolean get() = lost.isEmpty() || turn >= dawn
    val saved: Int get() = crew.count { it.fate == Fate.SAVED }

    fun markAt(pos: Pos): Mark? = marks.firstOrNull { it.at == pos }

    /** The night with a treat or a trick at [pos], or `null` when it cannot go there. */
    fun place(maze: Maze, pos: Pos, kind: MarkKind): Night? {
        if (over || pos !in maze || markAt(pos) != null || pos == maze.gate) return null
        if (crew.any { it.fate == Fate.LOST && it.at == pos } || hollow.at == pos) return null
        return when (kind) {
            MarkKind.TREAT -> if (treats > 0) copy(marks = marks + Mark(pos, kind), treats = treats - 1) else null
            MarkKind.TRICK -> if (tricks > 0) copy(marks = marks + Mark(pos, kind), tricks = tricks - 1) else null
        }
    }

    companion object {
        const val DAWN: Int = 45
        const val TREATS: Int = 6
        const val TRICKS: Int = 6
        const val MARIGOLDS: Int = 3
        const val TRICK_TURNS: Int = 6

        fun begin(maze: Maze): Night =
            Night(
                seed = maze.seed,
                crew = Role.entries.map { Agent(it, maze.start) },
                hollow = Hollow(maze.lair),
                log = listOf("Night falls. The crew wakes up in the maze."),
            )
    }
}

/**
 * The turn after the crew chose [Night.moves]: everyone walks, the dooms and the Hollow take whom
 * they reach, treats are eaten, tricks fade, and the night is a turn older.
 */
fun Night.step(maze: Maze): Night {
    val log = mutableListOf<String>()
    var marks = marks
    var hollow = hollow
    val crew =
        crew.map { agent ->
            if (agent.fate != Fate.LOST) return@map agent
            val move = moves.firstOrNull { it.role == agent.role }
            val to = move?.go?.takeIf { maze.canGo(agent.at, it) }?.let { agent.at + it } ?: agent.at
            var walked = agent.copy(at = to, trail = (agent.trail + to).takeLast(TRAIL), words = move?.says ?: agent.words)
            val doom = maze.doomAt(to)
            val treat = marks.firstOrNull { it.at == to && it.kind == MarkKind.TREAT }
            when {
                doom != null -> {
                    walked = walked.copy(fate = Fate.DEAD, death = doom.kind.word)
                    log += "${agent.role.title} walked into ${doom.kind.word}."
                }
                to == hollow.at -> {
                    walked = walked.copy(fate = Fate.DEAD, death = "the Hollow")
                    log += "${agent.role.title} walked into the Hollow."
                }
                to == maze.gate -> {
                    walked = walked.copy(fate = Fate.SAVED)
                    log += "${agent.role.title} is out!"
                }
                treat != null -> {
                    marks = marks - treat
                    log += "${agent.role.title} ate a treat."
                }
            }
            walked
        }.toMutableList()

    // The Hollow eats a treat it finds and rests over it; otherwise it hunts the nearest living
    // agent it senses, or the nearest treat when that is closer: a treat is also a lure. It stops to listen
    // every third turn, which is how a running agent gets away.
    if (hollow.eating > 0) {
        hollow = hollow.copy(eating = hollow.eating - 1)
    } else if (turn % HOLLOW_PACE != HOLLOW_PACE - 1) {
        val away = maze.distances(hollow.at, avoid = setOf(maze.gate))
        val prey =
            (crew.filter { it.fate == Fate.LOST }.map { it.at } + marks.filter { it.kind == MarkKind.TREAT }.map { it.at })
                .filter { it in away && away.getValue(it) <= HOLLOW_SENSE }
                .minByOrNull { away.getValue(it) }
        // Nothing within reach of its senses: it roams, never on the gate.
        val roam = maze.exits(hollow.at).filter { hollow.at + it != maze.gate }.randomOrNull(Random(seed * 13 + turn))
        val dir = prey?.let { maze.way(hollow.at, it, avoid = setOf(maze.gate))?.first } ?: roam
        if (dir != null) hollow = hollow.copy(at = hollow.at + dir)
        val treat = marks.firstOrNull { it.at == hollow.at && it.kind == MarkKind.TREAT }
        if (treat != null) {
            marks = marks - treat
            hollow = hollow.copy(eating = HOLLOW_EATS)
            log += "The Hollow found a treat and stops to eat it."
        }
    }
    crew.replaceAll { agent ->
        if (agent.fate == Fate.LOST && agent.at == hollow.at) {
            log += "The Hollow took ${agent.role.title}."
            agent.copy(fate = Fate.DEAD, death = "the Hollow")
        } else {
            agent
        }
    }

    marks = marks.map { if (it.kind == MarkKind.TRICK) it.copy(age = it.age + 1) else it }
        .filter { it.kind == MarkKind.TREAT || it.age < Night.TRICK_TURNS }
    val next = copy(crew = crew, hollow = hollow, marks = marks, turn = turn + 1, moves = emptyList())
    if (next.turn >= next.dawn && next.lost.isNotEmpty()) {
        log += "Dawn. The maze keeps ${next.lost.joinToString(" and ") { it.role.title }}."
    }
    return next.copy(log = (this.log + log).takeLast(LOG))
}

private const val TRAIL = 40
private const val LOG = 60
private const val HOLLOW_EATS = 3
private const val HOLLOW_PACE = 3
private const val HOLLOW_SENSE = 6
