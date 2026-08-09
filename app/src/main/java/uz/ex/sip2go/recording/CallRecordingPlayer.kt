package uz.ex.sip2go.recording

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

object CallRecordingPlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    fun play(context: Context, path: String, onComplete: () -> Unit = {}) {
        stop()
        val file = File(path)
        if (!file.exists()) {
            onComplete()
            return
        }
        val appContext = context.applicationContext
        Thread({
            try {
                val mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(path)
                    setOnCompletionListener {
                        mainHandler.post {
                            stop()
                            onComplete()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        mainHandler.post {
                            stop()
                            onComplete()
                        }
                        true
                    }
                    prepare()
                }
                mainHandler.post {
                    player = mediaPlayer
                    mediaPlayer.start()
                }
            } catch (_: Exception) {
                mainHandler.post(onComplete)
            }
        }, "siptg-recording-playback").start()
    }

    fun stop() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
    }

    fun isPlaying(): Boolean = player?.isPlaying == true
}
