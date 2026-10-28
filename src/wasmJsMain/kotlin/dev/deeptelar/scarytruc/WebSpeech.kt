@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.deeptelar.scarytruc

import kotlin.js.ExperimentalWasmJsInterop

/**
 * The phone's speaker in a browser: its own speech, in English. Each of the crew has a pitch and a
 * pace, and a voice of their own when the browser has several; the Hollow is low and slow.
 */
class WebSpeech : Speech {
    override fun say(text: String, role: Role?) {
        val (pitch, rate) =
            when (role) {
                null -> 0.1 to 0.7
                Role.SCOUT -> 1.35 to 1.15
                Role.CODER -> 1.1 to 1.08
                Role.PLANNER -> 0.9 to 0.98
                Role.CRITIC -> 0.75 to 1.0
            }
        runCatching { speak(text, pitch, rate, role?.ordinal ?: 4) }
    }

    override fun stop() {
        runCatching { cancel() }
    }

    override fun unlock() {
        runCatching { speak(" ", 1.0, 1.0, 0) }
    }
}

private fun speak(text: String, pitch: Double, rate: Double, voice: Int): Unit =
    js(
        """{
        if (!window.speechSynthesis) return;
        const words = new SpeechSynthesisUtterance(text);
        words.pitch = pitch;
        words.rate = rate;
        const voices = speechSynthesis.getVoices().filter(v => v.lang && v.lang.startsWith('en'));
        if (voices.length) words.voice = voices[voice % voices.length];
        words.lang = 'en';
        speechSynthesis.speak(words);
    }""",
    )

private fun cancel(): Unit = js("{ if (window.speechSynthesis) speechSynthesis.cancel(); }")
