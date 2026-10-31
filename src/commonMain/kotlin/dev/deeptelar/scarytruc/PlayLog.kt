package dev.deeptelar.scarytruc

import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * What was played on this device, to evaluate a play test: for each call what the player said, what
 * was understood, what came back and how the call stood. Kept in the [store], and it leaves the
 * device only when the player exports it. It holds what the player typed, and never a key.
 *
 * It also traces what went on under the game, for when a play test goes wrong: `*` is something the
 * player did, `~` a call to the model and `!` a fault, with its stack.
 *
 * Each call that ended is also kept on its own, as a [Play], for the statistics: the text is cut
 * when it grows too long, and the plays are not.
 */
@OptIn(ExperimentalTime::class)
class PlayLog(private val store: Store? = null) {
    private var text: String = runCatching { store?.get(KEY) }.getOrNull().orEmpty()

    /** How many calls are in the log. */
    var calls: Int = Regex("(?m)^$CALL").findAll(text).count()
        private set

    /** The key of the model: whatever an error message quotes of it, it leaves out of the log. */
    var secret: String? = null

    private val faults = mutableSetOf<String>()

    /** The calls played on this device that ended, oldest first: the first time, read from the log. */
    var plays: List<Play> = readPlays()
        private set

    /** How scared the caller of this call was at the worst, and how many times they ran off. */
    private var worst = 0
    private var bolts = 0

    /** Starts a call. */
    fun call(call: Call, mind: String?) {
        calls++
        worst = call.panic
        bolts = 0
        add("\n$CALL ${stamp()} ${call.plan.title} | ${call.name} | ${call.role} | ${call.lang.code} | model ${mind ?: "none"} | seed ${call.seed}\n")
    }

    /** Something the player did that is not a turn. */
    fun note(what: String) = add("${time()} * $what\n")

    /** A call to the model, or anything else that is no turn and no action of the player. */
    fun trace(what: String) = add("${time()} ~ $what\n")

    /** What went wrong in [where], with its stack. */
    fun error(where: String, error: Throwable) = fault(where, error.stackTraceToString())

    /**
     * Something that went wrong in [where]. The same fault comes once in a visit, however often it
     * happens, and after [MAX_FAULTS] others no more come: a broken frame could fill the log.
     */
    fun fault(where: String, what: String) {
        val first = what.lineSequence().first()
        if (!faults.add("$where $first") || faults.size > MAX_FAULTS + 1) return
        if (faults.size > MAX_FAULTS) return add("${time()} ! more faults, not logged\n")
        add("${time()} ! $where: ${what.take(MAX_FAULT).trim().replace("\n", "\n    ")}\n")
    }

    /** Takes in the faults the page kept in the [store] when the game could not: a crash on the last visit, or at the start. */
    fun recover() {
        val crash = runCatching { store?.get(CRASH) }.getOrNull().orEmpty()
        if (crash.isBlank()) return
        add("${time()} ! before this visit's game started: ${crash.trim().replace("\n", "\n    ")}\n")
        runCatching { store?.set(CRASH, "") }
    }

