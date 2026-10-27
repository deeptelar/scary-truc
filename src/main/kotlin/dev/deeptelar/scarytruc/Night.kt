package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The four agents of the crew. Gemma plays their [nature]; [rule] is how they react to the
 * player's treats and tricks, always the same, so that the player can plan with it. The weights
 * are their instinct, for a night without Gemma: how a direction pulls (positive) or pushes them.
 */
enum class Role(
    val title: String,
    val nature: String,
    val rule: String,
    val treat: Double,
    val trick: Double,
    val hollow: Double,
    val novelty: Double,
    val air: Double,
) {
    PLANNER(
        "Planner",
        "You are methodical. You trust fresh air and hate walking where you have already been. Sweets tempt you, but a plan is a plan.",
        "Tricked, runs back a step. Eats a treat next to it.",
        treat = 1.5, trick = -4.0, hollow = -12.0, novelty = 2.0, air = 4.0,
    ),
    SCOUT(
        "Scout",
        "You are curious and jumpy. You love corridors nobody has seen. A scream makes you run the other way at once.",
        "Tricked, panics and runs back TWO steps. Eats a treat next to it.",
        treat = 1.5, trick = -8.0, hollow = -10.0, novelty = 4.0, air = 1.0,
    ),
    CODER(
        "Coder",
        "You are always hungry, above all for sweets, and a little reckless. You would follow candy into any dark corridor.",
        "Tricked, runs back a step. Smells a treat TWO steps away and goes for it.",
        treat = 5.0, trick = -3.0, hollow = -8.0, novelty = 1.0, air = 1.0,
    ),
    CRITIC(
        "Critic",
        "You are brave and contrary. Sweets left in a haunted maze are obviously a trap. Screams are cheap tricks that make you curious. Only the Hollow scares you.",
        "The opposite: walks INTO a trick next to it. Runs back from a treat.",
        treat = -3.0, trick = 1.5, hollow = -12.0, novelty = 2.0, air = 2.0,
    ),
}

enum class Fate { LOST, SAVED, DEAD }

@Serializable
data class Agent(
    val role: Role,
    val at: Pos,
    val fate: Fate = Fate.LOST,
    /** The cells it walked, oldest first, which it remembers and can run back along. */
    val trail: List<Pos> = listOf(at),
    /** What it said last: on the ofrenda, its last words. */
    val words: String = "",
    /** What killed it. */
    val death: String? = null,
)

enum class MarkKind(val cost: Int) { TREAT(1), TRICK(2) }

/** A treat waits until someone eats it. A trick screams for [Night.TRICK_TURNS] turns, then is gone. */
@Serializable
data class Mark(val at: Pos, val kind: MarkKind, val age: Int = 0)

@Serializable
data class Hollow(val at: Pos, val eating: Int = 0)

/** Where an agent means to go this turn, or to stay when [go] is `null`, and what it says. */
@Serializable
data class Move(val role: Role, val go: Dir?, val says: String, val gemma: Boolean = false)

/**
 * A night in a maze, the state of the Telar graph. A turn is the crew deciding where it means to
 * go ([moves]), the player answering with one treat or trick, and then [act].
 */
