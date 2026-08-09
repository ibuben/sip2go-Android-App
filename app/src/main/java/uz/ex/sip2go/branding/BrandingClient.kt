package uz.ex.sip2go.branding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import uz.ex.sip2go.data.DeviceSettings
import java.util.concurrent.TimeUnit

object BrandingClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(serverBase: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = serverBase.trimEnd('/') + "/api/branding"
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val raw = response.body?.string().orEmpty()
                val name = JSONObject(raw).optString("organization_name", "")
                normalize(name)
            }
        }.getOrNull()
    }

    fun normalize(name: String?): String {
        return name?.trim()?.take(16)?.ifBlank { null }
            ?: DeviceSettings.DEFAULT_ORGANIZATION_NAME
    }
}
