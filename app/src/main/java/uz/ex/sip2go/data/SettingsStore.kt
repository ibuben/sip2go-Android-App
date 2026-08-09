package uz.ex.sip2go.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class DeviceSettings(
    val wsUrl: String = "",
    val deviceId: String = "",
    val deviceToken: String = "",
    val organizationName: String = DEFAULT_ORGANIZATION_NAME,
    val backgroundPushMode: Boolean = false,
    val missedCallNotifications: Boolean = true,
    val ringtoneUri: String? = null,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val recordCallsByDefault: Boolean = false,
) {
    val isComplete: Boolean
        get() = wsUrl.isNotBlank() && deviceId.isNotBlank() && deviceToken.isNotBlank()

    companion object {
        const val SILENT_RINGTONE = "silent"
        const val DEFAULT_ORGANIZATION_NAME = "sip2go"
    }
}

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.dataStore
    private val localePrefs = appContext.getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)

    val settings: Flow<DeviceSettings> = dataStore.data.map { prefs ->
        DeviceSettings(
            wsUrl = prefs[KEY_WS_URL] ?: "",
            deviceId = prefs[KEY_DEVICE_ID] ?: "",
            deviceToken = prefs[KEY_DEVICE_TOKEN] ?: "",
            organizationName = prefs[KEY_ORGANIZATION_NAME]
                ?: DeviceSettings.DEFAULT_ORGANIZATION_NAME,
            backgroundPushMode = prefs[KEY_BACKGROUND_PUSH] ?: false,
            missedCallNotifications = prefs[KEY_MISSED_NOTIFICATIONS] ?: true,
            ringtoneUri = prefs[KEY_RINGTONE_URI],
            appLanguage = AppLanguage.fromTag(
                readAppLanguageTag(appContext) ?: prefs[KEY_APP_LANGUAGE],
            ),
            recordCallsByDefault = prefs[KEY_RECORD_CALLS_DEFAULT] ?: false,
        )
    }

    init {
        scope.launch {
            if (localePrefs.contains(LOCALE_TAG_KEY)) {
                return@launch
            }
            val migrated = dataStore.data.first()[KEY_APP_LANGUAGE]
            if (!migrated.isNullOrBlank()) {
                persistAppLanguageTag(appContext, migrated)
            }
        }
    }

    suspend fun save(settings: DeviceSettings) {
        persistAppLanguageTag(appContext, settings.appLanguage.tag)
        dataStore.edit { prefs ->
            prefs[KEY_WS_URL] = settings.wsUrl.trim()
            prefs[KEY_DEVICE_ID] = settings.deviceId.trim()
            prefs[KEY_DEVICE_TOKEN] = settings.deviceToken.trim()
            prefs[KEY_ORGANIZATION_NAME] = settings.organizationName.trim()
                .take(16)
                .ifBlank { DeviceSettings.DEFAULT_ORGANIZATION_NAME }
            prefs[KEY_BACKGROUND_PUSH] = settings.backgroundPushMode
            prefs[KEY_MISSED_NOTIFICATIONS] = settings.missedCallNotifications
            prefs[KEY_RECORD_CALLS_DEFAULT] = settings.recordCallsByDefault
            if (settings.ringtoneUri.isNullOrBlank()) {
                prefs.remove(KEY_RINGTONE_URI)
            } else {
                prefs[KEY_RINGTONE_URI] = settings.ringtoneUri
            }
            if (settings.appLanguage == AppLanguage.SYSTEM) {
                prefs.remove(KEY_APP_LANGUAGE)
            } else {
                prefs[KEY_APP_LANGUAGE] = settings.appLanguage.tag
            }
        }
    }

    companion object {
        private const val LOCALE_PREFS = "app_locale"
        private const val LOCALE_TAG_KEY = "tag"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

        fun readAppLanguageTag(context: Context): String? {
            val prefs = context.applicationContext
                .getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
            return prefs.getString(LOCALE_TAG_KEY, null)?.takeIf { it.isNotBlank() }
        }

        fun persistAppLanguageTag(context: Context, tag: String) {
            val prefs = context.applicationContext
                .getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
                .edit()
            if (tag.isBlank()) {
                prefs.remove(LOCALE_TAG_KEY)
            } else {
                prefs.putString(LOCALE_TAG_KEY, tag)
            }
            prefs.apply()
        }

        private val KEY_WS_URL = stringPreferencesKey("ws_url")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
        private val KEY_ORGANIZATION_NAME = stringPreferencesKey("organization_name")
        private val KEY_BACKGROUND_PUSH = booleanPreferencesKey("background_push")
        private val KEY_MISSED_NOTIFICATIONS = booleanPreferencesKey("missed_call_notifications")
        private val KEY_RECORD_CALLS_DEFAULT = booleanPreferencesKey("record_calls_default")
        private val KEY_RINGTONE_URI = stringPreferencesKey("ringtone_uri")
    }
}
