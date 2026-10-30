package dev.deeptelar.scarytruc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The whole window: the phone on the left and the notebook on the right, or, on a narrow screen
 * like a phone's, the phone alone, with the notebook a tap away.
 */
@Composable
fun Game(model: CallModel) {
    var now by remember { mutableLongStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            now = now()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.dark)) {
        if (maxWidth < 760.dp) {
            Phone(model, now, narrow = true, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Phone(model, now, narrow = false, Modifier.width(420.dp).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val other = model.call.afterMidnight && !model.ringing && !model.call.over
                PixelText(if (other) "OTHERWORLD" else "SCARY TRUC", size = BIG, color = if (other) Ink.red else Ink.orange)
                PixelText("${model.plan.title}, after ${model.plan.film}", color = Ink.orange)
                PixelText(model.plan.hint, size = SMALL, color = Ink.faint, maxLines = 3)
                // The notebook is a second screen of the same kind, beside the phone.
                val call = model.call
                val lcd = Lcd.of(call, model.ringing, if (model.ringing || call.over) 0f else danger(call, model.maze), now)
                Box(Modifier.weight(1f).fillMaxWidth().pixelBox(Lcd.BODY, outline = Lcd.BODY_EDGE).padding(14.dp)) {
                    Notebook(model, lcd, Modifier.fillMaxSize().pixelBox(lcd.paper, outline = Ink.outline, raised = false).padding(12.dp))
                }
                PixelText(mind(model), size = SMALL, color = if (model.gemma && model.trouble == null) Ink.faint else Ink.candle, maxLines = 3)
                model.failure?.let { PixelText(it, size = SMALL, color = Ink.red, maxLines = 2) }
            }
        }
    }
}

/** Whose mind the caller has, and what went wrong with it. */
private fun mind(model: CallModel): String =
    when {
        !model.gemma -> "No model: the caller knows only a few words (left, right, ahead, back, stay, hide, mark, take, use). Menu > Model to add one."
        model.trouble != null -> "${model.minds.name} did not answer, so they heard only your keywords: ${model.trouble}"
        else -> "The caller and the Hollow are played by ${model.minds.name}."
    }

/**
 * The two shades of a monochrome phone screen of 2003, green-lit: [paper] where the pixels are off
 * and [ink] where they are on. After midnight the screen is inverted, and when the Hollow is close
 * the backlight flickers.
 */
private class Lcd(val paper: Color, val ink: Color) {
    /** Pixels half on: hints and what is less important. */
    val faint: Color get() = lerp(paper, ink, 0.55f)

    companion object {
        val LIGHT = Color(0xFFC7F0D8)
        val DARK = Color(0xFF43523D)

        /** The backlight, out for a moment. */
        val UNLIT = Color(0xFF8FA996)

        /** The plastic of the phone. */
        val BODY = Color(0xFF1E2731)
        val BODY_EDGE = Color(0xFF3A4A5A)

        fun of(call: Call, ringing: Boolean, danger: Float, now: Long): Lcd {
            val inverted = call.afterMidnight && !ringing && !call.over
            val flicker = danger >= 0.5f && Random(now / 110).nextFloat() < danger * 0.35f
            val paper = if (flicker) UNLIT else if (inverted) DARK else LIGHT
            return Lcd(paper, if (inverted) LIGHT else DARK)
        }
    }
}

/** What the phone shows when no call is on. */
private enum class Screen { CALLS, MENU, HELP, MODEL }

/**
 * The phone: a monochrome screen in a plastic body, with softkeys under the screen. On a narrow
 * screen it fills it.
 */