    /**
     * The turn from [before] to [after] in [maze]: the player [waited] milliseconds, and the answer
     * [took] as many; [trouble] is why the model wasn't used, if it wasn't.
     */
    fun turn(before: Call, after: Call, maze: Maze, waited: Long, took: Long, trouble: String?) {
        worst = maxOf(worst, after.panic)
        if (after.ran) bolts++
        val lines = after.lines.drop(before.lines.size)
        val heard = after.understood?.let { heard ->
            listOfNotNull(
                heard.order.word,
                heard.item?.word,
                heard.toward?.let { "toward ${it.word}" },
                heard.then?.let { "then ${it.word}" },
                "calm ${heard.calm}",
                "foreign".takeIf { heard.foreign },
                "off topic".takeIf { heard.offTopic },
                "asked again".takeIf { heard.again && before.doubt == null },
                "convinced ${heard.convinced}".takeIf { before.doubt != null },
            ).joinToString(", ")
        }
        val said = lines.filter { it.who == Who.YOU }.joinToString(" ") { it.text }.ifEmpty { if (before.lines.isEmpty()) "(answered)" else "(silence)" }
        add(buildString {
            append("${time()} YOU after ${seconds(waited)}: $said\n")
            if (heard != null) append("  understood: $heard\n")
            for (line in lines.filter { it.who != Who.YOU }) append("  ${line.who} after ${seconds(took)}: ${line.text}\n")
            append("  turn ${after.turn}: at ${after.at.x},${after.at.y} facing ${after.facing.word}, walked ${after.walked}, battery ${after.battery}, panic ${after.panic}, trust ${after.trust}, walks with nothing new ${after.stale}")
            after.hollow?.let { append(", hollow ${maze.distances(after.at)[it.at] ?: "?"} steps ${if (it.awake) "awake" else "asleep"}") }
            if (after.hidden) append(", hidden")
            after.doubt?.let { append(", doubting ${it.name.lowercase()}") }
            if (after.lostTrack) append(", lost")
            if (after.afterMidnight) append(", after midnight")
            if (after.bumped) append(", hit a wall")
            if (after.ran) append(", bolted")
            if (after.marks.isNotEmpty()) append(", marks ${after.marks.size}")
            if (after.carried.isNotEmpty()) append(", carrying ${after.carried.joinToString { it.word }}")
            append("\n")
            if (trouble != null) append("  trouble: $trouble\n")
            after.end?.let { append("  END $it${after.death?.let { death -> " ($death)" }.orEmpty()}, ${after.away ?: "?"} steps from the way out, after ${after.turn} turns\n") }
        })
        if (after.over && !before.over) ended(after)
    }

    /** The stranger hung up on [call]. */
    fun hungUp(call: Call) {
        note("hung up, ${call.away ?: "?"} steps from the way out, after ${call.turn} turns")
        ended(call)
    }

    /** Keeps [call], which just ended, with the plays. */
    private fun ended(call: Call) {
        val play = Play.of(call, worst, bolts, stamp()) ?: return
        plays = (plays + play).takeLast(MAX_PLAYS)
        savePlays()
    }

    private fun readPlays(): List<Play> {
        val saved = runCatching { store?.get(PLAYS) }.getOrNull()
        if (!saved.isNullOrBlank()) return runCatching { Json.decodeFromString<List<Play>>(saved) }.getOrDefault(emptyList())
        // Before the plays were kept on their own: what the log still holds of them.
        val old = runCatching { Play.fromLog(text) }.getOrDefault(emptyList()).takeLast(MAX_PLAYS)
        if (old.isNotEmpty()) runCatching { store?.set(PLAYS, Json.encodeToString(old)) }
        return old
    }

    private fun savePlays() {
        runCatching { store?.set(PLAYS, Json.encodeToString(plays)) }
    }

    /** The whole log, as a file to hand over. */
    fun exported(): String = "Scary Truc play log, exported ${stamp()}, $calls call${if (calls == 1) "" else "s"}\n$text"

    /** The name of the file for [exported]. */
    fun fileName(): String = "scary-truc-log-${stamp().take(16).replace(':', '-')}.txt"

    private fun add(more: String) {
        text += secret?.takeIf { it.isNotBlank() }?.let { more.replace(it, "***") } ?: more
        // Too long for the store: the oldest calls go first, whole.
        if (text.length > MAX) {
            val keep = text.indexOf("\n$CALL", text.length - MAX)
            text = if (keep < 0) text.takeLast(MAX) else text.substring(keep)
            calls = Regex("(?m)^$CALL").findAll(text).count()
        }
        runCatching { store?.set(KEY, text) }
    }

    private fun stamp(): String = Clock.System.now().toString()

    private fun time(): String = stamp().substring(11, 19)

    private fun seconds(millis: Long): String = "${millis / 1000}.${millis % 1000 / 100}s"

    private companion object {
        const val KEY = "log"
        const val PLAYS = "plays"
        const val MAX_PLAYS = 500
        const val CRASH = "crash"
        const val MAX_FAULTS = 50
        const val MAX_FAULT = 1500
        const val CALL = "=== Call"
        const val MAX = 200_000
    }
}
