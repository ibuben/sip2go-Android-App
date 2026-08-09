package uz.ex.sip2go.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import uz.ex.sip2go.network.Protocol
import uz.ex.sip2go.recording.CallRecorder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class CallAudioSession(
    private val context: Context,
    private val callId: UUID,
    private val onSendAudio: (ByteArray) -> Unit,
    private val onRouteChanged: (CallAudioRoute) -> Unit = {},
) {
    private val codec = OpusCodec(sampleRate = 8000)
    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private val held = AtomicBoolean(false)
    private val dtmfTonePlayer = DtmfTonePlayer()
    private val silenceFrame: ShortArray by lazy { ShortArray(codec.frameSamples) }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var audioRouter: CallAudioRouter? = null
    @Volatile
    private var recorder: CallRecorder? = null

    fun setRecorder(value: CallRecorder?) {
        recorder = value
    }

    fun start(initialRoute: CallAudioRoute? = null) {
        if (!running.compareAndSet(false, true)) return

        val audioManager = context.getSystemService(AudioManager::class.java)
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioRouter = CallAudioRouter(context, onRouteChanged).also { it.start(initialRoute) }
        dtmfTonePlayer.start()

        val sampleRate = codec.sampleRate
        val channelIn = AudioFormat.CHANNEL_IN_MONO
        val channelOut = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minRecord = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        val minTrack = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
        val bufferSize = maxOf(minRecord, minTrack, codec.frameBytes * 8)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelIn,
            encoding,
            bufferSize,
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            stop()
            return
        }

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelOut)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()

        try {
            audioRecord?.startRecording()
            audioTrack?.play()
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone access denied — is FGS microphone active?", e)
            stop()
            return
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start audio capture", e)
            stop()
            return
        }

        captureThread = Thread({
            val frame = ShortArray(codec.frameSamples)
            var offset = 0
            while (running.get()) {
                val read = audioRecord?.read(frame, offset, frame.size - offset) ?: break
                if (read < 0) {
                    Log.e(TAG, "AudioRecord.read error=$read")
                    break
                }
                if (read == 0) continue
                offset += read
                if (offset < frame.size) continue
                try {
                    val pcm = when {
                        held.get() -> silenceFrame
                        muted.get() -> silenceFrame
                        else -> frame
                    }
                    recorder?.onLocalFrame(pcm)
                    val opus = codec.encode(pcm)
                    onSendAudio(Protocol.packAudio(callId, opus))
                } catch (e: Exception) {
                    Log.e(TAG, "encode failed", e)
                }
                offset = 0
            }
        }, "siptg-audio-capture").also { it.start() }
    }

    fun playIncoming(opusPayload: ByteArray) {
        if (!running.get() || held.get()) return
        val track = audioTrack ?: return
        try {
            val pcm = codec.decode(opusPayload)
            recorder?.onRemoteFrame(pcm)
            synchronized(track) {
                track.write(pcm, 0, pcm.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "decode failed", e)
        }
    }

    fun applySpeakerRoute() {
        audioRouter?.applySpeakerRoute()
    }

    fun applyRoute(route: CallAudioRoute) {
        audioRouter?.applyRoute(route)
    }

    fun cycleOutputRoute(): CallAudioRoute {
        return audioRouter?.cycleOutputRoute() ?: CallAudioRoute.EARPIECE
    }

    fun hasExternalAudioDevice(): Boolean {
        return audioRouter?.hasExternalAudioDevice() ?: false
    }

    fun isSpeakerForced(): Boolean {
        return audioRouter?.isSpeakerForced() ?: false
    }

    fun currentRoute(): CallAudioRoute {
        return audioRouter?.currentRoute() ?: CallAudioRoute.EARPIECE
    }

    fun playDtmfTone(digit: Char) {
        dtmfTonePlayer.play(digit)
    }

    fun setMuted(value: Boolean) {
        muted.set(value)
    }

    fun isMuted(): Boolean = muted.get()

    fun setHeld(value: Boolean) {
        held.set(value)
    }

    fun isHeld(): Boolean = held.get()

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        muted.set(false)
        held.set(false)
        captureThread?.interrupt()
        captureThread = null
        audioRecord?.run {
            stop()
            release()
        }
        audioRecord = null
        audioTrack?.run {
            stop()
            release()
        }
        audioTrack = null
        audioRouter?.stop()
        audioRouter = null
        dtmfTonePlayer.stop()
        context.getSystemService(AudioManager::class.java).mode = previousAudioMode
    }

    companion object {
        private const val TAG = "CallAudioSession"
    }
}