@Composable
private fun Phone(model: CallModel, now: Long, narrow: Boolean, modifier: Modifier) {
    val call = model.call
    val danger = if (model.ringing || call.over) 0f else danger(call, model.maze)
    val lcd = Lcd.of(call, model.ringing, danger, now)
    var notebook by remember { mutableStateOf(false) }
    // Until a first call ends, the phone opens on how to save them.
    var screen by remember { mutableStateOf(if (model.best.isEmpty()) Screen.HELP else Screen.CALLS) }
    Column(
        modifier.pixelBox(Lcd.BODY, outline = Lcd.BODY_EDGE).padding(horizontal = if (narrow) 6.dp else 22.dp, vertical = if (narrow) 6.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!narrow) Grille()
        Column(
            Modifier.weight(1f).fillMaxWidth().pixelBox(lcd.paper, outline = Ink.outline, raised = false).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBar(call, danger, lcd)
            when {
                model.locked -> Locked(lcd, model::unlock)
                model.ringing && screen == Screen.MENU -> Menu(lcd, { screen = it })
                model.ringing && screen == Screen.HELP -> Help(lcd, now, { screen = Screen.CALLS }, { screen = Screen.CALLS; model.answer() })
                model.ringing && screen == Screen.MODEL -> Options(model, lcd) { screen = Screen.MENU }
                model.ringing -> Calls(model, now, lcd, Modifier.weight(1f)) { screen = Screen.MENU }
                else -> {
                    CallerBar(model, now, lcd)
                    val speaking = now - model.spokeAt < model.speaking()
                    when {
                        narrow && notebook -> Notebook(model, lcd, Modifier.weight(1f).fillMaxWidth())
                        // On a phone the map and the call share the screen: no switching to follow both.
                        narrow -> {
                            Notebook(model, lcd, Modifier.weight(0.4f).fillMaxWidth(), compact = true)
                            Box(Modifier.fillMaxWidth().height(2.dp).background(lcd.ink))
                            Transcript(model, now, danger, lcd, Modifier.weight(0.6f).fillMaxWidth())
                        }
                        else -> Transcript(model, now, danger, lcd, Modifier.weight(1f).fillMaxWidth())
                    }
                    if (call.over && !model.thinking && !speaking) {
                        Ending(model, lcd)
                    } else {
                        Talk(model, now, narrow, lcd, notebook) { notebook = !notebook }
                    }
                }
            }
        }
        if (!narrow) PixelText("TRUC", size = SMALL, color = Lcd.BODY_EDGE)
    }
}

/** The slits of the earpiece. */
@Composable
private fun Grille() {
    Box(
        Modifier.size(90.dp, 10.dp).drawBehind {
            repeat(5) { i -> drawRect(Ink.outline, Offset(i * size.width / 5 + 4f, 0f), Size(size.width / 5 - 8f, size.height)) }
        },
    )
}

/**
 * The top of the screen: the bars of the signal, which fail when the Hollow is near, the time, and
 * the bars of the battery.
 */
@Composable
private fun StatusBar(call: Call, danger: Float, lcd: Lcd) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val signal = (5 - (danger * 5).toInt()).coerceIn(1, 5)
        Bars(signal, lcd, antenna = true)
        Spacer(Modifier.weight(1f))
        PixelText(clock(call), size = SMALL, color = lcd.ink)
        Spacer(Modifier.weight(1f))
        Bars(((call.battery.coerceIn(0, 100) + 19) / 20), lcd, antenna = false)
    }
}

/** Five bars that grow to the right, with an antenna or a battery in front. */
@Composable
private fun Bars(on: Int, lcd: Lcd, antenna: Boolean) {
    Box(
        Modifier.size(44.dp, 16.dp).drawBehind {
            val p = 2.dp.toPx()
            if (antenna) {
                drawRect(lcd.ink, Offset(3 * p, 0f), Size(p, size.height))
                drawRect(lcd.ink, Offset(0f, 0f), Size(7 * p, p))
                drawRect(lcd.ink, Offset(p, p), Size(5 * p, p))
            } else {
                drawRect(lcd.ink, Offset(0f, 2 * p), Size(5 * p, size.height - 2 * p))
                drawRect(lcd.ink, Offset(1.5f * p, p), Size(2 * p, p))
            }
            val left = 9 * p
            val w = (size.width - left) / 5
            repeat(5) { i ->
                val h = size.height * (i + 1) / 5
                if (i < on) drawRect(lcd.ink, Offset(left + i * w, size.height - h), Size(w - p, h))
            }
        },
    )
}

/** The time on the phone: midnight comes on the call's midnight turn, a minute a turn. */
private fun clock(call: Call): String {
    val minutes = 24 * 60 - call.plan.midnight + call.turn
    return "${((minutes / 60) % 24).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
}

/** A title line, centred, as a menu of the time has. */
@Composable
private fun Header(text: String, lcd: Lcd) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PixelText(text, color = lcd.ink, maxLines = 1)
    }
}

