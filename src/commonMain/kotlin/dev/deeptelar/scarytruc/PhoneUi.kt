package dev.deeptelar.scarytruc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The whole window: a phone and nothing else. On a phone it fills the screen; on anything wider it
 * stands in the dark at the size of one.
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
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.dark), contentAlignment = Alignment.Center) {
        Phone(model, now, keys = maxWidth >= KEYBOARD_FROM, Modifier.size(minOf(maxWidth, PHONE_WIDTH), minOf(maxHeight, PHONE_HEIGHT)))
    }
}

/** The size of the phone, on a screen bigger than one. */
val PHONE_WIDTH = 390.dp
val PHONE_HEIGHT = 844.dp

/** From this wide, the player has a keyboard at hand. */
private val KEYBOARD_FROM = 760.dp

/** Whose mind the caller has, and what went wrong with it. */
private fun mind(model: CallModel): String =
    when {
        !model.gemma ->
            model.lang.t(
                "No model: the caller knows only a few words (left, right, ahead, back, stay, hide, mark, take, use). Menu > Model to add one.",
                "Sin modelo: solo entienden unas pocas palabras (izquierda, derecha, delante, atrás, quédate, escóndete, marca, coge, usa). Menú > Modelo para añadir uno.",
            )
        model.trouble != null -> model.lang.t("${model.minds.name} did not answer, so they heard only your keywords: ${model.trouble}", "${model.minds.name} no ha respondido, así que solo han oído tus palabras clave: ${model.trouble}")
        else -> model.lang.t("The caller and the Hollow are played by ${model.minds.name}.", "Quien llama y el Hueco los interpreta ${model.minds.name}.")
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
            val flicker = interfered(danger, now) && Random(now / 110).nextFloat() < danger * 0.35f
            val paper = if (flicker) UNLIT else if (inverted) DARK else LIGHT
            return Lcd(paper, if (inverted) LIGHT else DARK)
        }
    }
}

/** What the phone shows when no call is on. */
private enum class Screen { CALLS, MENU, HELP, MODEL, STATS }

/**
 * The phone: a monochrome screen in a plastic body, with softkeys under the screen, the notebook's
 * map above the call. With [keys], there is a keyboard to type on from the first moment.
 */
@Composable
private fun Phone(model: CallModel, now: Long, keys: Boolean, modifier: Modifier) {
    val call = model.call
    val danger = if (model.ringing || call.over) 0f else danger(call, model.maze)
    val lcd = Lcd.of(call, model.ringing, danger, now)
    // The phone opens on how to save them, every time; the first page can skip it.
    var screen by remember { mutableStateOf(Screen.HELP) }
    LaunchedEffect(screen) {
        model.reading = screen != Screen.CALLS
        model.log.trace("screen ${screen.name.lowercase()}")
    }
    Column(
        modifier.pixelBox(Lcd.BODY, outline = Lcd.BODY_EDGE).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().pixelBox(lcd.paper, outline = Ink.outline, raised = false).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBar(call, danger, lcd, model.muted, model::toggleMute)
            when {
                model.locked && model.tryCode != null -> OpenTryOut(model, lcd)
                model.locked && screen == Screen.MODEL -> Options(model, lcd) { screen = Screen.HELP }
                model.locked -> Locked(model, lcd) { screen = Screen.MODEL }
                model.ringing && screen == Screen.MENU -> Menu(model, lcd) { screen = it }
                model.ringing && screen == Screen.HELP -> Help(lcd, now, model.lang) { screen = Screen.CALLS }
                model.ringing && screen == Screen.MODEL -> Options(model, lcd) { screen = Screen.MENU }
                model.ringing && screen == Screen.STATS -> Stats(model, lcd) { screen = Screen.MENU }
                model.ringing -> Calls(model, now, lcd, Modifier.weight(1f)) { screen = Screen.MENU }
                else -> {
                    CallerBar(model, now, lcd)
                    val speaking = now - model.spokeAt < model.speaking()
                    // The map and the call share the screen: no switching to follow both.
                    Notebook(model, lcd, Modifier.weight(0.4f).fillMaxWidth())
                    Box(Modifier.fillMaxWidth().height(2.dp).background(lcd.ink))
                    Transcript(model, now, danger, lcd, Modifier.weight(0.6f).fillMaxWidth())
                    if (call.over && !model.thinking && !speaking) {
                        Ending(model, lcd)
                    } else {
                        Talk(model, now, keys, lcd)
                    }
                }
            }
        }
    }
}

/**
 * The top of the screen: the bars of the signal, which fail when the Hollow is near, the time, and
 * the bars of the battery.
 */
@Composable
private fun StatusBar(call: Call, danger: Float, lcd: Lcd, muted: Boolean, mute: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val signal = (5 - (danger * 5).toInt()).coerceIn(1, 5)
        Bars(signal, lcd, antenna = true)
        Spacer(Modifier.weight(1f))
        PixelText(clock(call), size = SMALL, color = lcd.ink)
        Spacer(Modifier.weight(1f))
        // The game's sound, on or off: a speaker, crossed out when it is off.
        Box(Modifier.padding(end = 10.dp).size(28.dp, 22.dp).clickable(onClick = mute), contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp, 16.dp).drawBehind { draw(Sprite(if (muted) SPEAKER_OFF else SPEAKER, mapOf('#' to lcd.ink)), center, 2.dp.toPx()) })
        }
        Bars(((call.battery.coerceIn(0, 100) + 19) / 20), lcd, antenna = false)
    }
}

