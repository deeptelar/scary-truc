package dev.deeptelar.scarytruc

import dev.deeptelar.telar.CompiledGraph
import dev.deeptelar.telar.GraphConfig
import dev.deeptelar.telar.MemoryCheckpointer
import dev.deeptelar.telar.NodeRef
import dev.deeptelar.telar.START
import dev.deeptelar.telar.StateGraph

const val LISTEN: String = "listen"
const val WALK: String = "walk"
const val WHISPER: String = "whisper"
const val SPEAK: String = "speak"

/**
 * A call as a Telar graph. The run pauses before `listen` on every turn, until the stranger says
 * something. `listen` asks the caller's [ears] what they understood, `walk` lets the maze answer,
 * `whisper` lets the Hollow on the line when it is close, and `speak` asks the caller's [voice] to
 * say it. Gemma never decides what is true: `walk` does, and `speak` gets only its facts. [mazeOf]
 * is the maze of a turn, which changes at midnight.
 */
fun callGraph(mazeOf: (Call) -> Maze, haunt: Haunt, ears: Ears, voice: Voice, whisperer: Whisperer = Echoes): CompiledGraph<Call> =
    StateGraph<Call> {
        val listen = node(LISTEN, work = { call -> ears.listen(call) }) { call, heard -> call.copy(heard = heard) }
        val walk = node(WALK) { call -> call.walk(mazeOf(call), haunt) }
        val whisper =
            node(WHISPER, work = { call -> if (call.whispers(mazeOf(call))) whisperer.whisper(call) else null }) { call, said ->
                if (said == null) call else call.whisper(said)
            }
        // Words in another language get the same answer every time, in the caller's own.
        val speak = node(SPEAK, work = { call -> if (call.heard?.foreign == true) call.facts.joinToString(" ") else voice.speak(call) }) { call, said -> call.spoke(said) }
        START then speak
        conditionalEdge(speak, targets = setOf(listen, NodeRef.END)) { call -> if (call.over) NodeRef.END else listen }
        listen then walk then whisper then speak
    }.compile()

/** One call of [plan], a turn at a time: the caller speaks, the stranger answers in [lang]. [seed] sets its chances. */
class CallRun(
    val plan: Plan,
    seed: Long = plan.seed,
    ears: Ears,
    voice: Voice,
    whisperer: Whisperer = Echoes,
    lang: Lang = Lang.EN,
) {
    private val before: Maze = plan.maze()
    private val after: Maze = before.shifted()
    val haunt: Haunt = plan.haunt(lang)

    /** The maze of [call]: after midnight, its walls may have moved. */
    fun mazeOf(call: Call): Maze = if (call.afterMidnight) after else before

    val maze: Maze get() = mazeOf(call)
    private val graph = callGraph(::mazeOf, haunt, ears, voice, whisperer)
    private val config = GraphConfig(threadId = "call", checkpointer = MemoryCheckpointer<Call>(), interruptBefore = setOf(LISTEN))

    var call: Call = Call.begin(plan, before, haunt, seed, lang)
        private set

    /** The phone is answered: the caller speaks first. */
    suspend fun begin(): Call {
        call = graph.invoke(call, config).state
        return call
    }

    /** The stranger says [words], or nothing when they are blank, and the caller answers. */
    suspend fun say(words: String): Call {
        if (call.over) return call
        val said = words.trim()
        call =
            graph.resume(config) {
                it.copy(said = said, lines = it.lines + Line(Who.YOU, said.ifEmpty { "…" }))
            }.state
        return call
    }
}
