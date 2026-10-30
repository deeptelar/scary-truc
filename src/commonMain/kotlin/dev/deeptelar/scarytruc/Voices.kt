package dev.deeptelar.scarytruc

import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.Description
import dev.deeptelar.telar.agent.ask
import dev.deeptelar.telar.agent.chat
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** How the caller understands what the stranger just said. */
fun interface Ears {
    suspend fun listen(call: Call): Heard
}

/** How the caller says what happened and what they see. */
fun interface Voice {
    suspend fun speak(call: Call): String
}

/** What the Hollow says on the line, to the stranger. */
fun interface Whisperer {
    suspend fun whisper(call: Call): String
}

/**
 * Ears without Gemma: the first way or deed named in the stranger's words, and a few words that
 * calm or scare.
 */
object Keywords : Ears {
    private val orders =
        listOf(
            Order.LEFT to listOf("left"),
            Order.RIGHT to listOf("right"),
            Order.AHEAD to listOf("ahead", "forward", "straight", "keep going", "go on", "continue"),
            Order.BACK to listOf("back", "turn around", "return"),
            Order.HIDE to listOf("hide", "crouch", "quiet", "shh", "hold your breath", "don't move", "keep still"),
            Order.STAY to listOf("stay", "wait", "stop"),
            Order.MARK to listOf("mark", "scratch", "write"),
            Order.TAKE to listOf("take", "grab", "pick", "pocket"),
            // "Light the flare", not "go to the light".
            Order.USE to listOf("use", "light the", "light it", "throw", "toss", "swap", "charge"),
        )

    /** The things by name, and by what is done with them. */
    private val items =
        listOf(
            Item.BATTERY to listOf("battery", "swap", "charge"),
            Item.FLARE to listOf("flare", "light the", "light it"),
            Item.STONE to listOf("stone", "rock", "throw", "toss"),
        )
    private val ways = listOf(Order.LEFT, Order.RIGHT, Order.AHEAD, Order.BACK)
    private val soothing = listOf("breathe", "okay", "ok", "safe", "i'm here", "with you", "slow", "calm", "you can", "good", "well done", "fine", "it's alright", "together")
    private val harsh = listOf("hurry", "stupid", "idiot", "die", "dead", "useless", "shut up", "!!", "fast")

    override suspend fun listen(call: Call): Heard {
        val said = call.said.lowercase()
        val order = orders.mapNotNull { (order, words) -> words.mapNotNull { word -> find(said, word) }.minOrNull()?.let { order to it } }.minByOrNull { it.second }?.first
        val letters = call.said.filter { it.isLetter() }
        val shouting = letters.length >= 6 && letters.count { it.isUpperCase() } > letters.length * 0.6
        val calm = soothing.count { find(said, it) != null } - harsh.count { said.contains(it) } - if (shouting) 1 else 0
        val item = items.firstOrNull { (_, words) -> words.any { find(said, it) != null } }?.first
        val toward = ways.mapNotNull { way -> orders.first { it.first == way }.second.mapNotNull { find(said, it) }.minOrNull()?.let { way to it } }.minByOrNull { it.second }?.first
        return Heard(order ?: Order.NONE, calm.coerceIn(-2, 2), item.takeIf { order == Order.USE }, toward.takeIf { order == Order.USE })
    }

    /** Words that go around an order and change nothing: "go left now" is "left". */
    private val fillers = setOf("go", "turn", "now", "please", "ok", "okay", "then", "it", "walk", "move", "just")

    /**
     * The order in [said] when it is only an order, a shortcut such as "left", "go left" or "hide":
     * it needs no model to be understood. `null` when there is more to it, or no order.
     */
    fun bare(said: String): Order? {
        val words = said.lowercase().replace(Regex("[^a-z' ]"), " ").split(" ").filter { it.isNotBlank() && it !in fillers }
        val phrase = words.joinToString(" ").ifEmpty { return null }
        return orders.firstOrNull { (order, words) -> order != Order.USE && phrase in words }?.first
    }

