package dev.deeptelar.scarytruc

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.time.measureTimedValue

/**
 * A call played against the real model on OpenRouter, in English and in Spanish, to read what the
 * caller says and how long it takes. It runs only when given a file with a key:
 * ./gradlew jvmTest --tests '*Live*' -Pkeyfile=FILE
 */
class Live {
    @Test
    fun calls() = runBlocking {
        val key = File(System.getProperty("keyfile") ?: return@runBlocking).readText().trim()
        val minds = Server.OPENROUTER.copy(key = key).minds(HttpClient(CIO))
        val said =
            mapOf(
                Lang.EN to listOf("hi, are you okay?", "go left, slowly", "what do you see?", "ve a la izquierda", "right", "hide, I'm here with you", "keep going, you're doing great"),
                Lang.ES to listOf("hola, ¿estás bien?", "ve a la izquierda, tranquila", "¿qué ves?", "go left please", "derecha", "escóndete, estoy contigo", "sigue recto, lo estás haciendo genial"),
            )
        for ((lang, lines) in said) {
            val run = CallRun(Plan.CAVE, seed = 3, ears = minds.ears { if (it != null) println("TROUBLE $it") }, voice = minds.voice, whisperer = minds.whisperer, lang = lang)
            val (first, took) = measureTimedValue { run.begin() }
            println("[$lang] CALLER (${took.inWholeMilliseconds} ms): ${first.lines.last().text}")
            for (line in lines) {
                val (call, time) = measureTimedValue { run.say(line) }
                println("[$lang] YOU: $line")
                println("[$lang]   heard ${call.heard ?: "-"}")
                call.lines.takeLastWhile { it.who != Who.YOU }.forEach { println("[$lang] ${it.who} (${time.inWholeMilliseconds} ms): ${it.text}") }
                if (call.over) break
            }
        }
    }
}
