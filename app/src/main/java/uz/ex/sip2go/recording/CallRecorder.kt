package uz.ex.sip2go.recording

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class CallRecordingResult(
    val path: String,
    val durationSec: Int,
)

class CallRecorder(
    private val outputFile: File,
    private val sampleRate: Int = 8000,
) {
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val localQueue = ArrayBlockingQueue<ShortArray>(64)
    private val remoteQueue = ArrayBlockingQueue<ShortArray>(64)
    private var writerThread: Thread? = null
    private var randomAccess: RandomAccessFile? = null
    private var dataBytesWritten = 0L
    private val frameSamples = sampleRate / 50 // 20 ms @ 8 kHz

    fun start() {
        if (!running.compareAndSet(false, true)) return
        paused.set(false)
        outputFile.parentFile?.mkdirs()
        randomAccess = RandomAccessFile(outputFile, "rw").also { file ->
            writeEmptyWavHeader(file)
        }
        writerThread = Thread({ writerLoop() }, "siptg-call-recorder").also { it.start() }
    }

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
    }

    fun isPaused(): Boolean = paused.get()

    fun onLocalFrame(frame: ShortArray) {
        if (!running.get() || paused.get()) return
        localQueue.offer(frame.copyOf())
    }

    fun onRemoteFrame(frame: ShortArray) {
        if (!running.get() || paused.get()) return
        remoteQueue.offer(frame.copyOf())
    }

    fun stop(): CallRecordingResult? {
        if (!running.compareAndSet(true, false)) return null
        writerThread?.interrupt()
        writerThread = null
        localQueue.clear()
        remoteQueue.clear()

        val file = randomAccess ?: return null
        randomAccess = null
        return try {
            finalizeWavHeader(file, dataBytesWritten)
            file.close()
            val durationSec = (dataBytesWritten / (2L * sampleRate)).toInt().coerceAtLeast(0)
            if (dataBytesWritten <= 0L) {
                outputFile.delete()
                null
            } else {
                CallRecordingResult(outputFile.absolutePath, durationSec.coerceAtLeast(1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize recording", e)
            outputFile.delete()
            null
        }
    }

    private fun writerLoop() {
        val silence = ShortArray(frameSamples)
        while (running.get()) {
            try {
                val local = localQueue.poll(25, TimeUnit.MILLISECONDS) ?: silence
                val remote = remoteQueue.poll(0, TimeUnit.MILLISECONDS) ?: silence
                val mixed = mixFrames(local, remote)
                writePcm(mixed)
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Recorder writer error", e)
            }
        }
    }

    private fun mixFrames(local: ShortArray, remote: ShortArray): ShortArray {
        val count = minOf(local.size, remote.size, frameSamples)
        val out = ShortArray(count)
        for (i in 0 until count) {
            val sum = local[i].toInt() + remote[i].toInt()
            out[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun writePcm(samples: ShortArray) {
        val file = randomAccess ?: return
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort(it) }
        file.write(buffer.array())
        dataBytesWritten += buffer.array().size
    }

    private fun writeEmptyWavHeader(file: RandomAccessFile) {
        file.setLength(0)
        file.write(ByteArray(WAV_HEADER_SIZE))
    }

    private fun finalizeWavHeader(file: RandomAccessFile, dataSize: Long) {
        val totalSize = dataSize + WAV_HEADER_SIZE - 8
        file.seek(0)
        file.write("RIFF".toByteArray())
        writeIntLE(file, totalSize.toInt())
        file.write("WAVE".toByteArray())
        file.write("fmt ".toByteArray())
        writeIntLE(file, 16)
        writeShortLE(file, 1) // PCM
        writeShortLE(file, 1) // mono
        writeIntLE(file, sampleRate)
        writeIntLE(file, sampleRate * 2) // byte rate
        writeShortLE(file, 2) // block align
        writeShortLE(file, 16) // bits
        file.write("data".toByteArray())
        writeIntLE(file, dataSize.toInt())
    }

    private fun writeIntLE(file: RandomAccessFile, value: Int) {
        file.write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                (value shr 8 and 0xFF).toByte(),
                (value shr 16 and 0xFF).toByte(),
                (value shr 24 and 0xFF).toByte(),
            ),
        )
    }

    private fun writeShortLE(file: RandomAccessFile, value: Int) {
        file.write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                (value shr 8 and 0xFF).toByte(),
            ),
        )
    }

    companion object {
        private const val TAG = "CallRecorder"
        private const val WAV_HEADER_SIZE = 44
    }
}