/** Labels over the keys under the screen; each is tapped. A blank label is no key. */
@Composable
private fun Softkeys(lcd: Lcd, left: Pair<String, () -> Unit>?, middle: Pair<String, () -> Unit>?, right: Pair<String, () -> Unit>?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((key, align) in listOf(left to Alignment.CenterStart, middle to Alignment.Center, right to Alignment.CenterEnd)) {
            Box(Modifier.weight(1f), contentAlignment = align) {
                if (key != null) {
                    PixelText(key.first, Modifier.clickable(onClick = key.second).padding(vertical = 6.dp, horizontal = 4.dp), color = lcd.ink, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The phone between calls: a menu of the calls, the chosen one in reverse, and the one chosen
 * ringing under it.
 */
@Composable
private fun Calls(model: CallModel, now: Long, lcd: Lcd, modifier: Modifier, options: () -> Unit) {
    Header("Calls", lcd)
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (plan in Plan.entries) {
            val chosen = plan == model.plan
            val best = model.best[plan]
            Row(
                Modifier.fillMaxWidth().background(if (chosen) lcd.ink else lcd.paper).clickable(enabled = !model.thinking) { model.play(plan) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelText(plan.title, Modifier.weight(1f), color = if (chosen) lcd.paper else lcd.ink, maxLines = 1)
                PixelText(
                    when (best) {
                        null -> ""
                        0 -> "out"
                        else -> "$best"
                    },
                    size = SMALL,
                    color = if (chosen) lcd.paper else lcd.ink,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        PixelText("after ${model.plan.film}", size = SMALL, color = lcd.ink)
        PixelText(model.plan.hint, size = SMALL, color = lcd.faint)
        if (model.best.isEmpty()) PixelText("First call? Menu > How to save them.", size = SMALL, color = lcd.ink)
        Spacer(Modifier.height(8.dp))
        // The phone rings: its picture shakes.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val shake = if ((now / 120) % 2 == 0L && (now / 1000) % 3 != 2L) 2.dp else 0.dp
            Box(Modifier.size(40.dp).offset(x = shake).drawBehind { draw(Sprite(HANDSET, mapOf('#' to lcd.ink)), center, 3.dp.toPx()) })
            Column(Modifier.padding(start = 10.dp)) {
                PixelText("Unknown number", color = lcd.ink)
                PixelText(if ((now / 600) % 2 == 0L) "calling" else "calling.", size = SMALL, color = lcd.ink)
            }
        }
    }
    Softkeys(lcd, "Menu" to options, "Answer" to model::answer, null)
}

/** A padlock, as a phone of the time showed its keys locked. */
private val PADLOCK =
    listOf(
        "...#####...",
        "..#.....#..",
        "..#.....#..",
        "..#.....#..",
        "###########",
        "###########",
        "#####.#####",
        "#####.#####",
        "######.####",
        "###########",
        "###########",
    )

/**
 * The phone as the page opens: its keys locked. Unlocking it is the tap a browser waits for before
 * it plays a sound, so the phone rings the moment it is unlocked.
 */
@Composable
private fun ColumnScope.Locked(lcd: Lcd, unlock: () -> Unit) {
    Header("SCARY TRUC", lcd)
    Column(
        Modifier.weight(1f).fillMaxWidth().clickable(onClick = unlock),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(60.dp).drawBehind { draw(Sprite(PADLOCK, mapOf('#' to lcd.ink)), center, 5.dp.toPx()) })
        PixelText("Keys locked", color = lcd.ink)
        PixelText("Turn the sound up, and unlock. Someone is about to call.", Modifier.fillMaxWidth(), size = SMALL, color = lcd.faint, align = TextAlign.Center)
    }
    Softkeys(lcd, null, "Unlock" to unlock, null)
}

/** A heart in pixels: the fonts of the web draw ♥ in colour. */
private val HEART = listOf(".##.##.", "#######", "#######", ".#####.", "..###..", "...#...")

/** A handset with its waves, as a phone of 2003 draws a call. */
private val HANDSET =
    listOf(
        "......#..#",
        ".......#..",
        "###..#..#.",
        "#..#..#.#.",
        "#..#...#..",
        ".#.#......",
        ".#..#.....",
        "..#..##...",
        "...#...###",
        "....###..#",
        "........##",
    )

/** The options of the phone, as a menu of the time: one line each, the first in reverse. */
@Composable
private fun ColumnScope.Menu(lcd: Lcd, go: (Screen) -> Unit) {
    Header("Menu", lcd)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((i, entry) in listOf("How to save them" to Screen.HELP, "Model" to Screen.MODEL).withIndex()) {
            PixelText(
                entry.first,
                Modifier.fillMaxWidth().background(if (i == 0) lcd.ink else lcd.paper).clickable { go(entry.second) }.padding(horizontal = 6.dp, vertical = 3.dp),
                color = if (i == 0) lcd.paper else lcd.ink,
                maxLines = 1,
            )
        }
    }
    Softkeys(lcd, "Back" to { go(Screen.CALLS) }, null, null)
}

/**
 * A page of how to save them: a picture, one line in the words of whoever built the mazes, and
 * what you could type. In [picture], `#` is ink, `+` half ink and `-` a hole in the ink; `a` is on in the first frame, `b`
 * in the second, and `m` fades from ink to half ink, so that the picture moves.
 */
private class Lesson(val line: String, val example: String?, val picture: List<String>)

/** How to save them: the rules of the game, read as a threat, a page at a time. */
private val LESSONS =
    listOf(
        // They face you, eyes and all, and turn to their left: your right.
        Lesson(
            "They see. You don't. Say where to go from where THEY face: facing you, their left is your right.",
            "go left",
            listOf(
                "++++++++.....++++++++",
                "++++++++.....++++++++",
                "++++++++.....++++++++",
                "++++++++.....++++++++",
                ".........###.........",
                "........#####.....b..",
                "........#-#-#.bbbbbb.",
                "........#####.....b..",
                ".........###.........",
                "++++++++.....++++++++",
                "++++++++.....++++++++",
                "++++++++.....++++++++",
                "++++++++.....++++++++",
            ),
        ),
        // Their steps fade from the notebook; the scratch on the wall stays.
        Lesson(
            "Your notebook forgets. A mark scratched on my walls does not.",
            "scratch a mark",
            listOf(
                "................+++++",
                "................+++++",
                "................+++++",
                "................+++++",
                ".............mm.#+++#",
                "...........mm...+#+#+",
                "................++#++",
                ".........mm.....+#+#+",
                "......mm........#+++#",
                "................+++++",
                "....mm..........+++++",
                ".mm.............+++++",
                "................+++++",
            ),
        ),
        Lesson(
            "The ones before them left notes. They learned what kills. Too late.",
            "what does it say?",
            listOf(
                "....###########......",
                "....#.........##.....",
                "....#.+++.++++.##....",
                "....#...........#....",
                "....#.++++.++.+.#....",
                "....#...........#....",
                "....#.++.+++++..#....",
                "....#...........#....",
                "....#.+++++.+++.#....",
                "....#...........#....",
                "....#.+++.......#....",
                "....#############....",
                ".....................",
            ),
        ),
        // The caller's fear carries to the Hollow, which has no eyes.
        Lesson(
            "My pet is blind. It hears fear. Keep them calm, and when it's close, make them hide.",
            "hide. stay still. I'm here.",
            listOf(
                ".....................",
                "..###................",
                ".#####...............",
                ".#####...............",
                "..###................",
                ".#####...............",
                "#######..............",
                "#.###.#..............",
                "#.###.#....b...a..#..",
                "..###.....b...a..###.",
                "..#.#.....b...a..###.",
                "..#.#.....b...a.##.##",
                ".##.##.....b...a.....",
            ),
        ),
        Lesson(
            "I leave things in corners. A battery buys time. A flare scares it. A stone, thrown, lures it.",
            "grab it",
            listOf(
                ".....................",
                ".........bab.........",
                ".........a#a.........",
                "..........#..........",
                "...###....#..........",
                "..#####...#..........",
                "..#...#...#..........",
                "..#...#...#....###...",
                "..#####...#...#####..",
                "..#####...#...#####..",
                "..#####...#...#####..",
                "..#####...#....###...",
                ".....................",
            ),
        ),
        // The bar under what you type empties while they wait for you.
        Lesson(
            "Don't leave them in silence. Wait too long and they choose for themselves. They choose badly.",
            null,
            listOf(
                "...#############.....",
                "...#...........#.....",
                "...#....###....#.....",
                "...#...#...#...#.....",
                "...#......#....#.....",
                "...#.....#.....#.....",
                "...#...........#.....",
                "...#.....#.....#.....",
                "...#############.....",
                "....##...............",
                "....#................",
                ".....................",
                "##########aaaaaaaaaaa",
            ),
        ),
        // Cold air blows out of the way out.
        Lesson(
            "At midnight I move the walls, and your notebook lies. The way out is cold. My cold never lies.",
            "where is it colder?",
            listOf(
                "...............######",
                "...............#....#",
                "...............#....#",
                "..aaa.bbb..aaa.#....#",
                "...............#....#",
                ".bbb..aaa..bbb.#....#",
                "...............#....#",
                "..aaa.bbb..aaa.#....#",
                "...............#....#",
                ".bbb..aaa..bbb.#....#",
                "...............#....#",
                "...............#....#",
                "+++++++++++++++######",
            ),
        ),
    )

/** A lesson's picture in its [frame], 0 or 1. */
private fun Lesson.sprite(frame: Int, lcd: Lcd): Sprite {
    val colors = mutableMapOf('#' to lcd.ink, '+' to lcd.faint, '-' to lcd.paper, 'm' to if (frame == 0) lcd.ink else lcd.faint)
    colors[if (frame == 0) 'a' else 'b'] = lcd.ink
    return Sprite(picture, colors)
}

/** How to save them, a page at a time: tap the picture or Next. The last page answers the call. */
@Composable
private fun ColumnScope.Help(lcd: Lcd, now: Long, back: () -> Unit, answer: () -> Unit, start: Int = 0) {
    var page by remember { mutableStateOf(start) }
    val lesson = LESSONS[page]
    val last = page == LESSONS.lastIndex
    val next: () -> Unit = { if (last) answer() else page += 1 }
    Header("How to save them", lcd)
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clickable(onClick = next).drawBehind {
                val sprite = lesson.sprite(((now / 700) % 2).toInt(), lcd)
                // Whole screen pixels, so that the picture stays sharp.
                val pixel = minOf(size.width / (sprite.width + 2), size.height / (sprite.height + 1)).toInt().toFloat()
                draw(sprite, center, pixel)
            },
        )
        PixelText(lesson.line, Modifier.fillMaxWidth(), color = lcd.ink)
        lesson.example?.let { Said(it, Who.YOU, lcd) }
    }
    if (last) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            PixelText("- the one who built the maze", size = SMALL, color = lcd.faint)
        }
    }
    Softkeys(lcd, "Back" to { if (page == 0) back() else page -= 1 }, "${page + 1}/${LESSONS.size}" to {}, (if (last) "Answer" else "Next") to next)
}

/** A page of how to save them on a lit screen, for the screenshots. */
@Composable
internal fun GuidePage(page: Int, now: Long) {
    val lcd = Lcd(Lcd.LIGHT, Lcd.DARK)
    Column(Modifier.fillMaxSize().background(Lcd.BODY).padding(6.dp).pixelBox(lcd.paper, outline = Ink.outline, raised = false).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Help(lcd, now, {}, {}, start = page)
    }
}

/**
 * Where the player connects a model that speaks the OpenAI API: Gemma on OpenRouter with their own
 * key, their Ollama, or a friend's proxy, whose address and token take the place of OpenRouter's.
 */
@Composable
private fun ColumnScope.Options(model: CallModel, lcd: Lcd, done: () -> Unit) {
    val saved = model.server
    var url by remember { mutableStateOf(saved?.url ?: Server.OPENROUTER.url) }
    var name by remember { mutableStateOf(saved?.model ?: Server.OPENROUTER.model) }
    var key by remember { mutableStateOf(saved?.key.orEmpty()) }
    Header("Model", lcd)
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelText(mind(model), size = SMALL, color = lcd.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PixelText("OpenRouter", Modifier.clickable { url = Server.OPENROUTER.url; name = Server.OPENROUTER.model }.padding(4.dp), size = SMALL, color = lcd.ink)
            PixelText("Ollama", Modifier.clickable { url = Server.OLLAMA.url; name = Server.OLLAMA.model; key = "" }.padding(4.dp), size = SMALL, color = lcd.ink)
            PixelText("None", Modifier.clickable { model.connect(null); done() }.padding(4.dp), size = SMALL, color = lcd.ink)
        }
        for ((value, set, hint) in listOf(Triple(url, { it: String -> url = it }, "Server address"), Triple(name, { it: String -> name = it }, "Model"), Triple(key, { it: String -> key = it }, "Key or token"))) {
            PixelField(value, set, hint, Modifier.fillMaxWidth(), maxLines = 1, fill = lcd.paper, ink = lcd.ink, hint = lcd.faint, size = SMALL)
        }
        PixelText("The key stays on this device and goes from it straight to the server. A friend's proxy works too: its address, and your token as the key.", size = SMALL, color = lcd.faint)
    }
    Softkeys(lcd, "Back" to done, "Save" to { model.connect(Server(url.trim(), name.trim(), key.trim())); done() }, null)
}

