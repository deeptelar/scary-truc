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

/** How a call ends. */
enum class End { OUT, TAKEN, DOOM, BATTERY }

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
    val facts: List<String> = emptyList(),
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
            facts = facts + "A voice that isn't the stranger's just whispered on the phone: \"$text\"",
        )

    /** The call after the caller said [text]. */
    fun spoke(text: String): Call =
        copy(
            lines = lines + Line(Who.CALLER, text) + listOfNotNull(if (over) Line(Who.LINE, "Call ended") else null),
            facts = emptyList(),
            said = "",
            heard = null,
        )

    companion object {
        fun begin(plan: Plan, maze: Maze, haunt: Haunt, seed: Long = maze.seed): Call {
            val start = maze.start
            val role = plan.role
            val call =
                Call(
                    plan = plan,
                    seed = seed,
                    role = role,
                    name = NAMES[Random(seed).nextInt(NAMES.size)],
                    at = start,
                    facing = maze.exits(start).first(),
                    battery = plan.battery,
                    hollow = Hollow(maze.lair, awake = plan.awake),
                )
            val opening =
                listOf(
                    "I just woke up here. I don't know how I got here.",
                    "My name is ${NAMES[Random(seed).nextInt(NAMES.size)]}.",
                    plan.setting,
                    "It's dark. My phone is my only light, and you're the only one who picked up.",
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
    val facts = mutableListOf<String>()
    var panic = panic
    var trust = trust
    fun chance(salt: Int) = Random(seed * 31 + turn * 131L + salt).nextDouble()
    if (said.isBlank()) {
        facts += "You said nothing for a long time."
        panic += 8
        trust -= 3
    } else {
        panic -= heard.calm * 6
        trust += heard.calm * 2
    }

    var order = heard.order
    var bolted = false
    // Left without an answer, they choose for themselves.
    if (said.isBlank()) {
        order = instinct(maze)
        if (order != Order.NONE) facts += "So I chose myself."
    }
    when {
        // Kind words hold them for a turn; anything else, and they may run.
        panic >= BOLT && heard.calm <= 0 && chance(1) < 0.6 -> {
            bolted = true
            order = Order.NONE
        }
        said.isNotBlank() && order.turn != null && trust < DOUBT && chance(2) < 0.5 -> {
            facts += "I didn't go ${order.word}: I don't trust you enough."
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
    var marks = marks
    var carried = carried
    var taken = taken
    var end: End? = null
    var death: String? = null
    var walked = 0
    val random = Random(seed * 17 + turn)
    var woke = false

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
                facts += if (distance <= 3) "Something right next to me just woke up." else "Far away, something howled. Something woke up."
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
            facts += "It was here. Right here, next to me. It sniffed the air, and it went on."
            panic += 15
            hollow = it.copy(deaf = DEAF)
            return false
        }
        end = End.TAKEN
        death = "the Hollow"
        facts += "It found me. It's here. It's taking me."
        return true
    }

    fun step(dir: Dir): Boolean {
        at += dir
        facing = dir
        battery--
        walked++
        if (!lostTrack) {
            mapped = mapped + (at to maze.exits(at).toSet())
            drawn = drawn + (at to pencil(turn, panic))
        }
        maze.doomAt(at)?.let {
            end = End.DOOM
            death = it.kind.word
            facts += "I went in. It was ${it.kind.word}."
            return false
        }
        if (at == maze.gate) {
            end = End.OUT
            facts += "I'm through the door. I'm outside. I can see the sky."
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
                facts += "I can't go ${order.word}: there's only a wall."
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
                    val bend = if (facing != dir) ", around a bend" else ""
                    facts.add(did, "I went ${if (order == Order.AHEAD) "ahead" else order.word} and followed the corridor $walked step${if (walked == 1) "" else "s"}$bend.")
                }
            }
        }
        Order.STAY -> {
            facts += "I stayed where I was."
            hunt()
            meet()
        }
        Order.HIDE -> {
            hidden = true
            facts += "I crouched against the wall and covered the light of my phone. I'm keeping still."
            hunt()
            meet()
        }
        Order.MARK -> {
            val number = marks.size + 1
            if (marks.none { it.at == at }) {
                marks = marks + Mark(at, number)
                facts += "I scratched a $number on the wall here, with a key."
            } else {
                facts += "There's already a mark here."
            }
            hunt()
            meet()
        }
        Order.TAKE -> {
            val item = haunt.items[at]?.takeIf { at !in taken }
            if (item == null) {
                facts += "There's nothing here to take."
            } else {
                carried = carried + item
                taken = taken + at
                facts += "I picked up ${item.thing}. I have it now."
            }
            hunt()
            meet()
        }
        Order.USE -> {
            val item = heard.item?.takeIf { it in carried } ?: carried.singleOrNull()?.takeIf { heard.item == null }
            if (item == null) {
                facts += heard.item?.let { "I don't have a ${it.word}." } ?: "I don't have anything to use."
            } else {
                carried = carried - item
                when (item) {
                    Item.BATTERY -> {
                        battery = (battery + RECHARGE).coerceAtMost(100)
                        facts += "I swapped in the other battery. The phone says $battery%."
                    }
                    Item.FLARE -> {
                        panic -= 10
                        val it = hollow?.takeIf { h -> h.awake && (maze.distances(at)[h.at] ?: 99) <= FLARE_REACH }
                        if (it == null) {
                            facts += "I lit the flare. Red light everywhere, and nothing in it. It's burning out."
                        } else {
                            // It flees the light: away from the caller, and deaf to them for a while.
                            val from = maze.distances(at)
                            var fled = it.copy(deaf = FLARE_FEAR, lure = null)
                            repeat(3) { _ ->
                                maze.exits(fled.at).maxByOrNull { d -> from[fled.at + d] ?: 0 }?.let { d -> fled = fled.copy(at = fled.at + d, from = fled.at) }
                            }
                            hollow = fled
                            facts += "I lit the flare. Red light everywhere. Something screamed and ran from it."
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
                        facts += if (fell == at) "I threw it, and it hit the wall right in front of me. So loud." else "I threw the stone ${pointing(facing.side(dir))}. It clattered somewhere down the corridor."
                        hollow?.let { h ->
                            if ((maze.distances(fell)[h.at] ?: 99) <= HEARS_STONE) {
                                if (!h.awake) {
                                    woke = true
                                    facts += "Something woke up."
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
                if (end == null || end == End.TAKEN) facts.add(did, "I panicked. I ran, I don't know which way. I don't know where I am anymore.")
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
    if (end == null && hidden && order != Order.HIDE) facts += "I'm still crouched in the dark, keeping still."

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
            marks = marks,
            carried = carried,
            taken = taken,
            turn = turn + 1,
            end = end,
            death = death,
            walked = walked,
            panic = panic.coerceIn(0, 100),
            trust = trust.coerceIn(0, 100),
        )
    if (end == null && next.battery <= 0) {
        next = next.copy(end = End.BATTERY, battery = 0)
        facts += "My battery is dying. The screen is going dark."
    }
    if (next.over) return next.copy(facts = facts, away = if (end == End.OUT) 0 else maze.distances(at)[maze.gate])
    if (next.turn == plan.midnight) facts += "A bell is ringing somewhere, twelve times. Midnight. The walls are grinding, like they're moving."
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
        seen += "Wait. I know this place. I've been here before."
        panic -= 10
    }
    var drawn = drawn
    if (!lostTrack) {
        mapped = mapped + (at to maze.exits(at).toSet())
        drawn = drawn + (at to pencil(turn, panic))
    }

    val names = listOf("Ahead", "To my right", "Behind me", "To my left")
    for (quarter in listOf(0, 3, 1, 2)) {
        val dir = facing.turned(quarter)
        val name = names[quarter]
        if (!maze.canGo(at, dir)) {
            if (quarter != 2) seen += "$name: a wall."
            continue
        }
        val next = at + dir
        val doom = maze.doomAt(next)
        when {
            doom != null -> {
                seen += "$name: ${doom.kind.sign}."
                if (!lostTrack && next !in glimpsed) panic += 4
                if (!lostTrack) glimpsed = glimpsed + (next to Glimpse.STRANGE)
            }
            next == maze.gate -> {
                seen += "$name: light under a door. Real light. And cold air."
                if (!lostTrack) glimpsed = glimpsed + (next to Glimpse.LIGHT)
            }
            next in haunt.oddities -> {
                seen += "$name: a corridor, and something in it: ${haunt.oddities.getValue(next)}."
                if (!lostTrack) glimpsed = glimpsed + (next to Glimpse.STRANGE)
            }
            quarter == 2 && walked > 0 -> seen += "Behind me: the way I came."
            else -> seen += "$name: a corridor."
        }
    }
    if (maze.exits(at).size == 1 && at != origin) seen += "It's a dead end."

    haunt.arrows[at]?.let { seen += "A chalk arrow on the wall here points ${pointing(facing.side(it))}." }
    haunt.notes[at]?.let {
        seen += "A note on the floor here, in pencil: \"$it\""
        if (walked > 0 || turn == 0) panic -= 6
    }
    haunt.oddities[at]?.let { seen += "Here, on the floor: $it." }
    haunt.items[at]?.takeIf { at !in taken }?.let { seen += "Here, on the floor: ${it.thing}. I could take it." }
    mark?.let { seen += "My own mark is scratched on the wall here: the number ${it.number}." }

    val toGate = maze.distances(at)[maze.gate] ?: 99
    if (toGate <= COLD) seen += if (toGate <= 1) "The air is freezing here. There's a draft." else "The air is cold here, colder than anywhere else."

    hollow?.let { hollow ->
        val steps = maze.distances(at)
        val distance = steps[hollow.at] ?: 99
        if (distance > HEARING) return@let
        if (distance == 0) {
            if (hollow.awake) {
                seen += "It's still here. Right next to me. I can hear it breathing."
                panic += 8
            }
            return@let
        }
        val from = maze.way(at, hollow.at)?.first?.let { facing.side(it) }?.let { listOf("ahead", "my right", "behind me", "my left")[it] } ?: "everywhere"
        if (hollow.awake) {
            val how = when (distance) { 1 -> "right around the corner"; 2 -> "close"; else -> "not far" }
            seen += "I can hear it: slow, wet footsteps, $how, from $from."
            panic += when (distance) { 1 -> 20; 2 -> 12; else -> 6 }
        } else if (distance <= 3) {
            seen += "Something is breathing, slow, like it's asleep, from $from."
            panic += 5
        }
    }

    // They ask which way, naming the ways they see: the stranger answers, or they choose themselves.
    val ways = listOf(0, 3, 1, 2).filter { maze.canGo(at, facing.turned(it)) }.map { listOf("ahead", "right", "back", "left")[it] }
    if (ways.size > 1) seen += "Which way do I go: ${ways.dropLast(1).joinToString(", ")} or ${ways.last()}?"

    // Finding what they were told to look for makes the stranger worth listening to.
    if (heard?.order?.turn != null && walked > 0 && (at in haunt.notes || at in haunt.arrows || toGate <= COLD)) trust += 5
    return copy(
        facts = seen,
        panic = panic.coerceIn(0, 100),
        trust = trust.coerceIn(0, 100),
        lostTrack = lostTrack,
        mapped = mapped,
        drawn = drawn,
        glimpsed = glimpsed,
    )
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

private fun pointing(side: Int): String = listOf("ahead", "to my right", "back the way I came", "to my left")[side]

/** First names for callers: no one in particular. */
private val NAMES =
    listOf(
        "Alex", "Sam", "Noa", "Jordan", "Maya", "Leo", "Iris", "Nico", "Ruth", "Theo", "Lena", "Omar",
        "Ada", "Hugo", "Mira", "Joel", "Vera", "Ivan", "Clara", "Eli", "Tess", "Rafa", "June", "Milo",
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
