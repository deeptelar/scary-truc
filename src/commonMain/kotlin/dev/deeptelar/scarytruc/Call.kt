package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * How the caller is: jumpy, methodical, joking or doubting, one for each call. Gemma plays their [voice].
 * They start the call with [trust] in the stranger who picked up, and with [panic].
 */
enum class Role(
    val voice: String,
    /** What they fear most, which only the Hollow knows and whispers back. */
    val fear: String,
    val trust: Int,
    val panic: Int,
) {
    PLANNER(
        "You want a plan: steps, numbers, a reason for everything. Not knowing scares you more than the dark.",
        "a plan of theirs that failed and cost everyone",
        trust = 50, panic = 35,
    ),
    SCOUT(
        "You are jumpy and you talk fast, and every sound makes you want to run.",
        "being left behind, alone in the dark, forgotten",
        trust = 60, panic = 50,
    ),
    CODER(
        "You cope with nervous jokes about snacks and bugs, and they get worse the more scared you are.",
        "the bug they shipped that took everything down at 3 a.m.",
        trust = 55, panic = 40,
    ),
    CRITIC(
        "You doubt everything, the stranger on the phone most of all, and you say so.",
        "the cruel review they wrote that made someone quit",
        trust = 25, panic = 30,
    ),
}

/** What the stranger tells the caller to do. A way is [turn] quarter turns right of where the caller faces. */
enum class Order(val word: String, val turn: Int? = null) {
    AHEAD("ahead", 0),
    RIGHT("right", 1),
    BACK("back", 2),
    LEFT("left", 3),
    STAY("stay"),
    HIDE("hide"),
    MARK("mark"),
    TAKE("take"),
    USE("use"),
    NONE("none"),
    ;

    companion object {
        fun of(word: String): Order? = entries.firstOrNull { it.word == word.trim().lowercase() }
    }
}

/** [quarters] quarter turns to the right of this direction. */
fun Dir.turned(quarters: Int): Dir = Dir.entries[(ordinal + quarters) % 4]

/** Where [dir] is for someone who faces this way: ahead, to the right, behind or to the left. */
fun Dir.side(dir: Dir): Int = (dir.ordinal - ordinal + 4) % 4

/** What the caller understood from the stranger's words: what to do, and how it made them feel, from -2 to 2. */
@Serializable
data class Heard(
    val order: Order,
    val calm: Int,
    /** What to use, when the order is [Order.USE]: `null` when the stranger didn't say. */
    val item: Item? = null,
    /** Where to throw the stone: a way from where the caller faces. */
    val toward: Order? = null,
    /** Whether the stranger asked them to say again what is around them. */
    val again: Boolean = false,
    /** Whether the stranger spoke another language than the caller's, who didn't understand. */
    val foreign: Boolean = false,
    /** Whether the stranger's words had nothing to do with getting out: a joke, trivia, nonsense. */
    val offTopic: Boolean = false,
)

/** What lies in a maze to pick up, and is used once. [code] is its letter in a drawn maze. */
enum class Item(val word: String, val thing: String, val code: Char) {
    BATTERY("battery", "a phone battery, the same model as mine", 'b'),
    FLARE("flare", "a road flare, unlit", 'f'),
    STONE("stone", "a stone that fits in my hand", 's'),
    ;

    companion object {
        fun of(word: String): Item? = entries.firstOrNull { it.word == word.trim().lowercase() }
    }
}

/**
 * The Hollow sleeps until midnight or a noise close by; then it hunts by sound. After it passed a
 * caller who hid, or fled a flare, it wanders off for [deaf] turns. A thrown stone draws it to
 * where it fell, its [lure], before anything else.
 */
@Serializable
data class Hollow(val at: Pos, val awake: Boolean = false, val from: Pos? = null, val deaf: Int = 0, val lure: Pos? = null)

/** A number the caller scratched on a wall, when the stranger asked. */
@Serializable
data class Mark(val at: Pos, val number: Int)

enum class Who { CALLER, YOU, HOLLOW, LINE }

/** One line of the call. */
@Serializable
data class Line(val who: Who, val text: String)

/** How a call ends: [ALONE] when the stranger hung up, and no one gets out alone. */
enum class End { OUT, TAKEN, DOOM, BATTERY, ALONE }

/** Something the caller saw from next to it, which the notebook keeps: strange, or the light of the way out. */
enum class Glimpse { STRANGE, LIGHT }

/**
 * The clues of a maze: chalk [arrows] along a safe way out, [notes] from someone who was here
 * before, and harmless [oddities] that look as bad as a doom.
 */
