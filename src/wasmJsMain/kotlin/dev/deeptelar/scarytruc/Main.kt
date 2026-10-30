@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.deeptelar.scarytruc

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeViewport
import dev.deeptelar.scarytruc.resources.PixelifySans
import dev.deeptelar.scarytruc.resources.Res
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The game in a browser. The caller has no model until the player connects one in the settings:
 * their Ollama, when the page and Ollama are both on http, or a hosted Gemma with a key.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    var failed: (Throwable) -> Unit = {}
    val scope = MainScope() + CoroutineExceptionHandler { _, error -> failed(error) }
    val audio = WebAudio()
    Sound.output = audio
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
    // In its own place, because Compose empties the element it gets: the loading screen is beside it.
    ComposeViewport(document.getElementById("game")!!) {
        // Every word on the phone is in this font: until it is here, the page's loading screen stays.
        val font by preloadFont(Res.font.PixelifySans, FontWeight.Normal, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(400)))
        var late by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(FONT_WAIT)
            late = true
        }
        if (font != null || late) {
            Game(game)
            LaunchedEffect(Unit) {
                // After the font, or the sounds, many and big, keep it waiting on a slow line.
                Sound.start(scope)
                repeat(DRAWN_FRAMES) { withFrameNanos {} }
                gone()
            }
        }
    }
}

private const val FONT_WAIT = 60_000L
private const val DRAWN_FRAMES = 40

/** Fades the page's loading screen away, once the game has drawn: the canvas may still come up white for a moment. */
private fun gone(): Unit =
    js("{ const loading = document.getElementById('loading'); loading.style.opacity = 0; setTimeout(() => loading.remove(), 800); }")

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