/** Who is calling, how fast their heart beats, and whether they hide. */
@Composable
private fun CallerBar(model: CallModel, now: Long, lcd: Lcd) {
    val call = model.call
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val bpm = 62 + call.panic * 9 / 10
        val beat = !call.over && (now % (60_000 / bpm)) < 140
        // The heart beats with their fear: a pixel heart that flashes on each beat, and its trace,
        // which goes flat when they are taken.
        val flat = call.over && call.end != End.OUT
        Box(Modifier.size(16.dp).drawBehind { draw(Sprite(HEART, mapOf('#' to if (beat) lcd.ink else lcd.faint)), center, 2.dp.toPx()) })
        Box(Modifier.size(60.dp, 18.dp).drawBehind { trace(if (flat) null else bpm, now, lcd.ink) })
        PixelText(if (flat) "--" else "$bpm", size = SMALL, color = lcd.faint)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PixelText(call.name + if (call.hidden && !call.over) " (hiding)" else "", color = lcd.ink, maxLines = 1)
        }
        // What they carry, in reverse, as a phone of the time showed its icons: a tap uses it.
        for (item in call.carried) {
            Row(
                Modifier.background(lcd.ink).clickable(enabled = !model.thinking && !call.over) { model.useItem(item) }.padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ItemIcon(item, lcd.paper, lcd.ink)
                PixelText(item.word, size = SMALL, color = lcd.paper)
            }
        }
        // The red key, in the corner: twice, to be sure, and it is over for them.
        if (!call.over) HangUp(now, model::hangUp, lcd)
    }
}

