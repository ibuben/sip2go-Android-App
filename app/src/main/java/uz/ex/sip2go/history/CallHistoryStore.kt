package uz.ex.sip2go.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import uz.ex.sip2go.recording.CallRecordingStorage

private val Context.callHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "call_history",
)

class CallHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.callHistoryDataStore

    val history: Flow<List<CallHistoryEntry>> = dataStore.data.map { prefs ->
        parseEntries(prefs[KEY_ENTRIES] ?: "[]")
    }

    val unreadMissedCount: Flow<Int> = dataStore.data.map { prefs ->
        val seenAt = prefs[KEY_HISTORY_SEEN_AT] ?: 0L
        parseEntries(prefs[KEY_ENTRIES] ?: "[]").count { entry ->
            entry.status == CallStatus.MISSED &&
                entry.direction == CallDirection.INCOMING &&
                entry.startedAt > seenAt
        }
    }

    val lastDialedNumber: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_DIALED]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveLastDialed(number: String) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[KEY_LAST_DIALED] = trimmed
        }
    }

    suspend fun markHistorySeen() {
        dataStore.edit { prefs ->
            prefs[KEY_HISTORY_SEEN_AT] = System.currentTimeMillis()
        }
    }

    suspend fun addEntry(entry: CallHistoryEntry) {
        dataStore.edit { prefs ->
            val current = parseEntries(prefs[KEY_ENTRIES] ?: "[]").toMutableList()
            current.removeAll { it.id == entry.id }
            current.add(0, entry)
            while (current.size > MAX_ENTRIES) {
                val removed = current.removeAt(current.lastIndex)
                deleteEntryRecording(removed)
            }
            prefs[KEY_ENTRIES] = encodeEntries(current)
        }
    }

    suspend fun deleteEntries(ids: Set<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val current = parseEntries(prefs[KEY_ENTRIES] ?: "[]").toMutableList()
            current.filter { it.id in ids }.forEach { deleteEntryRecording(it) }
            current.removeAll { it.id in ids }
            prefs[KEY_ENTRIES] = encodeEntries(current)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            parseEntries(prefs[KEY_ENTRIES] ?: "[]").forEach { deleteEntryRecording(it) }
            prefs[KEY_ENTRIES] = "[]"
        }
    }

    private fun deleteEntryRecording(entry: CallHistoryEntry) {
        CallRecordingStorage.deleteFile(entry.recordingPath)
        CallRecordingStorage.findNewestForCall(appContext, entry.id)?.delete()
    }

    private fun parseEntries(raw: String): List<CallHistoryEntry> {
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(decodeEntry(array.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeEntries(entries: List<CallHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { array.put(encodeEntry(it)) }
        return array.toString()
    }

    private fun encodeEntry(entry: CallHistoryEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("number", entry.number)
            put("localEndpoint", entry.localEndpoint)
            put("contactName", entry.contactName)
            if (entry.contactId != null) put("contactId", entry.contactId)
            put("direction", entry.direction.name)
            put("status", entry.status.name)
            put("startedAt", entry.startedAt)
            put("durationSec", entry.durationSec)
            if (!entry.recordingPath.isNullOrBlank()) {
                put("recordingPath", entry.recordingPath)
                put("recordingDurationSec", entry.recordingDurationSec)
            }
        }
    }

    private fun decodeEntry(obj: JSONObject): CallHistoryEntry {
        return CallHistoryEntry(
            id = obj.getString("id"),
            number = obj.getString("number"),
            localEndpoint = obj.getString("localEndpoint"),
            contactName = obj.optString("contactName").takeIf { it.isNotBlank() },
            contactId = if (obj.has("contactId") && !obj.isNull("contactId")) {
                obj.getLong("contactId")
            } else {
                null
            },
            direction = CallDirection.valueOf(obj.getString("direction")),
            status = CallStatus.valueOf(obj.getString("status")),
            startedAt = obj.getLong("startedAt"),
            durationSec = obj.optInt("durationSec", 0),
            recordingPath = obj.optString("recordingPath").takeIf { it.isNotBlank() },
            recordingDurationSec = obj.optInt("recordingDurationSec", 0),
        )
    }

    companion object {
        private val KEY_ENTRIES = stringPreferencesKey("entries")
        private val KEY_HISTORY_SEEN_AT = longPreferencesKey("history_seen_at")
        private val KEY_LAST_DIALED = stringPreferencesKey("last_dialed")
        private const val MAX_ENTRIES = 300
    }
}
