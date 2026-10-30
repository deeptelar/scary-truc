package dev.deeptelar.scarytruc

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * A call as a player sees it on a phone, a frame every tenth of a second, for the GIF of the README:
 * the lock, the guide, the cards, then the call, guided by [Balance]'s explorer. With a key file
 * Gemma plays the caller. It films nothing unless asked:
 * ./gradlew jvmTest --tests '*Film*' -Pshots=DIR [-Pnight=hedge] [-Pseed=1] [-Pturns=9] [-Pkeyfile=FILE]
 * and `frames.txt` in DIR is a list of the frames for ffmpeg's concat, each for as long as it lasted.
 */
class Film {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun film() = runBlocking {
        val out = File(System.getProperty("shots") ?: return@runBlocking)
        out.mkdirs()
        Sound.on = false
        val plan = Plan.valueOf((System.getProperty("night") ?: "hedge").uppercase())
        val seed = System.getProperty("seed")?.toLong() ?: 1
        val turns = System.getProperty("turns")?.toInt() ?: 9
        val minds = System.getProperty("keyfile")?.let { Server.OPENROUTER.copy(key = File(it).readText().trim()).minds(HttpClient(CIO)) } ?: Minds.none
        val model = CallModel(CoroutineScope(Dispatchers.Default), minds, plan = Plan.entries.first(), seed = seed, clock = false, locked = true)
        val scene = ImageComposeScene(780, 1688, Density(2f)) { Game(model) }
        var tap: Offset? = null
        var done = false
        val player =
            launch(Dispatchers.Default) {
                delay(2500)
                model.unlock()
                delay(3000)
                // Skip, the left softkey.
                tap = Offset(90f, 1620f)
                delay(1500)
                // The arrow under the cards, to the card of this call.
                repeat(Plan.entries.indexOf(plan)) {
                    tap = Offset(524f, 1426f)
                    delay(1400)
                }
                delay(1500)
                while (model.plan != plan) delay(10)
                model.answer()
                while (model.thinking) delay(10)
                delay(model.speaking() + 1500)
                val explorer = Balance()
                val tones = listOf("Okay. Slowly, go %s", "I'm with you. Go %s", "Don't stop. %s", "Quiet now. Go %s")
                var turn = 0
                while (turn < turns && !model.call.over) {
                    val said = explorer.explore(model.call, model.maze, model.haunt)
                    val way = listOf("ahead", "right", "back", "left").firstOrNull { said.endsWith(it) }
                    val line = way?.let { tones[turn % tones.size].format(it) } ?: said
                    for (i in 1..line.length) {
                        model.type(line.take(i))
                        delay(35)
                    }
                    delay(300)
                    model.send()
                    delay(20)
                    while (model.thinking) delay(10)
                    delay(model.speaking() + 1500)
                    turn++
                }
                delay(4000)
                File(out, "lines.txt").writeText(model.call.lines.joinToString("\n") { "${it.who}: ${it.text}" })
                done = true
            }
        val frames = StringBuilder()
        var frame = 0
        var last = System.nanoTime()
        val begin = last
        while (!done) {
            tap?.let {
                tap = null
                scene.sendPointerEvent(PointerEventType.Press, it)
                scene.sendPointerEvent(PointerEventType.Release, it)
            }
            val name = "frame-%05d.png".format(frame++)
            File(out, name).writeBytes(scene.render(System.nanoTime() - begin).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            delay((100 - (System.nanoTime() - last) / 1_000_000).coerceAtLeast(1))
            val now = System.nanoTime()
            frames.append("file '$name'\nduration ${(now - last) / 1e9}\n")
            last = now
        }
        File(out, "frames.txt").writeText(frames.toString())
        player.join()
        scene.close()
    }
}
