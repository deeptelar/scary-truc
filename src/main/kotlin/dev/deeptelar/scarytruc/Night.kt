package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The four agents of the crew. Gemma plays their [nature], and decides how they take the player's
 * treats and tricks; [trait] says it in a few words for the player. Each starts the night with
 * [nerve]. The weights are their instinct, for a night without Gemma: how a direction pulls
 * (positive) or pushes them.
 */
enum class Role(
    val title: String,
    val nature: String,
    val trait: String,
    /** What it fears most, which only the Hollow knows and whispers back to it. */
    val fear: String,
    val nerve: Int,
    val treat: Double,
    val trick: Double,
    val hollow: Double,
    val novelty: Double,
    val air: Double,
) {
    PLANNER(
        "Planner",
        "You are methodical. You trust fresh air and hate walking where you have already been. Sweets tempt you, but a plan is a plan.",
        "Methodical. Follows fresh air.",
        "a plan of yours that failed and cost everyone",
        nerve = 70, treat = 1.5, trick = -4.0, hollow = -12.0, novelty = 2.0, air = 4.0,
    ),
    SCOUT(
        "Scout",
        "You are curious and jumpy. You love corridors nobody has seen. A scream makes you want to run the other way.",
        "Jumpy. Loves corridors nobody has seen.",
        "being left behind, alone in the dark, forgotten",
        nerve = 45, treat = 1.5, trick = -8.0, hollow = -10.0, novelty = 4.0, air = 1.0,
    ),
    CODER(
        "Coder",
        "You are always hungry, above all for sweets, and a little reckless. You would follow candy into any dark corridor.",
        "Always hungry. Reckless for sweets.",
        "the bug you shipped that took everything down at 3 a.m.",
        nerve = 60, treat = 5.0, trick = -3.0, hollow = -8.0, novelty = 1.0, air = 1.0,
    ),
    CRITIC(
        "Critic",
        "You are brave and contrary. Sweets left in a haunted maze are obviously a trap. Screams are cheap tricks that make you curious. Only the Hollow scares you.",
        "Contrary. Distrusts treats, curious about screams.",
        "the cruel review you wrote that made someone quit",
        nerve = 85, treat = -3.0, trick = 1.5, hollow = -12.0, novelty = 2.0, air = 2.0,
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
    /** How brave it still is, from 0 to 100. Below [PANIC] it may bolt anywhere. */
    val nerve: Int = role.nerve,
    /** How many screams it heard tonight: each scares it less than the one before. */
    val screams: Int = 0,
    /** For how many more turns its lantern is out. */
    val dark: Int = 0,
)

enum class MarkKind(val cost: Int) { TREAT(1), TRICK(2) }

/** A treat waits until someone eats it. A trick screams for [Night.TRICK_TURNS] turns, then is gone. */
@Serializable
data class Mark(val at: Pos, val kind: MarkKind, val age: Int = 0)

/** The Hollow sleeps in its lair until something wakes it: someone near, a scream, or midnight. */
@Serializable
data class Hollow(val at: Pos, val eating: Int = 0, val awake: Boolean = false)

/** What the Hollow whispered to one of the crew from the dark, after [turn]. */
@Serializable
data class Whisper(val role: Role, val text: String, val turn: Int)

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
    /** Every cell a lantern has shone on from right next to it: only there does a doom show. */
    val near: Set<Pos> = emptySet(),
    val moves: List<Move> = emptyList(),
    /** The Hollow's last whisper. */
    val whisper: Whisper? = null,
    val log: List<String> = emptyList(),
) {
    val lost: List<Agent> get() = crew.filter { it.fate == Fate.LOST }
    val over: Boolean get() = lost.isEmpty() || turn >= dawn
    val saved: Int get() = crew.count { it.fate == Fate.SAVED }

    /** Half the night: the maze moves, and the Hollow wakes and stops resting. */
    val midnight: Int get() = dawn / 2
    val afterMidnight: Boolean get() = turn >= midnight

    fun markAt(pos: Pos): Mark? = marks.firstOrNull { it.at == pos }

    /** The whisper [role] still has in its ears: for a turn after it was said. */
    fun whisperTo(role: Role): Whisper? = whisper?.takeIf { it.role == role && turn - it.turn <= 1 }

    /**
     * Whom the Hollow whispers to now: the nearest of the crew within [WHISPER_REACH] steps, when
     * it is awake and has been quiet for [WHISPER_EVERY] turns.
     */
    fun whisperTarget(maze: Maze): Agent? {
        val hollow = hollow?.takeIf { it.awake } ?: return null
        if (turn - (whisper?.turn ?: -99) < WHISPER_EVERY) return null
        val steps = maze.distances(hollow.at)
        return lost.filter { (steps[it.at] ?: 99) <= WHISPER_REACH }.minByOrNull { steps.getValue(it.at) }
    }

    /** The night after the Hollow whispered [text] to [role]: its nerve takes the blow. */
    fun heard(role: Role, text: String): Night =
        copy(
            whisper = Whisper(role, text, turn),
            crew = crew.map { if (it.role == role) it.copy(nerve = (it.nerve - WHISPER_HURTS).coerceAtLeast(0)) else it },
            log = (log + "The Hollow whispers to ${role.title}.").takeLast(LOG),
        )

    /** The night with a treat or a trick at [pos], or `null` when it cannot go there. */
    fun place(maze: Maze, pos: Pos, kind: MarkKind): Night? {
        if (over || played || candy < kind.cost || pos !in seen || markAt(pos) != null || pos == maze.gate) return null
        if (lost.any { it.at == pos } || hollow?.at == pos) return null
        return copy(marks = marks + Mark(pos, kind), candy = candy - kind.cost, played = true)
    }

    /** The cells the lanterns that still burn light now, [steps] around each. */
    fun lit(maze: Maze, steps: Int = LANTERN): Set<Pos> =
        lost.filter { it.dark == 0 }.flatMap { agent -> maze.distances(agent.at).filterValues { it <= steps }.keys }.toSet()

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
            return night.copy(seen = night.lit(maze), near = night.lit(maze, 1))
        }
    }
}