/** A speaker, as a phone showed its ringer on, and off. */
private val SPEAKER = listOf("...#....#.", "..##..#..#", "####.#.#.#", "####.#.#.#", "####.#.#.#", "..##..#..#", "...#....#.")
private val SPEAKER_OFF = listOf("...#......", "..##.#...#", "####..#.#.", "####...#..", "####..#.#.", "..##.#...#", "...#......")

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
private fun Softkeys(lcd: Lcd, left: Pair<String, () -> Unit>?, middle: Pair<String, () -> Unit>?, right: Pair<String, () -> Unit>?, loud: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((key, align) in listOf(left to Alignment.CenterStart, middle to Alignment.Center, right to Alignment.CenterEnd)) {
            Box(Modifier.weight(1f), contentAlignment = align) {
                if (key != null) {
                    // The middle key in reverse and larger, when it is the one to reach for.
                    val reverse = loud && key === middle
                    PixelText(key.first, Modifier.background(if (reverse) lcd.ink else Color.Transparent).clickable(onClick = key.second).padding(vertical = if (reverse) 10.dp else 6.dp, horizontal = if (reverse) 18.dp else 4.dp), color = if (reverse) lcd.paper else lcd.ink, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The phone between calls: a card for each call, to slide through, and the one at rest ringing
 * under them.
 */
@Composable
private fun Calls(model: CallModel, now: Long, lcd: Lcd, modifier: Modifier, options: () -> Unit) {
    val lang = model.lang
    val plans = Plan.entries
    val pager = rememberPagerState(plans.indexOf(model.plan)) { plans.size }
    val scope = rememberCoroutineScope()
    // The card that comes to rest is the call that rings; while a call is being cleared, it waits.
    LaunchedEffect(pager.settledPage, model.thinking) {
        val plan = plans[pager.settledPage]
        if (plan != model.plan) model.play(plan)
    }
    Header(lang.t("Calls", "Llamadas"), lcd)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The next card shows at the edge: there is more to slide to.
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp), pageSpacing = 8.dp, verticalAlignment = Alignment.CenterVertically) { page ->
            PlaceCard(plans[page], model.best[plans[page]], now, lang, lcd)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            for ((label, step) in listOf("<" to -1, null to 0, ">" to 1)) {
                if (label == null) {
                    for (page in plans.indices) {
                        Box(Modifier.padding(horizontal = 4.dp).size(6.dp).background(if (page == pager.currentPage) lcd.ink else lcd.faint))
                    }
                } else {
                    val next = pager.currentPage + step
                    PixelText(label, Modifier.clickable(enabled = next in plans.indices) { scope.launch { pager.animateScrollToPage(next) } }.padding(horizontal = 18.dp, vertical = 2.dp), color = if (next in plans.indices) lcd.ink else lcd.faint)
                }
            }
        }
        // The phone rings: its picture shakes.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val shake = if ((now / 120) % 2 == 0L && (now / 1000) % 3 != 2L) 2.dp else 0.dp
            Box(Modifier.size(40.dp).offset(x = shake).drawBehind { draw(Sprite(HANDSET, mapOf('#' to lcd.ink)), center, 3.dp.toPx()) })
            Column(Modifier.padding(start = 10.dp)) {
                PixelText(lang.t("Unknown number", "Número oculto"), color = lcd.ink)
                PixelText(lang.t("calling", "llamando") + if ((now / 600) % 2 == 0L) "" else ".", size = SMALL, color = lcd.ink)
            }
        }
        if (model.microphone != null) PixelText(lang.t("Best out loud: tap Talk and speak.", "Mejor en voz alta: toca Hablar."), Modifier.padding(top = 14.dp), size = SMALL, color = lcd.ink)
    }
    Softkeys(lcd, lang.t("Menu", "Menú") to options, lang.t("Answer", "Atender") to model::answer, null)
}

/** The card of a call: a picture of the place, its name, how it first sounds on the line, and how close the player came. */
@Composable
private fun PlaceCard(plan: Plan, best: Int?, now: Long, lang: Lang, lcd: Lcd) {
    val text = plan.text(lang)
    Column(
        Modifier.fillMaxWidth().pixelBox(lcd.paper, outline = lcd.ink, raised = false).padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(PLACE_ASPECT).drawBehind {
                val sprite = Sprite(PlaceArt.of(plan), mapOf('#' to lcd.ink, (if ((now / 700) % 2 == 0L) 'a' else 'b') to lcd.ink))
                // Whole screen pixels, so that the picture stays sharp.
                draw(sprite, center, minOf(size.width / sprite.width, size.height / sprite.height).toInt().toFloat())
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelText(text.title, Modifier.weight(1f), color = lcd.ink, maxLines = 1)
            when (best) {
                null -> Unit
                0 -> PixelText(lang.t("got out", "salieron"), size = SMALL, color = lcd.ink)
                else -> PixelText(lang.t("closest: $best", "lo más cerca: $best"), size = SMALL, color = lcd.ink)
            }
        }
        PixelText(lang.t("after ${text.film}", "según ${text.film}"), size = SMALL, color = lcd.ink)
        PixelText(lang.t("\"", "«") + text.setting + lang.t("\"", "»"), size = SMALL, color = lcd.ink)
        PixelText(text.hint, size = SMALL, color = lcd.faint)
    }
}

/** How wide a picture of [PlaceArt] is for its height. */
private const val PLACE_ASPECT = 56f / 34f

/** Headphones: the night is heard better through them. */
private val HEADPHONES =
    listOf(
        "...#####...",
        "..#.....#..",
        ".#.......#.",
        "#.........#",
        "#.........#",
        "###.....###",
        "###.....###",
        "###.....###",
        ".##.....##.",
    )

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
 * The phone as the page opens: its keys locked, and under them the language and the model, to set
 * before the first call. Unlocking it is the tap a browser waits for before it plays a sound; then
 * comes how to save them, and the phone rings once the player is at the calls.
 */
@Composable
private fun ColumnScope.Locked(model: CallModel, lcd: Lcd, options: () -> Unit) {
    val lang = model.lang
    val unlock = model::unlock
    Header("SCARY TRUC", lcd)
    Column(
        Modifier.weight(1f).fillMaxWidth().clickable(onClick = unlock),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(60.dp).drawBehind { draw(Sprite(PADLOCK, mapOf('#' to lcd.ink)), center, 5.dp.toPx()) })
        PixelText(lang.t("Keys locked", "Teclado bloqueado"), color = lcd.ink)
        PixelText(lang.t("Turn the sound up, and unlock. Someone is about to call.", "Sube el volumen y desbloquea. Alguien está a punto de llamar."), Modifier.fillMaxWidth(), size = SMALL, color = lcd.faint, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(36.dp, 30.dp).drawBehind { draw(Sprite(HEADPHONES, mapOf('#' to lcd.ink)), center, 3.dp.toPx()) })
        PixelText(lang.t("For the real experience, put your headphones on.", "Para vivirlo de verdad, ponte los auriculares."), Modifier.fillMaxWidth(), size = SMALL, color = lcd.ink, align = TextAlign.Center)
    }
    if (model.tryEnded) PixelText(lang.t("Your friend's try-out has ended. To play with a model, add a key of your own.", "La prueba de tu amigo ha terminado. Para jugar con un modelo, añade una clave tuya."), Modifier.fillMaxWidth(), size = SMALL, color = lcd.ink)
    // The other language is named in itself, as in the menu.
    val other = Lang.entries.first { it != lang }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((line, go) in listOf(
            "${lang.t("Language", "Idioma")}: ${lang.title} > ${other.title}" to { model.speak(other) },
            "${lang.t("Model", "Modelo")}: ${model.minds.name ?: lang.t("none", "ninguno")}" to options,
        )) {
            PixelText(line, Modifier.fillMaxWidth().pixelBox(lcd.paper, outline = lcd.ink, raised = false).clickable(onClick = go).padding(horizontal = 6.dp, vertical = 3.dp), color = lcd.ink, maxLines = 1)
        }
    }
    Softkeys(lcd, null, lang.t("Unlock", "Desbloq.") to unlock, null)
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
private fun ColumnScope.Menu(model: CallModel, lcd: Lcd, go: (Screen) -> Unit) {
    val lang = model.lang
    Header(lang.t("Menu", "Menú"), lcd)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The language changes at once: the other one is named in itself.
        val other = Lang.entries.first { it != lang }
        val entries =
            listOf(
                lang.t("How to save them", "Cómo salvarles") to { go(Screen.HELP) },
                lang.t("Model", "Modelo") to { go(Screen.MODEL) },
                "${lang.t("Statistics", "Estadísticas")} (${model.log.plays.size})" to { go(Screen.STATS) },
                "${lang.t("Language", "Idioma")}: ${lang.title} > ${other.title}" to { model.speak(other) },
                "${lang.t("Export play log", "Exportar registro")} (${model.log.calls})" to model::exportLog,
            )
        for ((i, entry) in entries.withIndex()) {
            PixelText(
                entry.first,
                Modifier.fillMaxWidth().background(if (i == 0) lcd.ink else lcd.paper).clickable(onClick = entry.second).padding(horizontal = 6.dp, vertical = 3.dp),
                color = if (i == 0) lcd.paper else lcd.ink,
                maxLines = 1,
            )
        }
    }
    Exported(model, lcd)
    Softkeys(lcd, lang.t("Back", "Atrás") to { go(Screen.CALLS) }, null, null)
}

