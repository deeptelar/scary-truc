package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Something the caller thinks aloud, once in a call. It never says what to do: it names something
 * true of the night, so that one more thing to try comes to the stranger's mind.
 */
@Serializable
enum class Thought(val en: String, val es: String) {
    FIRE("This flare... Things that live in the dark hate fire, don't they?", "Esta bengala... Lo que vive a oscuras odia el fuego, ¿no?"),
    NOISE("This stone would make its noise wherever it lands, not where I am.", "Esta piedra haría ruido donde cayera, no donde estoy yo."),
    LISTENS("It doesn't seem to see me. I think it only listens.", "Creo que no me ve. Creo que solo escucha."),
    SPARE("The battery I picked up might fit this phone.", "La batería que cogí quizá le sirva a este móvil."),
    MOVED("I can see the walls moving. Whatever you drew may be wrong now. The cold doesn't move, though.", "Veo moverse las paredes. Lo que hayas dibujado quizá ya no valga. El frío, en cambio, no se mueve."),
    WRITTEN("I don't know where I am anymore. A mark I made would tell me.", "Ya no sé dónde estoy. Una marca mía me lo diría."),
    CALM("I can't think. If you could just talk to me slowly...", "No puedo pensar. Si me hablaras despacio..."),
    SLEEPER("Whatever that is, it's asleep. I'm scared it will hear how scared I am.", "Sea lo que sea, duerme. Me da miedo que oiga el miedo que tengo."),
    BATTERY_HERE("That battery could keep this phone alive.", "Esa batería podría mantener vivo este móvil."),
    FLARE_HERE("A flare. That might be useful.", "Una bengala. Podría venirnos bien."),
    STONE_HERE("That stone might be useful.", "Esa piedra podría venirnos bien."),
    DRAFT("I can feel the air moving. It has to be going somewhere.", "Noto que se mueve el aire. Tiene que ir a algún sitio."),
    DRAIN("My battery is going. Every step and every word costs me.", "Se me va la batería. Cada paso y cada palabra me cuestan."),
    LOOKALIKE("It all looks the same. I wish I could tell these walls apart.", "Todo parece igual. Ojalá pudiera distinguir estas paredes."),
    SO_SURE("You sound so sure. I don't know why I'm listening to you.", "Hablas con tanta seguridad. No sé por qué te hago caso."),
    A_VOICE("I don't even know your name. I'm doing what a voice tells me.", "Ni siquiera sé cómo te llamas. Estoy haciendo lo que me dice una voz."),
    IF_YOU_WANTED("If you wanted me dead, this is exactly how it would sound.", "Si quisieras verme morir, sonaría exactamente así."),
    ;

    fun text(lang: Lang): String = lang.t(en, es)

    companion object {
        /** What [call] would think aloud where it stands in [maze], the most pressing first, of what it hasn't said yet. */
        fun of(call: Call, maze: Maze, haunt: Haunt): Thought? {
            val steps = maze.distances(call.at)
            val hollow = call.hollow
            val near = hollow?.let { steps[it.at] } ?: 99
            val awake = hollow?.awake == true
            val hunted = awake && near <= 3
            val here = haunt.items[call.at]?.takeIf { call.at !in call.taken }
            val wanted =
                listOfNotNull(
                    FIRE.takeIf { hunted && Item.FLARE in call.carried },
                    NOISE.takeIf { hunted && Item.STONE in call.carried },
                    LISTENS.takeIf { hunted && !call.hidden },
                    SPARE.takeIf { call.battery <= 25 && Item.BATTERY in call.carried },
                    MOVED.takeIf { call.turn == call.plan.midnight || call.turn == call.plan.midnight + 1 },
                    WRITTEN.takeIf { call.lostTrack },
                    CALM.takeIf { call.panic >= 70 },
                    SLEEPER.takeIf { hollow != null && !awake && near <= 4 },
                    when (here) {
                        Item.BATTERY -> BATTERY_HERE
                        Item.FLARE -> FLARE_HERE
                        Item.STONE -> STONE_HERE
                        null -> null
                    },
                    DRAFT.takeIf { (steps[maze.gate] ?: 99) <= 3 },
                    DRAIN.takeIf { call.battery <= 30 },
                    LOOKALIKE.takeIf { call.turn >= 5 && call.marks.isEmpty() },
                    // They don't know who this is, and it comes back to them.
                    IF_YOU_WANTED.takeIf { call.trust < 40 },
                    SO_SURE.takeIf { call.turn >= 2 && call.trust < 70 },
                    A_VOICE.takeIf { call.turn >= 4 },
                )
            return wanted.firstOrNull { it !in call.thoughts }
        }
    }
}

/**
 * What the caller can't go on without asking. They don't know who picked up, why that number was
 * the only one in their phone, or if the voice is the one who put them here: every so often they
 * stop and ask, and until the stranger answers they won't take another step.
 */
@Serializable
enum class Doubt(val en: String, val es: String) {
    NUMBER("Yours is the only number in this phone, and I never saved it. Who are you?", "El tuyo es el único número de este móvil, y yo nunca lo guardé. ¿Quién eres?"),
    BROUGHT("How do I know it wasn't you who put me here? Why should I listen to you?", "¿Cómo sé que no has sido tú quien me ha metido aquí? ¿Por qué debería hacerte caso?"),
    SEES("You can't see me. Or can you? How do you know where I should go?", "No puedes verme. ¿O sí? ¿Cómo sabes por dónde tengo que ir?"),
    OTHERS("There were others here before me. Did they call you too? What happened to them?", "Aquí hubo otros antes que yo. ¿También te llamaron a ti? ¿Qué les pasó?"),
    HUMAN("Say something that thing could never say. Is there even a person on this phone?", "Di algo que esa cosa nunca diría. ¿Hay siquiera una persona al teléfono?"),
    VOICE("That voice on the line. It talks to you. Do you know it? Are you with it?", "Esa voz en la línea. Te habla a ti. ¿La conoces? ¿Vas con ella?"),
    NOTE("This note. It's about you, isn't it? Is that you, the voice on the phone?", "Esta nota. Habla de ti, ¿verdad? ¿Eres tú, la voz del teléfono?"),
    ;

    fun text(lang: Lang): String = lang.t(en, es)

    companion object {
        /** What [call] can't go on without asking now in [maze], if anything: having just [read] a note, after the Hollow spoke, or when enough time passed. */
        fun of(call: Call, maze: Maze, read: Boolean): Doubt? {
            if (call.turn < 2 || call.hidden || call.doubts.size >= MOST || call.turn - call.doubtAt < EVERY) return null
            val hollow = call.hollow?.takeIf { it.awake }
            val close = hollow != null && (maze.distances(call.at)[hollow.at] ?: 99) <= 2
            val random = Random(call.seed + 3)
            return when {
                VOICE !in call.doubts && call.lines.takeLast(4).any { it.who == Who.HOLLOW } -> VOICE
                close -> null
                read && NOTE !in call.doubts -> NOTE
                call.turn >= 3 + random.nextInt(2) -> listOf(NUMBER, BROUGHT, SEES, OTHERS, HUMAN).shuffled(random).firstOrNull { it !in call.doubts }
                else -> null
            }
        }

        /** The most a caller asks in a call. */
        private const val MOST = 3

        /** The fewest turns between two doubts. */
        private const val EVERY = 4
    }
}
