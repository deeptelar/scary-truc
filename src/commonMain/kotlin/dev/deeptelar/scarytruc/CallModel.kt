package dev.deeptelar.scarytruc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.openai.OpenAiChatModel
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.minutes
import kotlin.random.Random
import kotlin.time.TimeSource

/** Where the game keeps a few values between plays: a folder on the desktop, `localStorage` in a browser. */
interface Store {
    fun get(key: String): String?

    fun set(key: String, value: String)
}

/** A server with the OpenAI API, and the model on it that plays the caller and the Hollow. */
data class Server(val url: String, val model: String, val key: String = "") {
    /** The minds of [model] on this server, asked through [client]. */
    fun minds(client: HttpClient): Minds {
        // Ollama thinks before it answers unless told not to; a phone call can't wait for that.
        val ollama = ":11434" in url || "ollama" in url
        val parameters = if (ollama) JsonObject(mapOf("reasoning_effort" to JsonPrimitive("none"))) else JsonObject(emptyMap())
        val chat = OpenAiChatModel(client, apiKey = key.ifBlank { null }, model = model, parameters = parameters, baseUrl = url.trimEnd('/'), timeout = 1.minutes)
        return Minds.of(chat, model)
    }

    companion object {
        /** One key for the models of many providers: Gemma 4 among them, from a browser too. */
        val OPENROUTER: Server = Server("https://openrouter.ai/api/v1", "google/gemma-4-31b-it")
        val OLLAMA: Server = Server("http://localhost:11434/v1", "gemma4:12b")
    }
}

/**
 * The mind of the caller and of the Hollow: Gemma's, named [name], or none, where the caller
 * knows a few words and says the facts as they are.
 */
class Minds(
    val name: String?,
    val ears: ((String?) -> Unit) -> Ears,
    val voice: Voice,
    val whisperer: Whisperer,
) {
    val gemma: Boolean get() = name != null

    companion object {
        val none: Minds = Minds(null, { Keywords }, Script, Echoes)

        fun of(model: ChatModel, name: String): Minds = Minds(name, { GemmaEars(model, trouble = it) }, GemmaVoice(model), GemmaWhispers(model))
    }
}

/**
 * What the window shows and what it does: one [CallRun] at a time, and one turn at a time on it,
 * while [thinking] is false. The caller doesn't wait: after [silence] milliseconds without a word
 * from the stranger, the turn goes on with nothing said.
 *
 * @param minds the minds of the caller and the Hollow; [use] changes them.
 * @param store where the nights someone got out of are kept, or `null` to keep nothing.
 * @param clock whether silence counts; without it, only [send] moves the call on.
 */