/**
 * The turn after the crew chose [Night.moves] and the player answered: everyone walks where it
 * chose, unless it panics, the dooms and the Hollow take whom they reach, nerves fray or mend, and
 * the night is a turn older. [maze] is the maze of this turn: after midnight, the shifted one.
 */
fun Night.act(maze: Maze): Night {
    val log = mutableListOf<String>()
    var marks = marks
    var hollow = hollow
    var candy = candy
    var lostCandy = lostCandy
    fun chance(salt: Int) = Random(seed * 31 + turn * 131L + salt).nextDouble()

    val crew =
        crew.map { agent ->
            if (agent.fate != Fate.LOST) return@map agent
            val move = moves.firstOrNull { it.role == agent.role }
            var go = move?.go?.takeIf { maze.canGo(agent.at, it) }
            var words = move?.says ?: agent.words
            // Out of nerve, it may bolt down any corridor, whatever it meant to do.
            if (agent.nerve < PANIC && chance(agent.role.ordinal) < 0.5) {
                val exits = maze.exits(agent.at)
                if (exits.isNotEmpty()) {
                    go = exits[(chance(agent.role.ordinal + 10) * exits.size).toInt()]
                    words = PANIC_LINES[(chance(agent.role.ordinal + 20) * PANIC_LINES.size).toInt()]
                    log += "${agent.role.title} panics and runs."
                }
            }
            val to = go?.let { agent.at + it } ?: agent.at
            var walked = agent.copy(at = to, trail = (agent.trail + to).takeLast(TRAIL), words = words)
            val doom = maze.doomAt(to)
            when {
                // Running into the Hollow is death, unless it sleeps or is busy feeding.
                hollow != null && to == hollow.at && hollow.awake && hollow.eating == 0 -> {
                    walked = walked.copy(fate = Fate.DEAD, death = "the Hollow")
                    log += "${agent.role.title} ran into the Hollow."
                }
                doom != null -> {
                    walked = walked.copy(fate = Fate.DEAD, death = doom.kind.word)
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
                    walked = walked.copy(nerve = (walked.nerve + 15).coerceAtMost(100))
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

    if (hollow != null) {
        val barred = setOf(maze.gate) + marks.filter { it.kind == MarkKind.TRICK }.map { it.at }
        val away = maze.distances(hollow.at, avoid = barred)
        val near = maze.distances(hollow.at)
        val screams = marks.filter { it.kind == MarkKind.TRICK && it.age == 0 && (near[it.at] ?: 99) <= HOLLOW_HEARS }
        if (!hollow.awake) {
            // A scream, someone close, or midnight wakes it. It wakes this turn and hunts the next.
            val close = crew.any { it.fate == Fate.LOST && (near[it.at] ?: 99) <= HOLLOW_SENSE }
            if (screams.isNotEmpty() || close || turn + 1 >= midnight) {
                hollow = hollow.copy(awake = true)
                log += if (screams.isNotEmpty()) "The scream woke something up." else "Something wakes up in the maze."
            }
        } else if (hollow.eating > 0) {
            hollow = hollow.copy(eating = hollow.eating - 1)
        } else if (afterMidnight || turn % HOLLOW_PACE != HOLLOW_PACE - 1) {
            // It goes for the nearest thing it senses: one of the crew, a treat, or a scream it
            // heard from far. It never walks into a jack-o'-lantern: it waits next to it.
            val prey =
                (crew.filter { it.fate == Fate.LOST }.map { it.at } + marks.filter { it.kind == MarkKind.TREAT }.map { it.at })
                    .filter { (away[it] ?: 99) <= if (afterMidnight) HOLLOW_SENSE + 2 else HOLLOW_SENSE }
                    .map { it to away.getValue(it) } +
                    screams.map { it.at to near.getValue(it.at) }
            val target = prey.minByOrNull { it.second }?.first
            val roam = maze.exits(hollow.at).filter { hollow.at + it !in barred }.randomOrNull(Random(seed * 13 + turn))
            val dir = target?.let { maze.way(hollow.at, it, avoid = barred - it)?.first } ?: roam
            if (dir != null && hollow.at + dir !in barred) hollow = hollow.copy(at = hollow.at + dir)
            marks.firstOrNull { it.at == hollow.at && it.kind == MarkKind.TREAT }?.let {
                marks = marks - it
                hollow = hollow.copy(eating = HOLLOW_EATS)
                log += "Something ate a treat in the dark."
            }
        }
        // It takes one, and feeds: the others get away while it does.
        val victim = crew.indexOfFirst { it.fate == Fate.LOST && it.at == hollow.at }
        if (victim >= 0 && hollow.awake) {
            log += "The Hollow took ${crew[victim].role.title}."
            crew[victim] = crew[victim].copy(fate = Fate.DEAD, death = "the Hollow")
            hollow = hollow.copy(eating = HOLLOW_FEEDS)
        }
    }

    // Nerves: screams close by, the Hollow's breath and a friend's death fray them; a quiet turn mends them.
    val died = crew.count { it.fate == Fate.DEAD } > this.crew.count { it.fate == Fate.DEAD }
    crew.replaceAll { agent ->
        if (agent.fate != Fate.LOST) return@replaceAll agent
        val steps = maze.distances(agent.at)
        val scream = marks.any { it.kind == MarkKind.TRICK && (steps[it.at] ?: 99) <= 2 }
        val breath = hollow?.takeIf { it.awake }?.let { steps[it.at] }?.takeIf { it <= HEARING }
        var nerve = agent.nerve
        if (scream) nerve -= 16 / (1 + agent.screams)
        if (breath != null) nerve -= if (breath <= 2) 20 else 10
        if (died) nerve -= 15
        if (!scream && breath == null && !died) nerve += 4
        var dark = (agent.dark - 1).coerceAtLeast(0)
        if (agent.dark == 0 && plan.hollow && chance(agent.role.ordinal + 40) < LANTERN_OUT) {
            dark = 3
            log += "${agent.role.title}'s lantern went out."
        }
        agent.copy(nerve = nerve.coerceIn(0, 100), screams = agent.screams + if (scream) 1 else 0, dark = dark)
    }

    marks = marks.map { if (it.kind == MarkKind.TRICK) it.copy(age = it.age + 1) else it }
        .filter { it.kind == MarkKind.TREAT || it.age < Night.TRICK_TURNS }
    var next = copy(crew = crew, hollow = hollow, marks = marks, candy = candy, lostCandy = lostCandy, turn = turn + 1, played = false, moves = emptyList())
    next = next.copy(seen = seen + next.lit(maze), near = near + next.lit(maze, 1))
    if (next.turn == midnight && !next.over) log += "Midnight. The siren. The maze is not the same maze."
    if (next.turn >= next.dawn && next.lost.isNotEmpty()) {
        log += "Dawn. The maze keeps ${next.lost.joinToString(" and ") { it.role.title }}."
    }
    return next.copy(log = (this.log + log).takeLast(LOG))
}

const val PANIC: Int = 20
const val WHISPER_REACH: Int = 6
const val WHISPER_EVERY: Int = 4
const val WHISPER_HURTS: Int = 15

private val PANIC_LINES = listOf("RUN! RUN!", "I can't, I CAN'T!", "Get away from me!", "No no no no no!")
private const val TRAIL = 40
private const val LOG = 60
private const val HOLLOW_EATS = 4
private const val HOLLOW_FEEDS = 3
private const val HOLLOW_PACE = 3
private const val HOLLOW_SENSE = 5
private const val HOLLOW_HEARS = 9
private const val LANTERN_OUT = 0.05
