package uz.ex.sip2go.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import uz.ex.sip2go.data.SettingsStore

class FcmManager(
    private val settingsStore: SettingsStore,
) {
    suspend fun refreshAndUpload(): String? {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            val settings = settingsStore.settings.first()
            if (settings.isComplete) {
                PushTokenRegistrar.upload(settings, token)
            }
            token
        } catch (e: Exception) {
            Log.e(TAG, "FCM token refresh failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "FcmManager"
    }
}
