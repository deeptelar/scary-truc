@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.deeptelar.scarytruc

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise

private const val SALT_BYTES = 16
private const val IV_BYTES = 12

/**
 * Try-out links in a browser, locked with AES from a word stretched by PBKDF2: the same lock as
 * Foodie Route's. The many rounds make trying words slow; a phone opens a link in about a second.
 */
class WebLinks : Links {
    override suspend fun lock(text: String, word: String): String {
        val salt = random(SALT_BYTES)
        val iv = random(IV_BYTES)
        return seal(text, derive(word, salt).await<JsAny>(), iv, salt).await<JsString>().toString()
    }

    override suspend fun unlock(code: String, word: String): String? {
        val all = unbase64(code) ?: return null
        if (length(all) <= SALT_BYTES + IV_BYTES) return null
        val key = derive(word, slice(all, 0, SALT_BYTES)).await<JsAny>()
        return open(slice(all, SALT_BYTES + IV_BYTES, length(all)), key, slice(all, SALT_BYTES, SALT_BYTES + IV_BYTES)).await<JsString?>()?.toString()
    }

    override fun link(code: String): String = address() + "#" + TryOut.PREFIX + code

    override fun copy(text: String) = copied(text)

    override fun send(link: String, message: String) = shared(link, message)
}

private fun random(count: Int): JsAny = js("crypto.getRandomValues(new Uint8Array(count))")

private fun slice(bytes: JsAny, from: Int, to: Int): JsAny = js("bytes.slice(from, to)")

private fun length(bytes: JsAny): Int = js("bytes.length")

private fun derive(word: String, salt: JsAny): Promise<JsAny> =
    js(
        """(async () => {
        const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(word), 'PBKDF2', false, ['deriveKey']);
        return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: 600000 }, material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
    })()""",
    )

private fun seal(text: String, key: JsAny, iv: JsAny, salt: JsAny): Promise<JsString> =
    js(
        """(async () => {
        const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(text)));
        const all = new Uint8Array([...salt, ...iv, ...sealed]);
        return btoa(String.fromCharCode(...all)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+${'$'}/, '');
    })()""",
    )

/** What [sealed] says, or null with the wrong key. */
private fun open(sealed: JsAny, key: JsAny, iv: JsAny): Promise<JsString?> =
    js(
        """(async () => {
        try {
            return new TextDecoder().decode(await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, sealed));
        } catch (e) {
            return null;
        }
    })()""",
    )

private fun unbase64(code: String): JsAny? =
    js(
        """{
        try {
            const text = code.trim().replace(/-/g, '+').replace(/_/g, '/');
            return Uint8Array.from(atob(text + '='.repeat((4 - text.length % 4) % 4)), c => c.charCodeAt(0));
        } catch (e) {
            return null;
        }
    }""",
    )

private fun address(): String = js("location.origin + location.pathname")

private fun copied(text: String): Unit = js("{ navigator.clipboard.writeText(text).catch(e => console.error('copy failed: ' + e)); }")

private fun shared(link: String, message: String): Unit =
    js(
        """{
        if (navigator.share) navigator.share({ text: message, url: link }).catch(e => { if (e.name !== 'AbortError') console.error('share failed: ' + e); });
        else navigator.clipboard.writeText(link).catch(e => console.error('copy failed: ' + e));
    }""",
    )
