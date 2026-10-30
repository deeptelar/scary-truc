package dev.deeptelar.scarytruc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.deeptelar.scarytruc.resources.Res
import dev.deeptelar.scarytruc.resources.PixelifySans
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A small kit of pixel-art controls for the window: everything is drawn in square pixels of PX,
// with hard outlines and hard shadows, like the crew.

/** One pixel of the controls' drawings. */
internal val PX: Dp = 3.dp

/** Pixelify Sans, a pixel font under the SIL Open Font License (licenses/PixelifySans-OFL.txt). Only its regular weight sits on its grid. */
internal val PixelFont: FontFamily
    @Composable get() =
        FontFamily(Font(Res.font.PixelifySans, FontWeight.Normal, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(400))))

// A pixel of Pixelify Sans is 0.091 of its size: at these sizes its pixels fall on whole screen pixels.
internal val SMALL: TextUnit = 16.5.sp // a pixel and a half: slightly soft, for notes
internal val BODY: TextUnit = 22.sp
internal val BIG: TextUnit = 33.sp

/** No ligatures: in a pixel font, "fi" drawn as one letter reads as an A. */
private const val PLAIN = "liga 0, clig 0, calt 0"

/** The colors of the night: dark violet, candlelight and bone, and a color for each of the crew. */
internal object Ink {
    val text = Color(0xFFEDE3D1)
    val faint = Color(0xFF9A8CA8)
    val dark = Color(0xFF0E0A14)
    val paper = Color(0xFF2A2036)
    val paperDark = Color(0xFF3B2E4A)
    val field = Color(0xFF1A1322)
    val outline = Color(0xFF050308)
    val floor = Color(0xFF30243F)
    val lit = Color(0xFF4A3A55)
    val wall = Color(0xFF0B0812)
    val moon = Color(0xFF6E5A8A)
    val orange = Color(0xFFF28C28)
    val candle = Color(0xFFF2C14E)
    val marigold = Color(0xFFF5A623)
    val red = Color(0xFFC0392B)
    val bone = Color(0xFFE8E4F0)
    val green = Color(0xFF6FBF4A)
    val blue = Color(0xFF4A90D9)
    val purple = Color(0xFF9B59B6)
    val off = Color(0xFF5E5468)
    val rust = Color(0xFF3A1C17)
    val rustEdge = Color(0xFF8A3A22)
    val blood = Color(0xFFFF5A3C)
}

/** Text in the pixel font. */
@Composable
internal fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = BODY,
    color: Color = Ink.text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
) = BasicText(
    text,
    modifier,
    style = TextStyle(fontFamily = PixelFont, fontSize = size, color = color, lineHeight = size * 1.25f, fontFeatureSettings = PLAIN, textAlign = align),
    overflow = TextOverflow.Ellipsis,
    maxLines = maxLines,
)

/**
 * A pixel box: [fill] inside an outline with its corners cut off, a lighter line along the top
 * and a darker one along the bottom, and a hard shadow under it unless [raised] is false.
 */
internal fun Modifier.pixelBox(
    fill: Color,
    outline: Color = Ink.outline,
    raised: Boolean = true,
): Modifier =
    drawBehind {
        val p = PX.toPx()
        val w = size.width
        val h = size.height
        if (raised) notched(Color(0x55000000), Offset(p, p), w, h, p)
        notched(outline, Offset.Zero, w, h, p)
        drawRect(fill, Offset(p, p), Size(w - 2 * p, h - 2 * p))
        drawRect(lighter(fill), Offset(p, p), Size(w - 2 * p, p))
        drawRect(darker(fill), Offset(p, h - 2 * p), Size(w - 2 * p, p))
    }

/** A rectangle with one pixel cut from each corner. */
private fun DrawScope.notched(
    color: Color,
    at: Offset,
    w: Float,
    h: Float,
    p: Float,
) {
    drawRect(color, at + Offset(p, 0f), Size(w - 2 * p, h))
    drawRect(color, at + Offset(0f, p), Size(w, h - 2 * p))
}

private fun lighter(color: Color) = Color(color.red + (1 - color.red) * 0.25f, color.green + (1 - color.green) * 0.25f, color.blue + (1 - color.blue) * 0.25f)

private fun darker(color: Color) = Color(color.red * 0.82f, color.green * 0.82f, color.blue * 0.82f)

