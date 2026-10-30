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
 * asked: ./gradlew jvmTest --tests '*Screenshots*' -Pshots=DIR [-Pnight=cave] [-Pseed=7] [-Pturns=0,4,9] [-Pguide]
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
        // The page as it opens, locked until a tap.
        val locked = ImageComposeScene(1170, 2532, Density(3f)) { Game(CallModel(CoroutineScope(Dispatchers.Default), Minds.none, plan = plan, seed = seed, clock = false, locked = true)) }
        File(out, "locked-phone.png").writeBytes(locked.render(0L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        locked.close()
        shoot("ringing")
        // The pages of how to save them, as a phone shows them, in both frames of their pictures.
        if (System.getProperty("guide") != null) {
            for (page in 0 until 7) {
                for (frame in 0..1) {
                    val scene = ImageComposeScene(1170, 2532, Density(3f)) { GuidePage(page, frame * 700L) }
                    File(out, "guide-${page + 1}-$frame.png").writeBytes(scene.render(0L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                    scene.close()
                }
            }
        }
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
