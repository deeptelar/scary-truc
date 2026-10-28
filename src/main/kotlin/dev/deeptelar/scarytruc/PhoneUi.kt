package dev.deeptelar.scarytruc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** The whole window: the phone on the left, the notebook on the right. */
@Composable
fun Game(model: CallModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            now = System.currentTimeMillis()
        }
    }
    Row(Modifier.fillMaxSize().background(Ink.dark).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Phone(model, now, Modifier.width(440.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Nights(model)
            PixelText(model.plan.title + if (model.plan == Plan.ENDLESS) " · ${model.maze.seed}" else "", color = Ink.orange)
            PixelText(model.plan.hint, size = SMALL, color = Ink.faint, maxLines = 3)
            Notebook(model, Modifier.weight(1f).fillMaxWidth())
            if (!model.gemma) {
                PixelText("Without Gemma, the caller only knows a few words: left, right, ahead, back, stay, hide, mark.", size = SMALL, color = Ink.candle, maxLines = 2)
            } else if (model.trouble != null) {
                PixelText("Gemma did not answer, so they heard only your keywords: ${model.trouble}", size = SMALL, color = Ink.candle, maxLines = 2)
            }
            model.failure?.let { PixelText(it, size = SMALL, color = Ink.red, maxLines = 2) }
        }
    }
}

/** The nights to choose from, with a tick on those someone got out of. */
@Composable
private fun Nights(model: CallModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelText(if (model.call.afterMidnight && model.plan.hollow && !model.call.over) "OTHERWORLD" else "SCARY TRUC", size = BIG, color = if (model.call.afterMidnight && model.plan.hollow) Ink.red else Ink.orange)
        Spacer(Modifier.weight(1f))
        for (plan in Plan.entries) {
            val label = (if (plan == Plan.ENDLESS) "∞" else "${plan.ordinal + 1}") + if (plan in model.escaped) " ✓" else ""
            PixelButton(label, if (plan == model.plan) Ink.orange else Ink.paperDark, onClick = { model.play(plan) }, enabled = model.open(plan) && !model.thinking, small = true)
        }
    }
}

@Composable
private fun Phone(model: CallModel, now: Long, modifier: Modifier) {
    val call = model.call
    val danger = if (model.ringing || call.over) 0f else danger(call, model.maze)
    Column(modifier.pixelBox(Color(0xFF15111B)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusBar(call, danger)
        if (model.ringing) {
            Ringing(model, now, Modifier.weight(1f).fillMaxWidth())
            return@Column
        }
        CallerBar(call, now)
        val speaking = now - model.spokeAt < CallModel.speaking(call)
        Transcript(model, now, danger, Modifier.weight(1f).fillMaxWidth())
        if (call.over && !model.thinking && !speaking) {
            Ending(model)
        } else {
            Talk(model)
        }
    }
}

/** The top of the phone: the time, the signal that fails when the Hollow is near, and the battery. */
@Composable
private fun StatusBar(call: Call, danger: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelText(clock(call), size = SMALL, color = if (call.afterMidnight && call.plan.hollow) Ink.blood else Ink.text)
        Spacer(Modifier.weight(1f))
        val bars = (4 - (danger * 4).toInt()).coerceIn(1, 4)
        Box(
            Modifier.size(28.dp, 16.dp).drawBehind {
                val w = size.width / 4
                repeat(4) { i ->
                    val h = size.height * (i + 1) / 4
                    drawRect(if (i < bars) Ink.text else Ink.off, Offset(i * w, size.height - h), Size(w - 2.dp.toPx(), h))
                }
            },
        )
        val low = call.battery <= 15
        PixelText("${call.battery.coerceAtLeast(0)}%", size = SMALL, color = if (low) Ink.red else Ink.text)
        Box(
            Modifier.size(30.dp, 14.dp).drawBehind {
                drawRect(Ink.text, Offset.Zero, Size(size.width - 3.dp.toPx(), size.height))
                drawRect(Color(0xFF15111B), Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 7.dp.toPx(), size.height - 4.dp.toPx()))
                drawRect(Ink.text, Offset(size.width - 3.dp.toPx(), size.height / 3), Size(3.dp.toPx(), size.height / 3))
                val fill = (size.width - 11.dp.toPx()) * call.battery.coerceIn(0, 100) / 100f
                drawRect(if (low) Ink.red else Ink.green, Offset(4.dp.toPx(), 4.dp.toPx()), Size(fill, size.height - 8.dp.toPx()))
            },
        )
    }
}

