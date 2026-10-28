package dev.deeptelar.scarytruc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * What the window shows and what it does: one [CallRun] at a time, and one turn at a time on it,
 * while [thinking] is false. The caller doesn't wait: after [silence] milliseconds without a word
 * from the stranger, the turn goes on with nothing said.
 *
 * @param ears how the caller hears; it is told why Gemma could not answer, or `null` when it did.
 * @param progress the file that keeps the nights someone got out of, or `null` to keep nothing.
 * @param clock whether silence counts; without it, only [send] moves the call on.
 */
class CallModel(
    private val scope: CoroutineScope,
    ears: ((String?) -> Unit) -> Ears,
    private val voice: Voice,
    val gemma: Boolean,
    private val whisperer: Whisperer = Echoes,
    plan: Plan = Plan.FIRST,
    seed: Long? = null,
    private val progress: Path? = null,
    private val clock: Boolean = true,
    private val silence: Long = SILENCE,
) {
    /** Why the last turn was understood without Gemma. */
    var trouble: String? by mutableStateOf(null)
        private set

    private val hearing = ears { trouble = it }

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

    private fun newRun(plan: Plan, seed: Long?) =
        CallRun(plan, seed ?: plan.seed ?: Random.nextLong(1, 1_000_000), hearing, voice, whisperer)

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
        val file = progress ?: return
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, escaped.joinToString("\n") { it.name })
        }
    }

    private fun readProgress(): Set<Plan> {
        val file = progress?.takeIf { Files.exists(it) } ?: return emptySet()
        return runCatching { Files.readAllLines(file).mapNotNull { line -> Plan.entries.firstOrNull { it.name == line.trim() } }.toSet() }
            .getOrDefault(emptySet())
    }

    companion object {
        /** How long the caller waits for a word before the silence scares them. */
        const val SILENCE: Long = 25_000

        /** How long a letter of the caller's line takes to appear. */
        const val LETTER: Long = 28

        private fun now() = System.currentTimeMillis()

        /** How long the caller's last line takes to say. */
        fun speaking(call: Call): Long = (call.lines.lastOrNull { it.who == Who.CALLER }?.text?.length ?: 0) * LETTER
    }
}

/** How close the awake Hollow is to the caller, from 0 (far or asleep) to 1 (next to them). */
fun danger(call: Call, maze: Maze): Float {
    val hollow = call.hollow?.takeIf { it.awake } ?: return 0f
    val steps = maze.distances(hollow.at)[call.at] ?: return 0f
    return ((5 - steps) / 4f).coerceIn(0f, 1f)
}
