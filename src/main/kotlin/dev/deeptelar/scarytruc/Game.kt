package dev.deeptelar.scarytruc

import dev.deeptelar.telar.Checkpoint
import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.NodeRef
import dev.deeptelar.telar.START
import dev.deeptelar.telar.StateGraph

const val CREW: String = "crew"

/**
 * A night as a Telar graph. `crew` asks every living agent where it goes, all at once, and `dark`
 * walks them there and lets the maze answer. The run pauses before `crew` on every turn, for the
 * player's treats and tricks.
 */
fun nightGraph(maze: Maze, brain: Brain): CompiledGraph<Night> =
    StateGraph<Night> {
        val crew =
            node(
                CREW,
                forEach = { night -> night.lost },
                work = { night, agent -> brain.decide(night, maze, agent) },
            ) { night, moves -> night.copy(moves = moves) }
        val dark = node("dark") { night -> night.step(maze) }
        START then crew then dark
        conditionalEdge(dark, targets = setOf(crew, NodeRef.END)) { night -> if (night.over) NodeRef.END else crew }
    }.compile()

/**
 * One night of the maze of [seed], played turn by turn. Every turn is a checkpoint, so a marigold
 * can take the night back to any of them: [remember] forks the run there, on a thread of its own,
 * and the turns after it are forgotten.
 */
class NightRun(val maze: Maze, brain: Brain) {
    private val graph = nightGraph(maze, brain)
    private val checkpointer = MemoryCheckpointer<Night>()
    private var thread = 0

    private val config: GraphConfig<Night>
        get() = GraphConfig(threadId = "night-${maze.seed}-$thread", checkpointer = checkpointer, interruptBefore = setOf(CREW))

    /** The night as the player sees it: where the run paused, with the marks placed since. */
    var night: Night = Night.begin(maze)
        private set

    /** The start of every turn so far, the first one first: where a marigold can go back to. */
    var past: List<Checkpoint<Night>> = emptyList()
        private set

    suspend fun begin(): Night {
        night = graph.invoke(Night.begin(maze), config).state
        past = listOfNotNull(checkpointer.load(config.threadId))
        return night
    }

    /** Puts a treat or a trick at [pos] for the next turn; `false` when it cannot go there. */
    fun place(pos: Pos, kind: MarkKind): Boolean {
        night = night.place(maze, pos, kind) ?: return false
        return true
    }

    /** Takes back a mark placed this turn, and its supply. */
    fun takeBack(pos: Pos): Boolean {
        val mark = night.markAt(pos) ?: return false
        if (past.last().state.markAt(pos) == mark) return false
        night =
            when (mark.kind) {
                MarkKind.TREAT -> night.copy(marks = night.marks - mark, treats = night.treats + 1)
                MarkKind.TRICK -> night.copy(marks = night.marks - mark, tricks = night.tricks + 1)
            }
        return true
    }

    /** Lets the crew decide and walk one turn, with the marks placed. */
    suspend fun next(): Night {
        if (night.over) return night
        val marked = night
        night = graph.resume(config) { it.copy(marks = marked.marks, treats = marked.treats, tricks = marked.tricks) }.state
        if (!night.over) past = past + listOfNotNull(checkpointer.load(config.threadId))
        return night
    }

    /** Spends a marigold to take the night back to the start of [turn], when one is left. */
    suspend fun remember(turn: Int): Night {
        val marigolds = night.marigolds
        if (marigolds == 0) return night
        val then = past.firstOrNull { it.state.turn == turn } ?: return night
        thread++
        night =
            graph.fork(then.copy(interruptedBefore = false), config) {
                it.copy(
                    marigolds = marigolds - 1,
                    log = it.log + "A marigold for the dead. The night goes back to turn ${turn + 1}.",
                )
            }.state
        past = past.filter { it.state.turn < turn } + listOfNotNull(checkpointer.load(config.threadId))
        return night
    }
}