/**
 * The trace of a heart at [bpm], as a monitor draws it, in pixels: the newest beat on the right,
 * moving left. A `null` [bpm] is a flat line.
 */
private fun DrawScope.trace(bpm: Int?, now: Long, ink: Color) {
    val pixel = 2.dp.toPx()
    val columns = (size.width / pixel).toInt()
    val base = (size.height / pixel).toInt() - 3
    var last: Int? = null
    for (i in 0 until columns) {
        val t = now - (columns - 1 - i) * TRACE_STEP
        val height = if (bpm == null) 0 else wave(t.mod(60_000L / bpm))
        val y = base - height
        // A line, not dots: every row between this column and the last.
        val from = minOf(y, last ?: y)
        val to = maxOf(y, last ?: y)
        drawRect(ink, Offset(i * pixel, from * pixel), Size(pixel, (to - from + 1) * pixel))
        last = y
    }
}

/** How many milliseconds a column of the heart's trace lasts. */
private const val TRACE_STEP = 40L

/** How high the trace of one heartbeat stands, in pixels, [ms] into the beat. */
private fun wave(ms: Long): Int =
    when (ms) {
        in 0 until 60 -> 1
        in 100 until 130 -> -1
        in 130 until 180 -> 5
        in 180 until 215 -> -2
        in 300 until 400 -> 1
        else -> 0
    }

