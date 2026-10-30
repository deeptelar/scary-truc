package dev.deeptelar.scarytruc

/**
 * A clue the player may read, behind the bulb at the top of the screen. It never says what to do:
 * it names something that is true of the night, so that one more thing to try comes to mind.
 */
enum class Clue(val en: String, val es: String) {
    LISTENS("Some hunters see nothing at all. They only listen.", "Algunos cazadores no ven nada. Solo escuchan."),
    FIRE("Most things that live in the dark hate fire.", "Casi todo lo que vive en la oscuridad odia el fuego."),
    NOISE("A stone makes its noise where it lands, not where it was thrown from.", "Una piedra hace ruido donde cae, no desde donde la tiran."),
    SPARE("A phone of that time took any battery of its model.", "Un móvil de aquella época aceptaba cualquier batería de su modelo."),
    DRAIN("Every step and every word costs the phone a little light.", "Cada paso y cada palabra le cuestan al móvil un poco de luz."),
    LEFT_HERE("Things left lying in a maze were left for someone.", "Lo que se deja tirado en un laberinto se deja para alguien."),
    WRITTEN("A number scratched on a wall looks the same every time you find it.", "Un número rayado en una pared es igual cada vez que lo encuentras."),
    LOOKALIKE("Corridors look alike. Walls can be written on.", "Los pasillos se parecen. En las paredes se puede escribir."),
    WRONG_WAY("Not everything that looks like a way is one.", "No todo lo que parece un camino lo es."),
    MOVED("When walls move, a map is only a memory. The cold doesn't move.", "Cuando las paredes se mueven, un mapa es solo un recuerdo. El frío no se mueve."),
    DRAFT("Air only moves where there is somewhere for it to go.", "El aire solo se mueve si tiene adónde ir."),
    SLEEPER("Sleepers wake to fear sooner than to footsteps.", "Lo que duerme despierta antes con el miedo que con los pasos."),
    CALM("A calm voice is easier to follow than an order.", "Una voz tranquila se sigue mejor que una orden."),
    ;

    fun text(lang: Lang): String = lang.t(en, es)

    companion object {
        /** The clue for where [call] stands now in [maze], the most pressing first; `null` when nothing calls for one. */
        fun of(call: Call, maze: Maze, haunt: Haunt): Clue? {
            if (call.over || call.turn == 0) return null
            val hollow = call.hollow
            val near = hollow?.let { maze.distances(call.at)[it.at] } ?: 99
            val awake = hollow?.awake == true
            val toGate = maze.distances(call.at)[maze.gate] ?: 99
            val strange = maze.exits(call.at).any { maze.doomAt(call.at + it) != null }
            return when {
                awake && near <= 3 && Item.FLARE in call.carried -> FIRE
                awake && near <= 3 && Item.STONE in call.carried -> NOISE
                awake && near <= 3 && !call.hidden -> LISTENS
                call.battery <= 25 && Item.BATTERY in call.carried -> SPARE
                call.turn == call.plan.midnight || call.turn == call.plan.midnight + 1 -> MOVED
                strange -> WRONG_WAY
                call.lostTrack -> WRITTEN
                call.panic >= 70 -> CALM
                !awake && near <= 4 -> SLEEPER
                call.at in haunt.items && call.at !in call.taken -> LEFT_HERE
                toGate <= 3 -> DRAFT
                call.battery <= 30 -> DRAIN
                call.turn >= 5 && call.marks.isEmpty() -> LOOKALIKE
                else -> null
            }
        }
    }
}
