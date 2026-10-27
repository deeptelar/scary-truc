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
        val run = NightRun(Plan.FOURTH, 3292, Gemma(model) { if (it != null) println("PROBE trouble $it") })
        run.begin()
        run.night.moves.forEach { println("PROBE   intent ${it.role.title} ${it.go}: \"${it.says}\"") }
        repeat(6) { t ->
            // A scream on the cell each one means to walk into, in turn: how do they take it?
            val target = run.night.moves.getOrNull(t % run.night.moves.size)?.let { m -> run.night.lost.firstOrNull { it.role == m.role }?.let { a -> m.go?.let { a.at + it } } }
            if (target != null && run.place(target, MarkKind.TRICK)) {
                val before = run.night.moves
                val after = run.rethink(target).moves
                after.zip(before).filter { it.first != it.second }.forEach { (a, b) -> println("PROBE   SCREAM ${a.role.title}: ${b.go} -> ${a.go}: \"${a.says}\" nerve ${run.night.crew.first { it.role == a.role }.nerve}") }
            }
                        val took = measureTime { run.next() }
            println("PROBE turn ${t + 1} took $took")
            run.night.crew.forEach { println("PROBE   ${it.role.title} at ${it.at} ${it.fate}") }
            run.night.moves.forEach { println("PROBE   intent ${it.role.title} ${it.go}: \"${it.says}\"") }
        }
    }
}
