package uz.ex.sip2go.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Local ringback (gudki) while an outgoing call is ringing. */
class RingbackTonePlayer {
    private var toneGenerator: ToneGenerator? = null
    private var loopJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) {
            return
        }
        toneGenerator = createToneGenerator()
        if (toneGenerator == null) {
            Log.w(TAG, "Ringback tone generator unavailable")
            return
        }
        loopJob = scope.launch {
            while (isActive) {
                toneGenerator?.startTone(TONE_RINGBACK, TONE_MS)
                delay(PAUSE_MS)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        runCatching { toneGenerator?.stopTone() }
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun createToneGenerator(): ToneGenerator? {
        return runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, TONE_VOLUME)
        }.getOrNull() ?: runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "RingbackTonePlayer"
        /** Supervisory ringback tone id (ToneGenerator.TONE_SUP_RINGBACK = 23). */
        private const val TONE_RINGBACK = 23
        private const val TONE_VOLUME = 80
        private const val TONE_MS = 1200
        private const val PAUSE_MS = 3600L
    }
}