/** The time on the phone: midnight comes on the night's midnight turn, a minute a turn. */
private fun clock(call: Call): String {
    val minutes = (if (call.plan.hollow) 24 * 60 - call.plan.midnight else 23 * 60 + 20) + call.turn
    return "%02d:%02d".format((minutes / 60) % 24, minutes % 60)
}

@Composable
private fun Ringing(model: CallModel, now: Long, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        val shake = if ((now / 120) % 2 == 0L && (now / 1000) % 3 != 2L) 3.dp else 0.dp
        Box(Modifier.size(120.dp).offset(x = shake).drawBehind { draw(Sprites.agent.tinted(Ink.off), center, 9.dp.toPx()) })
        PixelText("Incoming call", color = Ink.faint)
        PixelText("Unknown number", size = BIG)
        Spacer(Modifier.height(10.dp))
        PixelButton("Answer (Enter)", Ink.green, onClick = model::answer, modifier = Modifier.width(240.dp))
        PixelText("Sound on. Headphones, if you dare.", size = SMALL, color = Ink.faint)
    }
}

/** Who is calling, how fast their heart beats, and for how long the call has gone on. */
@Composable
private fun CallerBar(call: Call, now: Long) {
    Row(Modifier.fillMaxWidth().pixelBox(Ink.paper).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val scared = call.panic >= CALM_ENOUGH && !call.over
        val sprite = (if (scared) Sprites.scared else Sprites.agent).tinted(if (call.end == End.TAKEN || call.end == End.DOOM) Ink.off else call.role.color)
        Box(Modifier.size(44.dp).drawBehind { draw(sprite, center, 4.5.dp.toPx()) })
        Column(Modifier.weight(1f)) {
            PixelText(call.role.title, color = call.role.color)
            val bpm = 62 + call.panic * 9 / 10
            val beat = !call.over && (now % (60_000 / bpm)) < 140
            PixelText(if (call.over) "—" else "♥ $bpm", size = SMALL, color = if (beat) Ink.red else Ink.faint)
        }
        PixelText(if (call.hidden && !call.over) "hiding" else "", size = SMALL, color = Ink.faint)
    }
}

/** The call so far, the newest at the bottom; the caller's last line appears letter by letter. */
@Composable
private fun Transcript(model: CallModel, now: Long, danger: Float, modifier: Modifier) {
    val call = model.call
    val lines = call.lines
    val last = lines.indexOfLast { it.who == Who.CALLER }
    val shown = ((now - model.spokeAt) / CallModel.LETTER).toInt()
    LazyColumn(modifier, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.thinking) item { Bubble(".".repeat(1 + ((now / 400) % 3).toInt()), Who.CALLER, call.role) }
        items(lines.indices.reversed().toList()) { i ->
            val line = lines[i]
            var text = line.text
            if (i == last) text = text.take(shown.coerceAtLeast(0))
            // Static eats the words when the Hollow is close.
            if (i == last && danger >= 0.5f) text = static(text, danger, now / 90 + i)
            if (i > last && i == lines.lastIndex && line.who == Who.LINE && shown < lines[last].text.length) return@items
            if (text.isNotEmpty() || line.who != Who.CALLER) Bubble(text, line.who, call.role, now)
        }
    }
}

private fun static(text: String, danger: Float, tick: Long): String {
    val random = Random(tick)
    return text.map { c -> if (c != ' ' && random.nextFloat() < (danger - 0.4f) * 0.25f) "░▒▓#"[random.nextInt(4)] else c }.joinToString("")
}

@Composable
private fun Bubble(text: String, who: Who, role: Role, now: Long = 0) {
    when (who) {
        Who.CALLER ->
            Row(Modifier.fillMaxWidth()) {
                PixelText(text, Modifier.widthIn(max = 340.dp).pixelBox(Ink.paper, raised = false).padding(horizontal = 12.dp, vertical = 9.dp), size = SMALL, color = Ink.text)
            }
        Who.YOU ->
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                PixelText(text, Modifier.widthIn(max = 320.dp).pixelBox(Ink.paperDark, raised = false).padding(horizontal = 12.dp, vertical = 9.dp), size = SMALL, color = Ink.candle)
            }
        Who.HOLLOW -> {
            val shake = if ((now / 60) % 3 == 0L) 2.dp else (-2).dp
            Box(Modifier.fillMaxWidth().offset(x = shake), contentAlignment = Alignment.Center) {
                PixelText(text, size = SMALL, color = Ink.blood)
            }
        }
        Who.LINE ->
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                PixelText("— $text —", size = SMALL, color = Ink.faint)
            }
    }
}

