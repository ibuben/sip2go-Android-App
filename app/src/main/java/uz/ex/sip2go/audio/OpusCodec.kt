package uz.ex.sip2go.audio

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder

class OpusCodec(
    sampleRate: Int = 8000,
    frameMs: Int = 20,
) {
    val sampleRate: Int = sampleRate
    val frameSamples: Int = sampleRate * frameMs / 1000
    val frameBytes: Int = frameSamples * 2

    private val encoder = OpusEncoder(sampleRate, 1, OpusApplication.OPUS_APPLICATION_VOIP)
    private val decoder = OpusDecoder(sampleRate, 1)
    private val encodeBuffer = ByteArray(4000)

    fun encode(pcm: ShortArray): ByteArray {
        val frame = pcm.copyOf(frameSamples)
        val length = encoder.encode(frame, 0, frameSamples, encodeBuffer, 0, encodeBuffer.size)
        return encodeBuffer.copyOf(length)
    }

    fun decode(packet: ByteArray): ShortArray {
        val pcm = ShortArray(frameSamples)
        val samples = decoder.decode(packet, 0, packet.size, pcm, 0, frameSamples, false)
        return pcm.copyOf(samples)
    }
}