/** Where the play log went, when the phone has to say. */
@Composable
private fun Exported(model: CallModel, lcd: Lcd) {
    model.exported?.let { PixelText(it, Modifier.fillMaxWidth().background(lcd.ink).padding(4.dp), size = SMALL, color = lcd.paper, maxLines = 4) }
}

/**
 * The calls played on this device, just for fun: how many got out, what took the others, how close
 * they came, and how often fear was to blame. The last calls come last, newest first.
 */
@Composable
private fun ColumnScope.Stats(model: CallModel, lcd: Lcd, back: () -> Unit) {
    val lang = model.lang
    val plays = model.log.plays
    Header(lang.t("Statistics", "Estadísticas"), lcd)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (plays.isEmpty()) {
            PixelText(lang.t("No calls yet. Someone is waiting for you to pick up.", "Aún no hay llamadas. Alguien espera a que contestes."), size = SMALL, color = lcd.ink)
        } else {
            val lost = plays.filter { it.end != End.OUT }
            val dead = lost.filter { it.end == End.TAKEN || it.end == End.DOOM }
            StatLine(lang.t("Calls", "Llamadas"), "${plays.size}", lcd)
            StatLine(lang.t("Got out", "Salieron"), "${plays.size - lost.size}", lcd)
            StatLine(lang.t("Lost to panic", "Perdidos por el pánico"), "${dead.count { it.panicked }} / ${dead.size}", lcd)
            StatLine(lang.t("Times they ran in a panic", "Veces que huyeron en pánico"), "${plays.sumOf { it.bolts }}", lcd)
            val steps = lost.mapNotNull { it.away }
            if (steps.isNotEmpty()) {
                StatLine(lang.t("Steps from the way out, on average", "Pasos hasta la salida, de media"), "${(steps.average() * 10).toInt() / 10.0}", lcd)
                StatLine(lang.t("Closest without getting out", "Lo más cerca sin salir"), "${steps.min()}", lcd)
            }
            StatTitle(lang.t("How it ended", "Cómo acabó"), lcd)
            for ((ending, count) in plays.groupingBy { it.ending(lang) }.eachCount().entries.sortedByDescending { it.value }) StatLine(ending, "$count", lcd)
            StatTitle(lang.t("Last calls", "Últimas llamadas"), lcd)
            for (play in plays.takeLast(LAST_PLAYS).asReversed()) {
                val away = play.away?.takeIf { play.end != End.OUT }?.let { lang.t(", $it steps away", ", a $it pasos") }.orEmpty()
                val fear = if (play.panicked) lang.t(", panic", ", pánico") else ""
                PixelText("${play.plan.text(lang).title}: ${play.ending(lang)}$away$fear", Modifier.fillMaxWidth(), size = SMALL, color = lcd.ink)
            }
        }
    }
    Softkeys(lcd, lang.t("Back", "Atrás") to back, null, null)
}