class Haunt(
    val arrows: Map<Pos, Dir>,
    val notes: Map<Pos, String>,
    val oddities: Map<Pos, String>,
    val items: Map<Pos, Item> = emptyMap(),
) {
    companion object {
        /**
         * The clues drawn in [rows], as [Maze.parse] reads them: the first of [notes] lies at `S`,
         * the others at the digits 2 to 9; the `o`s are the [oddities], in reading order; `^`, `>`,
         * `v` and `<` are chalk arrows.
         */
        fun of(rows: List<String>, notes: List<String>, oddities: List<String>): Haunt {
            val cells = (0 until (rows.size - 1) / 2).flatMap { y -> (0 until (rows.first().length - 1) / 2).map { x -> Pos(x, y) } }
            fun cell(pos: Pos): Char = rows[pos.y * 2 + 1][pos.x * 2 + 1]
            val arrows = mapOf('^' to Dir.NORTH, '>' to Dir.EAST, 'v' to Dir.SOUTH, '<' to Dir.WEST)
            val odd = cells.filter { cell(it) == 'o' }
            require(odd.size == oddities.size) { "The maze has ${odd.size} oddities and ${oddities.size} texts." }
            return Haunt(
                arrows = cells.mapNotNull { pos -> arrows[cell(pos)]?.let { pos to it } }.toMap(),
                notes =
                    cells.mapNotNull { pos ->
                        when (val c = cell(pos)) {
                            'S' -> notes.firstOrNull()?.let { pos to it }
                            in '2'..'9' -> pos to notes[c - '1']
                            else -> null
                        }
                    }.toMap(),
                oddities = odd.zip(oddities).toMap(),
                items = cells.mapNotNull { pos -> Item.entries.firstOrNull { it.code == cell(pos) }?.let { pos to it } }.toMap(),
            )
        }
    }
}

/**
 * A call, the state of the Telar graph. A turn is the stranger saying something ([said]), the
 * caller understanding it ([heard]), [walk] (where the maze answers), and the caller speaking.
 * What the caller says comes from [facts] only: the maze decides what is true, Gemma only how it
 * sounds.
 */
@Serializable
data class Call(
    val plan: Plan,
    val seed: Long,
    val role: Role,
    /** The caller's name, a different one on every try: the [role] is only how they are. */
    val name: String,
    /** Whether the caller screams in a woman's voice or a man's. */
    val woman: Boolean,
    /** The language the caller speaks and understands. */
    val lang: Lang = Lang.EN,
    val at: Pos,
    val facing: Dir,
    val battery: Int,
    val hollow: Hollow?,
    val panic: Int = role.panic,
    val trust: Int = role.trust,
    val turn: Int = 0,
    val hidden: Boolean = false,
    /** After a panicked run the caller doesn't know where they are, until they find something they know. */
    val lostTrack: Boolean = false,
    val marks: List<Mark> = emptyList(),
    /** The notebook: every cell the caller stood in while they knew where they were, with its ways out then. */
    val mapped: Map<Pos, Set<Dir>> = emptyMap(),
    /**
     * The turn the pencil last went over each cell of [mapped]: the notebook fades after [FADE]
     * turns, except where the caller scratched a mark. What is drawn in a panic starts half gone.
     */
    val drawn: Map<Pos, Int> = emptyMap(),
    val glimpsed: Map<Pos, Glimpse> = emptyMap(),
    /** Where the notebook starts, and which way is up in it: where the caller faced when they woke up. */
    val origin: Pos = at,
    val up: Dir = facing,
    val said: String = "",
    val heard: Heard? = null,
    /** What the caller understood the last time they spoke, for the play log. */
    val understood: Heard? = null,
    val facts: List<String> = emptyList(),
    /**
     * What the caller already said about where they stand, since they last moved: they don't say it
     * again unless the stranger asks.
     */
    val told: Set<String> = emptySet(),
    /** How many times in a row the stranger said something that had nothing to do with getting out. */
    val strays: Int = 0,
    /** The notes the caller read out: a note is read out once. */
    val read: Set<Pos> = emptySet(),
    /** Every cell the caller walked through, known to them or not. */
    val been: Set<Pos> = emptySet(),
    /** How many walks in a row took the caller nowhere new. */
    val stale: Int = 0,
    /**
     * What the caller thinks aloud: the way they haven't tried, or a [Thought]. It comes after what
     * the voice says, in these words: Gemma, given many facts, was leaving out the one that helps.
     */
    val hunch: String? = null,
    /** What the caller thought aloud in this call: once each. */
    val thoughts: Set<Thought> = emptySet(),
    /** The turn of the last thought: they don't think aloud on every turn. */
    val thoughtAt: Int = -99,
    val lines: List<Line> = emptyList(),
    /** The turn the Hollow last spoke on the line. */
    val whispered: Int = -99,
    /** What the caller picked up and still has. */
    val carried: List<Item> = emptyList(),
    /** Where the caller picked something up: it is gone from there. */
    val taken: Set<Pos> = emptySet(),
    val end: End? = null,
    val death: String? = null,
    /** How many steps the caller walked this turn, for the sound of footsteps. */
    val walked: Int = 0,
    /** Whether the caller ran in a panic this turn, for the sound of running. */
    val ran: Boolean = false,
    /** Whether the caller walked into a wall this turn, for its sound. */
    val bumped: Boolean = false,
    /** Whether the Hollow passed the hidden caller this turn, sniffing, for its sound. */
    val sniffed: Boolean = false,
    /** Once the call is over: how many steps the caller was from the way out. */
    val away: Int? = null,
) {
    val afterMidnight: Boolean get() = turn >= plan.midnight
    val over: Boolean get() = end != null

    /** The Hollow speaks on the line when it is awake and close, every few turns. */
    fun whispers(maze: Maze): Boolean {
        val hollow = hollow?.takeIf { it.awake } ?: return false
        if (over || turn - whispered < WHISPER_EVERY) return false
        return (maze.distances(hollow.at)[at] ?: 99) <= WHISPER_REACH
    }

    /** The call after the Hollow said [text] on the line: the caller heard it too. */
    fun whisper(text: String): Call =
        copy(
            whispered = turn,
            panic = (panic + WHISPER_HURTS).coerceAtMost(100),
            lines = lines + Line(Who.HOLLOW, text),
            facts = facts + lang.t("A voice that isn't the stranger's just whispered on the phone: \"$text\"", "Una voz que no es la del desconocido acaba de susurrar por el teléfono: \"$text\""),
        )

    /** The call after the stranger hung up, with the caller somewhere in [maze]: without them, it is over. */
    fun hungUp(maze: Maze): Call =
        copy(
            end = End.ALONE,
            death = "the dark",
            away = maze.distances(at)[maze.gate],
            lines = lines + Line(Who.LINE, lang.t("You hung up", "Has colgado")),
        )

    /** The call after the caller said [text]. */
    fun spoke(text: String): Call =
        copy(
            lines = lines + Line(Who.CALLER, listOfNotNull(text, hunch).joinToString(" ")) + listOfNotNull(if (over) Line(Who.LINE, lang.t("Call ended", "Llamada finalizada")) else null),
            facts = emptyList(),
            hunch = null,
            said = "",
            heard = null,
            understood = heard,
        )

    companion object {
        fun begin(plan: Plan, maze: Maze, haunt: Haunt, seed: Long = maze.seed, lang: Lang = Lang.EN): Call {
            val start = maze.start
            val role = plan.role
            val call =
                Call(
                    plan = plan,
                    seed = seed,
                    role = role,
                    name = CALLERS[Random(seed).nextInt(CALLERS.size)].first,
                    woman = CALLERS[Random(seed).nextInt(CALLERS.size)].second,
                    lang = lang,
                    at = start,
                    facing = maze.exits(start).first(),
                    battery = plan.battery,
                    hollow = Hollow(maze.lair, awake = plan.awake),
                )
            val opening =
                listOf(
                    lang.t("I just woke up here. I don't know how I got here.", "Me acabo de despertar aquí. No sé cómo he llegado."),
                    lang.t("My name is ${call.name}.", "Me llamo ${call.name}."),
                    plan.text(lang).setting,
                    lang.t("It's dark. My phone is my only light, and you're the only one who picked up.", "Está oscuro. Mi móvil es mi única luz, y eres la única persona que ha contestado."),
                )
            return call.look(maze, haunt, opening)
        }
    }
}

