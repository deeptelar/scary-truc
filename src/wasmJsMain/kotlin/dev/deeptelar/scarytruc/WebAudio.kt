@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.deeptelar.scarytruc

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.math.pow

/**
 * The sounds in a browser, through Web Audio: a buffer per cue and per drone, made when the player
 * first answers, because a browser plays sound only after a tap.
 */
class WebAudio : SoundOutput {
    private var context: JsAny? = null
    private val buffers = mutableMapOf<Cue, JsAny>()
    private val droneBuffers = mutableMapOf<Drone, JsAny>()
    private var droning: Drone? = null
    private var source: JsAny? = null

    /** Opens the sound, from inside a tap. */
    fun unlock() {
        val context = context ?: newContext().also { context = it }
        resume(context)
        // Every buffer now, so that the first scream doesn't wait to be made.
        for (cue in Cue.entries) buffers.getOrPut(cue) { buffer(Sound.samples(cue)) }
    }

    private fun buffer(samples: FloatArray): JsAny {
        val buffer = newBuffer(context!!, samples.size, Sound.RATE.toDouble())
        for (i in samples.indices) setSample(buffer, i, samples[i].toDouble())
        return buffer
    }

    override fun play(cue: Cue, gain: Float) {
        val context = context ?: return
        val buffer = buffers.getOrPut(cue) { buffer(Sound.samples(cue)) }
        start(context, buffer, decibels(gain), false)
    }

    override fun drone(drone: Drone?) {
        val context = context ?: return
        if (drone == droning) return
        source?.let { stop(it) }
        source = null
        droning = drone
        if (drone != null) {
            val buffer = droneBuffers.getOrPut(drone) { buffer(Sound.samples(drone)) }
            source = start(context, buffer, decibels(-14f), true)
        }
    }

    private fun decibels(gain: Float): Double = 10.0.pow(gain / 20.0)
}

private fun newContext(): JsAny = js("new (window.AudioContext || window.webkitAudioContext)()")

private fun resume(context: JsAny): Unit = js("{ if (context.state !== 'running') context.resume(); }")

private fun newBuffer(context: JsAny, length: Int, rate: Double): JsAny = js("context.createBuffer(1, length, rate)")

private fun setSample(buffer: JsAny, index: Int, value: Double): Unit = js("{ buffer.getChannelData(0)[index] = value; }")

private fun start(context: JsAny, buffer: JsAny, gain: Double, loop: Boolean): JsAny =
    js(
        """{
        const source = context.createBufferSource();
        source.buffer = buffer;
        source.loop = loop;
        const volume = context.createGain();
        volume.gain.value = gain;
        source.connect(volume).connect(context.destination);
        source.start();
        return source;
    }""",
    )

private fun stop(source: JsAny): Unit = js("{ try { source.stop(); } catch (e) {} }")
