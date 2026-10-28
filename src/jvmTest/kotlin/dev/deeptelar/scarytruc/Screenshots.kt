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
 * Pictures of the window drawn off screen, for the README and the post: the phone ringing, then the
 * call after each of the given turns, guided by [Balance]'s explorer. It draws nothing unless
 * asked: ./gradlew jvmTest --tests '*Screenshots*' -Pshots=DIR [-Pnight=cave] [-Pseed=7] [-Pturns=0,4,9]
 */
class Screenshots {
    @Test
    fun shots() = runBlocking {
        Sound.on = false
        val out = File(System.getProperty("shots") ?: return@runBlocking)
        val plan = Plan.valueOf((System.getProperty("night") ?: "cave").uppercase())
        val seed = System.getProperty("seed")?.toLong() ?: 1
        val turns = (System.getProperty("turns") ?: "0,4,9").split(",").map { it.toInt() }
        val model = CallModel(CoroutineScope(Dispatchers.Default), Minds.none, plan = plan, seed = seed, clock = false)
        // The window on a desktop, and the page on a phone 390 wide, at its density.
        fun shoot(name: String) {
            for ((size, suffix) in listOf(Triple(1240, 800, 1f) to "", Triple(1170, 2532, 3f) to "-phone")) {
                val scene = ImageComposeScene(size.first, size.second, Density(size.third)) { Game(model) }
                repeat(3) { scene.render(it * 16_000_000L) }
                File(out, "${plan.name.lowercase()}-$name$suffix.png").writeBytes(scene.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                scene.close()
            }
        }
        shoot("ringing")
        model.answer()
        while (model.thinking) delay(10)
        val explorer = Balance()
        for (t in turns) {
            while (model.call.turn < t && !model.call.over) {
                model.type(explorer.explore(model.call, model.maze, model.haunt))
                model.send()
                delay(20)
                while (model.thinking) delay(10)
            }
            // Let the last line finish appearing.
            delay(model.speaking() + 100)
            shoot("turn${model.call.turn}")
        }
    }
}