/** A line of the statistics: what is counted, and how many. */
@Composable
private fun StatLine(label: String, value: String, lcd: Lcd) {
    Row(Modifier.fillMaxWidth()) {
        PixelText(label, Modifier.weight(1f), size = SMALL, color = lcd.ink)
        PixelText(value, size = SMALL, color = lcd.ink)
    }
}

/** A part of the statistics, in reverse, as a menu of the time marks it. */
@Composable
private fun StatTitle(text: String, lcd: Lcd) = PixelText(text, Modifier.fillMaxWidth().background(lcd.ink).padding(horizontal = 4.dp, vertical = 1.dp), size = SMALL, color = lcd.paper)

/** How many of the last calls the statistics list one by one. */
private const val LAST_PLAYS = 10

/**
 * A page of how to save them: a picture, one line in the words of whoever built the mazes, and
 * what you could type. In [picture], `#` is ink, `+` half ink and `-` a hole in the ink; `a` is on in the first frame, `b`
 * in the second, and `m` fades from ink to half ink, so that the picture moves.
 */
private class Lesson(val line: String, val example: String?, val picture: List<String>)

/** How to save them: the rules of the game, read as a threat, a page at a time. */
private val LESSONS =
    listOf(
        // Who is talking comes first, before anything they teach: someone is walking in what they built.
        Lesson(
            "I built this maze. I put them in it. If you want them back, these are my rules.",
            null,
            listOf(
                "+++++++++++++++++++++",
                "+.......+.....+.....+",
                "+.+++++.+.+++.+.+++.+",
                "+.+.....+.+...+...+.+",
                "+.+.+++++.+.+++++.+.+",
                "+.+.......+.......+.+",
                "+.+++++.+++++.+++++.+",
                "+.....+...a.b...+...+",
                "+.+++.+++.+.+++.+++.+",
                "+.+.......+.......+.+",
                "+.+.+++++.+.+++++.+.+",
                "+.........+.........+",
                "+++++++++++++++++++++",
            ),
        ),
        // The caller as the notebook draws them, an arrow facing down the page, towards YOU: their
        // left, the L, is on your right, and that is where they go.
        Lesson(
            "They see. You don't. Speak to them out loud. Say where to go from where THEY face. Here they face you: their left is on your right.",
            "go left",
            listOf(
                ".........###.........",
                ".++......###......#..",
                ".+.+.....###....b.#..",
                ".++.....#####.bbbb#..",
                ".+.+.....###....b.#..",
                ".+.+......#.......###",
                ".....................",
                ".....................",
                ".....+.+.+++.+.+.....",
                ".....+.+.+.+.+.+.....",
                "......+..+.+.+.+.....",
                "......+..+.+.+.+.....",
                "......+..+++.+++.....",
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
            "While you think, my pet walks. Wait too long and they choose for themselves. They choose badly.",
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

/** How to save them in Spanish: the same pages, the one that spells words with its letters in Spanish (I is izquierda). */
private val LESSONS_ES: List<Lesson> =
    listOf(
        "Yo construí este laberinto. Yo los metí dentro. Si los quieres de vuelta, estas son mis reglas." to null,
        "Ellos ven. Tú no. Háblales en voz alta. Di adónde ir desde donde ELLOS miran. Aquí te miran a ti: su izquierda está a tu derecha." to "ve a la izquierda",
        "Tu libreta olvida. Una marca rayada en mis paredes, no." to "raya una marca",
        "Mi mascota es ciega. Oye el miedo. Mantenlos en calma y, cuando esté cerca, que se escondan." to "escóndete. no te muevas. estoy aquí.",
        "Dejo cosas en los rincones. Una batería da tiempo. Una bengala lo asusta. Una piedra, lanzada, lo atrae." to "cógela",
        "Mientras piensas, mi mascota camina. Si esperas demasiado, eligen ellos. Y eligen mal." to null,
        "A medianoche muevo las paredes, y tu libreta miente. La salida está fría. Mi frío nunca miente." to "¿dónde hace más frío?",
    ).mapIndexed { i, (line, example) ->
        Lesson(
            line,
            example,
            if (i != 1) {
                LESSONS[i].picture
            } else {
                listOf(
                    ".........###.........",
                    ".++......###......###",
                    ".+.+.....###....b..#.",
                    ".+.+....#####.bbbb.#.",
                    ".+.+.....###....b..#.",
                    ".++.......#.......###",
                    ".....................",
                    ".....................",
                    ".......+++.+.+.......",
                    "........+..+.+.......",
                    "........+..+.+.......",
                    "........+..+.+.......",
                    "........+..+++.......",
                )
            },
        )
    }

/** A lesson's picture in its [frame], 0 or 1. */
private fun Lesson.sprite(frame: Int, lcd: Lcd): Sprite {
    val colors = mutableMapOf('#' to lcd.ink, '+' to lcd.faint, '-' to lcd.paper, 'm' to if (frame == 0) lcd.ink else lcd.faint)
    colors[if (frame == 0) 'a' else 'b'] = lcd.ink
    return Sprite(picture, colors)
}

/** How to save them, a page at a time: only Next turns it, so that a tap on the picture can't skip what it says. The last page goes to the calls, to pick one. */
@Composable
private fun ColumnScope.Help(lcd: Lcd, now: Long, lang: Lang, start: Int = 0, back: () -> Unit) {
    var page by remember { mutableStateOf(start) }
    val lesson = if (lang == Lang.ES) LESSONS_ES[page] else LESSONS[page]
    val last = page == LESSONS.lastIndex
    val next: () -> Unit = { if (last) back() else page += 1 }
    Header(lang.t("How to save them", "Cómo salvarles"), lcd)
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().drawBehind {
                val sprite = lesson.sprite(((now / 700) % 2).toInt(), lcd)
                // Whole screen pixels, so that the picture stays sharp.
                val pixel = minOf(size.width / (sprite.width + 2), size.height / (sprite.height + 1)).toInt().toFloat()
                draw(sprite, center, pixel)
            },
        )
        PixelText(lesson.line, Modifier.fillMaxWidth(), color = lcd.ink)
        lesson.example?.let { Said(it, Who.YOU, lcd) }
    }
    // Signed on every page: whoever skips to the end, or stops halfway, knows who wrote this.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        PixelText(lang.t("- the one who put them there", "- quien los metió ahí"), size = SMALL, color = lcd.ink)
    }
    Softkeys(lcd, (if (page == 0) lang.t("Skip", "Saltar") else lang.t("Back", "Atrás")) to { if (page == 0) back() else page -= 1 }, "${page + 1}/${LESSONS.size}" to {}, (if (last) lang.t("Calls", "Llamadas") else lang.t("Next", "Siguiente")) to next)
}

