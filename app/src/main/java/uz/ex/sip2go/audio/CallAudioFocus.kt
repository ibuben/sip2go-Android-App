package uz.ex.sip2go.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/** Pauses other apps (music, podcasts) while a call is active or ringing. */
object CallAudioFocus {
    private const val TAG = "CallAudioFocus"

    private var held = false
    private var focusRequest: AudioFocusRequest? = null

    fun acquire(context: Context) {
        if (held) {
            return
        }
        val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { change ->
                    Log.d(TAG, "focus change=$change")
                }
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            held = true
            Log.d(TAG, "audio focus acquired")
        } else {
            Log.w(TAG, "audio focus denied (code=$result)")
        }
    }

    fun release(context: Context) {
        if (!held) {
            return
        }
        val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        held = false
        Log.d(TAG, "audio focus released")
    }
}
