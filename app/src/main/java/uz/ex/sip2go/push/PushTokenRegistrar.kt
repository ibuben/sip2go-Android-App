package uz.ex.sip2go.push

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import uz.ex.sip2go.data.DeviceSettings

object PushTokenRegistrar {
    private const val TAG = "PushTokenRegistrar"
    private val client = OkHttpClient()

    suspend fun upload(settings: DeviceSettings, fcmToken: String): Boolean = withContext(Dispatchers.IO) {
        if (!settings.isComplete || fcmToken.isBlank()) {
            return@withContext false
        }
        val apiUrl = settings.wsUrl
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .replace("/ws/device", "/api/device/push-token")
        val body = JSONObject()
            .put("device_id", settings.deviceId)
            .put("token", settings.deviceToken)
            .put("fcm_token", fcmToken)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .build()
        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "FCM token uploaded")
                    true
                } else {
                    Log.e(TAG, "FCM upload failed: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FCM upload error", e)
            false
        }
    }
}
