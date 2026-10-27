package dev.deeptelar.scarytruc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * A drawing in pixels: each row is a line of characters, and [colors] says which color each
 * character is. A `.` is nothing.
 */
class Sprite(val rows: List<String>, val colors: Map<Char, Color>) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size

    fun tinted(main: Color): Sprite = Sprite(rows, colors + ('#' to main))
}

/** Draws [sprite] centered at [center], with pixels of [pixel], at [alpha]. */
fun DrawScope.draw(sprite: Sprite, center: Offset, pixel: Float, alpha: Float = 1f) {
    val left = center.x - sprite.width * pixel / 2
    val top = center.y - sprite.height * pixel / 2
    sprite.rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            val color = sprite.colors[c] ?: return@forEachIndexed
            drawRect(color, Offset(left + x * pixel, top + y * pixel), Size(pixel + 0.5f, pixel + 0.5f), alpha = alpha)
        }
    }
}

object Sprites {
    private val o = Ink.outline

    /** An agent of the crew: `#` takes the color of its role. */
    val agent =
        Sprite(
            listOf(
                "..oooo..",
                ".o####o.",
                "o#e##e#o",
                "o######o",
                ".o####o.",
                "o######o",
                "o.#..#.o",
                "..o..o..",
            ),
            mapOf('o' to o, '#' to Ink.blue, 'e' to Color.White),
        )

    /** An agent with its eyes wide open: a turn where something scares it. */
    val scared =
        Sprite(
            listOf(
                "..oooo..",
                ".o####o.",
                "oeo##eoo",
                "o######o",
                ".o#oo#o.",
                "o######o",
                "o.#..#.o",
                "..o..o..",
            ),
            mapOf('o' to o, '#' to Ink.blue, 'e' to Color.White),
        )

    val ghost =
        Sprite(
            listOf(
                "..####..",
                ".######.",
                "##k##k##",
                "########",
                "###kk###",
                "########",
                "########",
                "#.##.##.",
            ),
            mapOf('#' to Ink.bone, 'k' to Ink.outline),
        )

    val hollow =
        Sprite(
            listOf(
                "...kkkk...",
                "..kkkkkk..",
                ".kkkkkkkk.",
                ".kkrkkrkk.",
                "kkkkkkkkkk",
                "kkk.kk.kkk",
                "kkkkkkkkkk",
                "kkkkkkkkkk",
                "k.kk.k.k.k",
                "k..k...k.k",
            ),
            mapOf('k' to Color(0xFF000000), 'r' to Color(0xFFFF2A2A), '.' to Color.Transparent),
        )

    val treat =
        Sprite(
            listOf(
                "y.......y",
                "yy.ooo.yy",
                "yyo###oyy",
                "yyo#w#oyy",
                "yyo###oyy",
                "yy.ooo.yy",
                "y.......y",
            ),
            mapOf('y' to Ink.candle, 'o' to o, '#' to Color(0xFFE0457B), 'w' to Color.White),
        )

    /** Candy corn: what the bag holds, and what lies lost in dead ends. */
    val candy =
        Sprite(
            listOf(
                "...ww...",
                "..owwo..",
                "..oooo..",
                ".oooooo.",
                ".yyyyyy.",
                "oyyyyyyo",
                "oyyyyyyo",
                ".oooooo.",
            ),
            mapOf('w' to Color(0xFFFFF8E7), 'o' to Ink.orange, 'y' to Color(0xFFFFD23F)),
        )

    val trick =
        Sprite(
            listOf(
                "....g....",
                "...gg....",
                ".ooooooo.",
                "ooyooooyo",
                "oyyyoyyyo",
                "ooooooooo",
                "oyoyoyoyo",
                "ooyyyyyoo",
                ".ooooooo.",
            ),
            mapOf('o' to Ink.orange, 'y' to Color(0xFFFFE066), 'g' to Ink.green),
        )

    val skull =
        Sprite(
            listOf(
                ".wwwww.",
                "wwwwwww",
                "wkkwkkw",
                "wkkwkkw",
                "wwwkwww",
                ".wwwww.",
                ".w.w.w.",
            ),
            mapOf('w' to Color(0xFFD8C8B8), 'k' to Ink.red),
        )

    val gate =
        Sprite(
            listOf(
                "..oooo..",
                ".oyyyyo.",
                "oyyyyyyo",
                "oyyyyyyo",
                "oyyyyyyo",
                "oyyyyyyo",
                "oyyyyyyo",
                "oooooooo",
            ),
            mapOf('o' to Color(0xFF6B4226), 'y' to Color(0xFFFFE9A8)),
        )

    val marigold =
        Sprite(
            listOf(
                "..m.m..",
                ".mmmmm.",
                "mmmcmmm",
                ".mmmmm.",
                "..mgm..",
                "...g...",
                "..gg...",
            ),
            mapOf('m' to Ink.marigold, 'c' to Color(0xFFB8541B), 'g' to Ink.green),
        )

    val candle =
        Sprite(
            listOf(
                "..y..",
                ".yyy.",
                "..o..",
                ".www.",
                ".www.",
                ".www.",
                ".www.",
                "wwwww",
            ),
            mapOf('y' to Ink.candle, 'o' to Ink.orange, 'w' to Color(0xFFF3EBD8)),
        )
}

/** The color of each of the crew. */
val Role.color: Color
    get() =
        when (this) {
            Role.PLANNER -> Ink.blue
            Role.SCOUT -> Ink.green
            Role.CODER -> Ink.orange
            Role.CRITIC -> Ink.purple
        }