/** A chunky button in [color], that goes down when pressed. */
@Composable
internal fun PixelButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    small: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val face = if (enabled) color else Ink.off
    val light = (face.red * 0.3f + face.green * 0.59f + face.blue * 0.11f) > 0.62f
    Box(
        modifier
            .offset(y = if (pressed) PX else 0.dp)
            .pixelBox(face, raised = !pressed)
            .clickable(press, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 10.dp, vertical = if (small) 7.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text, size = if (small) SMALL else BODY, color = if (light || !enabled) Ink.outline else Color.White, maxLines = 1)
    }
}

/** Text to click, for what is used less than a button: underlined, in the color of notes. */
@Composable
internal fun PixelLink(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) Ink.faint else Ink.off
    PixelText(
        text,
        Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .drawBehind { drawRect(color, Offset(0f, size.height - PX.toPx() / 1.5f), Size(size.width, PX.toPx() / 1.5f)) }
            .padding(vertical = 4.dp),
        size = SMALL,
        color = color,
    )
}

/** The three bars of a menu, in pixels: `#` is ink. */
internal val MENU_ICON = listOf("#######", ".......", "#######", ".......", "#######")

/** A pencil, to open an item of the pile and edit it. */
internal val OPEN_ICON = listOf(".....##", "....###", "...###.", "..###..", ".###...", "##.....", "#......")

/** A point up, to fold the window up to the sloth. */
internal val FOLD_ICON = listOf(".......", "...#...", "..###..", ".##.##.", "##...##")

/** A point down, to open the folded window again. */
internal val UNFOLD_ICON = listOf("##...##", ".##.##.", "..###..", "...#...", ".......")

/** A microphone, to speak to the caller instead of typing. */
internal val MIC_ICON = listOf("..###..", "..###..", "..###..", "#.###.#", "#.....#", ".#####.", "...#...", ".#####.")

/** A cross, to hide the window. */
internal val CLOSE_ICON = listOf("#...#", ".#.#.", "..#..", ".#.#.", "#...#")

/** A small square button with an [icon] drawn in pixels, which screen readers and tests know as [description]. */
@Composable
internal fun PixelIconButton(
    icon: List<String>,
    description: String,
    onClick: () -> Unit,
    color: Color = Ink.paperDark,
) {
    Box(
        Modifier
            .pixelBox(color)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = description }
            .padding(9.dp)
            .size(PX * icon.first().length, PX * icon.size)
            .drawBehind {
                val p = PX.toPx()
                icon.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c == '#') drawRect(Ink.text, Offset(x * p, y * p), Size(p, p)) } }
            },
    )
}

/** A list of [items] on parchment, to pick one: the [selected] one is marked. */
@Composable
internal fun PixelMenu(
    items: List<String>,
    selected: Int?,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(modifier.width(IntrinsicSize.Max).pixelBox(Ink.paper).padding(PX * 2)) {
        items.forEachIndexed { index, item ->
            PixelText(
                item,
                Modifier
                    .fillMaxWidth()
                    .background(if (index == selected) Ink.paperDark else Color.Transparent)
                    .clickable { onSelect(index) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                maxLines = 1,
            )
        }
    }
}

/** A text field in a pixel frame, with [placeholder] while it is empty. */
@Composable
internal fun PixelField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 4,
    fill: Color = Ink.field,
    ink: Color = Ink.text,
    hint: Color = Ink.faint,
    size: TextUnit = BODY,
) {
    // The cursor starts at the end, to add to what is there.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        field,
        {
            field = it
            if (it.text != value) onChange(it.text)
        },
        modifier.pixelBox(fill, outline = ink, raised = false),
        textStyle = TextStyle(fontFamily = PixelFont, fontSize = size, color = ink, lineHeight = size * 1.25f, fontFeatureSettings = PLAIN),
        cursorBrush = SolidColor(ink),
        maxLines = maxLines,
        decorationBox = { field ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (value.isEmpty()) PixelText(placeholder, size = size, color = hint)
                field()
            }
        },
    )
}

/** A square to tick, with its label. */
@Composable
internal fun PixelCheck(
    checked: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(Modifier.clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).pixelBox(if (checked) Ink.green else Ink.field, raised = false), contentAlignment = Alignment.Center) {
            if (checked) PixelText("✓", size = SMALL, color = Color.White)
        }
        PixelText(label, maxLines = 1)
    }
}

/** A bar of blocks with one bright block running across it, while it waits. */
@Composable
internal fun PixelProgress(tick: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(14) { block ->
            val distance = (tick % 26).let { if (it < 14) it else 26 - it } - block
            val color = if (distance == 0) Ink.green else if (distance in -1..1) Ink.green.copy(alpha = 0.55f) else Ink.paperDark
            Box(Modifier.size(16.dp, 12.dp).drawBehind { drawRect(color) })
        }
    }
}
