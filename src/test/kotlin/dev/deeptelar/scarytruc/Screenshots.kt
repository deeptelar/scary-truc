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
        Sound.on = false
        val out = File(System.getProperty("shots") ?: return@runBlocking)
        val plan = Plan.entries[(System.getProperty("night") ?: "5").toInt() - 1]
        val seed = System.getProperty("seed")?.toLong()
        val turns = (System.getProperty("turns") ?: "0,6,14").split(",").map { it.toInt() }
        val model = GameModel(CoroutineScope(Dispatchers.Default), { Instinct }, gemma = false, plan = plan, seed = seed, clock = false)
        while (model.thinking) delay(10)
        var done = 0
        for (t in turns) {
            // -Pturns=-1 stops on the first turn the Hollow whispers.
            while ((t < 0 && model.night.whisper?.turn != model.night.turn || done < t) && !model.night.over) {
                
                model.next(); while (model.thinking) delay(10); done++
            }
            val scene = ImageComposeScene(1240, 800, Density(1f)) { Game(model) }
            repeat(3) { scene.render(it * 16_000_000L) }
            File(out, "${plan.name.lowercase()}-${model.maze.seed}-turn$t.png").writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
