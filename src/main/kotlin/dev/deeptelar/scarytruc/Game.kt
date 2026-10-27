package dev.deeptelar.scarytruc

import dev.deeptelar.telar.Checkpoint
import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphEvent
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.NodeRef
import dev.deeptelar.telar.START
import dev.deeptelar.telar.StateGraph
import kotlinx.coroutines.flow.Flow

const val THINK: String = "think"
const val ACT: String = "act"

/**
 * A night as a Telar graph. `think` asks every lost agent where it means to go, all at once, and
 * `act` walks them there, after the player answered. The run pauses before `act` on every turn:
 * the agents decided, and the player sees it before it happens.
 */
fun nightGraph(maze: Maze, brain: Brain): CompiledGraph<Night> =
    StateGraph<Night> {
        val think =
            node(
                THINK,
                forEach = { night -> night.lost },
                work = { night, agent -> brain.decide(night, maze, agent) },
            ) { night, moves -> night.copy(moves = moves) }
        val act = node(ACT) { night -> night.act(maze) }
        START then think then act
        conditionalEdge(act, targets = setOf(think, NodeRef.END)) { night -> if (night.over) NodeRef.END else think }
    }.compile()

/**
 * One night of [plan] in the maze of [seed], played turn by turn. Every turn is a checkpoint, so a
 * marigold can take the night back to any of them: [remember] forks the run there, on a thread of
 * its own, and the turns after it are forgotten.
 *
 * @param seen is told about the night as it changes inside a command: after the crew walked, and
 * as each agent decides.
 */
class NightRun(
    val plan: Plan,
    seed: Long,
    brain: Brain,
    private val seen: (Night) -> Unit = {},
) {
    val maze: Maze = plan.maze(seed)
    private val graph = nightGraph(maze, brain)
    private val checkpointer = MemoryCheckpointer<Night>()
    private var thread = 0

    private val config: GraphConfig<Night>
        get() = GraphConfig(threadId = "night-$thread", checkpointer = checkpointer, interruptBefore = setOf(ACT))

    /** The night as the player sees it: where the run paused, with the treat or trick placed since. */
    var night: Night = Night.begin(plan, maze)
        private set

    /** The start of every turn so far, the first one first: where a marigold can go back to. */
    var past: List<Checkpoint<Night>> = emptyList()
        private set

    suspend fun begin(): Night {
        night = follow(graph.stream(night, config))
        past = listOfNotNull(checkpointer.load(config.threadId))
        return night
    }

    /** Puts a treat or a trick at [pos] for this turn; `false` when it cannot go there. */
    fun place(pos: Pos, kind: MarkKind): Boolean {
        night = night.place(maze, pos, kind) ?: return false
        return true
    }

    /** Takes back the treat or trick placed this turn, and its candy. */
    fun takeBack(pos: Pos): Boolean {
        val mark = night.markAt(pos) ?: return false
        if (past.last().state.markAt(pos) == mark) return false
        night = night.copy(marks = night.marks - mark, candy = night.candy + mark.kind.cost, played = false)
        return true
    }

    /** The crew walks, with the player's answer, and decides its next turn. */
    suspend fun next(): Night {
        if (night.over) return night
        val answered = night
        night = follow(graph.streamResume(config) { it.copy(marks = answered.marks, candy = answered.candy, played = answered.played) })
        if (!night.over) past = past + listOfNotNull(checkpointer.load(config.threadId))
        return night
    }

    /** Spends a marigold to take the night back to [turn], as the crew had decided it, when one is left. */
    suspend fun remember(turn: Int): Night {
        val marigolds = night.marigolds
        if (marigolds == 0) return night
        val then = past.firstOrNull { it.state.turn == turn } ?: return night
        thread++
        val back =
            graph.fork(then.copy(interruptedBefore = false), config) {
                it.copy(marigolds = marigolds - 1, log = it.log + "A marigold for the dead. The night goes back to turn ${turn + 1}.")
            }
        night = back.state
        past = past.filter { it.state.turn < turn } + listOfNotNull(checkpointer.load(config.threadId))
        return night
    }

    /** Runs [events] to their end, telling [seen] about the night after the walk and after each decision. */
    private suspend fun follow(events: Flow<GraphEvent<Night>>): Night {
        var last = night
        var decided = emptyList<Move>()
        events.collect { event ->
            when (event) {
                is GraphEvent.StepCompleted -> {
                    last = event.state
                    decided = emptyList()
                    seen(last)
                }
                is GraphEvent.ItemCompleted -> {
                    (event.result as? Move)?.let {
                        decided = decided + it
                        seen(event.state.copy(moves = decided))
                    }
                }
                is GraphEvent.Interrupted -> last = event.state
                is GraphEvent.Completed -> last = event.state
                else -> Unit
            }
        }
        return last
    }
}
