package uz.ex.sip2go.provision

import android.net.Uri
import org.json.JSONObject

data class QrProvisionPayload(
    val server: String,
    val code: String,
)

object QrProvisionParser {
    fun parse(raw: String): QrProvisionPayload? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        parseJson(text)?.let { return it }
        return parseUri(runCatching { Uri.parse(text) }.getOrNull())
    }

    fun parseUri(uri: Uri?): QrProvisionPayload? {
        if (uri == null) return null
        return when {
            uri.scheme == "sip2go" && uri.host == "setup" -> parseQuery(
                server = uri.getQueryParameter("s"),
                code = uri.getQueryParameter("c"),
            )
            uri.scheme in HTTP_SCHEMES && uri.path?.endsWith("/sip2go/setup") == true -> {
                val code = uri.getQueryParameter("c")
                val server = uri.getQueryParameter("s")?.trim()?.trimEnd('/')
                    ?: "${uri.scheme}://${uri.authority}".trimEnd('/')
                parseQuery(server = server, code = code)
            }
            else -> null
        }
    }

    private fun parseJson(text: String): QrProvisionPayload? {
        return runCatching {
            val json = JSONObject(text)
            if (json.optInt("v", 0) != 1) return null
            parseQuery(
                server = json.getString("s"),
                code = json.getString("c"),
            )
        }.getOrNull()
    }

    private fun parseQuery(server: String?, code: String?): QrProvisionPayload? {
        val normalizedServer = server?.trim()?.trimEnd('/') ?: return null
        val normalizedCode = code?.trim().orEmpty()
        if (normalizedServer.isEmpty() || normalizedCode.isEmpty()) return null
        return QrProvisionPayload(server = normalizedServer, code = normalizedCode)
    }

    private val HTTP_SCHEMES = setOf("http", "https")
}
