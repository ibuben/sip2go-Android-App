package uz.ex.sip2go.recording

import android.content.Context
import java.io.File
import java.util.UUID

object CallRecordingStorage {
    /** Total disk budget for all call recordings on the device. */
    const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    /** Target free space to keep after cleanup. */
    private const val TARGET_FREE_BYTES = 64L * 1024 * 1024

    fun recordingsDir(context: Context): File =
        File(context.applicationContext.filesDir, "recordings").apply { mkdirs() }

    fun prepareFile(context: Context, callId: UUID): File {
        enforceQuota(context.applicationContext)
        val name = "${callId}_${System.currentTimeMillis()}.wav"
        return File(recordingsDir(context), name)
    }

    fun totalSizeBytes(context: Context): Long {
        val dir = recordingsDir(context)
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun formattedUsage(context: Context): Pair<Long, Long> {
        val used = totalSizeBytes(context)
        return used to MAX_TOTAL_BYTES
    }

    fun enforceQuota(context: Context, protectedPaths: Set<String> = emptySet()) {
        val dir = recordingsDir(context)
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "wav" } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_TOTAL_BYTES) return

        val deletable = files
            .filter { it.absolutePath !in protectedPaths }
            .sortedBy { it.lastModified() }

        for (file in deletable) {
            if (total <= MAX_TOTAL_BYTES - TARGET_FREE_BYTES) break
            val size = file.length()
            if (file.delete()) {
                total -= size
            }
        }
    }

    fun findNewestForCall(context: Context, callId: String): File? {
        val dir = recordingsDir(context)
        return dir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.extension.equals("wav", ignoreCase = true) &&
                    file.name.startsWith("${callId}_")
            }
            ?.maxByOrNull { it.lastModified() }
    }

    fun estimateDurationSec(file: File, sampleRate: Int = 8000): Int {
        val dataBytes = (file.length() - 44L).coerceAtLeast(0L)
        return (dataBytes / (2L * sampleRate)).toInt().coerceAtLeast(0)
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    fun deleteOrphans(context: Context, knownPaths: Set<String>) {
        val dir = recordingsDir(context)
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in knownPaths) {
                file.delete()
            }
        }
    }
}
