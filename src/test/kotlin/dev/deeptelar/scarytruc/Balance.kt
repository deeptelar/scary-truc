package dev.deeptelar.scarytruc

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * How hard each night is, played by instinct: with no help, and with a simple helper that spends
 * its one play a turn on whoever is about to walk into a doom. It prints and checks nothing:
 * ./gradlew test --tests '*Balance*' -Pbalance=200 -i
 */
class Balance {
    @Test
    fun nights() =
        runBlocking {
            val nights = System.getProperty("balance")?.toInt() ?: return@runBlocking
            for (plan in Plan.entries) {
                for (helped in listOf(0, 1, 2)) {
                    var saved = 0
                    var total = 0
                    val deaths = mutableMapOf<String, Int>()
                    for (i in 1..nights) {
                        // The fixed nights of the plans, but on other mazes of the same size, to see the spread.
                        val run = NightRun(plan, if (i == 1 && plan.seed != null) plan.seed else 1000L + i, Instinct)
                        run.begin()
                        while (!run.night.over) {
                            if (helped > 0) help(run, guide = helped == 2)
                            run.next()
                        }
                        saved += run.night.saved
                        total += plan.crew.size
                        run.night.crew.filter { it.fate == Fate.DEAD }.forEach { deaths.merge(it.death!!, 1, Int::plus) }
                        run.night.log.filter { "ran into the Hollow" in it }.forEach { _ -> deaths.merge("ran into it", 1, Int::plus) }
                        deaths.merge("kept at dawn", run.night.lost.size, Int::plus)
                    }
                    println("BALANCE ${plan.name} ${listOf("alone ", "helped", "guided")[helped]} saved ${100 * saved / total}% deaths $deaths")
                }
            }
        }

    private fun help(run: NightRun, guide: Boolean) {
        val night = run.night
        for (move in night.moves) {
            val agent = night.lost.first { it.role == move.role }
            val to = move.go?.let { agent.at + it } ?: continue
            if (run.maze.doomAt(to) == null) continue
            val kind = if (agent.role == Role.CRITIC) MarkKind.TREAT else MarkKind.TRICK
            if (run.place(to, kind)) return
        }
        // The Hollow close to someone: a treat on its way, as a decoy.
        val hollow = night.hollow ?: return guide(run, guide)
        if (hollow.eating == 0 && night.marks.none { it.kind == MarkKind.TREAT }) {
            val prey = night.lost.minByOrNull { run.maze.distances(hollow.at)[it.at] ?: 99 }
            val steps = prey?.let { run.maze.distances(hollow.at)[it.at] }
            if (prey != null && steps != null && steps in 2..5) {
                var at = hollow.at
                repeat(steps / 2) { at += run.maze.way(at, prey.at)!!.first }
                if (run.place(at, MarkKind.TREAT)) return
                // No decoy there: a lantern in its way, next to the prey.
                if (run.place(prey.at + run.maze.way(prey.at, hollow.at)!!.first, MarkKind.TRICK)) return
            }
        }
        guide(run, guide)
    }

    /** A treat on the way out, for someone heading elsewhere, while the bag has candy to spare. */
    private fun guide(run: NightRun, guide: Boolean) {
        val night = run.night
        if (!guide || night.played || night.candy < 2) return
        val dooms = run.maze.dooms.map { it.at }.toSet()
        for (agent in night.lost.filter { it.role != Role.CRITIC }.sortedBy { run.maze.distances(it.at)[run.maze.gate] }) {
            val (dir, _) = run.maze.way(agent.at, run.maze.gate, dooms) ?: continue
            if (night.moves.firstOrNull { it.role == agent.role }?.go == dir) continue
            if (run.place(agent.at + dir, MarkKind.TREAT)) return
        }
    }
}