/** The key that hangs up: the first tap asks, a second within three seconds hangs up. */
@Composable
private fun HangUp(now: Long, hangUp: () -> Unit, lcd: Lcd) {
    var asked by remember { mutableLongStateOf(-HANG_UP_WAIT) }
    val sure = now - asked < HANG_UP_WAIT
    PixelText(
        if (sure) "Hang up?" else "End",
        Modifier.background(lcd.ink).clickable { if (sure) hangUp() else asked = now() }.padding(horizontal = 6.dp, vertical = 2.dp),
        size = SMALL,
        color = lcd.paper,
        maxLines = 1,
    )
}

/** How long the hang-up key waits for its second tap. */
private const val HANG_UP_WAIT = 3000L

/** A thing the caller can carry, in pixels, [ink] on [paper]. */
@Composable
private fun ItemIcon(item: Item, ink: Color, paper: Color) {
    Box(Modifier.size(12.dp, 14.dp).drawBehind { draw(Sprite(NotebookArt.item(item), mapOf('#' to ink, '+' to lerp(paper, ink, 0.55f), '-' to paper)), center, 2.dp.toPx()) })
}

/** What the caller carries, under the notebook's map: a tap on a thing uses it. */
@Composable
private fun Pocket(model: CallModel, lcd: Lcd) {
    val call = model.call
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelText("Pocket:", size = SMALL, color = lcd.ink)
        if (call.carried.isEmpty()) PixelText("nothing yet", size = SMALL, color = lcd.faint)
        for (item in call.carried) {
            Row(
                Modifier.clickable(enabled = !model.thinking && !call.over) { model.useItem(item) }.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ItemIcon(item, lcd.ink, lcd.paper)
                PixelText(item.word, size = SMALL, color = lcd.ink)
            }
        }
    }
}

/** The call so far, the newest at the bottom; the caller's last line appears letter by letter. */
@Composable
private fun Transcript(model: CallModel, now: Long, danger: Float, lcd: Lcd, modifier: Modifier) {
    val call = model.call
    val lines = call.lines
    val last = lines.indexOfLast { it.who == Who.CALLER }
    val shown = ((now - model.spokeAt) / CallModel.LETTER).toInt()
    LazyColumn(modifier, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.thinking) item { Said(".".repeat(1 + ((now / 400) % 3).toInt()), Who.CALLER, lcd) }
        items(lines.indices.reversed().toList()) { i ->
            val line = lines[i]
            var text = line.text
            if (i == last) text = text.take(shown.coerceAtLeast(0))
            // Static eats the words when the Hollow is close.
            if (i == last && danger >= 0.5f) text = static(text, danger, now / 90 + i)
            if (i > last && i == lines.lastIndex && line.who == Who.LINE && shown < lines[last].text.length) return@items
            if (text.isNotEmpty() || line.who != Who.CALLER) Said(text, line.who, lcd, now)
        }
    }
}

