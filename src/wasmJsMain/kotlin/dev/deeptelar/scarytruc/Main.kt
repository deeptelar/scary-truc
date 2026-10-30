@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.deeptelar.scarytruc

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The game in a browser. The caller has no model until the player connects one in the settings:
 * their Ollama, when the page and Ollama are both on http, or a hosted Gemma with a key.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    var failed: (Throwable) -> Unit = {}
    val scope = MainScope() + CoroutineExceptionHandler { _, error -> failed(error) }
    val audio = WebAudio()
    Sound.output = audio
    Sound.start(scope)
    val store = LocalStore()
    // In the browser's language the first time, Spanish or English; the player can change it.
    val game = CallModel(scope, store = store, locked = true, lang = Lang.of(navigatorLanguage()))
    failed = { game.log.error("game", it) }
    // From here the page's own net hands what it catches to the log, see index.html.
    onFault { game.log.fault("page", it) }
    game.log.trace("visit: ${visit()}")
    onHidden { hidden -> game.log.trace(if (hidden) "page hidden" else "page shown") }
    game.saved()?.let { game.use(it) }
    game.links = WebLinks()
    game.tryCode = tryCode()
    game.onAnswer = audio::unlock
    game.export = ::handOver
    // A browser plays sound only after a tap: the phone opens locked, and the first tap anywhere
    // opens the sound too.
    onGesture(audio::unlock)
    // Some browsers let a page they know play at once: there the dark loop is heard on the lock screen.
    audio.unlock()
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

private fun onGesture(open: () -> Unit): Unit =
    js("{ for (const type of ['pointerdown', 'pointerup', 'touchend', 'keydown']) window.addEventListener(type, () => open(), { capture: true }); }")

/** Offers a text file to the phone's share sheet, or downloads it where there is none. */
private fun handOver(name: String, text: String): Unit =
    js(
        """{
        const file = new File([text], name, { type: 'text/plain' });
        const download = () => {
            const link = document.createElement('a');
            link.href = URL.createObjectURL(file);
            link.download = name;
            document.body.appendChild(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(link.href), 10000);
        };
        if (navigator.canShare && navigator.canShare({ files: [file] })) navigator.share({ files: [file] }).catch(e => { if (e.name !== 'AbortError') { console.error('share failed: ' + e); download(); } });
        else download();
    }""",
    )

/** Takes the code of a try-out link out of the address, so that the history and a screenshot do not keep it. */
private fun tryCode(): String? =
    js("{ const hash = location.hash; if (!hash.startsWith('#try=')) return null; history.replaceState(null, '', location.pathname + location.search); return hash.slice(5); }")

private fun onFault(report: (String) -> Unit): Unit = js("{ window.scaryTrucFault = text => report(text); }")

private fun onHidden(report: (Boolean) -> Unit): Unit = js("document.addEventListener('visibilitychange', () => report(document.hidden))")

/** Which build runs and on what: the build is written in the page when it is deployed. */
private fun visit(): String =
    js("`build ${'$'}{document.querySelector('meta[name=build]')?.content}, ${'$'}{navigator.userAgent}, ${'$'}{innerWidth}x${'$'}{innerHeight}, ${'$'}{navigator.language}, ${'$'}{navigator.onLine ? 'online' : 'offline'}`")

private fun localGet(key: String): String? = js("window.localStorage.getItem(key)")

private fun localSet(key: String, value: String): Unit = js("window.localStorage.setItem(key, value)")