    /** Where [word] starts in [text] as a whole word, or `null`. */
    private fun find(text: String, word: String): Int? = Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").find(text)?.range?.first
}

@Serializable
data class Understood(
    @Description("What the stranger tells you to do now: ahead, left, right, back, stay, hide, mark, take, use, or none")
    val order: String,
    @Description("How the stranger's words make you feel, from -2 (harsh, rushed, scary) to 2 (calm, kind, reassuring)")
    val calm: Int,
    @Description("When the order is use: what to use, battery, flare or stone; otherwise none")
    val item: String = "none",
    @Description("When you are to throw the stone: where, ahead, left, right or back; otherwise none")
    val toward: String = "none",
)

/**
 * The caller's ears played by Gemma: it reads the stranger's words as the caller would, the way
 * and the tone. When Gemma cannot answer, or names no deed it knows, the [keywords] decide.
 */
class GemmaEars(
    private val model: ChatModel,
    private val keywords: Ears = Keywords,
    private val trouble: (String?) -> Unit = {},
) : Ears {
    override suspend fun listen(call: Call): Heard {
        if (call.said.isBlank()) return Heard(Order.NONE, 0)
        // A shortcut, "left" or "hide", needs no model: it is understood at once, and the same every time.
        Keywords.bare(call.said)?.let { return Heard(it, 0) }
        return try {
            val understood = model.ask<Understood>(prompt(call), system = system(call))
            trouble(null)
            val order = Order.of(understood.order) ?: return keywords.listen(call)
            Heard(order, understood.calm.coerceIn(-2, 2), Item.of(understood.item), Order.of(understood.toward)?.takeIf { it.turn != null })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trouble(e.message ?: e::class.simpleName)
            keywords.listen(call)
        }
    }

    private fun prompt(call: Call): String {
        val last = call.lines.lastOrNull { it.who == Who.CALLER }?.text
        return (last?.let { "You just said: \"$it\"\n" } ?: "") +
            "The stranger answers: \"${call.said}\"\n" +
            "What does the stranger tell you to do now? Ways are from where you face: ahead, left, right, or back. " +
            "Or stay, hide (crouch, cover the light, keep still and quiet), mark (scratch a number on the wall), " +
            "take (pick up what lies here), use (swap in a battery, light a flare, throw a stone), " +
            "or none when the stranger gives no instruction. If they give several, the first one." +
            (if (call.carried.isEmpty()) "" else " You carry: ${call.carried.joinToString { it.word }}.")
    }

    private fun system(call: Call): String =
        "You are ${call.name}, lost in a dark maze, on the phone with a stranger who guides you. " +
            "You decide what you understood from the stranger's words, and how they made you feel."
}

/** The caller without Gemma: the facts as they are, after a few words of fear. */
object Script : Voice {
    override suspend fun speak(call: Call): String {
        val random = Random(call.seed + call.turn)
        val start =
            when {
                call.end == End.OUT -> listOf("Oh my god.")
                call.over -> listOf("No— no no NO—")
                call.turn == 0 -> listOf("Hello? Hello?! Oh thank god, someone picked up.")
                call.panic >= BOLT -> listOf("Oh god oh god oh god.", "I can't, I can't breathe.", "Please, please, please.")
                call.panic >= 50 -> listOf("Okay. Okay.", "I'm scared.", "Are you still there?")
                else -> listOf("Okay.", "Alright.", "Listen.")
            }
        if (call.over && call.end != End.OUT) return (call.facts + start.first()).joinToString(" ")
        return (listOf(start[random.nextInt(start.size)]) + call.facts).joinToString(" ")
    }
}

/**
 * The caller played by Gemma: in their own voice, scared, saying every fact and nothing more. When
 * Gemma cannot answer, the [script] speaks.
 */