private fun static(text: String, danger: Float, tick: Long): String {
    val random = Random(tick)
    return text.map { c -> if (c != ' ' && random.nextFloat() < (danger - 0.4f) * 0.25f) "░▒▓#"[random.nextInt(4)] else c }.joinToString("")
}

/** One line of the call: the caller's plain, the player's in reverse on the right, the Hollow's shaking in reverse. */
@Composable
private fun Said(text: String, who: Who, lcd: Lcd, now: Long = 0) {
    when (who) {
        Who.CALLER -> PixelText(text, Modifier.fillMaxWidth(), size = SMALL, color = lcd.ink)
        Who.YOU ->
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f).widthIn(min = 40.dp))
                PixelText(text, Modifier.background(lcd.ink).padding(horizontal = 8.dp, vertical = 4.dp), size = SMALL, color = lcd.paper)
            }
        Who.HOLLOW -> {
            val shake = if ((now / 60) % 3 == 0L) 2.dp else (-2).dp
            Box(Modifier.fillMaxWidth().offset(x = shake).background(lcd.ink).padding(6.dp), contentAlignment = Alignment.Center) {
                PixelText(text, size = SMALL, color = lcd.paper)
            }
        }
        Who.LINE ->
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                PixelText("- $text -", size = SMALL, color = lcd.ink)
            }
    }
}

/** Where the player talks: Enter or Say says it; the microphone listens instead. */
@Composable
private fun Talk(model: CallModel, now: Long, narrow: Boolean, lcd: Lcd, notebook: Boolean, toggleNotebook: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(model.ringing, model.call.turn) { runCatching { focus.requestFocus() } }
    PixelField(
        model.typed,
        model::type,
        when {
            model.listening -> "Listening..."
            model.thinking -> "They're talking..."
            else -> "Talk to them"
        },
        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                if (!model.thinking) model.send()
                true
            } else {
                false
            }
        },
        maxLines = 3,
        fill = lcd.paper,
        ink = lcd.ink,
        hint = lcd.faint,
        size = SMALL,
    )
    // Their patience: a bar that empties while they wait, and then they choose themselves.
    val left = 1f - (model.waited(now) ?: 0f)
    Box(Modifier.fillMaxWidth().height(3.dp).drawBehind { drawRect(lcd.ink, size = size.copy(width = size.width * left)) })
    Softkeys(
        lcd,
        model.microphone?.let { (if (model.listening) "Stop" else "Mic") to model::listen },
        "Say" to model::send,
        if (narrow) (if (notebook) "Call" else "Notes") to toggleNotebook else null,
    )
}

/** The end of the call: how it ended, how close they came, and the key to hang up. */
@Composable
private fun Ending(model: CallModel, lcd: Lcd) {
    val call = model.call
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText("Call ended", color = lcd.ink)
        PixelText(
            when (call.end) {
                End.OUT -> "${call.name} got out."
                End.BATTERY -> "The battery died."
                End.ALONE -> "You hung up. Alone, ${call.name} never found the way out."
                else -> "Taken by ${call.death}."
            },
            size = SMALL,
            color = lcd.ink,
        )
        call.away?.takeIf { it > 0 }?.let { steps ->
            PixelText("${call.name} was $steps step${if (steps == 1) "" else "s"} from the way out.", size = SMALL, color = lcd.ink)
        }
        val best = model.best[call.plan]
        if (best != null && call.end != End.OUT) PixelText(if (best == 0) "Once, you got them out." else "Your closest: $best.", size = SMALL, color = lcd.faint)
    }
    Softkeys(lcd, null, "OK" to model::again, null)
}

/**
 * The player's notebook: what the caller told them, drawn by itself, with the way the caller faced
 * when they woke up at the top. It keeps what was true when the caller stood there: after midnight
 * it can lie. While the caller is lost, nothing is drawn and the caller isn't on it.
 */
@Composable
private fun Notebook(model: CallModel, lcd: Lcd, modifier: Modifier, compact: Boolean = false) {
    val call = model.call
    val haunt = model.haunt
    val measurer = rememberTextMeasurer()
    val font = PixelFont
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!compact) Header("Notebook", lcd)
        Box(
            Modifier.weight(1f).fillMaxWidth().drawBehind {
                if (model.ringing) return@drawBehind
                drawNotebook(call, haunt, measurer, font, lcd)
            },
        )
        if (!compact && !model.ringing) Pocket(model, lcd)
        val notes = if (model.ringing || compact) emptyList() else haunt.notes.filterKeys { it in call.mapped }.values
        for (note in notes.toList().takeLast(3)) PixelText("\"$note\"", size = SMALL, color = lcd.ink, maxLines = 2)
        if (call.lostTrack && !call.over) PixelText("They ran. They don't know where they are: find a mark or a note you know.", Modifier.background(lcd.ink).padding(6.dp), size = SMALL, color = lcd.paper, maxLines = 3)
    }
}

