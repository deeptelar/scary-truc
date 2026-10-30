package dev.deeptelar.scarytruc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A model to play with until [until], as a friend shares it: what a try-out link carries, locked
 * with a secret word. The link has it after the `#`, which a browser sends to no server, not even
 * to the one of the page.
 *
 * The lock keeps a forwarded link from working without the word, and no more: a word is easy to
 * guess for whoever tries many, and the key has to reach the browser that plays. What stops a key
 * is the provider's limit on it, and deleting it.
 *
 * @property until when the page forgets the key, in milliseconds since 1970.
 */
@Serializable
data class TryOut(val url: String, val model: String, val key: String, val until: Long) {
    /** This as the text to lock. */
    fun text(): String = Json.encodeToString(this)

    companion object {
        /** What starts the part of an address after the `#` that is a try-out. */
        const val PREFIX = "try="

        /** The try-out in [text], or `null` if it isn't one. */
        fun of(text: String): TryOut? = runCatching { Json.decodeFromString<TryOut>(text) }.getOrNull()

        /** The word as it may be typed: "Hollow ", "hollow" and "hóllow" are one word. */
        fun secret(word: String): String =
            word.trim().lowercase().replace(Regex("\\s+"), " ").map { ACCENTS[it] ?: it }.joinToString("")

        private val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')
    }
}

/** What a page with an address does for a try-out link. */
interface Links {
    /** [text] locked with [word], to go in an address. */
    suspend fun lock(text: String, word: String): String

    /** What [code] locks, or `null` when [word] is not its word or the code was cut short. */
    suspend fun unlock(code: String, word: String): String?

    /** The address of this page with [code] in it. */
    fun link(code: String): String

    fun copy(text: String)

    /** Hands [link] over with the [message], where the device can; or copies it. */
    fun send(link: String, message: String)
}
