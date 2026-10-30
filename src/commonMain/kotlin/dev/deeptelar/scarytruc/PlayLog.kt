package dev.deeptelar.scarytruc

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * What was played on this device, to evaluate a play test: for each call what the player said, what
 * was understood, what came back and how the call stood. Kept in the [store], and it leaves the
 * device only when the player exports it. It holds what the player typed, and never a key.
 */
@OptIn(ExperimentalTime::class)
class PlayLog(private val store: Store? = null) {
    private var text: String = runCatching { store?.get(KEY) }.getOrNull().orEmpty()

    /** How many calls are in the log. */
    var calls: Int = Regex("(?m)^$CALL").findAll(text).count()
        private set

    /** Starts a call. */
    fun call(call: Call, mind: String?) {
        calls++
        add("\n$CALL ${stamp()} ${call.plan.title} | ${call.name} | ${call.role} | ${call.lang.code} | model ${mind ?: "none"} | seed ${call.seed}\n")
    }

    /** Something the player did that is not a turn. */
    fun note(what: String) = add("${time()} * $what\n")

    /**
     * The turn from [before] to [after] in [maze]: the player [waited] milliseconds, and the answer
     * [took] as many; [trouble] is why the model wasn't used, if it wasn't.
     */
    fun turn(before: Call, after: Call, maze: Maze, waited: Long, took: Long, trouble: String?) {
        val lines = after.lines.drop(before.lines.size)
        val heard = after.understood?.let { heard ->
            listOfNotNull(
                heard.order.word,
                heard.item?.word,
                heard.toward?.let { "toward ${it.word}" },
                "calm ${heard.calm}",
                "foreign".takeIf { heard.foreign },
                "off topic".takeIf { heard.offTopic },
                "asked again".takeIf { heard.again },
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
    }

    /** The whole log, as a file to hand over. */
    fun exported(): String = "Scary Truc play log, exported ${stamp()}, $calls call${if (calls == 1) "" else "s"}\n$text"

    /** The name of the file for [exported]. */
    fun fileName(): String = "scary-truc-log-${stamp().take(16).replace(':', '-')}.txt"

    private fun add(more: String) {
        text += more
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
        const val CALL = "=== Call"
        const val MAX = 200_000
    }
}
