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
    private val droneBuffers = mutableMapOf<Drone, Pair<FloatArray, JsAny>>()
    private var droning: FloatArray? = null
    private var source: JsAny? = null
    /** The last source of each cue, to cut it short. */
    private val playing = mutableMapOf<Cue, JsAny>()

    /** Opens the sound, from inside a tap. */
    fun unlock() {
        val context = context ?: newContext().also { context = it }
        resume(context)
        // Every buffer now, so that the first scream doesn't wait to be made; the keys once read.
        for (cue in Cue.entries) if (Sound.ready(cue)) buffers.getOrPut(cue) { buffer(Sound.samples(cue)) }
    }

    private fun buffer(samples: FloatArray): JsAny {
        val buffer = newBuffer(context!!, samples.size, Sound.RATE.toDouble())
        for (i in samples.indices) setSample(buffer, i, samples[i].toDouble())
        return buffer
    }

    override fun play(cue: Cue, gain: Float) {
        val context = context ?: return
        val buffer = buffers.getOrPut(cue) { buffer(Sound.samples(cue)) }
        playing[cue] = start(context, buffer, decibels(gain), false)
    }

    override fun stop(cue: Cue) {
        playing.remove(cue)?.let { stop(it) }
    }

    override fun drone(drone: Drone?) {
        val context = context ?: return
        // A drone's samples change once its recording is read: then its loop is made again.
        val samples = drone?.let { Sound.samples(it) }
        if (samples === droning) return
        source?.let { stop(it) }
        source = null
        droning = samples
        if (drone != null && samples != null) {
            val buffer = droneBuffers[drone]?.takeIf { it.first === samples }?.second ?: buffer(samples).also { droneBuffers[drone] = samples to it }
            source = start(context, buffer, decibels(-8f), true)
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
