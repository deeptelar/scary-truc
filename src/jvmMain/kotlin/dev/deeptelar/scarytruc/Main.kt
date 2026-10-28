package dev.deeptelar.scarytruc

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The model of the caller: `SCARY_TRUC_MODEL`, or Gemma 4 12B. */
val modelName: String = System.getenv("SCARY_TRUC_MODEL") ?: "gemma4:12b"

/**
 * `scary-truc [--call cave|woods|cube|tape|hedge|ship] [--no-gemma] [--mute]`: the phone rings. With Ollama running
 * and the model pulled, Gemma plays the caller; otherwise, or with `--no-gemma`, the caller knows
 * a few words and says what they see as it is. The closest each call came to the way out is kept in
 * `~/.scary-truc/progress`.
 */
fun main(args: Array<String>) {
    val plan = args.valueOf("--call")?.let { name -> Plan.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } } ?: Plan.CAVE
    val client = HttpClient(CIO)
    val ollama = ollamaUrl(System.getenv("OLLAMA_HOST"))
    val off = "--no-gemma" in args || "--instinct" in args
    val gemma = !off && runBlocking { pulled(client, ollama, modelName) }
    if (!gemma && !off) {
        println("Gemma is not here ($modelName at $ollama), so the caller knows only a few words. To give them a mind: ollama pull $modelName")
    }
    val model =
        OpenAiChatModel.ollama(
            client,
            model = modelName,
            baseUrl = "$ollama/v1",
            // A phone call needs a quick answer, not a long thought.
            parameters = buildJsonObject { put("reasoning_effort", "none") },
            timeout = 2.minutes,
        )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    Sound.output = JavaSound()
    Sound.on = "--mute" !in args
    Sound.start(scope)
    val home = Path.of(System.getProperty("user.home"), ".scary-truc")
    val game = CallModel(scope, if (gemma) Minds.of(model, modelName) else Minds.none, plan, store = FileStore(home))
    application {
        val state = rememberWindowState(size = DpSize(1240.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Scary Truc",
            onKeyEvent = { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    game.ringing && (event.key == Key.Enter || event.key == Key.Spacebar) -> {
                        game.answer()
                        true
                    }
                    game.call.over && !game.thinking && event.key == Key.Enter -> {
                        game.again()
                        true
                    }
                    event.key == Key.M && event.isCtrlPressed -> {
                        Sound.on = !Sound.on
                        true
                    }
                    else -> false
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

/** Keeps each value in a file of its own, in [folder]. */
private class FileStore(private val folder: Path) : Store {
    override fun get(key: String): String? = folder.resolve(key).takeIf { Files.exists(it) }?.let { Files.readString(it) }

    override fun set(key: String, value: String) {
        Files.createDirectories(folder)
        Files.writeString(folder.resolve(key), value)
    }
}