/**
 * The turn of the maze after the caller understood [Call.heard]: they do it, or refuse, or bolt;
 * they walk a corridor up to where something changes; the Hollow hunts; the battery drains; and
 * [Call.facts] says what happened and what they see now. [maze] is the maze of this turn.
 */
fun Call.walk(maze: Maze, haunt: Haunt): Call {
    val heard = heard ?: Heard(Order.NONE, 0)
    // Words in another language: nothing happens, and they say so.
    if (heard.foreign) return copy(facts = listOf(lang.t("I don't understand what you're saying.", "No entiendo lo que dices.")), walked = 0, ran = false, bumped = false, sniffed = false)
    val facts = mutableListOf<String>()
    var panic = panic
    var trust = trust
    fun chance(salt: Int) = Random(seed * 31 + turn * 131L + salt).nextDouble()
    if (said.isBlank()) {
        facts += lang.t("You said nothing for a long time.", "No has dicho nada en mucho rato.")
        panic += 8
        trust -= 3
    } else {
        panic -= heard.calm * 6
        trust += heard.calm * 2
    }
    // Words that have nothing to do with getting out cost time, and more trust each time in a row.
    val strays = if (heard.offTopic) strays + 1 else 0
    if (heard.offTopic) {
        trust -= minOf(3 * strays, 9)
        panic += 3
    }

    var order = heard.order
    var bolted = false
    // Left without an answer, they choose for themselves.
    if (said.isBlank()) {
        order = instinct(maze)
        if (order != Order.NONE) facts += lang.t("So I chose myself.", "Así que he elegido yo.")
    }
    when {
        // Kind words hold them for a turn; anything else, and they may run.
        panic >= BOLT && heard.calm <= 0 && chance(1) < 0.6 -> {
            bolted = true
            order = Order.NONE
        }
        said.isNotBlank() && order.turn != null && trust < DOUBT && chance(2) < 0.5 -> {
            facts += lang.t("I didn't go ${order.word}: I don't trust you enough.", "No he ido ${order.way(lang)}: no me fío lo bastante de ti.")
            order = Order.NONE
        }
    }

    var at = at
    var facing = facing
    var battery = battery - 1
    var hollow = hollow
    var hidden = hidden && !bolted && order in setOf(Order.HIDE, Order.NONE, Order.STAY)
    var lostTrack = lostTrack || bolted
    var mapped = mapped
    var drawn = drawn
    var been = been + at
    var somewhereNew = false
    var marks = marks
    var carried = carried
    var taken = taken
    var end: End? = null
    var death: String? = null
    var walked = 0
    val random = Random(seed * 17 + turn)
    var woke = false
    var sniffed = false
    var bumped = false

    /** The Hollow's move: it wakes to midnight or a noise close by, and goes for what it hears. */
    fun hunt() {
        val it = hollow ?: return
        val steps = maze.distances(it.at)
        val distance = steps[at] ?: 99
        if (it.awake && it.lure != null) {
            // A noise it heard comes first: it goes to see what fell.
            val dir = maze.way(it.at, it.lure)?.first
            hollow = if (dir == null) it.copy(lure = null) else it.copy(at = it.at + dir, from = it.at, lure = it.lure.takeIf { lure -> lure != it.at + dir })
            return
        }
        if (!it.awake) {
            // Calm enough, the caller can creep right past it.
            if (afterMidnight || (distance <= 1 && panic >= QUIET) || (panic >= LOUD && distance <= 6)) {
                hollow = it.copy(awake = true)
                woke = true
                facts +=
                    if (distance <= 3) {
                        lang.t("Something right next to me just woke up.", "Algo justo a mi lado se acaba de despertar.")
                    } else {
                        lang.t("Far away, something howled. Something woke up.", "A lo lejos, algo ha aullado. Algo se ha despertado.")
                    }
            }
            return
        }
        val hearing = if (it.deaf > 0) -1 else (if (hidden) (if (panic < CALM_ENOUGH) 0 else 2) else 2 + panic / 20) + if (afterMidnight) 2 else 0
        val dir =
            if (distance in 1..hearing) {
                maze.way(it.at, at)?.first
            } else {
                maze.exits(it.at).filter { d -> it.at + d != it.from }.ifEmpty { maze.exits(it.at) }.randomOrNull(random)
            }
        if (dir != null) hollow = it.copy(at = it.at + dir, from = it.at)
    }

    /** Whether the Hollow and the caller are in the same place: it takes them, unless they hide, calm. */
    fun meet(): Boolean {
        val it = hollow?.takeIf { h -> h.awake && h.at == at } ?: return false
        if (hidden && panic < CALM_ENOUGH) {
            facts += lang.t("It was here. Right here, next to me. It sniffed the air, and it went on.", "Estaba aquí. Justo aquí, a mi lado. Ha olfateado el aire y ha seguido.")
            sniffed = true
            panic += 15
            hollow = it.copy(deaf = DEAF)
            return false
        }
        end = End.TAKEN
        death = "the Hollow"
        facts += lang.t("It found me. It's here. It's taking me.", "Me ha encontrado. Está aquí. Me está llevando.")
        return true
    }

    fun step(dir: Dir): Boolean {
        at += dir
        facing = dir
        battery--
        walked++
        if (at !in been) somewhereNew = true
        been = been + at
        if (!lostTrack) {
            mapped = mapped + (at to maze.exits(at).toSet())
            drawn = drawn + (at to pencil(turn, panic))
        }
        maze.doomAt(at)?.let {
            end = End.DOOM
            death = it.kind.word
            facts += lang.t("I went in. It was ${it.kind.word}.", "He entrado. Era ${it.kind.word(lang)}.")
            return false
        }
        if (at == maze.gate) {
            end = End.OUT
            facts += lang.t("I'm through the door. I'm outside. I can see the sky.", "He cruzado la puerta. Estoy fuera. Veo el cielo.")
            return false
        }
        if (meet()) return false
        // It is slow: a step for every two of the caller's. Standing still is what lets it come.
        if (walked % 2 == 0) hunt()
        return !meet()
    }

    // What the caller did comes first in what they say, after the stranger's silence or their refusal.
    val did = facts.size
    when (order) {
        Order.AHEAD, Order.RIGHT, Order.BACK, Order.LEFT -> {
            val dir = facing.turned(order.turn!!)
            if (!maze.canGo(at, dir)) {
                facts += lang.t("I can't go ${order.word}: there's only a wall.", "No puedo ir ${order.way(lang)}: solo hay una pared.")
                bumped = true
                trust -= 4
                hunt()
                meet()
            } else {
                var going = dir
                while (step(going)) {
                    val exits = maze.exits(at)
                    val ahead = exits.singleOrNull { it != going.opposite }
                    val stop =
                        exits.size != 2 || ahead == null || at in haunt.notes || at in haunt.arrows || at in haunt.oddities ||
                            marks.any { it.at == at } || (at in haunt.items && at !in taken) || maze.doomAt(at + ahead) != null || at + ahead == maze.gate || woke ||
                            hollow?.takeIf { it.awake }?.let { (maze.distances(at)[it.at] ?: 99) <= 3 } == true || walked >= MAX_WALK
                    if (stop) break
                    going = ahead!!
                }
                if (end == null || end == End.TAKEN) {
                    val bend = if (facing != dir) lang.t(", around a bend", ", tras una curva") else ""
                    facts.add(
                        did,
                        lang.t(
                            "I went ${order.word} and followed the corridor $walked step${if (walked == 1) "" else "s"}$bend.",
                            "He ido ${order.way(lang)} y he seguido el pasillo $walked paso${if (walked == 1) "" else "s"}$bend.",
                        ),
                    )
                }
            }
        }
        Order.STAY -> {
            facts += lang.t("I stayed where I was.", "Me he quedado donde estaba.")
            hunt()
            meet()
        }
        Order.HIDE -> {
            hidden = true
            facts += lang.t("I crouched against the wall and covered the light of my phone. I'm keeping still.", "Me he agachado contra la pared y he tapado la luz del móvil. No me muevo.")
            hunt()
            meet()
        }
        Order.MARK -> {
            val number = marks.size + 1
            if (marks.none { it.at == at }) {
                marks = marks + Mark(at, number)
                facts += lang.t("I scratched a $number on the wall here, with a key.", "He rayado un $number en la pared, con una llave.")
            } else {
                facts += lang.t("There's already a mark here: my number ${marks.first { it.at == at }.number}.", "Aquí ya hay una marca: mi número ${marks.first { it.at == at }.number}.")
            }
            hunt()
            meet()
        }
        Order.TAKE -> {
            val item = haunt.items[at]?.takeIf { at !in taken }
            if (item == null) {
                facts += lang.t("There's nothing here to take.", "Aquí no hay nada que coger.")
            } else {
                carried = carried + item
                taken = taken + at
                facts += lang.t("I picked up ${item.thing}. I have it now.", "He cogido ${item.thing(lang)}. Ahora la llevo yo.")
            }
            hunt()
            meet()
        }
        Order.USE -> {
            val item = heard.item?.takeIf { it in carried } ?: carried.singleOrNull()?.takeIf { heard.item == null }
            if (item == null) {
                facts += heard.item?.let { lang.t("I don't have a ${it.word}.", "No tengo ninguna ${it.word(lang)}.") } ?: lang.t("I don't have anything to use.", "No tengo nada que usar.")
            } else {
                carried = carried - item
                when (item) {
                    Item.BATTERY -> {
                        battery = (battery + RECHARGE).coerceAtMost(100)
                        facts += lang.t("I swapped in the other battery. The phone says $battery%.", "He cambiado la batería. El móvil marca un $battery %.")
                    }
                    Item.FLARE -> {
                        panic -= 10
                        val it = hollow?.takeIf { h -> h.awake && (maze.distances(at)[h.at] ?: 99) <= FLARE_REACH }
                        if (it == null) {
                            facts += lang.t("I lit the flare. Red light everywhere, and nothing in it. It's burning out.", "He encendido la bengala. Luz roja por todas partes, y nada en ella. Se está apagando.")
                        } else {
                            // It flees the light: away from the caller, and deaf to them for a while.
                            val from = maze.distances(at)
                            var fled = it.copy(deaf = FLARE_FEAR, lure = null)
                            repeat(3) { _ ->
                                maze.exits(fled.at).maxByOrNull { d -> from[fled.at + d] ?: 0 }?.let { d -> fled = fled.copy(at = fled.at + d, from = fled.at) }
                            }
                            hollow = fled
                            facts += lang.t("I lit the flare. Red light everywhere. Something screamed and ran from it.", "He encendido la bengala. Luz roja por todas partes. Algo ha chillado y ha huido de ella.")
                        }
                    }
                    Item.STONE -> {
                        val dir = facing.turned((heard.toward ?: Order.AHEAD).turn ?: 0)
                        var fell = at
                        var going = dir
                        while (maze.canGo(fell, going) && maze.distances(at)[fell]!! < THROW) {
                            fell += going
                            going = maze.exits(fell).singleOrNull { d -> d != going.opposite } ?: break
                        }
                        facts +=
                            if (fell == at) {
                                lang.t("I threw it, and it hit the wall right in front of me. So loud.", "La he tirado y ha dado en la pared que tengo delante. Qué ruido.")
                            } else {
                                lang.t("I threw the stone ${pointing(facing.side(dir), lang)}. It clattered somewhere down the corridor.", "He tirado la piedra ${pointing(facing.side(dir), lang)}. Ha rebotado en algún sitio del pasillo.")
                            }
                        hollow?.let { h ->
                            if ((maze.distances(fell)[h.at] ?: 99) <= HEARS_STONE) {
                                if (!h.awake) {
                                    woke = true
                                    facts += lang.t("Something woke up.", "Algo se ha despertado.")
                                }
                                hollow = h.copy(awake = true, lure = fell)
                            }
                        }
                    }
                }
            }
            hunt()
            meet()
        }
        Order.NONE -> {
            if (bolted) {
                // Out of nerve: down any corridor, away from what they hear.
                val away = hollow?.takeIf { it.awake }?.let { h -> maze.way(at, h.at)?.first }
                val exits = maze.exits(at).filter { it != away }.ifEmpty { maze.exits(at) }
                val dir = exits[(chance(3) * exits.size).toInt()]
                var going = dir
                while (step(going) && walked < 3) {
                    going = maze.exits(at).filter { it != going.opposite }.randomOrNull(random) ?: break
                }
                if (end == null || end == End.TAKEN) facts.add(did, lang.t("I panicked. I ran, I don't know which way. I don't know where I am anymore.", "Me ha entrado el pánico. He corrido, no sé hacia dónde. Ya no sé dónde estoy."))
            } else {
                hunt()
                meet()
            }
        }
    }
    if (end == null) {
        hunt()
        meet()
    }
    hollow = hollow?.let { it.copy(deaf = (it.deaf - 1).coerceAtLeast(0)) }
    if (end == null && hidden && order != Order.HIDE) facts += lang.t("I'm still crouched in the dark, keeping still.", "Sigo ${if (woman) "agachada" else "agachado"} en la oscuridad, sin moverme.")

    var next =
        copy(
            at = at,
            facing = facing,
            battery = battery,
            hollow = hollow,
            hidden = hidden,
            lostTrack = lostTrack,
            mapped = mapped,
            drawn = drawn,
            been = been,
            stale = if (walked == 0) stale else if (somewhereNew) 0 else stale + 1,
            marks = marks,
            carried = carried,
            taken = taken,
            turn = turn + 1,
            strays = strays,
            end = end,
            death = death,
            walked = walked,
            ran = bolted && walked > 0,
            sniffed = sniffed,
            bumped = bumped,
            panic = panic.coerceIn(0, 100),
            trust = trust.coerceIn(0, 100),
        )
    if (end == null && next.battery <= 0) {
        next = next.copy(end = End.BATTERY, battery = 0)
        facts += lang.t("My battery is dying. The screen is going dark.", "Me quedo sin batería. La pantalla se apaga.")
    }
    if (next.over) return next.copy(facts = facts, away = if (end == End.OUT) 0 else maze.distances(at)[maze.gate])
    if (next.turn == plan.midnight) {
        facts += lang.t("A bell is ringing somewhere, twelve times. Midnight. The walls are grinding, like they're moving.", "Una campana suena en algún sitio, doce veces. Medianoche. Las paredes rechinan, como si se movieran.")
    }
    if (heard.order == Order.NONE && heard.calm >= 1 && hollow?.awake != true && !bolted) next = next.copy(panic = (next.panic - 4).coerceAtLeast(0))
    return next.look(maze, haunt, facts)
}

