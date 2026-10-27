package dev.deeptelar.scarytruc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * What the window shows and what its clicks do: one [NightRun] at a time, and one command at a
 * time on it, while [thinking] is false.
 *
 * @param brain the minds of the crew; it is told why Gemma could not answer, or `null` when it did.
 */
class GameModel(
    private val scope: CoroutineScope,
    seed: Long,
    private val brain: ((String?) -> Unit) -> Brain,
    val gemma: Boolean,
) {
    /** Why the last turn went to instinct instead of Gemma. */
    var trouble: String? by mutableStateOf(null)
        private set

    private val mind = brain { trouble = it }

    private var run = NightRun(Maze.of(seed), mind)

    val maze: Maze get() = run.maze

    var night: Night by mutableStateOf(run.night)
        private set

    var thinking: Boolean by mutableStateOf(false)
        private set

    /** Why the last command failed, in words. */
    var failure: String? by mutableStateOf(null)
        private set

    /** The turns a marigold can take the night back to, the latest first. */
    val turnsBack: List<Int>
        get() = run.past.map { it.state.turn }.filter { it < night.turn }.reversed()

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

    /** The same night from its start, with every marigold again. */
    fun again() = restart(maze.seed)

    fun another() = restart(Random.nextLong(1, 1_000_000))

    private fun restart(seed: Long) {
        if (thinking) return
        run = NightRun(Maze.of(seed), mind)
        command { run.begin() }
    }

    private fun command(work: suspend () -> Night) {
        if (thinking) return
        thinking = true
        failure = null
        scope.launch {
            try {
                night = work()
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
}