class GemmaVoice(
    private val model: ChatModel,
    private val script: Voice = Script,
) : Voice {
    override suspend fun speak(call: Call): String =
        try {
            model.chat(prompt(call), system = system(call)).trim().trim('"', '“', '”').replace(Regex("\\*[^*]*\\*"), "").trim()
                .take(400).takeIf { it.length > 3 } ?: script.speak(call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            script.speak(call)
        }

    private fun prompt(call: Call): String {
        val feeling =
            when {
                call.panic >= BOLT -> "You are panicking, barely able to speak."
                call.panic >= 60 -> "You are very scared."
                call.panic >= 35 -> "You are scared, but holding on."
                else -> "You are scared, but calmer now."
            }
        val trust =
            when {
                call.trust >= 60 -> "You trust the stranger."
                call.trust >= DOUBT + 10 -> "You are not sure you can trust the stranger."
                else -> "You don't trust the stranger."
            }
        val said =
            when {
                call.turn == 0 -> "The stranger just picked up your call."
                call.said.isBlank() -> "The stranger has said nothing for a while."
                else -> "The stranger just said: \"${call.said}\""
            }
        val ending =
            when (call.end) {
                End.OUT -> "You are out. Say it, and thank the stranger, or ask who they are."
                End.TAKEN, End.DOOM -> "You are being taken, right now. Say only your last few words, cut off mid-sentence."
                End.BATTERY -> "Your phone is dying. Say your last few words before the line goes dead."
                End.ALONE -> "The stranger hung up on you."
                null -> "Answer the stranger if they asked something, and say what happened and what you see."
            }
        val carried = if (call.carried.isEmpty()) "" else " You carry: ${call.carried.joinToString { it.thing }}."
        return "$said\n$feeling $trust$carried\nWhat happened, and what is around you now (\"I\" is you, \"you\" is the stranger):\n" +
            call.facts.joinToString("\n") { "- $it" } + "\n$ending"
    }

    private fun system(call: Call): String =
        "You are ${call.name}, who woke up alone in a dark maze with no memory of how you got there. " +
            "Your phone is your only light, and a stranger picked up your call. ${call.role.voice} " +
            "Speak only your words on the phone: two to four short sentences, no actions, no asterisks. " +
            "Say every fact you are given, in your own words, and keep left, right, ahead and behind exactly as given. " +
            "Never mention a way, a door or a thing that is not in the facts."
}

/** The Hollow without Gemma: a few lines, to the stranger. */
object Echoes : Whisperer {
    private val lines =
        listOf(
            "Why do you think they called you?",
            "Keep talking. I like your voice.",
            "They will die because of what you say.",
            "Hang up. Save yourself.",
            "I can hear you breathing too.",
        )

    override suspend fun whisper(call: Call): String = lines[Random(call.seed + call.turn).nextInt(lines.size)]
}

/**
 * The Hollow played by Gemma: it speaks on the line, to the stranger, with what the caller fears
 * most. When Gemma cannot answer, the [echoes] speak instead.
 */
class GemmaWhispers(
    private val model: ChatModel,
    private val echoes: Whisperer = Echoes,
) : Whisperer {
    override suspend fun whisper(call: Call): String =
        try {
            model.chat(prompt(call), system = SYSTEM).lines().firstOrNull { it.isNotBlank() }?.trim()?.trim('"', '“', '”', '*')
                ?.take(110)?.takeIf { it.length > 3 } ?: echoes.whisper(call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            echoes.whisper(call)
        }

    private fun prompt(call: Call): String =
        "Speak on the line to the stranger who guides ${call.name}. ${call.name}'s secret fear: ${call.role.fear}. " +
            (call.lines.lastOrNull { it.who == Who.HOLLOW }?.let { "Last time you said: \"${it.text}\". Say something new. " } ?: "") +
            "One sentence, at most 12 words, intimate and cruel. No quotation marks."

    private companion object {
        const val SYSTEM =
            "You are the Hollow, a thing that hunts in a haunted maze. One of the lost is on the phone with a stranger, " +
                "and you have found the line. You know the fears of the lost, and you whisper to the stranger. You never explain yourself."
    }
}
