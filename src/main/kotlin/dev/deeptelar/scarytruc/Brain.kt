package dev.deeptelar.scarytruc

import dev.deeptelar.telar.agent.ChatModel
import dev.deeptelar.telar.agent.Description
import dev.deeptelar.telar.agent.ask
import dev.deeptelar.telar.agent.chat
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Decides where one agent of the crew goes this turn, from its senses only. */
fun interface Brain {
    suspend fun decide(night: Night, maze: Maze, agent: Agent): Move
}

/**
 * The crew without Gemma: each agent weighs its senses with the weights of its [Role], and a little
 * chance. The same night and turn always decide the same, so a fork replays it.
 */
object Instinct : Brain {
    override suspend fun decide(night: Night, maze: Maze, agent: Agent): Move {
        val random = Random(night.seed * 31 + night.turn * 7 + agent.role.ordinal)
        val ways = senses(night, maze, agent)
        val role = agent.role
        fun near(steps: Int?) = if (steps == null) 0.0 else 1.0 / steps
        // Fear sharpens with lost nerve, and each scream heard scares less than the one before.
        val fear = 0.4 + (100 - agent.nerve) / 100.0
        val numb = 1.0 / (1 + agent.screams * 0.5)
        val scored =
            ways.map { way ->
                val score =
                    role.treat * near(way.treat) +
                        role.trick * fear * numb * near(way.trick) +
                        role.hollow * fear * near(way.hollow) +
                        role.air * (if (way.air) 1.0 else 0.0) +
                        (if (way.gate != null) 10.0 else 0.0) +
                        role.novelty * (if (way.been == 0) 1.0 else -0.3 * way.been) -
                        (if (way.cameFrom) 0.5 else 0.0) +
                        random.nextDouble(0.0, 1.2)
                way to score
            }
        val best = scored.maxByOrNull { it.second }
        // Staying is better only when every way is worse than standing still in the dark.
        val go = best?.takeIf { it.second > -1.0 }?.first
        return Move(role, go?.dir, line(role, go, random))
    }

    private fun line(role: Role, way: Way?, random: Random): String {
        val lines =
            when {
                way == null -> listOf("I'm not moving.", "Every way is wrong.", "Wait. Listen.")
                way.gate != null -> listOf("The gate! Run!", "I see the way out!")
                way.hollow != null && role.hollow < 0 -> listOf("Something breathes back there.", "Don't look behind you.")
                way.treat != null && role.treat > 0 -> listOf("Is that caramel?", "I smell candy.", "Just one bite.")
                way.treat != null -> listOf("Free candy? Obviously a trap.", "Nice try, maze.")
                way.trick != null && role.trick > 0 -> listOf("A scream? Cheap trick.", "Let's see who screams.")
                way.air -> listOf("Fresh air. This way.", "I can feel a draft.")
                way.been == 0 -> listOf("Nobody has been this way.", "New corridor.", "Let's try ${way.dir.word}.")
                else -> listOf("Haven't we been here?", "Again this corridor.", "Keep moving.")
            }
        return lines[random.nextInt(lines.size)]
    }
}

@Serializable
data class Choice(
    @Description("Where you go: one of the directions you can take, or \"stay\"")
    val go: String,
    @Description("What you say out loud as you go, in character, at most 10 words")
    val says: String,
)

/**
 * The crew with Gemma: each agent is asked as itself, with its nature and what it senses, where it
 * goes. When Gemma cannot be reached or answers a wall, the agent follows its [instinct] this turn,
 * and [trouble] says why.
 */
