package dev.deeptelar.scarytruc

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.deeptelar.telar.openai.OpenAiChatModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The model of the crew: `SCARY_TRUC_MODEL`, or Gemma 4 12B. */
val modelName: String = System.getenv("SCARY_TRUC_MODEL") ?: "gemma4:12b"

/**
 * `scary-truc [--night 1-6] [--seed N] [--instinct]`: opens a night in the maze. With Ollama running
 * and the model pulled, Gemma plays the crew; otherwise, or with `--instinct`, they follow their
 * instincts. The best night of each plan is kept in `~/.scary-truc/progress`.
 */
fun main(args: Array<String>) {
    val seed = args.valueOf("--seed")?.toLongOrNull()
    val plan = args.valueOf("--night")?.toIntOrNull()?.let { Plan.entries.getOrNull(it - 1) } ?: if (seed != null) Plan.ENDLESS else Plan.FIRST
    val client = HttpClient(CIO)
    val ollama = ollamaUrl(System.getenv("OLLAMA_HOST"))
    val gemma = "--instinct" !in args && runBlocking { pulled(client, ollama, modelName) }
    if (!gemma && "--instinct" !in args) {
        println("Gemma is not here ($modelName at $ollama), so the crew follows its instincts. To give them minds: ollama pull $modelName")
    }
    val model =
        OpenAiChatModel.ollama(
            client,
            model = modelName,
            baseUrl = "$ollama/v1",
            // A turn needs a quick answer, not a long thought.
            parameters = buildJsonObject { put("reasoning_effort", "none") },
            timeout = 2.minutes,
        )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val home = Path.of(System.getProperty("user.home"), ".scary-truc")
    val game = GameModel(scope, { trouble -> if (gemma) Gemma(model, trouble = trouble) else Instinct }, gemma, plan, seed, home.resolve("progress"))
    application {
        val state = rememberWindowState(size = DpSize(1240.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Scary Truc",
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Spacebar || event.key == Key.Enter)) {
                    if (!game.night.over) game.next() else game.play(game.plan.next ?: Plan.ENDLESS)
                    true
                } else {
                    false
                }
            },
        ) {
            Game(game)
        }
    }
}

private fun Array<String>.valueOf(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

/** Whether Ollama answers at [ollama] and has [model]. */
private suspend fun pulled(client: HttpClient, ollama: String, model: String): Boolean =
    withTimeoutOrNull(3.seconds) {
        runCatching { client.get("$ollama/api/tags").bodyAsText() }.getOrNull()?.contains("\"$model\"")
    } == true

/**
 * The address of Ollama for the value of `OLLAMA_HOST`: `127.0.0.1:11434` when it is empty, as
 * Ollama does. `0.0.0.0`, which tells Ollama to listen everywhere, is reached at `127.0.0.1`.
 */
internal fun ollamaUrl(host: String?): String {
    val value = host?.trim()?.trimEnd('/').orEmpty()
    if (value.isEmpty()) return "http://127.0.0.1:11434"
    val uri = URI(if ("://" in value) value else "http://$value")
    val port = if (uri.port != -1) uri.port else if ("://" !in value) 11434 else if (uri.scheme == "https") 443 else 80
    val name = uri.host?.takeUnless { it == "0.0.0.0" || it == "[::]" } ?: "127.0.0.1"
    return "${uri.scheme}://$name:$port"
}
