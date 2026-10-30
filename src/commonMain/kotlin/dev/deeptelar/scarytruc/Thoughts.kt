package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable

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
    WRONG_WAY("Something here feels wrong. Not everything that looks like a way is one.", "Aquí hay algo raro. No todo lo que parece un camino lo es."),
    WRITTEN("I don't know where I am anymore. A mark I made would tell me.", "Ya no sé dónde estoy. Una marca mía me lo diría."),
    CALM("I can't think. If you could just talk to me slowly...", "No puedo pensar. Si me hablaras despacio..."),
    SLEEPER("Whatever that is, it's asleep. I'm scared it will hear how scared I am.", "Sea lo que sea, duerme. Me da miedo que oiga el miedo que tengo."),
    BATTERY_HERE("That battery could keep this phone alive.", "Esa batería podría mantener vivo este móvil."),
    FLARE_HERE("A flare. That might be useful.", "Una bengala. Podría venirnos bien."),
    STONE_HERE("That stone might be useful.", "Esa piedra podría venirnos bien."),
    DRAFT("I can feel the air moving. It has to be going somewhere.", "Noto que se mueve el aire. Tiene que ir a algún sitio."),
    DRAIN("My battery is going. Every step and every word costs me.", "Se me va la batería. Cada paso y cada palabra me cuestan."),
    LOOKALIKE("It all looks the same. I wish I could tell these walls apart.", "Todo parece igual. Ojalá pudiera distinguir estas paredes."),
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
                    WRONG_WAY.takeIf { maze.exits(call.at).any { maze.doomAt(call.at + it) != null } },
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
                )
            return wanted.firstOrNull { it !in call.thoughts }
        }
    }
}