/**
 * The call with what the caller sees and hears from where they stand added to [facts]: each way
 * from them, the clues here, the cold of the way out and the Hollow; the notebook and the nerves
 * take it in.
 */
private fun Call.look(maze: Maze, haunt: Haunt, facts: List<String>): Call {
    val seen = facts.toMutableList()
    var panic = panic
    var trust = trust
    var lostTrack = lostTrack
    var mapped = mapped
    var glimpsed = glimpsed

    val mark = marks.firstOrNull { it.at == at }
    if (lostTrack && at in mapped && (mark != null || at in haunt.notes || at in haunt.arrows || at == origin)) {
        lostTrack = false
        seen += lang.t("Wait. I know this place. I've been here before.", "Espera. Conozco este sitio. Ya he estado aquí.")
        panic -= 10
    }
    // Walking where they already walked, again and again, they say so, and what they haven't tried.
    var hunch: String? = null
    if (walked > 0 && !lostTrack && stale >= CIRCLES) {
        val first = stale == CIRCLES
        if (first) seen += lang.t("Wait. I've been here already. We're going in circles.", "Espera. Ya he estado aquí. Estamos dando vueltas.")
        // The way can be many walks off: they keep pointing it out until they get there.
        hunch =
            untried(maze)?.let {
                if (first) {
                    lang.t("Maybe we should go ${it.word}: there's a way down there I haven't tried.", "Quizá deberíamos ir ${it.way(lang)}: por ahí queda un camino que no he probado.")
                } else {
                    lang.t("The way I haven't tried is ${it.word} from here, I think.", "Creo que el camino que no he probado queda ${it.way(lang)} desde aquí.")
                }
            }
    }
    // What happened is said; what is around them, from here on, only once.
    val happened = seen.size
    var drawn = drawn
    if (!lostTrack) {
        mapped = mapped + (at to maze.exits(at).toSet())
        drawn = drawn + (at to pencil(turn, panic))
    }

    val names = if (lang == Lang.ES) listOf("Delante", "A mi derecha", "Detrás de mí", "A mi izquierda") else listOf("Ahead", "To my right", "Behind me", "To my left")
    for (quarter in listOf(0, 3, 1, 2)) {
        val dir = facing.turned(quarter)
        val name = names[quarter]
        if (!maze.canGo(at, dir)) {
            if (quarter != 2) seen += lang.t("$name: a wall.", "$name: una pared.")
            continue
        }
        val next = at + dir
        val doom = maze.doomAt(next)
        when {
            doom != null -> {
                seen += "$name: ${doom.kind.sign(lang)}."
                if (!lostTrack && next !in glimpsed) panic += 4
                if (!lostTrack) glimpsed = glimpsed + (next to Glimpse.STRANGE)
            }
            next == maze.gate -> {
                seen += lang.t("$name: light under a door. Real light. And cold air.", "$name: luz bajo una puerta. Luz de verdad. Y aire frío.")
                if (!lostTrack) glimpsed = glimpsed + (next to Glimpse.LIGHT)
            }
            next in haunt.oddities -> {
                seen += lang.t("$name: a corridor, and something in it: ${haunt.oddities.getValue(next)}.", "$name: un pasillo, y algo en él: ${haunt.oddities.getValue(next)}.")
                if (!lostTrack) glimpsed = glimpsed + (next to Glimpse.STRANGE)
            }
            quarter == 2 && walked > 0 -> seen += lang.t("Behind me: the way I came.", "Detrás de mí: por donde he venido.")
            else -> seen += lang.t("$name: a corridor.", "$name: un pasillo.")
        }
    }
    if (maze.exits(at).size == 1 && at != origin) seen += lang.t("It's a dead end.", "Es un callejón sin salida.")

    haunt.arrows[at]?.let { seen += lang.t("A chalk arrow on the wall here points ${pointing(facing.side(it), lang)}.", "Una flecha de tiza en la pared señala ${pointing(facing.side(it), lang)}.") }
    // A note read out once is only mentioned again when they come back to it.
    haunt.notes[at]?.takeIf { at !in read || walked > 0 }?.let {
        seen += if (at in read) lang.t("The note I already read is here.", "Aquí está la nota que ya he leído.") else lang.t("A note on the floor here, in pencil: \"$it\"", "Una nota en el suelo, a lápiz: \"$it\"")
        if (walked > 0 || turn == 0) panic -= 6
    }
    haunt.oddities[at]?.let { seen += lang.t("Here, on the floor: $it.", "Aquí, en el suelo: $it.") }
    haunt.items[at]?.takeIf { at !in taken }?.let { seen += lang.t("Here, on the floor: ${it.thing}. I could take it.", "Aquí, en el suelo: ${it.thing(lang)}. Podría cogerla.") }
    // A mark they just scratched, or found again when asked to, they already spoke of.
    mark?.takeIf { heard?.order != Order.MARK }?.let { seen += lang.t("My own mark is scratched on the wall here: the number ${it.number}.", "Mi propia marca está rayada en la pared: el número ${it.number}.") }

    val toGate = maze.distances(at)[maze.gate] ?: 99
    if (toGate <= COLD) {
        seen +=
            if (toGate <= 1) {
                lang.t("The air is freezing here. There's a draft.", "Aquí el aire está helado. Hay corriente.")
            } else {
                lang.t("The air is cold here, colder than anywhere else.", "Aquí el aire está frío, más que en ningún otro sitio.")
            }
    }

    hollow?.let { hollow ->
        val steps = maze.distances(at)
        val distance = steps[hollow.at] ?: 99
        if (distance > HEARING) return@let
        if (distance == 0) {
            if (hollow.awake) {
                seen += lang.t("It's still here. Right next to me. I can hear it breathing.", "Sigue aquí. Justo a mi lado. Lo oigo respirar.")
                panic += 8
            }
            return@let
        }
        val sides = if (lang == Lang.ES) listOf("delante", "mi derecha", "detrás de mí", "mi izquierda") else listOf("ahead", "my right", "behind me", "my left")
        val from = maze.way(at, hollow.at)?.first?.let { facing.side(it) }?.let { sides[it] } ?: lang.t("everywhere", "todas partes")
        if (hollow.awake) {
            val how =
                when (distance) {
                    1 -> lang.t("right around the corner", "justo a la vuelta de la esquina")
                    2 -> lang.t("close", "cerca")
                    else -> lang.t("not far", "no muy lejos")
                }
            seen += lang.t("I can hear it: slow, wet footsteps, $how, from $from.", "Lo oigo: pasos lentos, húmedos, $how, desde $from.")
            panic += when (distance) { 1 -> 20; 2 -> 12; else -> 6 }
        } else if (distance <= 3) {
            seen += lang.t("Something is breathing, slow, like it's asleep, from $from.", "Algo respira, despacio, como si durmiera, desde $from.")
            panic += 5
        }
    }

    // They ask which way, naming the ways they see: the stranger answers, or they choose themselves.
    val words = if (lang == Lang.ES) listOf("delante", "derecha", "atrás", "izquierda") else listOf("ahead", "right", "back", "left")
    val ways = listOf(0, 3, 1, 2).filter { maze.canGo(at, facing.turned(it)) }.map { words[it] }
    if (ways.size > 1) seen += lang.t("Which way do I go: ${ways.dropLast(1).joinToString(", ")} or ${ways.last()}?", "¿Por dónde voy: ${ways.dropLast(1).joinToString(", ")} o ${ways.last()}?")

    // Finding what they were told to look for makes the stranger worth listening to.
    if (heard?.order?.turn != null && walked > 0 && (at in haunt.notes || at in haunt.arrows || toGate <= COLD)) trust += 5
    // Standing where they stood, they say only what changed, unless the stranger asked again.
    val around = seen.drop(happened)
    val stayed = turn > 0 && walked == 0 && heard?.again != true
    val thought = if (hunch == null && turn > 0 && turn - thoughtAt >= THINKS_EVERY) Thought.of(copy(panic = panic, lostTrack = lostTrack), maze, haunt) else null
    return copy(
        facts = seen.take(happened) + if (stayed) around.filter { it !in told } else around,
        told = if (stayed) told + around else around.toSet(),
        read = if (at in haunt.notes) read + at else read,
        hunch = hunch ?: thought?.text(lang),
        thoughts = thoughts + listOfNotNull(thought),
        thoughtAt = if (thought != null) turn else thoughtAt,
        panic = panic.coerceIn(0, 100),
        trust = trust.coerceIn(0, 100),
        lostTrack = lostTrack,
        mapped = mapped,
        drawn = drawn,
        glimpsed = glimpsed,
    )
}