/** A page of how to save them on a lit screen, for the screenshots. */
@Composable
internal fun GuidePage(page: Int, now: Long, lang: Lang = Lang.EN) {
    val lcd = Lcd(Lcd.LIGHT, Lcd.DARK)
    Column(Modifier.fillMaxSize().background(Lcd.BODY).padding(6.dp).pixelBox(lcd.paper, outline = Ink.outline, raised = false).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Help(lcd, now, lang, start = page) {}
    }
}

/**
 * Where the player connects a model that speaks the OpenAI API: Gemma on OpenRouter with their own
 * key, their Ollama, or a friend's proxy, whose address and token take the place of OpenRouter's.
 */
@Composable
private fun ColumnScope.Options(model: CallModel, lcd: Lcd, done: () -> Unit) {
    var sharing by remember { mutableStateOf(false) }
    if (sharing) return ShareTryOut(model, lcd) { sharing = false }
    val saved = model.server
    val borrowed = model.borrowedUntil
    var url by remember { mutableStateOf(saved?.url ?: Server.OPENROUTER.url) }
    var name by remember { mutableStateOf(saved?.model ?: Server.OPENROUTER.model) }
    var key by remember { mutableStateOf(saved?.key.orEmpty()) }
    val lang = model.lang
    Header(lang.t("Model", "Modelo"), lcd)
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelText(mind(model), size = SMALL, color = lcd.ink)
        if (borrowed != null) PixelText(lang.t("You play with a friend's model until ${day(borrowed)}.", "Juegas con el modelo de un amigo hasta el ${day(borrowed)}."), size = SMALL, color = lcd.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (borrowed == null) {
                PixelText("OpenRouter", Modifier.clickable { url = Server.OPENROUTER.url; name = Server.OPENROUTER.model }.padding(4.dp), size = SMALL, color = lcd.ink)
                PixelText("Ollama", Modifier.clickable { url = Server.OLLAMA.url; name = Server.OLLAMA.model; key = "" }.padding(4.dp), size = SMALL, color = lcd.ink)
            }
            PixelText(lang.t("None", "Ninguno"), Modifier.clickable { model.connect(null); done() }.padding(4.dp), size = SMALL, color = lcd.ink)
        }
        if (borrowed == null) for ((value, set, hint) in listOf(Triple(url, { it: String -> url = it }, lang.t("Server address", "Dirección del servidor")), Triple(name, { it: String -> name = it }, lang.t("Model", "Modelo")), Triple(key, { it: String -> key = it }, lang.t("Key or token", "Clave o token")))) {
            PixelField(value, set, hint, Modifier.fillMaxWidth(), maxLines = 1, fill = lcd.paper, ink = lcd.ink, hint = lcd.faint, size = SMALL)
        }
        if (borrowed == null && model.links != null && !saved?.key.isNullOrBlank()) {
            PixelText(lang.t("Share a try-out", "Compartir una prueba"), Modifier.fillMaxWidth().pixelBox(lcd.paper, outline = lcd.ink, raised = false).clickable { sharing = true }.padding(horizontal = 6.dp, vertical = 3.dp), size = SMALL, color = lcd.ink)
        }
        if (borrowed == null) PixelText(
            lang.t(
                "The key stays on this device and goes from it straight to the server. A friend's proxy works too: its address, and your token as the key.",
                "La clave se queda en este dispositivo y va de él directa al servidor. También sirve el proxy de un amigo: su dirección, y tu token como clave.",
            ),
            size = SMALL,
            color = lcd.faint,
        )
    }
    Softkeys(lcd, lang.t("Back", "Atrás") to done, if (borrowed != null) null else lang.t("Save", "Guardar") to { model.connect(Server(url.trim(), name.trim(), key.trim())); done() }, null)
}

