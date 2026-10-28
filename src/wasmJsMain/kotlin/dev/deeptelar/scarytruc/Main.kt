@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.deeptelar.scarytruc

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The game in a browser. The caller has no model until the player connects one in the settings:
 * their Ollama, when the page and Ollama are both on http, or a hosted Gemma with a key.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val scope = MainScope()
    val audio = WebAudio()
    Sound.output = audio
    Sound.start(scope)
    val store = LocalStore()
    val game = CallModel(scope, store = store)
    game.saved()?.let { game.use(it) }
    game.onAnswer = audio::unlock
    game.speech = WebSpeech()
    if (WebMicrophone.available) game.microphone = WebMicrophone()
    ComposeViewport(document.body!!) {
        Game(game)
    }
}

/** Keeps values in the browser's `localStorage`, so they survive a reload. */
private class LocalStore : Store {
    override fun get(key: String): String? = localGet("scary-truc.$key")

    override fun set(key: String, value: String) = localSet("scary-truc.$key", value)
}

private fun localGet(key: String): String? = js("window.localStorage.getItem(key)")

private fun localSet(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")