/**
 * The way to the nearest place the caller hasn't walked, the one closer to the way out if two are
 * as near, and never through a doom; `null` when they have walked everywhere.
 */
private fun Call.untried(maze: Maze): Order? {
    val deadly = maze.dooms.map { it.at }.toSet()
    val steps = maze.distances(at, deadly)
    val toGate = maze.distances(maze.gate, deadly)
    val next = steps.keys.filter { it !in been }.minWithOrNull(compareBy({ steps[it] }, { toGate[it] })) ?: return null
    val dir = maze.way(at, next, deadly)?.first ?: return null
    return Order.entries.first { it.turn == facing.side(dir) }
}

/**
 * Where the caller goes when the stranger says nothing: somewhere they haven't been, away from what
 * they hear, and back only when there is nothing else. They can't see a doom for what it is.
 */
private fun Call.instinct(maze: Maze): Order {
    val random = Random(seed * 7 + turn)
    val open = listOf(0, 1, 3, 2).filter { maze.canGo(at, facing.turned(it)) }
    if (open.isEmpty()) return Order.NONE
    val heard = hollow?.takeIf { it.awake && (maze.distances(at)[it.at] ?: 99) <= HEARING }?.let { maze.way(at, it.at)?.first }?.let { facing.side(it) }
    val choice =
        open.filter { it != heard }.ifEmpty { open }
            .sortedWith(compareBy({ it == 2 }, { at + facing.turned(it) in mapped }, { random.nextDouble() }))
            .first()
    return Order.entries.first { it.turn == choice }
}

