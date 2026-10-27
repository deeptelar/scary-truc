package dev.deeptelar.scarytruc
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
/**
 * Pictures of the window drawn off screen, for the README and the post. It draws nothing unless
 * asked: ./gradlew test --tests '*Screenshots*' -Pshots=DIR [-Pseed=7] [-Pturns=0,6,14]
 */
class Screenshots {
    @Test fun shots() = runBlocking {
        val out = File(System.getProperty("shots") ?: return@runBlocking)
        val seed = (System.getProperty("seed") ?: "7").toLong()
        val turns = (System.getProperty("turns") ?: "0,6,14").split(",").map { it.toInt() }
        val model = GameModel(CoroutineScope(Dispatchers.Default), seed, { Instinct }, gemma = false)
        while (model.thinking) delay(10)
        var done = 0
        for (t in turns) {
            while (done < t && !model.night.over) {
                if (done == 2) { model.click(Pos(3, 1), MarkKind.TREAT); model.click(Pos(1, 3), MarkKind.TRICK) }
                model.next(); while (model.thinking) delay(10); done++
            }
            val scene = ImageComposeScene(1240, 800, Density(1f)) { Game(model) }
            repeat(3) { scene.render(it * 16_000_000L) }
            File(out, "seed$seed-turn$t.png").writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