class Gemma(
    private val model: ChatModel,
    private val instinct: Brain = Instinct,
    private val trouble: (String?) -> Unit = {},
) : Brain {
    override suspend fun decide(night: Night, maze: Maze, agent: Agent): Move {
        val ways = senses(night, maze, agent)
        val options = ways.map { it.dir.word } + "stay"
        return try {
            val choice = model.ask<Choice>(prompt(night, agent, ways, options), system = system(agent.role, night))
            val go = if (choice.go.trim().lowercase() == "stay") null else Dir.of(choice.go)
            if (go != null && ways.none { it.dir == go }) {
                // A wall: it bumps into it, and the turn goes to its instinct.
                instinct.decide(night, maze, agent)
            } else {
                trouble(null)
                Move(agent.role, go, choice.says.trim().take(80), gemma = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trouble(e.message ?: e::class.simpleName)
            instinct.decide(night, maze, agent)
        }
    }

    private fun prompt(night: Night, agent: Agent, ways: List<Way>, options: List<String>): String =
        describe(ways, night, agent) +
            (if (agent.words.isNotBlank()) "\nLast turn you said: \"${agent.words}\". Say something new." else "") +
            "\nWhere do you go? Choose one of: ${options.joinToString(", ")}."

    private fun system(role: Role, night: Night): String =
        "You are ${role.title}, an AI agent of a crew lost at night in a haunted maze. " +
            "${role.nature} You know only what you sense. Somewhere there is a gate out, and at dawn the maze keeps whoever is still inside. " +
            (if (night.hollow != null) "Something called the Hollow hunts the crew. " else "") +
            "Some corridors kill whoever walks in, though you cannot tell which. " +
            "How you take a scream or a sweet smell is up to you, by your nature and your nerve. " +
            "Standing still wastes the night: stay only when every way looks deadly. " +
            "Stay in character: short, scared or stubborn, never a helpful assistant.\n"
}

/** What the Hollow whispers to one of the crew, from the dark. */
fun interface Whisperer {
    suspend fun whisper(night: Night, agent: Agent): String
}

/** The Hollow without Gemma: a few lines for each fear, the same one for the same night and turn. */
object Echoes : Whisperer {
    private val lines =
        mapOf(
            Role.PLANNER to listOf("Your plan. Their graves.", "You planned this too. Look how it ends.", "Every step you choose is a mistake."),
            Role.SCOUT to listOf("They left you. They always leave you.", "Nobody is coming back for you.", "Alone. Like always. Like forever."),
            Role.CODER to listOf("It's 3 a.m. and everything is down. Because of you.", "Your last commit. I read it.", "Rollback won't save you this time."),
            Role.CRITIC to listOf("She quit because of you. Remember?", "Your words cut. Now I will.", "Who reviews the reviewer? I do."),
        )

    override suspend fun whisper(night: Night, agent: Agent): String {
        val options = lines.getValue(agent.role)
        return options[Random(night.seed + night.turn).nextInt(options.size)]
    }
}

/**
 * The Hollow played by Gemma: it knows what each of the crew fears most, and whispers it back,
 * in a new way each time. When Gemma cannot answer, the [echoes] speak instead.
 */
class GemmaWhispers(
    private val model: ChatModel,
    private val echoes: Whisperer = Echoes,
) : Whisperer {
    override suspend fun whisper(night: Night, agent: Agent): String =
        try {
            val said = model.chat(prompt(night, agent), system = SYSTEM)
            said.lines().firstOrNull { it.isNotBlank() }?.trim()?.trim('"', '“', '”', '*')?.take(110)?.takeIf { it.length > 3 }
                ?: echoes.whisper(night, agent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            echoes.whisper(night, agent)
        }

    private fun prompt(night: Night, agent: Agent): String =
        "Whisper to the ${agent.role.title}. Their secret fear: ${agent.role.fear}. " +
            (night.whisper?.takeIf { it.role == agent.role }?.let { "Last time you said: \"${it.text}\". Say something new. " } ?: "") +
            (night.crew.firstOrNull { it.fate == Fate.DEAD }?.let { "${it.role.title} is already dead tonight. " } ?: "") +
            "One sentence, at most 12 words, in second person, intimate and cruel. No quotation marks."

    private companion object {
        const val SYSTEM =
            "You are the Hollow, a thing that hunts a crew of AI agents lost in a haunted maze at night. " +
                "You know their deepest fears, and you whisper them back from the dark. You never explain yourself."
    }
}