/** Where the player talks: Enter says it. */
@Composable
private fun Talk(model: CallModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.ringing, model.call.turn) { runCatching { focus.requestFocus() } }
    PixelField(
        model.typed,
        model::type,
        if (model.thinking) "They're talking…" else "Talk to them…",
        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                if (!model.thinking) model.send()
                true
            } else {
                false
            }
        },
        maxLines = 3,
    )
    PixelText("Enter to speak. Silence scares them.", size = SMALL, color = Ink.faint)
}

/** The end of the call: how it ended, and what next. */
@Composable
private fun Ending(model: CallModel) {
    val call = model.call
    Column(Modifier.fillMaxWidth().pixelBox(Ink.paper).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val (title, color) =
            when (call.end) {
                End.OUT -> "${call.role.title} got out" to Ink.green
                End.BATTERY -> "The battery died" to Ink.faint
                else -> "Taken by ${call.death}" to Ink.red
            }
        PixelText(title, color = color)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelButton("Same call again", Ink.paperDark, onClick = model::again, small = true)
            val next = model.plan.next
            PixelButton(if (next == null || next == Plan.ENDLESS) "Another call" else "Next night (Enter)", Ink.orange, onClick = { model.play(next ?: Plan.ENDLESS) }, small = true)
        }
    }
}

/** Paper, pencil and the red of a mark. */
private object Pencil {
    val paper = Color(0xFFD9CDB0)
    val cell = Color(0xFFCBBD9C)
    val lead = Color(0xFF3E372F)
    val faint = Color(0xFF9C8F76)
    val red = Color(0xFFA8322A)
    val chalk = Color(0xFF4B6C8F)
    val light = Color(0xFFD9A21E)
}

/**
 * The player's notebook: what the caller told them, drawn by itself, with the way the caller faced
 * when they woke up at the top. It keeps what was true when the caller stood there: after midnight
 * it can lie. While the caller is lost, nothing is drawn and the caller isn't on it.
 */
@Composable
private fun Notebook(model: CallModel, modifier: Modifier) {
    val call = model.call
    val haunt = model.haunt
    val measurer = rememberTextMeasurer()
    Column(modifier.pixelBox(Pencil.paper).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().drawBehind {
                if (model.ringing) return@drawBehind
                drawNotebook(call, haunt, measurer)
            },
        )
        val notes = if (model.ringing) emptyList() else haunt.notes.filterKeys { it in call.mapped }.values
        for (note in notes.toList().takeLast(3)) PixelText("“$note”", size = SMALL, color = Pencil.lead, maxLines = 2)
        if (call.lostTrack && !call.over) PixelText("They ran. They don't know where they are: find a mark or a note you know.", size = SMALL, color = Pencil.red, maxLines = 2)
    }
}

