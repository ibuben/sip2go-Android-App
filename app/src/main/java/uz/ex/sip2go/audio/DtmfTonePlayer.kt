package uz.ex.sip2go.audio

import android.media.AudioManager
import android.media.ToneGenerator

class DtmfTonePlayer {
    private var toneGenerator: ToneGenerator? = null

    fun start() {
        if (toneGenerator != null) {
            return
        }
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
        }.getOrNull()
    }

    fun play(digit: Char) {
        val tone = when (digit) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        toneGenerator?.startTone(tone, 120)
    }

    fun stop() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
