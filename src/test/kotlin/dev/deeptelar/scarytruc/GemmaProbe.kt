package dev.deeptelar.scarytruc
import dev.deeptelar.telar.openai.OpenAiChatModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime
class GemmaProbe {
    @Test fun probe() = runBlocking {
        if (System.getProperty("shots") != "gemma") return@runBlocking
        val model = OpenAiChatModel.ollama(HttpClient(CIO), model = "gemma4:12b", baseUrl = "http://127.0.0.1:11434/v1",
            parameters = buildJsonObject { put("reasoning_effort", "none") }, timeout = 5.minutes)
        val run = NightRun(Plan.FOURTH, 3298, Gemma(model) { if (it != null) println("PROBE trouble $it") })
        run.begin()
        run.night.moves.forEach { println("PROBE   intent ${it.role.title} ${it.go}: \"${it.says}\"") }
        repeat(5) { t ->
                        val took = measureTime { run.next() }
            println("PROBE turn ${t + 1} took $took")
            run.night.crew.forEach { println("PROBE   ${it.role.title} at ${it.at} ${it.fate}") }
            run.night.moves.forEach { println("PROBE   intent ${it.role.title} ${it.go}: \"${it.says}\"") }
        }
    }
}