class CallModel(
    private val scope: CoroutineScope,
    minds: Minds = Minds.none,
    plan: Plan = Plan.FIRST,
    seed: Long? = null,
    private val store: Store? = null,
    private val clock: Boolean = true,
    private val silence: Long = SILENCE,
) {
    /** Why the last turn was understood without Gemma. */
    var trouble: String? by mutableStateOf(null)
        private set

    var minds: Minds by mutableStateOf(minds)
        private set

    /** The server the player chose in the settings, kept in the [store]. */
    var server: Server? by mutableStateOf(readServer())
        private set

    private val client by lazy { HttpClient() }

    val gemma: Boolean get() = minds.gemma

    /** The game's own sounds hook in here: a browser opens its sound on the first tap. */
    var onAnswer: () -> Unit = {}

    private var run = newRun(plan, seed)

    val plan: Plan get() = run.plan
    val haunt: Haunt get() = run.haunt
    val maze: Maze get() = run.mazeOf(call)

    var call: Call by mutableStateOf(run.call)
        private set

    /** The phone rings until the player answers. */
    var ringing: Boolean by mutableStateOf(true)
        private set

    var thinking: Boolean by mutableStateOf(false)
        private set

    /** What the player is typing. */
    var typed: String by mutableStateOf("")
        private set

    /** When the caller's last line arrived, for its letters to appear one by one. */
    var spokeAt: Long by mutableLongStateOf(0L)
        private set

    private var lastKey = 0L

    /** Why the last turn failed, in words. */
    var failure: String? by mutableStateOf(null)
        private set

    /** The nights someone got out of. */
    var escaped: Set<Plan> by mutableStateOf(readProgress())
        private set

    /** A night can be played once the one before it was played: getting out is not needed. */
    fun open(plan: Plan): Boolean = plan == Plan.FIRST || plan.ordinal <= played + 1

    private var played = escaped.maxOfOrNull { it.ordinal } ?: 0

    init {
        scope.launch {
            while (true) {
                if (ringing && !call.over) Sound.play(Cue.RING)
                delay(3000)
            }
        }
        if (clock) {
            scope.launch {
                while (true) {
                    delay(250)
                    if (ringing || thinking || call.over) continue
                    val since = maxOf(spokeAt + speaking(call), lastKey)
                    if (now() - since > silence) say("")
                }
            }
        }
    }

    fun answer() {
        if (!ringing) return
        onAnswer()
        ringing = false
        command { run.begin() }
    }

    fun type(text: String) {
        typed = text
        lastKey = now()
    }

    fun send() {
        if (typed.isBlank()) return
        val words = typed
        typed = ""
        say(words)
    }

    private fun say(words: String) = command { run.say(words) }

    /** The same night from its start. */
    fun again() = play(plan, run.maze.seed)

    fun play(plan: Plan, seed: Long? = null) {
        if (thinking) return
        run = newRun(plan, seed)
        call = run.call
        typed = ""
        ringing = true
        Sound.danger = 0f
        Sound.otherworld = false
    }

    /** Other minds for the caller and the Hollow, from the next call on: this one starts again. */
    fun use(minds: Minds) {
        if (thinking) return
        this.minds = minds
        trouble = null
        play(plan, run.maze.seed)
    }

    /** Plays the caller with the model on [server], or with none, and remembers the choice. */
    fun connect(server: Server?) {
        this.server = server
        runCatching { store?.set(SERVER, server?.let { listOf(it.url, it.model, it.key).joinToString("\n") }.orEmpty()) }
        use(server?.minds(client) ?: Minds.none)
    }

    /** The minds of the server the player chose before, if any. */
    fun saved(): Minds? = server?.minds(client)

    private fun readServer(): Server? {
        val lines = runCatching { store?.get(SERVER) }.getOrNull()?.split("\n") ?: return null
        return if (lines.size >= 2 && lines[0].isNotBlank()) Server(lines[0], lines[1], lines.getOrElse(2) { "" }) else null
    }

    private fun newRun(plan: Plan, seed: Long?): CallRun {
        val minds = minds
        return CallRun(plan, seed ?: plan.seed ?: Random.nextLong(1, 1_000_000), minds.ears { trouble = it }, minds.voice, minds.whisperer)
    }

    /** Runs [work] alone, then plays the sounds of what changed. */
    private fun command(work: suspend () -> Call) {
        if (thinking) return
        thinking = true
        failure = null
        val before = call
        scope.launch {
            try {
                val after = work()
                call = after
                spokeAt = now()
                sounds(before, after)
                if (after.over) keep(after)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message ?: e::class.simpleName
                call = run.call
            } finally {
                thinking = false
            }
        }
    }

    private fun sounds(before: Call, after: Call) {
        val maze = run.mazeOf(after)
        Sound.otherworld = after.afterMidnight && after.plan.hollow && !after.over
        Sound.danger = if (after.over) 0f else danger(after, maze)
        scope.launch {
            repeat(after.walked) {
                Sound.play(Cue.STEP, gain = -4f)
                delay(380)
            }
            if (before.hollow?.awake == false && after.hollow?.awake == true) Sound.play(Cue.WAKE)
            if (!before.afterMidnight && after.afterMidnight && after.plan.hollow) {
                Sound.play(Cue.BELL)
                delay(1200)
                Sound.play(Cue.SIREN)
            }
            if (after.lines.count { it.who == Who.HOLLOW } > before.lines.count { it.who == Who.HOLLOW }) Sound.play(Cue.WHISPER)
            if (danger(after, maze) >= 0.75f && danger(before, run.mazeOf(before)) < 0.75f) Sound.play(Cue.STINGER, gain = -3f)
            when (after.end) {
                End.TAKEN, End.DOOM -> {
                    delay(speaking(after) - 600)
                    Sound.play(Cue.SCREAM)
                    delay(900)
                    Sound.play(Cue.HANGUP)
                }
                End.BATTERY -> {
                    delay(speaking(after))
                    Sound.play(Cue.HANGUP)
                }
                End.OUT -> Sound.play(Cue.CHIME)
                null -> Unit
            }
        }
    }

    private fun keep(call: Call) {
        played = maxOf(played, call.plan.ordinal)
        if (call.end != End.OUT || call.plan in escaped) return
        escaped = escaped + call.plan
        runCatching { store?.set(PROGRESS, escaped.joinToString(",") { it.name }) }
    }

    private fun readProgress(): Set<Plan> {
        val saved = runCatching { store?.get(PROGRESS) }.getOrNull() ?: return emptySet()
        return saved.split(",").mapNotNull { name -> Plan.entries.firstOrNull { it.name == name.trim() } }.toSet()
    }

    companion object {
        /** How long the caller waits for a word before the silence scares them. */
        const val SILENCE: Long = 25_000

        /** How long a letter of the caller's line takes to appear. */
        const val LETTER: Long = 28

        private const val PROGRESS = "progress"
        private const val SERVER = "server"

        /** How long the caller's last line takes to say. */
        fun speaking(call: Call): Long = (call.lines.lastOrNull { it.who == Who.CALLER }?.text?.length ?: 0) * LETTER
    }
}

private val started = TimeSource.Monotonic.markNow()

/** Milliseconds since the game started. */
fun now(): Long = started.elapsedNow().inWholeMilliseconds

/** How close the awake Hollow is to the caller, from 0 (far or asleep) to 1 (next to them). */
fun danger(call: Call, maze: Maze): Float {
    val hollow = call.hollow?.takeIf { it.awake } ?: return 0f
    val steps = maze.distances(hollow.at)[call.at] ?: return 0f
    return ((5 - steps) / 4f).coerceIn(0f, 1f)
}
