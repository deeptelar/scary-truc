package dev.deeptelar.scarytruc

/**
 * What an agent notices down one corridor: only this reaches its mind, never the map. Distances
 * are steps along the corridors; `null` is nothing noticed.
 *
 * @property treat a sweet smell, from [SMELL] steps.
 * @property trick a scream, from [HEARING] steps.
 * @property hollow the Hollow, heard breathing from [HEARING] steps or seen down a straight corridor.
 * @property air fresh air: the gate is [AIR] steps away or less, this way.
 * @property gate the gate itself, in sight.
 * @property friends agents of the crew in sight.
 * @property ghosts the dead of the crew in sight.
 * @property been how often it walked into the next cell before, as far as it remembers.
 */
data class Way(
    val dir: Dir,
    val treat: Int? = null,
    val trick: Int? = null,
    val hollow: Int? = null,
    val hollowSeen: Boolean = false,
    val air: Boolean = false,
    val gate: Int? = null,
    val friends: List<Role> = emptyList(),
    val ghosts: List<Role> = emptyList(),
    val been: Int = 0,
    val cameFrom: Boolean = false,
)

fun senses(night: Night, maze: Maze, agent: Agent): List<Way> {
    val here = agent.at
    val previous = agent.trail.dropLast(1).lastOrNull { it != here }
    // The first step towards each thing, along the corridors, if it is close enough to notice.
    fun noticed(things: List<Pos>, range: Int): Map<Dir, Int> =
        things.mapNotNull { maze.way(here, it) }.filter { it.second <= range }
            .groupBy({ it.first }, { it.second }).mapValues { (_, steps) -> steps.min() }

    val treats = noticed(night.marks.filter { it.kind == MarkKind.TREAT }.map { it.at }, SMELL)
    val tricks = noticed(night.marks.filter { it.kind == MarkKind.TRICK }.map { it.at }, HEARING)
    val hollow = noticed(listOfNotNull(night.hollow?.at), HEARING)
    val air = noticed(listOf(maze.gate), AIR)
    return maze.exits(here).map { dir ->
        val sight = generateSequence(here + dir) { it + dir }.take(SIGHT)
            .withIndex().takeWhile { (i, pos) -> i == 0 || maze.canGo(pos + dir.opposite, dir) }
            .map { it.value }.toList()
        val seenHollow = sight.indexOf(night.hollow?.at).takeIf { it >= 0 }?.plus(1)
        Way(
            dir = dir,
            treat = treats[dir],
            trick = tricks[dir],
            hollow = seenHollow ?: hollow[dir],
            hollowSeen = seenHollow != null,
            air = dir in air,
            gate = sight.indexOf(maze.gate).takeIf { it >= 0 }?.plus(1),
            friends = night.crew.filter { it.fate == Fate.LOST && it.role != agent.role && it.at in sight }.map { it.role },
            ghosts = night.crew.filter { it.fate == Fate.DEAD && it.at in sight }.map { it.role },
            been = agent.trail.dropLast(1).count { it == here + dir },
            cameFrom = previous == here + dir,
        )
    }
}

/** The senses in words, for Gemma. */
fun describe(ways: List<Way>, night: Night, agent: Agent): String =
    buildString {
        val others = night.lost.filter { it.role != agent.role && it.at == agent.at }
        append("It is turn ${night.turn + 1}; dawn comes after turn ${night.dawn}.\n")
        if (others.isNotEmpty()) append("With you here: ${others.joinToString { it.role.title }}.\n")
        val dead = night.crew.filter { it.fate == Fate.DEAD }
        if (dead.isNotEmpty()) append("Dead tonight: ${dead.joinToString { "${it.role.title} (${it.death})" }}.\n")
        if (ways.isEmpty()) append("There is no way out of this cell.\n")
        for (way in ways) {
            val things = mutableListOf<String>()
            way.gate?.let { things += "THE GATE OUT, $it steps away" }
            if (way.air) things += "fresh air blows from there"
            way.hollow?.let { things += if (way.hollowSeen) "THE HOLLOW, you see it $it steps away" else "heavy breathing, $it steps away" }
            way.trick?.let { things += "a scream and something white moving, $it steps away" }
            way.treat?.let { things += "a sweet smell of caramel, $it steps away" }
            if (way.friends.isNotEmpty()) things += "you see ${way.friends.joinToString { it.title }}"
            if (way.ghosts.isNotEmpty()) things += "the ghost of ${way.ghosts.joinToString { it.title }} stands there"
            things += when {
                way.cameFrom -> "you just came from there"
                way.been > 0 -> "you have been there ${way.been} times"
                else -> "you have never been there"
            }
            append("${way.dir.word.replaceFirstChar { it.uppercase() }}: ${things.joinToString("; ")}.\n")
        }
    }

const val SMELL: Int = 5
const val HEARING: Int = 4
const val AIR: Int = 14
const val SIGHT: Int = 5