@Serializable
data class Night(
    val plan: Plan,
    val seed: Long,
    val crew: List<Agent>,
    val hollow: Hollow?,
    val dawn: Int,
    val candy: Int,
    val marigolds: Int,
    val lostCandy: List<Pos>,
    val turn: Int = 0,
    val marks: List<Mark> = emptyList(),
    /** The player placed their treat or trick of this turn. */
    val played: Boolean = false,
    /** Every cell a lantern has lit tonight: the player sees these, and nothing else. */
    val seen: Set<Pos> = emptySet(),
    val moves: List<Move> = emptyList(),
    val log: List<String> = emptyList(),
) {
    val lost: List<Agent> get() = crew.filter { it.fate == Fate.LOST }
    val over: Boolean get() = lost.isEmpty() || turn >= dawn
    val saved: Int get() = crew.count { it.fate == Fate.SAVED }

    fun markAt(pos: Pos): Mark? = marks.firstOrNull { it.at == pos }

    /** The night with a treat or a trick at [pos], or `null` when it cannot go there. */
    fun place(maze: Maze, pos: Pos, kind: MarkKind): Night? {
        if (over || played || candy < kind.cost || pos !in seen || markAt(pos) != null || pos == maze.gate) return null
        if (lost.any { it.at == pos } || hollow?.at == pos) return null
        return copy(marks = marks + Mark(pos, kind), candy = candy - kind.cost, played = true)
    }

    /** The cells the lanterns of the lost light now, [LANTERN] steps around each. */
    fun lit(maze: Maze): Set<Pos> = lost.flatMap { agent -> maze.distances(agent.at).filterValues { it <= LANTERN }.keys }.toSet()

    companion object {
        const val TRICK_TURNS: Int = 3
        const val LANTERN: Int = 2

        fun begin(plan: Plan, maze: Maze): Night {
            val night =
                Night(
                    plan = plan,
                    seed = maze.seed,
                    crew = plan.crew.mapIndexed { i, role -> Agent(role, maze.starts[i]) },
                    hollow = if (plan.hollow) Hollow(maze.lair) else null,
                    dawn = plan.dawn,
                    candy = plan.candy,
                    marigolds = plan.marigolds,
                    lostCandy = maze.candy,
                    log = listOf(if (plan.crew.size == 1) "Night falls. The ${plan.crew.single().title} wakes up in the maze." else "Night falls. The crew wakes up in the maze, each one alone."),
                )
            return night.copy(seen = night.lit(maze))
        }
    }
}

/**
 * The turn after the crew chose [Night.moves] and the player answered: everyone reacts to the
 * treats and tricks by its [Role.rule] and walks, the dooms and the Hollow take whom they reach,
 * and the night is a turn older.
 */
fun Night.act(maze: Maze): Night {
    val log = mutableListOf<String>()
    var marks = marks
    var hollow = hollow
    var candy = candy
    var lostCandy = lostCandy
    val crew =
        crew.map { agent ->
            if (agent.fate != Fate.LOST) return@map agent
            val move = moves.firstOrNull { it.role == agent.role }
            val (path, reaction) = react(maze, agent, move?.go, marks)
            val to = path.lastOrNull() ?: agent.at
            var walked = agent.copy(at = to, trail = (agent.trail + path).takeLast(TRAIL), words = reaction ?: move?.says ?: agent.words)
            // Running into the Hollow is death, unless it is busy feeding on someone else.
            val caught = path.firstOrNull { it == hollow?.at && hollow?.eating == 0 }
            val doom = path.firstNotNullOfOrNull { maze.doomAt(it) }
            when {
                caught != null -> {
                    walked = walked.copy(at = caught, fate = Fate.DEAD, death = "the Hollow")
                    log += "${agent.role.title} ran into the Hollow."
                }
                doom != null -> {
                    walked = walked.copy(at = doom.at, fate = Fate.DEAD, death = doom.kind.word)
                    log += "${agent.role.title} walked into ${doom.kind.word}."
                }
                to == maze.gate -> {
                    walked = walked.copy(fate = Fate.SAVED)
                    log += "${agent.role.title} is out!"
                }
            }
            if (walked.fate == Fate.LOST) {
                marks.firstOrNull { it.at == to && it.kind == MarkKind.TREAT }?.let {
                    marks = marks - it
                    log += "${agent.role.title} ate the treat."
                }
                if (to in lostCandy) {
                    lostCandy = lostCandy - to
                    candy++
                    log += "${agent.role.title} found lost candy: one more in your bag."
                }
            }
            walked
        }.toMutableList()

    // The Hollow eats a treat it finds and rests over it; otherwise it hunts the nearest living
    // agent it senses, or the nearest treat when that is closer: a treat is also a lure. It stops
    // to listen every third turn, which is how a running agent gets away, and never walks into a
    // trick.
    if (hollow != null) {
        if (hollow.eating > 0) {
            hollow = hollow.copy(eating = hollow.eating - 1)
        } else if (turn % HOLLOW_PACE != HOLLOW_PACE - 1) {
            // A jack-o'-lantern keeps it out, as they were carved to keep evil spirits out.
            val barred = setOf(maze.gate) + marks.filter { it.kind == MarkKind.TRICK }.map { it.at }
            val away = maze.distances(hollow.at, avoid = barred)
            val prey =
                (crew.filter { it.fate == Fate.LOST }.map { it.at } + marks.filter { it.kind == MarkKind.TREAT }.map { it.at })
                    .filter { it in away && away.getValue(it) <= HOLLOW_SENSE }
                    .minByOrNull { away.getValue(it) }
            // Nothing within reach of its senses: it roams, never on the gate.
            val roam = maze.exits(hollow.at).filter { hollow.at + it !in barred }.randomOrNull(Random(seed * 13 + turn))
            val dir = prey?.let { maze.way(hollow.at, it, avoid = barred)?.first } ?: roam
            if (dir != null) hollow = hollow.copy(at = hollow.at + dir)
            marks.firstOrNull { it.at == hollow.at && it.kind == MarkKind.TREAT }?.let {
                marks = marks - it
                hollow = hollow.copy(eating = HOLLOW_EATS)
                log += "Something ate a treat in the dark."
            }
        }
        // It takes one, and feeds: the others get away while it does.
        val victim = crew.indexOfFirst { it.fate == Fate.LOST && it.at == hollow.at }
        if (victim >= 0) {
            log += "The Hollow took ${crew[victim].role.title}."
            crew[victim] = crew[victim].copy(fate = Fate.DEAD, death = "the Hollow")
            hollow = hollow.copy(eating = HOLLOW_FEEDS)
        }
    }

    marks = marks.map { if (it.kind == MarkKind.TRICK) it.copy(age = it.age + 1) else it }
        .filter { it.kind == MarkKind.TREAT || it.age < Night.TRICK_TURNS }
    var next = copy(crew = crew, hollow = hollow, marks = marks, candy = candy, lostCandy = lostCandy, turn = turn + 1, played = false, moves = emptyList())
    next = next.copy(seen = seen + next.lit(maze))
    if (next.turn >= next.dawn && next.lost.isNotEmpty()) {
        log += "Dawn. The maze keeps ${next.lost.joinToString(" and ") { it.role.title }}."
    }
    return next.copy(log = (this.log + log).takeLast(LOG))
}

