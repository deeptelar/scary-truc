package dev.deeptelar.scarytruc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The whole window: the maze on the left, the crew and the controls on the right. */
@Composable
fun Game(model: GameModel) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            frame++
        }
    }
    val night = model.night
    Row(Modifier.fillMaxSize().background(Ink.dark).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Nights(model)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Board(model.maze, night, Modifier.fillMaxSize(), frame) { pos, secondary ->
                    model.click(pos, if (secondary) MarkKind.TRICK else MarkKind.TREAT)
                }
                if (night.over && !model.thinking) Ofrenda(model, Modifier.align(Alignment.Center))
            }
        }
        Side(model, frame, Modifier.width(390.dp).fillMaxHeight())
    }
}

/** The nights to choose from, with the most saved on each. */
@Composable
private fun Nights(model: GameModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (plan in Plan.entries) {
            val label = if (plan == Plan.ENDLESS) "∞" else "${plan.ordinal + 1}"
            val best = model.best[plan]?.let { " ${it}/${plan.crew.size}" }.orEmpty()
            PixelButton(
                label + best,
                if (plan == model.plan) Ink.orange else Ink.paperDark,
                onClick = { model.play(plan) },
                enabled = model.open(plan) && !model.thinking,
                small = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Side(model: GameModel, frame: Int, modifier: Modifier) {
    val night = model.night
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelText("SCARY TRUC", size = BIG, color = Ink.orange)
        PixelText(
            "${model.plan.title}" + (if (model.plan == Plan.ENDLESS) " · ${model.maze.seed}" else "") + " · turn ${night.turn + 1} of ${night.dawn}",
            size = SMALL,
            color = Ink.faint,
        )
        PixelText(model.plan.hint, size = SMALL, color = Ink.text, maxLines = 5)

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Supply(Sprites.candy, night.candy)
            PixelText("treat 1 · trick 2", size = SMALL, color = Ink.faint)
            if (night.plan.marigolds > 0) Supply(Sprites.marigold, night.marigolds)
        }

        Column(Modifier.fillMaxWidth().pixelBox(Ink.paper).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            for (agent in night.crew) CrewRow(agent, night)
        }

        if (!model.gemma) {
            PixelText("Without Gemma: the crew follows its instincts.", size = SMALL, color = Ink.candle)
        } else if (model.trouble != null) {
            PixelText("Gemma did not answer, so instinct moved them: ${model.trouble}", size = SMALL, color = Ink.candle, maxLines = 3)
        }
        model.failure?.let { PixelText(it, size = SMALL, color = Ink.red, maxLines = 3) }

        if (!night.over) {
            if (model.thinking) {
                PixelText(if (model.gemma) "Gemma is deciding for the crew…" else "The crew is deciding…", size = SMALL, color = Ink.faint)
                PixelProgress(frame)
            } else if (model.paused) {
                PixelText(
                    if (night.turn == 0) "The arrows are what they mean to do. Once the night starts, they walk every few seconds: answer in time." else "Paused.",
                    size = SMALL,
                    color = Ink.candle,
                    maxLines = 3,
                )
                PixelButton(if (night.turn == 0) "Start the night (Space)" else "Go on (Space)", Ink.orange, onClick = { model.pause(false) }, modifier = Modifier.fillMaxWidth())
            } else {
                PixelText(
                    if (night.played) "Done. Click it again to take it back." else "One treat or trick before they walk, or none.",
                    size = SMALL,
                    color = Ink.candle,
                    maxLines = 2,
                )
                Fuse(model.waited)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton("Walk now (Enter)", Ink.orange, onClick = model::next, small = true)
                    PixelButton("Pause (Space)", Ink.paperDark, onClick = { model.pause(true) }, small = true)
                }
            }
        }
        if (night.marigolds > 0 && model.turnsBack.isNotEmpty() && !model.thinking && night.crew.any { it.fate == Fate.DEAD }) {
            PixelText("A marigold takes the night back to turn…", size = SMALL, color = Ink.marigold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (turn in model.turnsBack.take(8)) PixelButton("${turn + 1}", Ink.marigold, onClick = { model.remember(turn) }, small = true)
            }
        }

        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in night.log.takeLast(4)) PixelText(line, size = SMALL, color = Ink.faint, maxLines = 2)
        }
    }
}

