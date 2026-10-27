package dev.deeptelar.scarytruc

import dev.deeptelar.telar.Checkpoint
import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.GraphEvent
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.NodeRef
import dev.deeptelar.telar.START
import dev.deeptelar.telar.StateGraph
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

const val THINK: String = "think"
const val ACT: String = "act"
const val WHISPER: String = "whisper"

/**
 * A night as a Telar graph. `think` asks every lost agent where it means to go, all at once, and
 * `act` walks them there, after the player answered: the run pauses before `act` on every turn, so
 * the player sees what they decided before it happens. Between a walk and the next decision,
 * `whisper` lets the Hollow speak to whoever is near it. [mazeOf] is the maze of a night's turn,
 * which changes at midnight.
 */
fun nightGraph(mazeOf: (Night) -> Maze, brain: Brain, whisperer: Whisperer = Echoes): CompiledGraph<Night> =
    StateGraph<Night> {
        val think =
            node(
                THINK,
                forEach = { night -> night.lost },
                work = { night, agent -> brain.decide(night, mazeOf(night), agent) },
            ) { night, moves -> night.copy(moves = moves) }
        val act = node(ACT) { night -> night.act(mazeOf(night)) }
        val whisper =
            node(
                WHISPER,
                work = { night -> night.whisperTarget(mazeOf(night))?.let { agent -> agent.role to whisperer.whisper(night, agent) } },
            ) { night, said -> if (said == null) night else night.heard(said.first, said.second) }
        START then think then act
        conditionalEdge(act, targets = setOf(whisper, NodeRef.END)) { night -> if (night.over) NodeRef.END else whisper }
        whisper then think
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
    private val brain: Brain,
    whisperer: Whisperer = Echoes,
    private val seen: (Night) -> Unit = {},
) {
    private val before: Maze = plan.maze(seed)
    private val after: Maze = before.shifted()

    /** The maze of [night]: after midnight, its walls have moved. */
    fun mazeOf(night: Night): Maze = if (night.afterMidnight) after else before

    val maze: Maze get() = mazeOf(night)
    private val graph = nightGraph(::mazeOf, brain, whisperer)
    private val checkpointer = MemoryCheckpointer<Night>()
    private var thread = 0

    private val config: GraphConfig<Night>
        get() = GraphConfig(threadId = "night-$thread", checkpointer = checkpointer, interruptBefore = setOf(ACT))

    /** The night as the player sees it: where the run paused, with the treat or trick placed since. */
    var night: Night = Night.begin(plan, before)
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

    /**
     * Those who can hear or smell what the player just put at [pos] decide again, with it in their
     * senses: they take the scream or the sweet smell their own way.
     */
    suspend fun rethink(pos: Pos): Night {
        val maze = maze
        val steps = maze.distances(pos)
        val near = night.lost.filter { (steps[it.at] ?: 99) <= SMELL }
        if (near.isEmpty()) return night
        val then = night
        val again = coroutineScope { near.map { agent -> async { brain.decide(then, maze, agent) } }.awaitAll() }
        night = night.copy(moves = night.moves.map { move -> again.firstOrNull { it.role == move.role } ?: move })
        seen(night)
        return night
    }

    /** Takes back the treat or trick placed this turn, and its candy. */
    fun takeBack(pos: Pos): Boolean {
        val mark = night.markAt(pos) ?: return false
        if (past.last().state.markAt(pos) == mark) return false
        // What they decided after seeing it stands: a scare can't be taken back.
        night = night.copy(marks = night.marks - mark, candy = night.candy + mark.kind.cost, played = false)
        return true
    }

    /** The crew walks, with the player's answer, and decides its next turn. */
    suspend fun next(): Night {
        if (night.over) return night
        val answered = night
        night =
            follow(
                graph.streamResume(config) {
                    it.copy(marks = answered.marks, candy = answered.candy, played = answered.played, moves = answered.moves)
                },
            )
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