/**
 * Where [agent] walks this turn, cell by cell, when it meant to go [go] and finds [marks], and
 * what it shouts when a mark changed its mind.
 */
fun react(maze: Maze, agent: Agent, go: Dir?, marks: List<Mark>): Pair<List<Pos>, String?> {
    val here = agent.at
    val target = go?.takeIf { maze.canGo(here, it) }?.let { here + it }
    fun has(pos: Pos, kind: MarkKind) = marks.any { it.at == pos && it.kind == kind }
    val next = maze.exits(here).map { here + it }

    // Back along its own trail, the way it came, as far as it remembers.
    fun back(steps: Int): List<Pos> {
        val path = mutableListOf<Pos>()
        var at = here
        for (cell in agent.trail.dropLast(1).asReversed()) {
            if (path.size == steps) break
            if (cell == at) continue
            if (maze.exits(at).none { at + it == cell }) break
            path += cell
            at = cell
        }
        return path
    }

    if (agent.role == Role.CRITIC) {
        if (target != null && has(target, MarkKind.TREAT)) return back(1) to "Free candy? Obviously a trap."
        next.firstOrNull { has(it, MarkKind.TRICK) }?.let { return listOf(it) to "A scream? Cheap trick. Let me see." }
        return listOfNotNull(target) to null
    }
    if (target != null && has(target, MarkKind.TRICK)) {
        return if (agent.role == Role.SCOUT) back(2) to "AAAH! Back, back, back!" else back(1) to "What was that?! No way."
    }
    if (agent.role == Role.CODER) {
        val reach = maze.distances(here)
        val treat = marks.filter { it.kind == MarkKind.TREAT && reach[it.at] in 1..2 }.minByOrNull { reach.getValue(it.at) }
        if (treat != null) {
            val step = maze.way(here, treat.at)!!.first
            if (!has(here + step, MarkKind.TRICK)) return listOf(here + step) to "Caramel! Mine!"
        }
    } else {
        next.firstOrNull { has(it, MarkKind.TREAT) }?.let { return listOf(it) to "Ooh, a treat." }
    }
    return listOfNotNull(target) to null
}

private const val TRAIL = 40
private const val LOG = 60
private const val HOLLOW_EATS = 4
private const val HOLLOW_FEEDS = 3
private const val HOLLOW_PACE = 3
private const val HOLLOW_SENSE = 5