private fun DrawScope.drawNotebook(call: Call, haunt: Haunt, measurer: TextMeasurer) {
    val cells = call.mapped.keys + call.glimpsed.keys + call.origin
    val page = cells.associateWith { call.onPage(it) }
    val minX = page.values.minOf { it.first } - 1
    val maxX = page.values.maxOf { it.first } + 1
    val minY = page.values.minOf { it.second } - 1
    val maxY = page.values.maxOf { it.second } + 1
    val cell = minOf(size.width / (maxX - minX + 1), size.height / (maxY - minY + 1), 70.dp.toPx())
    val left = (size.width - cell * (maxX - minX + 1)) / 2 - minX * cell
    val top = (size.height - cell * (maxY - minY + 1)) / 2 - minY * cell
    fun corner(pos: Pos): Offset = page.getValue(pos).let { (x, y) -> Offset(left + x * cell, top + y * cell) }
    fun center(pos: Pos): Offset = corner(pos) + Offset(cell / 2, cell / 2)
    fun write(text: String, at: Offset, color: Color, size: TextUnit) {
        val layout = measurer.measure(text, TextStyle(fontFamily = PixelFont, fontSize = size, color = color))
        drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
    val line = maxOf(2.dp.toPx(), cell / 18)

    for ((pos, exits) in call.mapped) {
        val at = corner(pos)
        drawRect(Pencil.cell, at + Offset(line, line), Size(cell - 2 * line, cell - 2 * line))
        for (dir in Dir.entries) {
            if (dir in exits) continue
            when (call.onPage(dir)) {
                Dir.NORTH -> drawRect(Pencil.lead, at, Size(cell, line))
                Dir.SOUTH -> drawRect(Pencil.lead, at + Offset(0f, cell - line), Size(cell, line))
                Dir.WEST -> drawRect(Pencil.lead, at, Size(line, cell))
                Dir.EAST -> drawRect(Pencil.lead, at + Offset(cell - line, 0f), Size(line, cell))
            }
        }
        haunt.arrows[pos]?.let { arrow(center(pos), call.onPage(it), cell, Pencil.chalk) }
        if (pos in haunt.notes) drawRect(Pencil.faint, center(pos) + Offset(-cell / 6, cell / 8), Size(cell / 3, cell / 5))
        if (pos in haunt.oddities) write("?", center(pos) + Offset(cell / 4, -cell / 4), Pencil.lead, (cell / 5).toSp())
    }
    write("×", center(call.origin) + Offset(-cell / 4, -cell / 4), Pencil.lead, (cell / 4).toSp())
    for ((pos, glimpse) in call.glimpsed) {
        if (pos in call.mapped && glimpse == Glimpse.STRANGE && call.over.not()) continue
        when (glimpse) {
            Glimpse.STRANGE -> write("!", center(pos), Pencil.red, (cell / 2.5f).toSp())
            Glimpse.LIGHT -> {
                drawRect(Pencil.light, center(pos) - Offset(cell / 5, cell / 4), Size(cell * 2 / 5, cell / 2))
                write("light", center(pos) + Offset(0f, cell / 2.6f), Pencil.lead, (cell / 6).toSp())
            }
        }
    }
    for (mark in call.marks) {
        if (mark.at !in call.mapped) continue
        val at = center(mark.at) + Offset(cell / 4, cell / 4)
        drawCircle(Pencil.red, cell / 7, at, style = androidx.compose.ui.graphics.drawscope.Stroke(line))
        write("${mark.number}", at, Pencil.red, (cell / 6).toSp())
    }
    if (!call.lostTrack && call.at in page) {
        val at = center(call.at)
        drawCircle(call.role.color, cell / 6, at)
        val dir = call.onPage(call.facing)
        drawCircle(Pencil.lead, cell / 6, at, style = androidx.compose.ui.graphics.drawscope.Stroke(line / 2))
        drawRect(Pencil.lead, at + Offset(dir.dx * cell / 4f, dir.dy * cell / 4f) - Offset(line, line), Size(line * 2, line * 2))
    }
}

/** A chalk arrow from [at] towards [dir]. */
private fun DrawScope.arrow(at: Offset, dir: Dir, cell: Float, color: Color) {
    val w = maxOf(2.dp.toPx(), cell / 16)
    val tip = at + Offset(dir.dx * cell / 3, dir.dy * cell / 3)
    val tail = at - Offset(dir.dx * cell / 4, dir.dy * cell / 4)
    drawLine(color, tail, tip, w)
    val side = Offset(-dir.dy * cell / 8, dir.dx * cell / 8)
    val back = Offset(dir.dx * cell / 8, dir.dy * cell / 8)
    drawLine(color, tip, tip - back + side, w)
    drawLine(color, tip, tip - back - side, w)
}

/** Where [pos] is on the notebook's page: the start at (0, 0), and up the way the caller first faced. */
fun Call.onPage(pos: Pos): Pair<Int, Int> {
    var x = pos.x - origin.x
    var y = pos.y - origin.y
    repeat(up.ordinal) {
        val turned = y to -x
        x = turned.first
        y = turned.second
    }
    return x to y
}

/** Where [dir] points on the notebook's page. */
fun Call.onPage(dir: Dir): Dir = Dir.entries[(dir.ordinal - up.ordinal + 4) % 4]
