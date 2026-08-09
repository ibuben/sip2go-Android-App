package uz.ex.sip2go.provision

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ProvisionClaimResult(
    val wsUrl: String,
    val deviceId: String,
    val deviceToken: String,
    val endpointNumber: String,
    val name: String,
    val organizationName: String,
)

object ProvisionClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun claim(serverBase: String, code: String): Result<ProvisionClaimResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = serverBase.trimEnd('/') + "/api/provision/claim"
                val body = JSONObject().put("code", code).toString()
                    .toRequestBody(jsonMedia)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()
                http.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            JSONObject(raw).optString("detail", "")
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?: "HTTP ${response.code}"
                        error(message)
                    }
                    val json = JSONObject(raw)
                    ProvisionClaimResult(
                        wsUrl = json.getString("ws_url"),
                        deviceId = json.getString("device_id"),
                        deviceToken = json.getString("device_token"),
                        endpointNumber = json.getString("endpoint_number"),
                        name = json.optString("name", ""),
                        organizationName = json.optString(
                            "organization_name",
                            uz.ex.sip2go.data.DeviceSettings.DEFAULT_ORGANIZATION_NAME,
                        ),
                    )
                }
            }
        }
    }
}
