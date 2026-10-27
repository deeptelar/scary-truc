package dev.deeptelar.scarytruc

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min

/** Where the maze sits in a canvas of [size]: the side of a cell and the corner of the maze. */
private class Layout(maze: Maze, size: Size) {
    val cell: Float = min(size.width / maze.width, size.height / maze.height)
    val origin: Offset = Offset((size.width - cell * maze.width) / 2, (size.height - cell * maze.height) / 2)

    fun center(pos: Pos): Offset = origin + Offset((pos.x + 0.5f) * cell, (pos.y + 0.5f) * cell)

    fun at(point: Offset, maze: Maze): Pos? =
        Pos(((point.x - origin.x) / cell).toInt(), ((point.y - origin.y) / cell).toInt()).takeIf {
            point.x >= origin.x && point.y >= origin.y && it in maze
        }
}

/**
 * The maze seen from above, as far as the crew's lanterns have lit it: the cells lit tonight stay
 * on the map, dimly, and those lit now are bright. The Hollow shows only in the light; elsewhere
 * the screen reddens as it comes closer. A left click is a treat and a right click a trick, by
 * [onClick].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Board(
    maze: Maze,
    night: Night,
    modifier: Modifier = Modifier,
    frame: Int = 0,
    onClick: (Pos, secondary: Boolean) -> Unit = { _, _ -> },
) {
    val text = rememberTextMeasurer()
    Canvas(
        modifier.onPointerEvent(PointerEventType.Press) { event ->
            val layout = Layout(maze, Size(size.width.toFloat(), size.height.toFloat()))
            val pos = layout.at(event.changes.first().position, maze) ?: return@onPointerEvent
            onClick(pos, event.buttons.isSecondaryPressed)
        },
    ) {
        val layout = Layout(maze, size)
        val cell = layout.cell
        val px = cell / 12
        val lit = night.lit(maze)

        drawRect(Ink.dark)
        for (pos in night.seen) {
            val corner = layout.origin + Offset(pos.x * cell, pos.y * cell)
            drawRect(Ink.floor, corner, Size(cell, cell))
            drawRect(Ink.dark.copy(alpha = 0.35f), corner, Size(cell, px / 2))
            drawRect(Ink.dark.copy(alpha = 0.35f), corner, Size(px / 2, cell))
            // What the lanterns do not light now is a memory: darker.
            if (pos !in lit) drawRect(Ink.dark.copy(alpha = 0.55f), corner, Size(cell, cell))
        }
        for (agent in night.lost) {
            val at = layout.center(agent.at)
            drawCircle(Brush.radialGradient(listOf(Ink.candle.copy(alpha = 0.22f), Color.Transparent), at, cell * 2f), cell * 2f, at)
        }
        drawWalls(maze, layout, night.seen)

        // The gate is always in sight: the crew has to know there is a way out.
        val glow = 0.35f + 0.1f * ((frame % 20) / 10f).let { if (it > 1) 2 - it else it }
        drawCircle(Brush.radialGradient(listOf(Ink.candle.copy(alpha = glow), Color.Transparent), layout.center(maze.gate), cell * 1.3f), cell * 1.3f, layout.center(maze.gate))
        draw(Sprites.gate, layout.center(maze.gate), px)

        for (doom in maze.dooms.filter { it.at in night.seen }) {
            drawCircle(Brush.radialGradient(listOf(Ink.red.copy(alpha = 0.45f), Color.Transparent), layout.center(doom.at), cell * 0.6f), cell * 0.6f, layout.center(doom.at))
            draw(Sprites.skull, layout.center(doom.at), px * 0.8f, alpha = 0.9f)
        }
        for (candy in night.lostCandy.filter { it in night.seen }) {
            val bob = if ((frame / 5) % 2 == 0) 0f else -px / 2
            draw(Sprites.candy, layout.center(candy) + Offset(0f, bob), px * 0.7f)
        }
        for (mark in night.marks) {
            val sprite = if (mark.kind == MarkKind.TREAT) Sprites.treat else Sprites.trick
            // A trick fades as it ages.
            val alpha = if (mark.kind == MarkKind.TRICK) 1f - mark.age.toFloat() / (Night.TRICK_TURNS + 1) else 1f
            draw(sprite, layout.center(mark.at), px * 0.9f, alpha)
        }
        for (dead in night.crew.filter { it.fate == Fate.DEAD }) {
            val bob = if ((frame / 6 + dead.role.ordinal) % 2 == 0) 0f else -px
            draw(Sprites.ghost, layout.center(dead.at) + Offset(0f, bob - cell * 0.1f), px * 0.8f, alpha = 0.55f)
        }

        night.hollow?.let { hollow ->
            if (hollow.at in lit) {
                val at = layout.center(hollow.at)
                drawCircle(Brush.radialGradient(listOf(Color(0xCC5A0000), Color.Transparent), at, cell * 0.8f), cell * 0.8f, at)
                draw(Sprites.hollow, at, px * 0.95f)
            }
        }

        // Where each one means to go: an arrow, the player's moment to answer.
        val places = night.lost.groupBy { it.at }
        fun spot(agent: Agent): Offset {
            val here = places.getValue(agent.at)
            val i = here.indexOf(agent)
            return layout.center(agent.at) +
                if (here.size == 1) Offset.Zero else Offset((i - (here.size - 1) / 2f) * cell * 0.22f, (i % 2) * cell * 0.08f)
        }
        for (move in night.moves) {
            val agent = night.lost.firstOrNull { it.role == move.role } ?: continue
            val dir = move.go ?: continue
            arrow(spot(agent), dir, cell, agent.role.color)
        }
        for (agent in night.lost) {
            val scared = night.marks.any { it.kind == MarkKind.TRICK && near(it.at, agent.at) }
            val sprite = if (scared) Sprites.scared else Sprites.agent
            draw(sprite.tinted(agent.role.color), spot(agent), if (places.getValue(agent.at).size == 1) px else px * 0.6f)
        }
        // What they say, above them; when several share a cell, one above the other.
        for ((pos, here) in places) {
            here.forEachIndexed { i, agent ->
                val words = night.moves.firstOrNull { it.role == agent.role }?.says ?: agent.words
                if (words.isNotBlank()) bubble(text, words, layout.center(pos) - Offset(0f, cell * 0.55f + i * cell * 0.42f), cell, agent.role.color)
            }
        }

        // The Hollow, felt before it is seen: red at the edges as it nears the nearest of the crew.
        night.hollow?.let { hollow ->
            val near = night.lost.mapNotNull { maze.distances(hollow.at)[it.at] }.minOrNull() ?: return@let
            if (near <= 6) {
                val beat = if ((frame / 4) % 2 == 0) 1f else 0.8f
                val strength = (7 - near) / 7f * beat
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Ink.red.copy(alpha = 0.55f * strength)),
                        center,
                        size.maxDimension * 0.75f,
                    ),
                )
            }
        }
    }
}

private fun near(a: Pos, b: Pos) = abs(a.x - b.x) + abs(a.y - b.y) <= 1

/** An arrow from [from] a little over half a cell towards [dir]. */
private fun DrawScope.arrow(from: Offset, dir: Dir, cell: Float, color: Color) {
    val unit = Offset(dir.dx.toFloat(), dir.dy.toFloat())
    val side = Offset(-unit.y, unit.x)
    val start = from + unit * (cell * 0.3f)
    val tip = from + unit * (cell * 0.78f)
    drawLine(Ink.outline, start, tip, strokeWidth = cell / 9)
    drawLine(color, start, tip, strokeWidth = cell / 16)
    val head =
        Path().apply {
            moveTo(tip.x + unit.x * cell * 0.1f, tip.y + unit.y * cell * 0.1f)
            lineTo(tip.x + side.x * cell * 0.12f, tip.y + side.y * cell * 0.12f)
            lineTo(tip.x - side.x * cell * 0.12f, tip.y - side.y * cell * 0.12f)
            close()
        }
    drawPath(head, color)
}

