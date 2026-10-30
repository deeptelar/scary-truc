package dev.deeptelar.scarytruc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    fun minds(client: HttpClient): Minds = Minds.of(chat(client), model)

    /** [model] on this server, asked through [client]. */
    fun chat(client: HttpClient): ChatModel {
        // Ollama thinks before it answers unless told not to; a phone call can't wait for that.
        val ollama = ":11434" in url || "ollama" in url
        val parameters = if (ollama) JsonObject(mapOf("reasoning_effort" to JsonPrimitive("none"))) else JsonObject(emptyMap())
        return OpenAiChatModel(client, apiKey = key.ifBlank { null }, model = model, parameters = parameters, baseUrl = url.trimEnd('/'), timeout = 1.minutes)
    }

    companion object {
        /** One key for the models of many providers: Gemma 4 among them, from a browser too. */
        val OPENROUTER: Server = Server("https://openrouter.ai/api/v1", "google/gemma-4-31b-it")
        val OLLAMA: Server = Server("http://localhost:11434/v1", "gemma4:12b")
    }
}

/** The phone's microphone: the browser turns the player's voice into text, where it can. */
interface Microphone {
    /**
     * Listens until the player pauses, for words in [lang]. [heard] gets the words so far as they
     * come; [done] gets why it could not listen, or `null` when it heard what there was to hear.
     */
    fun listen(lang: Lang, heard: (String) -> Unit, done: (String?) -> Unit)