/** The turn to write in [Call.drawn] for a cell drawn on [turn]: in a panic, as if half faded already. */
private fun pencil(turn: Int, panic: Int): Int = if (panic >= CALM_ENOUGH) turn - FADE / 2 else turn

/** How faint the notebook's drawing of [pos] is now, from 0 (gone) to 1 (fresh, or marked). */
fun Call.ink(pos: Pos): Float {
    if (marks.any { it.at == pos } || pos == origin && turn == 0) return 1f
    val age = turn - (drawn[pos] ?: return 0f)
    return (1f - age.toFloat() / FADE).coerceIn(0f, 1f)
}

/** After this many turns, a cell without a mark is gone from the notebook. */
const val FADE: Int = 6

private fun pointing(side: Int, lang: Lang): String =
    (if (lang == Lang.ES) listOf("hacia delante", "a mi derecha", "hacia atrás, por donde he venido", "a mi izquierda") else listOf("ahead", "to my right", "back the way I came", "to my left"))[side]

/** First names for callers, no one in particular, and whether they have a woman's voice. */
private val CALLERS =
    listOf(
        "Alex" to false, "Sam" to true, "Noa" to true, "Jordan" to false, "Maya" to true, "Leo" to false,
        "Iris" to true, "Nico" to false, "Ruth" to true, "Theo" to false, "Lena" to true, "Omar" to false,
        "Ada" to true, "Hugo" to false, "Mira" to true, "Joel" to false, "Vera" to true, "Ivan" to false,
        "Clara" to true, "Eli" to false, "Tess" to true, "Rafa" to false, "June" to true, "Milo" to false,
    )

/** At or above it, the caller may bolt down any corridor, whatever they were told. */
const val BOLT: Int = 85

/** Below it, the caller may not do what they're told. */
const val DOUBT: Int = 20

/** Hiding works only while the caller is calmer than this: fear is loud. */
const val CALM_ENOUGH: Int = 60

/** Calmer than this, the caller can creep past the sleeping Hollow. */
const val QUIET: Int = 40

/** Fear this loud wakes the Hollow from afar. */
const val LOUD: Int = 70

private const val MAX_WALK = 6

/** After this many walks in a row that found nothing new, the caller says they are going in circles. */
private const val CIRCLES = 3

/** The fewest turns between two thoughts said aloud. */
private const val THINKS_EVERY = 2
private const val RECHARGE = 35
private const val FLARE_REACH = 4
private const val FLARE_FEAR = 4
private const val THROW = 4
private const val HEARS_STONE = 6
private const val DEAF = 3
private const val COLD = 3
private const val HEARING = 4
private const val WHISPER_EVERY = 3
private const val WHISPER_REACH = 3
private const val WHISPER_HURTS = 10