/** A speech bubble with [words], its bottom at [bottom]. */
private fun DrawScope.bubble(text: TextMeasurer, words: String, bottom: Offset, cell: Float, color: Color) {
    val style = TextStyle(fontFamily = PixelFont, fontSize = (cell / 5.2f).coerceIn(10f, 15f).sp, color = Ink.outline, textAlign = TextAlign.Center)
    val laid = text.measure(words, style, constraints = Constraints(maxWidth = (cell * 2.4f).toInt()), maxLines = 2)
    val pad = cell / 14
    val w = laid.size.width + pad * 2
    val h = laid.size.height + pad * 2
    val left = (bottom.x - w / 2).coerceIn(0f, size.width - w)
    val top = (bottom.y - h).coerceAtLeast(0f)
    drawRoundRect(Ink.outline, Offset(left - 2, top - 2), Size(w + 4, h + 4), CornerRadius(pad))
    drawRoundRect(Color(0xFFF3EBD8), Offset(left, top), Size(w, h), CornerRadius(pad))
    drawRect(color, Offset(left, top), Size(pad / 1.5f, h))
    drawText(laid, topLeft = Offset(left + pad, top + pad))
}

private fun DrawScope.drawWalls(maze: Maze, layout: Layout, seen: Set<Pos>) {
    val cell = layout.cell
    val thick = cell / 5
    fun wall(at: Offset, size: Size) {
        drawRect(Ink.wall, at, size)
        drawRect(Ink.moon, at, Size(size.width, thick / 3))
    }
    for (pos in maze.cells.filter { it in seen }) {
        val corner = layout.origin + Offset(pos.x * cell, pos.y * cell)
        if (!maze.canGo(pos, Dir.NORTH)) wall(corner + Offset(-thick / 2, -thick / 2), Size(cell + thick, thick))
        if (!maze.canGo(pos, Dir.WEST)) wall(corner + Offset(-thick / 2, -thick / 2), Size(thick, cell + thick))
        if (!maze.canGo(pos, Dir.SOUTH)) wall(corner + Offset(-thick / 2, cell - thick / 2), Size(cell + thick, thick))
        if (!maze.canGo(pos, Dir.EAST)) wall(corner + Offset(cell - thick / 2, -thick / 2), Size(thick, cell + thick))
    }
}