    fun stop()
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
 * @param locked whether the phone opens locked: a browser plays sound only after a tap, so the
 *   player unlocks it first, and hears it ring.
 */
class CallModel(
    private val scope: CoroutineScope,
    minds: Minds = Minds.none,
    plan: Plan = Plan.CAVE,
    seed: Long? = null,
    private val store: Store? = null,
    private val clock: Boolean = true,
    private val silence: Long = SILENCE,
    locked: Boolean = false,
    lang: Lang = Lang.EN,
) {
    /** The language of the game, as the player last chose it, or [lang] the first time. */
    var lang: Lang by mutableStateOf(runCatching { store?.get(LANG) }.getOrNull()?.let { Lang.of(it) } ?: lang)
        private set

    /** The phone's keys are locked: nothing rings until the player unlocks it. */
    var locked: Boolean by mutableStateOf(locked)
        private set

    /** Whether the player turned the game's sound off; kept in the [store]. */
    var muted: Boolean by mutableStateOf(runCatching { store?.get(MUTED) }.getOrNull() == "true")
        private set

    /** Turns the game's sound off, or back on, and remembers it. */
    fun toggleMute() {
        muted = !muted
        Sound.on = !muted
        runCatching { store?.set(MUTED, muted.toString()) }
    }

    /** When the phone rings next. */
    private var ringAt = 0L

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

    /** The phone's microphone, where the platform can hear; `null` hides the button. */
    var microphone: Microphone? = null

    /** Whether the microphone is listening to the player now. */
    var listening: Boolean by mutableStateOf(false)
        private set

    /**
     * Listens to the player, or stops listening. What they say goes into the field as it is heard,
     * and is said to the caller when they pause, as if they had typed it.
     */
    fun listen() {
        val microphone = microphone ?: return
        if (listening) {
            microphone.stop()
            return
        }
        listening = true
        failure = null
        microphone.listen(
            lang,
            heard = { type(it) },
            done = { problem ->
                listening = false
                if (problem != null) failure = problem else if (!thinking) send()
            },
        )
    }

    /** How long the caller's last line takes to say. */
    fun speaking(): Long = (call.lines.lastOrNull { it.who == Who.CALLER }?.text?.length ?: 0) * LETTER

    private var run = newRun(plan, seed)

    val plan: Plan get() = run.plan
    val haunt: Haunt get() = run.haunt
    val maze: Maze get() = run.mazeOf(call)

    var call: Call by mutableStateOf(run.call)
        private set

    /** The clue for this moment of the call, behind the bulb; `null` when there is none. */
    val clue: Clue? get() = if (ringing || thinking) null else Clue.of(call, maze, haunt)

    /** The clues the player already opened: the bulb only blinks for a new one. */
    var cluesRead: Set<Clue> by mutableStateOf(emptySet())
        private set

    /** Whether the clue is open on the screen. */
    var clueOpen: Boolean by mutableStateOf(false)
        private set

    /** Opens the clue of this moment, or closes it. */
    fun toggleClue() {
        val clue = clue
        clueOpen = !clueOpen && clue != null
        if (clue != null) cluesRead = cluesRead + clue
    }

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

    /** The closest each call came to the way out, in steps: 0 when the caller got out. */
    var best: Map<Plan, Int> by mutableStateOf(readProgress())
        private set

    init {
        if (muted) Sound.on = false
        Sound.place = plan.drone
        Sound.midnight = Drone.MIDNIGHT.random()
        scope.launch {
            while (true) {
                // this.locked: in here, locked alone is the argument the phone was made with.
                if (ringing && !this@CallModel.locked && !call.over && now() >= ringAt) {
                    Sound.play(Cue.RING)
                    // Two rings, then a breath of quiet.
                    ringAt = now() + 4500
                }
                delay(100)
            }
        }
        scope.launch {
            while (true) {
                delay(300)
                // While Gemma finds the caller's words, the line carries their breathing.
                if (thinking && !ringing && !call.over) {
                    val breath = Sound.breaths(call.woman).random()
                    Sound.play(breath, gain = -3f)
                    delay(Sound.millis(breath) - 300)
                }
            }
        }
        if (clock) {
            scope.launch {
                while (true) {
                    delay(250)
                    if ((waited(now()) ?: 0f) >= 1f) say("")
                }
            }
        }
    }

    /**
     * How much of the caller's patience is gone, from 0 to 1, while they wait for the player: once
     * they are done speaking, and since the player last typed. `null` when they don't wait.
     */
    fun waited(now: Long): Float? {
        if (!clock || ringing || thinking || call.over) return null
        val since = maxOf(spokeAt + speaking(), lastKey)
        return ((now - since).toFloat() / silence).coerceIn(0f, 1f)
    }

    /** Unlocks the phone, from inside a tap: its sound opens, and it rings at once. */
    fun unlock() {
        onAnswer()
        locked = false
        ringAt = 0L
    }

    fun answer() {
        if (!ringing) return
        onAnswer()
        Sound.stop(Cue.RING)
        Sound.play(Cue.PICKUP)
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

    /** Tells the caller to use [item], as if the player had said it: a stone is thrown ahead. */
    fun useItem(item: Item) {
        if (thinking || ringing || call.over) return
        say(
            when (item) {
                Item.BATTERY -> lang.t("swap in the battery", "cambia la batería")
                Item.FLARE -> lang.t("light the flare", "enciende la bengala")
                Item.STONE -> lang.t("throw the stone ahead", "tira la piedra hacia delante")
            },
        )
    }

    /** The same call from its start. */
    fun again() = play(plan)

    fun play(plan: Plan, seed: Long? = null) {
        if (thinking) return
        run = newRun(plan, seed)
        call = run.call
        typed = ""
        ringing = true
        clueOpen = false
        cluesRead = emptySet()
        Sound.danger = 0f
        Sound.hushed = false
        Sound.otherworld = false
        Sound.place = plan.drone
        // Each call has its own midnight.
        Sound.midnight = Drone.MIDNIGHT.random()
    }

    /** Plays in [lang] from now on, and remembers it: this call starts again. */
    fun speak(lang: Lang) {
        if (thinking) return
        this.lang = lang
        runCatching { store?.set(LANG, lang.code) }
        play(plan)
    }

    /** Other minds for the caller and the Hollow, from the next call on: this one starts again. */
    fun use(minds: Minds) {
        if (thinking) return
        this.minds = minds
        trouble = null
        play(plan)
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
        // The maze is the same every time; the chances of the call are not.
        return CallRun(plan, seed ?: Random.nextLong(), minds.ears { trouble = it }, minds.voice, minds.whisperer, lang)
    }

    /** The turn being played, to stop when the player hangs up. */
    private var turn: Job? = null

    /**
     * The player hangs up: the turn stops where it is, and the caller is left alone in the dark,
     * which no one gets out of. It counts as no try.
     */
    fun hangUp() {
        if (ringing || call.over) return
        turn?.cancel()
        listening = false
        microphone?.stop()
        call = call.hungUp(run.mazeOf(call))
        spokeAt = 0L
        Sound.danger = 0f
        Sound.otherworld = false
        Sound.stop(Cue.MESSAGE)
        Sound.play(Cue.HANGUP)
    }

    /** Runs [work] alone, then plays the sounds of what changed. */
    private fun command(work: suspend () -> Call) {
        if (thinking) return
        thinking = true
        failure = null
        val before = call
        clueOpen = false
        turn = scope.launch {
            try {
                val after = work()
                call = after
                spokeAt = now()
                // Their words arrive like a message on a phone of the time.
                if (after.lines.lastOrNull { it.who == Who.CALLER } != before.lines.lastOrNull { it.who == Who.CALLER }) Sound.play(Cue.MESSAGE, gain = -4f)
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
        Sound.otherworld = after.afterMidnight && !after.over
        Sound.danger = if (after.over) 0f else danger(after, maze)
        scope.launch {
            // What the caller did, before where they went.
            when {
                after.bumped -> Sound.play(Sound.WALLS.random())
                !before.hidden && after.hidden -> Sound.play(Sound.RUSTLES.random())
                after.marks.size > before.marks.size -> Sound.play(Cue.SCRATCH)
                after.carried.size > before.carried.size -> Sound.play(Cue.PICK)
            }
            if (after.ran) {
                Sound.play(Cue.RUN)
                delay(Sound.millis(Cue.RUN) - 1500)
            } else {
                repeat(after.walked) {
                    Sound.play(Sound.STEPS.random(), gain = -4f)
                    delay(380)
                }
            }
            if (after.sniffed) Sound.play(Cue.SNIFF)
            if (Item.BATTERY in before.carried && Item.BATTERY !in after.carried) Sound.play(Cue.BATTERY_IN)
            // The phone beeps as its last bar goes, and again near the end.
            if (!after.over && listOf(20, 10).any { before.battery > it && after.battery <= it }) Sound.play(Cue.LOW_BATTERY)
            if (Item.FLARE in before.carried && Item.FLARE !in after.carried) Sound.play(Cue.FLARE)
            if (Item.STONE in before.carried && Item.STONE !in after.carried) {
                // Thrown, and a moment later it hits.
                delay(350)
                Sound.play(Cue.STONE)
            }
            after.hollow?.takeIf { before.hollow?.awake == false && it.awake }?.let { hollow ->
                // Next to the caller it growls; far away, it howls.
                val far = (maze.distances(hollow.at)[after.at] ?: 99) > 3
                Sound.play(if (far) Cue.HOWL else Cue.WAKE, gain = if (far) -4f else 0f)
            }
            // A caller at the edge of panic sobs into the phone, once and low: the quiet is scarier.
            if (!after.over && before.panic < LOUD && after.panic >= LOUD) Sound.play(Sound.sob(after.woman), gain = -9f)
            // Near the way out, a cold draft: stronger the closer it is.
            val toGate = maze.distances(maze.gate)[after.at] ?: 99
            if (!after.over && after.walked > 0 && toGate <= 3) Sound.play(Cue.WIND, gain = -8f + 3f * (3 - toGate))
            if (!before.afterMidnight && after.afterMidnight) {
                Sound.play(Cue.BELL)
                // The walls grind as they move, and the siren once the toll has rung out a little.
                delay(1200)
                Sound.play(Cue.GRIND)
                delay(1800)
                Sound.play(Cue.SIREN)
            }
            if (after.lines.count { it.who == Who.HOLLOW } > before.lines.count { it.who == Who.HOLLOW }) Sound.play(Cue.WHISPER)
            if (danger(after, maze) >= 0.75f && danger(before, run.mazeOf(before)) < 0.75f) Sound.play(Cue.STINGER)
            when (after.end) {
                End.TAKEN, End.DOOM -> {
                    // The drone and the static stop half a second before, so that what took them hits harder.
                    delay(speaking() - 1100)
                    Sound.hushed = true
                    delay(500)
                    // What took them is heard first; a pit is only a long fall.
                    val doom = DoomKind.entries.firstOrNull { it.word == after.death }.takeIf { after.end == End.DOOM }
                    if (doom == DoomKind.PIT) {
                        Sound.play(Sound.fall(after.woman))
                        delay(2200)
                    } else {
                        when (doom) {
                            DoomKind.MOUTH -> Sound.play(Cue.CRUNCH)
                            DoomKind.MIRROR -> Sound.play(Cue.SHATTER)
                            else -> Sound.play(Cue.ROAR)
                        }
                        delay(700)
                        Sound.play(Sound.screams(after.woman).random())
                        delay(900)
                    }
                    Sound.play(Cue.HANGUP)
                    Sound.hushed = false
                }
                End.BATTERY -> {
                    delay(speaking())
                    Sound.play(Cue.POWER_OFF)
                    delay(700)
                    Sound.play(Cue.HANGUP)
                }
                End.ALONE -> Unit
                End.OUT -> {
                    Sound.play(Cue.DOOR)
                    delay(2400)
                    Sound.play(Cue.CHIME)
                }
                null -> Unit
            }
        }
    }

    private fun keep(call: Call) {
        if (call.end == End.ALONE) return
        val away = call.away ?: return
        if ((best[call.plan] ?: Int.MAX_VALUE) <= away) return
        best = best + (call.plan to away)
        runCatching { store?.set(PROGRESS, best.entries.joinToString(",") { "${it.key.name}=${it.value}" }) }
    }

    private fun readProgress(): Map<Plan, Int> {
        val saved = runCatching { store?.get(PROGRESS) }.getOrNull() ?: return emptyMap()
        return saved.split(",").mapNotNull { entry ->
            val (name, steps) = entry.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            Plan.entries.firstOrNull { it.name == name.trim() }?.let { plan -> steps.trim().toIntOrNull()?.let { plan to it } }
        }.toMap()
    }

    companion object {
        /** How long the caller waits for a word before the silence scares them. */
        const val SILENCE: Long = 45_000

        /** How long a letter of the caller's line takes to appear: time to read as it comes. */
        const val LETTER: Long = 42

        private const val PROGRESS = "best"
        private const val SERVER = "server"
        private const val LANG = "lang"
        private const val MUTED = "muted"
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
