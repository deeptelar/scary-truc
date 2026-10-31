package dev.deeptelar.scarytruc

/**
 * What the player can say with a tap this turn, in the words they would type: the ways the caller
 * named, something to calm them, and what can be done here. The choice is the player's: a kind word
 * costs a turn, except once a call when the caller is in a panic, and only typing can do both at once. They open
 * only as time runs out, and the hurry scares the caller a little.
 */
object Quick {
    /** The words for going [order], in [lang]. */
    private fun way(order: Order, lang: Lang): String =
        when (order) {
            Order.AHEAD -> lang.t("Go ahead", "Sigue recto")
            Order.RIGHT -> lang.t("Go right", "Ve a la derecha")
            Order.BACK -> lang.t("Go back", "Vuelve atrás")
            else -> lang.t("Go left", "Ve a la izquierda")
        }

    private val CALM =
        listOf(
            "Breathe. I'm here with you." to "Respira. Estoy aquí contigo.",
            "You're doing great. I'm with you." to "Lo estás haciendo genial. Estoy contigo.",
            "Slow down. I won't leave you." to "Despacio. No voy a dejarte.",
        )

    /** The ways open to [call] in [maze]. */
    fun ways(call: Call, maze: Maze): List<Order> =
        listOf(Order.AHEAD, Order.LEFT, Order.RIGHT, Order.BACK).filter { maze.canGo(call.at, call.facing.turned(it.turn!!)) }

    /** What [call] can be told in [maze] with [haunt]: the ways first. A stone goes down a way: against a wall it only brings the Hollow. */
    fun of(call: Call, maze: Maze, haunt: Haunt): List<String> {
        val lang = call.lang
        val ways = ways(call, maze)
        // By the lines: a breather is no turn, and the next kind word is another.
        val calm = CALM[call.lines.size % CALM.size].let { lang.t(it.first, it.second) }
        val here = haunt.items[call.at]?.takeIf { call.at !in call.taken }
        return ways.map { way(it, lang) } +
            calm +
            listOfNotNull(
                if (call.hidden) lang.t("Don't move", "No te muevas") else lang.t("Hide", "Escóndete"),
                lang.t("Mark the wall", "Marca la pared").takeIf { call.marks.none { it.at == call.at } },
                lang.t("Take it", "Cógelo").takeIf { here != null },
            ) +
            call.carried.flatMap { item -> if (item == Item.STONE) ways.map { item.use(lang, it) } else listOf(item.use(lang)) }
    }
}

/** The words for using this, in [lang]: a stone is thrown [toward] a way. */
fun Item.use(lang: Lang, toward: Order = Order.AHEAD): String =
    when (this) {
        Item.BATTERY -> lang.t("Swap in the battery", "Cambia la batería")
        Item.FLARE -> lang.t("Light the flare", "Enciende la bengala")
        Item.STONE -> lang.t("Throw the stone ${toward.word}", "Tira la piedra ${toward.way(Lang.ES)}")
    }