/** The time left to answer: a fuse that burns down, red at the end. */
@Composable
private fun Fuse(waited: Float) {
    Box(
        Modifier.fillMaxWidth().height(14.dp).pixelBox(Ink.field, raised = false).drawBehind {
            val left = (1 - waited).coerceIn(0f, 1f)
            val color = if (left < 0.35f) Ink.red else Ink.candle
            drawRect(color, size = size.copy(width = size.width * left))
        },
    )
}

/** How brave one of the crew still is: a bar that goes red when it may panic. */
@Composable
private fun Nerve(nerve: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PixelText("nerve", size = SMALL, color = Ink.faint)
        Box(
            Modifier.width(120.dp).height(8.dp).drawBehind {
                drawRect(Ink.field)
                val color = when {
                    nerve < PANIC -> Ink.red
                    nerve < 45 -> Ink.orange
                    else -> Ink.green
                }
                drawRect(color, size = size.copy(width = size.width * nerve / 100f))
            },
        )
        if (nerve < PANIC) PixelText("may panic", size = SMALL, color = Ink.red)
    }
}

@Composable
private fun Supply(sprite: Sprite, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(30.dp).drawBehind { draw(sprite, center, 3.dp.toPx()) })
        PixelText("×$count", color = if (count == 0) Ink.off else Ink.text)
    }
}

@Composable
private fun CrewRow(agent: Agent, night: Night) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        val sprite = if (agent.fate == Fate.DEAD) Sprites.ghost else Sprites.agent.tinted(agent.role.color)
        Box(Modifier.size(32.dp).drawBehind { draw(sprite, center, 3.5.dp.toPx(), if (agent.fate == Fate.DEAD) 0.6f else 1f) })
        Column {
            val state =
                when {
                    agent.fate == Fate.SAVED -> "out!"
                    agent.fate == Fate.DEAD -> "taken by ${agent.death}"
                    night.over -> "kept by the maze"
                    else -> null
                }
            PixelText(agent.role.title + (state?.let { ": $it" } ?: ""), color = agent.role.color, maxLines = 1)
            if (agent.fate == Fate.LOST && !night.over) {
                PixelText(agent.role.trait + if (agent.dark > 0) " Its lantern is out." else "", size = SMALL, color = Ink.faint, maxLines = 2)
                Nerve(agent.nerve)
            }
        }
    }
}

/** The end of the night: an altar for the dead with their last words, and what to do next. */
@Composable
private fun Ofrenda(model: GameModel, modifier: Modifier) {
    val night = model.night
    val dead = night.crew.filter { it.fate == Fate.DEAD }
    Column(
        modifier.widthIn(max = 560.dp).pixelBox(Color(0xF0140E1C)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val title =
            when {
                night.saved == night.crew.size -> "Everyone got out"
                dead.isEmpty() -> "Dawn"
                else -> "Ofrenda"
            }
        PixelText(title, size = BIG, color = Ink.marigold)
        PixelText("${night.saved} of ${night.crew.size} out of the maze", color = Ink.text)
        if (dead.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(dead.size + 1) { i ->
                    val sprite = if (i % 2 == 0) Sprites.candle else Sprites.marigold
                    Box(Modifier.size(28.dp).drawBehind { draw(sprite, center, 3.dp.toPx()) })
                }
            }
            for (agent in dead) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelText("${agent.role.title}, taken by ${agent.death}", color = agent.role.color)
                    if (agent.words.isNotBlank()) PixelText("Last words: “${agent.words}”", size = SMALL, color = Ink.text)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelButton("Same night again", Ink.paperDark, onClick = model::again)
            val next = model.plan.next
            if (next != null) {
                PixelButton(if (next == Plan.ENDLESS) "Endless nights" else "Next night", Ink.orange, onClick = { model.play(next) })
            } else {
                PixelButton("Another night", Ink.orange, onClick = { model.play(Plan.ENDLESS) })
            }
        }
    }
}