/** The day of [millis] since 1970, as the phone writes it. */
@OptIn(ExperimentalTime::class)
private fun day(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString().take(10)

/** Makes a link for friends to play with this model, locked with a secret word they are told apart from it. */
@Composable
private fun ColumnScope.ShareTryOut(model: CallModel, lcd: Lcd, back: () -> Unit) {
    val lang = model.lang
    var word by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(3) }
    var copied by remember { mutableStateOf(false) }
    val link = model.shared
    val leave = { model.shared = null; back() }
    Header(lang.t("Share a try-out", "Compartir una prueba"), lcd)
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (link == null) {
            PixelText(
                lang.t(
                    "A link that plays with this model and key, locked with a secret word. Tell your friends the word apart from the link. Set a credit limit on the key, and delete it when the test ends: until then, anyone with the link and the word can use it.",
                    "Un enlace que juega con este modelo y esta clave, cerrado con una palabra secreta. Di la palabra aparte del enlace. Pon un límite de crédito a la clave y bórrala al acabar la prueba: hasta entonces, quien tenga el enlace y la palabra puede usarla.",
                ),
                size = SMALL,
                color = lcd.ink,
            )
            PixelField(word, { word = it }, lang.t("Secret word", "Palabra secreta"), Modifier.fillMaxWidth(), maxLines = 1, fill = lcd.paper, ink = lcd.ink, hint = lcd.faint, size = SMALL)
            PixelText(lang.t("The page forgets the key after:", "La página olvida la clave tras:"), size = SMALL, color = lcd.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in listOf(1, 3, 7, 14)) {
                    val on = option == days
                    PixelText(
                        lang.t("$option d", "$option d"),
                        Modifier.pixelBox(if (on) lcd.ink else lcd.paper, outline = lcd.ink, raised = false).clickable { days = option }.padding(horizontal = 8.dp, vertical = 4.dp),
                        size = SMALL,
                        color = if (on) lcd.paper else lcd.ink,
                    )
                }
            }
        } else {
            PixelText(lang.t("Your link. Send the word another way.", "Tu enlace. La palabra, por otro camino."), size = SMALL, color = lcd.ink)
            PixelText(link, size = SMALL, color = lcd.ink)
            if (copied) PixelText(lang.t("Copied.", "Copiado."), size = SMALL, color = lcd.ink)
        }
    }
    if (link == null) {
        Softkeys(lcd, lang.t("Back", "Atrás") to leave, if (TryOut.secret(word).isEmpty()) null else lang.t("Make link", "Crear") to { model.shareTryOut(word, days) }, null)
    } else {
        Softkeys(
            lcd,
            lang.t("Back", "Atrás") to leave,
            lang.t("Copy", "Copiar") to { model.links?.copy(link); copied = true },
            lang.t("Share", "Enviar") to { model.links?.send(link, lang.t("A phone is ringing. Pick up: Scary Truc. I'll tell you the secret word.", "Suena un teléfono. Contesta: Scary Truc. La palabra secreta te la digo yo.")) },
        )
    }
}

