package dev.deeptelar.scarytruc

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
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
 * The maze seen from above: the player sees everything, dimly, and the floor around each lost
 * agent lit by its lantern. A left click is a treat and a right click a trick, by [onClick].
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

        drawRect(Ink.wall)
        for (pos in maze.cells) {
            val corner = layout.origin + Offset(pos.x * cell, pos.y * cell)
            drawRect(Ink.floor, corner, Size(cell, cell))
            // Flagstones: a darker seam on two sides of every cell.
            drawRect(Ink.dark.copy(alpha = 0.35f), corner, Size(cell, px / 2))
            drawRect(Ink.dark.copy(alpha = 0.35f), corner, Size(px / 2, cell))
        }
        // The lanterns of the crew.
        for (agent in night.lost) {
            val at = layout.center(agent.at)
            drawCircle(Brush.radialGradient(listOf(Ink.candle.copy(alpha = 0.28f), Color.Transparent), at, cell * 2.2f), cell * 2.2f, at)
        }
        drawWalls(maze, layout)

        // The gate glows, a little more and less with the frames.
        val glow = 0.35f + 0.1f * ((frame % 20) / 10f).let { if (it > 1) 2 - it else it }
        drawCircle(Brush.radialGradient(listOf(Ink.candle.copy(alpha = glow), Color.Transparent), layout.center(maze.gate), cell * 1.3f), cell * 1.3f, layout.center(maze.gate))
        draw(Sprites.gate, layout.center(maze.gate), px)

        for (doom in maze.dooms) {
            drawCircle(Brush.radialGradient(listOf(Ink.red.copy(alpha = 0.45f), Color.Transparent), layout.center(doom.at), cell * 0.6f), cell * 0.6f, layout.center(doom.at))
            draw(Sprites.skull, layout.center(doom.at), px * 0.8f, alpha = 0.85f)
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

        val hollow = layout.center(night.hollow.at)
        drawCircle(Brush.radialGradient(listOf(Color(0xCC5A0000), Color.Transparent), hollow, cell * 0.8f), cell * 0.8f, hollow)
        draw(Sprites.hollow, hollow, px * 0.95f)

        // Agents on the same cell stand side by side.
        night.lost.groupBy { it.at }.forEach { (pos, here) ->
            here.forEachIndexed { i, agent ->
                val shift = if (here.size == 1) Offset.Zero else Offset((i - (here.size - 1) / 2f) * cell * 0.22f, (i % 2) * cell * 0.08f)
                val sprite = if (night.marks.any { it.kind == MarkKind.TRICK && near(it.at, pos) }) Sprites.scared else Sprites.agent
                draw(sprite.tinted(agent.role.color), layout.center(pos) + shift, if (here.size == 1) px else px * 0.6f)
            }
        }
    }
}

private fun near(a: Pos, b: Pos) = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y) <= 1

private fun DrawScope.drawWalls(maze: Maze, layout: Layout) {
    val cell = layout.cell
    val thick = cell / 5
    fun wall(at: Offset, size: Size) {
        drawRect(Ink.wall, at, size)
        drawRect(Ink.moon, at, Size(size.width, thick / 3))
    }
    for (pos in maze.cells) {
        val corner = layout.origin + Offset(pos.x * cell, pos.y * cell)
        // Each cell draws its walls to the north and the west; the edge of the maze closes the rest.
        if (!maze.canGo(pos, Dir.NORTH)) wall(corner + Offset(-thick / 2, -thick / 2), Size(cell + thick, thick))
        if (!maze.canGo(pos, Dir.WEST)) wall(corner + Offset(-thick / 2, -thick / 2), Size(thick, cell + thick))
        if (pos.y == maze.height - 1) wall(corner + Offset(-thick / 2, cell - thick / 2), Size(cell + thick, thick))
        if (pos.x == maze.width - 1) wall(corner + Offset(cell - thick / 2, -thick / 2), Size(thick, cell + thick))
    }
    // A pillar where walls meet, so corners read as stone and not as gaps.
    for (y in 0..maze.height) {
        for (x in 0..maze.width) {
            if (maze.edgeClosed(x - 1, y, true) || maze.edgeClosed(x, y, true) || maze.edgeClosed(x, y - 1, false) || maze.edgeClosed(x, y, false)) {
                wall(layout.origin + Offset(x * cell - thick / 2, y * cell - thick / 2), Size(thick, thick))
            }
        }
    }
}

/**
 * Whether the edge of the grid at cell ([x], [y]) is a wall: its top edge when [top], else its left
 * edge. The edges around the maze are walls; those outside it are not.
 */
private fun Maze.edgeClosed(x: Int, y: Int, top: Boolean): Boolean {
    val here = Pos(x, y)
    val there = if (top) Pos(x, y - 1) else Pos(x - 1, y)
    if (top && x !in 0 until width || !top && y !in 0 until height) return false
    if (here !in this || there !in this) return here in this || there in this
    return !canGo(here, if (top) Dir.NORTH else Dir.WEST)
}
