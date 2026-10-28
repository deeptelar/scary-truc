package dev.deeptelar.scarytruc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * How the nights play for a guide who knows only what the caller says: they follow arrows and the
 * cold, keep to the right wall, stay away from anything strange, and say hide when it's close.
 * Prints the outcomes, nothing to pass: ./gradlew test --tests '*Balance*' -Pbalance=40
 */
class Balance {
    @Test
    fun nights() = runBlocking {
        val seeds = (System.getProperty("balance") ?: return@runBlocking).toInt()
        for (plan in Plan.entries) {
            val ends = mutableMapOf<String, Int>()
            val turns = mutableListOf<Int>()
            val taken = mutableListOf<Int>()
            val away = mutableListOf<Int>()
            for (seed in 1L..seeds) {
                val run = CallRun(plan, seed, Keywords, Script)
                var call = run.begin()
                while (!call.over) call = run.say(explore(call, run.maze, run.haunt))
                val end = call.end!!.name
                ends[end] = (ends[end] ?: 0) + 1
                if (call.end == End.OUT) turns += call.turn
                if (call.end == End.TAKEN) taken += call.turn
                if (call.end != End.OUT) away += call.away ?: -1
                if (call.end == End.TAKEN && taken.size == 1 && System.getProperty("show") == plan.name) call.lines.forEach { println("LINE ${it.who}: ${it.text}") }
            }
            println("BALANCE ${plan.name}: $ends, steps away ${away.sorted()}, out after ${turns.sorted().let { if (it.isEmpty()) "-" else "${it.first()}..${it[it.size / 2]}..${it.last()}" }} turns, taken on ${taken.sorted()}")
        }
    }

    fun explore(call: Call, maze: Maze, haunt: Haunt): String {
        val said = call.lines.lastOrNull { it.who == Who.CALLER }?.text.orEmpty()
        if ("still here" in said || "I can hear it" in said && ("close" in said || "corner" in said)) return "Shh. Hide. Breathe slowly, I'm here with you."
        val words = listOf("ahead", "right", "back", "left")
        if (call.panic >= BOLT - 10 && "I can hear it" !in said) return "Breathe. Slowly. I'm here with you, okay? You're safe."
        val open = (0..3).filter { maze.canGo(call.at, call.facing.turned(it)) }
        maze.exits(call.at).firstOrNull { call.at + it == maze.gate }?.let { return "Go " + words[call.facing.side(it)] }
        haunt.arrows[call.at]?.let { return "Follow the arrow, go " + words[call.facing.side(it)] }
        // Away from what they hear: breathing or footsteps "from" a way.
        val heard = listOf("ahead", "my right", "behind me", "my left").indexOfFirst { ("from $it" in said) }
        fun strange(quarter: Int) = quarter == heard || (call.at + call.facing.turned(quarter)).let { maze.doomAt(it) != null || it in haunt.oddities }
        // Somewhere new first, as a player with a notebook would; then the right-hand wall.
        val new = { quarter: Int -> call.at + call.facing.turned(quarter) !in call.mapped }
        val choice =
            listOf(1, 0, 3, 2).filter { it in open }.sortedBy { if (new(it)) 0 else 1 }
                .let { ways -> ways.firstOrNull { !strange(it) } ?: ways.firstOrNull { maze.doomAt(call.at + call.facing.turned(it)) == null } }
        return if (choice == null) "Stay" else "Okay, breathe. Go ${words[choice]}"
    }
}
