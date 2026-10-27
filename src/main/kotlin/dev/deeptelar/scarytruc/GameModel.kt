package dev.deeptelar.scarytruc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * What the window shows and what its clicks do: one [NightRun] at a time, and one command at a
 * time on it, while [thinking] is false.
 *
 * @param brain the minds of the crew; it is told why Gemma could not answer, or `null` when it did.
 * @param progress the file that keeps the best night of each plan, or `null` to keep nothing.
 */
class GameModel(
    private val scope: CoroutineScope,
    brain: ((String?) -> Unit) -> Brain,
    val gemma: Boolean,
    plan: Plan = Plan.FIRST,
    seed: Long? = null,
    private val progress: Path? = null,
) {
    /** Why the last turn went to instinct instead of Gemma. */
    var trouble: String? by mutableStateOf(null)
        private set

    private val mind = brain { trouble = it }

    private var run = newRun(plan, seed)

    val maze: Maze get() = run.maze
    val plan: Plan get() = run.plan

    var night: Night by mutableStateOf(run.night)
        private set

    var thinking: Boolean by mutableStateOf(false)
        private set

    /** Why the last command failed, in words. */
    var failure: String? by mutableStateOf(null)
        private set

    /** The most agents saved on each plan so far. */
    var best: Map<Plan, Int> by mutableStateOf(readProgress())
        private set

    /** The turns a marigold can take the night back to, the latest first. */
    val turnsBack: List<Int>
        get() = run.past.map { it.state.turn }.filter { it < night.turn }.reversed()

    /** A plan can be played once the one before it was played to its end. */
    fun open(plan: Plan): Boolean = plan == Plan.FIRST || plan.ordinal <= (best.keys.maxOfOrNull { it.ordinal } ?: -1) + 1

    init {
        command { run.begin() }
    }

    fun click(pos: Pos, kind: MarkKind) {
        if (thinking) return
        if (!run.takeBack(pos)) run.place(pos, kind)
        night = run.night
    }

    fun next() = command { run.next() }

    fun remember(turn: Int) = command { run.remember(turn) }

    /** The same night from its start, with every candy and marigold again. */
    fun again() = play(plan, maze.seed)

    fun play(plan: Plan, seed: Long? = null) {
        if (thinking) return
        run = newRun(plan, seed)
        night = run.night
        command { run.begin() }
    }

    private fun newRun(plan: Plan, seed: Long?) =
        NightRun(plan, seed ?: plan.seed ?: Random.nextLong(1, 1_000_000), mind) { night = it }

    private fun command(work: suspend () -> Night) {
        if (thinking) return
        thinking = true
        failure = null
        scope.launch {
            try {
                night = work()
                if (night.over) keep()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: e::class.simpleName
                night = run.night
            } finally {
                thinking = false
            }
        }
    }

    private fun keep() {
        val saved = night.saved
        if ((best[plan] ?: -1) >= saved) return
        best = best + (plan to saved)
        val file = progress ?: return
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, best.entries.joinToString("\n") { "${it.key.name}=${it.value}" })
        }
    }

    private fun readProgress(): Map<Plan, Int> {
        val file = progress?.takeIf { Files.exists(it) } ?: return emptyMap()
        return runCatching {
            Files.readAllLines(file).mapNotNull { line ->
                val (name, saved) = line.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                Plan.entries.firstOrNull { it.name == name }?.let { plan -> saved.toIntOrNull()?.let { plan to it } }
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