private fun DrawScope.drawNotebook(call: Call, haunt: Haunt, measurer: TextMeasurer, font: FontFamily, lcd: Lcd) {
    val shade = lerp(lcd.paper, lcd.ink, 0.14f)
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
        val layout = measurer.measure(text, TextStyle(fontFamily = font, fontSize = size, color = color))
        drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
    val line = maxOf(2.dp.toPx(), cell / 18)

    // The drawing fades: a cell is as dark as it is fresh, and gone after a few turns, unless marked.
    for ((pos, exits) in call.mapped) {
        val ink = call.ink(pos)
        if (ink <= 0f) continue
        val at = corner(pos)
        drawRect(shade, at + Offset(line, line), Size(cell - 2 * line, cell - 2 * line), alpha = ink)
        for (dir in Dir.entries) {
            if (dir in exits) continue
            when (call.onPage(dir)) {
                Dir.NORTH -> drawRect(lcd.ink, at, Size(cell, line), alpha = ink)
                Dir.SOUTH -> drawRect(lcd.ink, at + Offset(0f, cell - line), Size(cell, line), alpha = ink)
                Dir.WEST -> drawRect(lcd.ink, at, Size(line, cell), alpha = ink)
                Dir.EAST -> drawRect(lcd.ink, at + Offset(cell - line, 0f), Size(line, cell), alpha = ink)
            }
        }
        // What the walls of this place look like, twice along each wall.
        val art = Sprite(NotebookArt.wall(call.plan), mapOf('#' to lcd.ink, '+' to lcd.faint, '-' to lcd.paper))
        val pixel = cell * 0.36f / art.width
        for (dir in Dir.entries) {
            if (dir in exits) continue
            for (along in listOf(0.28f, 0.72f)) {
                val spot =
                    when (call.onPage(dir)) {
                        Dir.NORTH -> Offset(cell * along, 0f)
                        Dir.SOUTH -> Offset(cell * along, cell)
                        Dir.WEST -> Offset(0f, cell * along)
                        Dir.EAST -> Offset(cell, cell * along)
                    }
                draw(art, at + spot, pixel, alpha = ink)
            }
        }
        haunt.arrows[pos]?.let { arrow(center(pos), call.onPage(it), cell, lcd.ink.copy(alpha = ink)) }
        // What lies here and wasn't taken, as the caller saw it.
        haunt.items[pos]?.takeIf { pos !in call.taken }?.let { item ->
            draw(Sprite(NotebookArt.item(item), mapOf('#' to lcd.ink, '+' to lcd.faint, '-' to lcd.paper)), center(pos) + Offset(-cell / 5, cell / 6), cell * 0.3f / 6, alpha = ink)
        }
        if (pos in haunt.notes) drawRect(lcd.faint, center(pos) + Offset(-cell / 6, cell / 8), Size(cell / 3, cell / 5), alpha = ink)
    }
    write("x", center(call.origin) + Offset(-cell / 4, -cell / 4), lcd.ink, (cell / 4).toSp())
    // Only the light of the way out is noted; what is dangerous, the player has to remember.
    for ((pos, glimpse) in call.glimpsed) {
        if (glimpse != Glimpse.LIGHT) continue
        drawRect(lcd.ink, center(pos) - Offset(cell / 5, cell / 4), Size(cell * 2 / 5, cell / 2))
        write("light", center(pos) + Offset(0f, cell / 2.6f), lcd.ink, (cell / 6).toSp())
    }
    for (mark in call.marks) {
        if (mark.at !in call.mapped) continue
        val at = center(mark.at) + Offset(cell / 4, cell / 4)
        // A mark in reverse: it lasts, where everything else fades.
        drawCircle(lcd.ink, cell / 7, at)
        write("${mark.number}", at, lcd.paper, (cell / 6).toSp())
    }
    if (!call.lostTrack && call.at in page) {
        // The caller: an arrow, on a disc of paper, that points where they face.
        val at = center(call.at)
        drawCircle(lcd.paper, cell * 0.3f, at)
        drawCircle(lcd.ink, cell * 0.3f, at, style = androidx.compose.ui.graphics.drawscope.Stroke(line / 2))
        val arrow = NotebookArt.turned(NotebookArt.caller, call.onPage(call.facing).ordinal)
        draw(Sprite(arrow, mapOf('#' to lcd.ink)), at, cell * 0.4f / 7)
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
