package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable

/**
 * A call played on this device, once it ended, as the statistics count it: where, how it ended,
 * how far from the way out, and whether fear had a hand in it.
 *
 * @property turns how many turns the call lasted.
 * @property panic how scared the caller was at the end; [worst], at the worst of it.
 * @property bolts how many times the caller ran off in a panic.
 * @property panicked whether fear took them: they ran into what took them, or the Hollow heard their fear.
 * @property at when it ended, as the log writes it.
 */
@Serializable
data class Play(
    val plan: Plan,
    val end: End,
    val death: String? = null,
    val away: Int? = null,
    val turns: Int = 0,
    val panic: Int = 0,
    val worst: Int = 0,
    val bolts: Int = 0,
    val panicked: Boolean = false,
    val at: String = "",
) {
    /** How it ended, in a few words in [lang]. */
    fun ending(lang: Lang): String =
        when (end) {
            End.OUT -> lang.t("Got out", "Salió")
            End.TAKEN -> lang.t("Taken by the Hollow", "Se lo llevó el Hueco")
            End.BATTERY -> lang.t("Battery died", "Sin batería")
            End.ALONE -> lang.t("You hung up", "Colgaste")
            End.TRUST -> lang.t("They hung up on you", "Te colgó")
            End.DOOM -> DoomKind.entries.firstOrNull { it.word == death }?.word(lang) ?: lang.t("Something in the dark", "Algo en la oscuridad")
        }

    companion object {
        /** [call], once over: the caller was [worst] scared at most, and ran off [bolts] times. */
        fun of(call: Call, worst: Int, bolts: Int, at: String): Play? {
            val end = call.end ?: return null
            return Play(call.plan, end, call.death, call.away, call.turn, call.panic, maxOf(worst, call.panic), bolts, panicked(end, call.ran, call.panic), at)
        }

        /** Whether fear took the caller who ended with [end]: they [ran] into it, or the Hollow found them too [panic]ked to hide. */
        private fun panicked(end: End, ran: Boolean, panic: Int): Boolean =
            when (end) {
                End.TAKEN -> ran || panic >= CALM_ENOUGH
                End.DOOM -> ran
                else -> false
            }

        /**
         * The calls that ended in a play log written as [PlayLog] writes it: for the calls played
         * before the statistics were kept on their own.
         */
        fun fromLog(text: String): List<Play> {
            val plays = mutableListOf<Play>()
            var plan: Plan? = null
            var started = ""
            var last = ""
            var worst = 0
            var bolts = 0
            fun ended(end: End, death: String?, away: Int?, turns: Int) {
                val where = plan ?: return
                val panic = PANIC.find(last)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                plays += Play(where, end, death, away, turns, panic, maxOf(worst, panic), bolts, panicked(end, ", bolted" in last, panic), started)
                plan = null
            }
            for (line in text.lineSequence()) {
                when {
                    line.startsWith(CALL) -> {
                        val rest = line.removePrefix(CALL)
                        started = rest.substringBefore(' ')
                        plan = Plan.entries.firstOrNull { rest.substringAfter(' ').startsWith("${it.title} |") }
                        last = ""
                        worst = 0
                        bolts = 0
                    }
                    line.startsWith("  turn ") -> {
                        last = line
                        worst = maxOf(worst, PANIC.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0)
                        if (", bolted" in line) bolts++
                    }
                    else -> {
                        END.find(line)?.let { m ->
                            End.entries.firstOrNull { it.name == m.groupValues[1] }?.let { ended(it, m.groupValues[2].ifEmpty { null }, m.groupValues[3].toIntOrNull(), m.groupValues[4].toInt()) }
                        }
                        HUNG_UP.find(line)?.let { m -> ended(End.ALONE, "the dark", m.groupValues[1].toIntOrNull(), m.groupValues[2].toInt()) }
                    }
                }
            }
            return plays
        }

        private const val CALL = "=== Call "
        private val PANIC = Regex(", panic (\\d+),")
        private val END = Regex("^  END (\\w+)(?: \\((.*)\\))?, (\\S+) steps from the way out, after (\\d+) turns")
        private val HUNG_UP = Regex("\\* hung up, (\\S+) steps from the way out, after (\\d+) turns")
    }
}
