@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.deeptelar.scarytruc

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/**
 * The microphone in a browser, through its speech recognition, as Foodie Route does it. Most
 * browsers turn the voice into text on their maker's servers (Chrome on Google's), so the
 * recording goes there and the page only gets the text. It listens in the language of the game.
 * Where there is none, as in Firefox, [available] is false.
 */
class WebMicrophone : Microphone {
    private var recognition: JsAny? = null

    override fun listen(lang: Lang, heard: (String) -> Unit, done: (String?) -> Unit) {
        stop()
        recognition =
            runCatching {
                // The language of the game, in the browser's own variant of it where it has one.
                val language = navigatorLanguage().takeIf { Lang.of(it) == lang } ?: if (lang == Lang.ES) "es-ES" else "en-US"
                start(language, heard) { error ->
                    recognition = null
                    done(
                        when (error) {
                            "not-allowed", "service-not-allowed" -> "The browser didn't allow the microphone."
                            "network" -> "The browser's speech service can't be reached."
                            "audio-capture" -> "No microphone was found."
                            else -> null
                        },
                    )
                }
            }.getOrElse {
                done("This browser can't listen.")
                null
            }
    }

    override fun stop() {
        recognition?.let { runCatching { stop(it) } }
        recognition = null
    }

    companion object {
        val available: Boolean get() = runCatching { supported() }.getOrDefault(false)
    }
}

private fun supported(): Boolean = js("('SpeechRecognition' in window) || ('webkitSpeechRecognition' in window)")

// It stops at the first pause: a phone call is said a sentence at a time.
private fun start(language: String, heard: (String) -> Unit, ended: (String) -> Unit): JsAny =
    js(
        """{
        const recognition = new (window.SpeechRecognition || window.webkitSpeechRecognition)();
        recognition.lang = language;
        recognition.interimResults = true;
        recognition.continuous = false;
        let error = '';
        recognition.onresult = (event) => {
            let text = '';
            for (let i = 0; i < event.results.length; i++) text += event.results[i][0].transcript;
            heard(text.trim());
        };
        recognition.onerror = (event) => { error = event.error || ''; };
        recognition.onend = () => ended(error);
        recognition.start();
        return recognition;
    }""",
    )

/** The language the browser is set to, such as `es-ES`. */
internal fun navigatorLanguage(): String = js("navigator.language || 'en-US'")

private fun stop(recognition: JsAny): Unit = js("{ try { recognition.stop(); } catch (e) {} }")