/** A friend's link opened the page: the secret word opens their model, or the player goes on without. */
@Composable
private fun ColumnScope.OpenTryOut(model: CallModel, lcd: Lcd) {
    val lang = model.lang
    var word by remember { mutableStateOf("") }
    Header(lang.t("A friend sent this", "Te lo ha enviado alguien"), lcd)
    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        PixelText(lang.t("Enter the secret word they told you, and the caller will have a mind of their own.", "Escribe la palabra secreta que te han dicho, y quien llama tendrá mente propia."), Modifier.fillMaxWidth(), size = SMALL, color = lcd.ink)
        PixelField(word, { word = it }, lang.t("Secret word", "Palabra secreta"), Modifier.fillMaxWidth(), maxLines = 1, fill = lcd.paper, ink = lcd.ink, hint = lcd.faint, size = SMALL)
        if (model.tryOpening) PixelText(lang.t("Opening...", "Abriendo..."), size = SMALL, color = lcd.ink)
        model.tryFailure?.let { PixelText(it, Modifier.fillMaxWidth(), size = SMALL, color = lcd.ink) }
    }
    Softkeys(
        lcd,
        lang.t("Skip", "Saltar") to { model.tryCode = null },
        if (model.tryOpening || TryOut.secret(word).isEmpty()) null else lang.t("Open", "Abrir") to { model.openTryOut(word) },
        null,
    )
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
            PixelText(call.name + if (call.hidden && !call.over) model.lang.t(" (hiding)", if (call.woman) " (escondida)" else " (escondido)") else "", color = lcd.ink, maxLines = 1)
        }
        // What they carry, in reverse, as a phone of the time showed its icons: a tap uses it.
        for (item in call.carried) {
            Row(
                Modifier.background(lcd.ink).clickable(enabled = !model.thinking && !call.over) { model.useItem(item) }.padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ItemIcon(item, lcd.paper, lcd.ink)
                PixelText(item.word(model.lang), size = SMALL, color = lcd.paper)
            }
        }
        // The red key, in the corner: twice, to be sure, and it is over for them.
        if (!call.over) HangUp(now, model.lang, model::hangUp, lcd)
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
private fun HangUp(now: Long, lang: Lang, hangUp: () -> Unit, lcd: Lcd) {
    var asked by remember { mutableLongStateOf(-HANG_UP_WAIT) }
    val sure = now - asked < HANG_UP_WAIT
    PixelText(
        if (sure) lang.t("Hang up?", "¿Colgar?") else lang.t("End", "Colgar"),
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

/** The call so far, the newest at the bottom; the caller's last line appears letter by letter. */
@Composable
private fun Transcript(model: CallModel, now: Long, danger: Float, lcd: Lcd, modifier: Modifier) {
    val call = model.call
    val lines = call.lines
    val last = lines.indexOfLast { it.who == Who.CALLER }
    val shown = ((now - model.spokeAt) / CallModel.LETTER).toInt()
    LazyColumn(modifier, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.thinking) item { Said(".".repeat(1 + ((now / 400) % 3).toInt()), Who.CALLER, lcd) }
        model.nudge?.let { nudge -> item { Said(nudge, Who.CALLER, lcd) } }
        items(lines.indices.reversed().toList()) { i ->
            val line = lines[i]
            var text = line.text
            if (i == last) text = text.take(shown.coerceAtLeast(0))
            // Static eats the words when the Hollow is close.
            if (i == last) text = static(text, danger, now)
            if (i > last && i == lines.lastIndex && line.who == Who.LINE && shown < lines[last].text.length) return@items
            if (text.isNotEmpty() || line.who != Who.CALLER) Said(text, line.who, lcd, now)
        }
    }
}

private const val STATIC_EVERY = 2000L
private const val STATIC_BURST = 600f

/** Interference comes in bursts, longer the closer the Hollow is: between them the screen can be read. */
internal fun interfered(danger: Float, now: Long): Boolean = danger >= 0.5f && now % STATIC_EVERY < (danger * STATIC_BURST).toLong()

/** [text] with a few letters lost, during a burst of interference. */
internal fun static(text: String, danger: Float, now: Long): String {
    if (!interfered(danger, now)) return text
    val random = Random(now / 90)
    return text.map { c -> if (c != ' ' && random.nextFloat() < (danger - 0.4f) * 0.12f) "░▒▓#"[random.nextInt(4)] else c }.joinToString("")
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

/**
 * Where the player talks. This is a phone call, so the key to reach for is the microphone, where
 * there is one, and the keyboard stays down on a phone until the player taps the field. With words
 * in the field the same key says them, as Enter does.
 */
@Composable
private fun Talk(model: CallModel, now: Long, keys: Boolean, lcd: Lcd) {
    val focus = remember { FocusRequester() }
    val talking = model.microphone != null
    LaunchedEffect(model.ringing, model.call.turn) { if (keys || !talking) runCatching { focus.requestFocus() } }
    model.failure?.let { PixelText(it, Modifier.background(lcd.ink).padding(4.dp), size = SMALL, color = lcd.paper, maxLines = 2) }
    PixelField(
        model.typed,
        model::type,
        when {
            model.listening -> model.lang.t("Listening...", "Escuchando...")
            model.thinking -> model.lang.t("They're talking...", "Está hablando...")
            talking -> model.lang.t("Tap Talk and speak, or type", "Toca Hablar, o escribe")
            else -> model.lang.t("Talk to them, or just: left", "Háblale, o solo: izquierda")
        },
        Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
            // The arrows are shortcuts while nothing is typed: the way, from where they face.
            val way = mapOf(Key.DirectionLeft to Order.LEFT, Key.DirectionRight to Order.RIGHT, Key.DirectionUp to Order.AHEAD, Key.DirectionDown to Order.BACK)[event.key]
            when {
                event.type != KeyEventType.KeyDown -> false
                event.key == Key.Enter && !event.isShiftPressed -> {
                    if (!model.thinking) model.send()
                    true
                }
                way != null && model.typed.isEmpty() -> {
                    if (!model.thinking) {
                        model.type(model.lang.t(way.word, listOf("delante", "derecha", "atrás", "izquierda")[way.turn!!]))
                        model.send()
                    }
                    true
                }
                else -> false
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
    val mic = talking && (model.listening || model.typed.isBlank())
    Softkeys(
        lcd,
        null,
        when {
            model.listening -> model.lang.t("Stop", "Parar") to model::listen
            mic -> model.lang.t("Talk", "Hablar") to model::listen
            else -> model.lang.t("Say", "Decir") to model::send
        },
        null,
        loud = mic,
    )
}

/** The end of the call: how it ended, how close they came, and the key to hang up. */
@Composable
private fun Ending(model: CallModel, lcd: Lcd) {
    val call = model.call
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val lang = model.lang
        PixelText(lang.t("Call ended", "Llamada finalizada"), color = lcd.ink)
        PixelText(
            when (call.end) {
                End.OUT -> lang.t("${call.name} got out.", "${call.name} ha salido.")
                End.BATTERY -> lang.t("The battery died.", "Se acabó la batería.")
                End.ALONE -> lang.t("You hung up. Alone, ${call.name} never found the way out.", "Has colgado. Sin ti, ${call.name} nunca encontró la salida.")
                End.TRUST -> lang.t("${call.name} stopped believing you, and hung up. Alone, they never found the way out.", "${call.name} dejó de creerte y colgó. Sin ti, nunca encontró la salida.")
                else -> lang.t("Taken by ${call.death}.", "${if (call.woman) "Se la llevó" else "Se lo llevó"} ${death(call.death, lang)}.")
            },
            size = SMALL,
            color = lcd.ink,
        )
        call.away?.takeIf { it > 0 }?.let { steps ->
            PixelText(lang.t("${call.name} was $steps step${if (steps == 1) "" else "s"} from the way out.", "${call.name} estaba a $steps paso${if (steps == 1) "" else "s"} de la salida."), size = SMALL, color = lcd.ink)
        }
        val best = model.best[call.plan]
        if (best != null && call.end != End.OUT) PixelText(if (best == 0) lang.t("Once, you got them out.", "Una vez lo lograste.") else lang.t("Your closest: $best.", "Lo más cerca: $best."), size = SMALL, color = lcd.faint)
    }
    Exported(model, lcd)
    Softkeys(lcd, model.lang.t("Log", "Registro") to model::exportLog, "OK" to model::again, null)
}

/**
 * The player's notebook: what the caller told them, drawn by itself, with the way the caller faced
 * when they woke up at the top. It keeps what was true when the caller stood there: after midnight
 * it can lie. While the caller is lost, nothing is drawn and the caller isn't on it.
 */
@Composable
private fun Notebook(model: CallModel, lcd: Lcd, modifier: Modifier) {
    val call = model.call
    val haunt = model.haunt
    val measurer = rememberTextMeasurer()
    val font = PixelFont
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().drawBehind {
                if (model.ringing) return@drawBehind
                drawNotebook(call, haunt, measurer, font, lcd, model.lang)
            },
        )
        if (call.lostTrack && !call.over) PixelText(model.lang.t("They ran. They don't know where they are: find a mark or a note you know.", "Ha huido. No sabe dónde está: busca una marca o una nota que conozcas."), Modifier.background(lcd.ink).padding(6.dp), size = SMALL, color = lcd.paper, maxLines = 3)
    }
}

private fun DrawScope.drawNotebook(call: Call, haunt: Haunt, measurer: TextMeasurer, font: FontFamily, lcd: Lcd, lang: Lang) {
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
        write(lang.t("light", "luz"), center(pos) + Offset(0f, cell / 2.6f), lcd.ink, (cell / 6).toSp())
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
