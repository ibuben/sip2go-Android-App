package uz.ex.sip2go.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

interface DeviceWebSocketListener {
    fun onConnected()
    fun onRegistered(organizationName: String? = null, reconnectGraceSec: Int = 10)
    fun onDisconnected(reason: String)
    fun onError(message: String)
    fun onIncomingCall(callId: UUID, caller: String, called: String)
    fun onDialing(callId: UUID, caller: String, number: String)
    fun onCallRinging(callId: UUID)
    fun onCallAccepted(callId: UUID)
    fun onCallEnded(callId: UUID, reason: String)
    fun onCallResync(
        callId: UUID,
        caller: String,
        called: String,
        outgoing: Boolean,
        state: String,
        held: Boolean = false,
    )
    fun onCallHoldChanged(callId: UUID, held: Boolean)
    fun onAudio(callId: UUID, opus: ByteArray)
}

class DeviceWebSocket(
    private val wsUrl: String,
    private val deviceId: String,
    private val deviceToken: String,
    private val fcmToken: String? = null,
    private val listener: DeviceWebSocketListener,
) {
    private var client: OkHttpClient = buildClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var webSocket: WebSocket? = null
    @Volatile
    private var registered = false
    @Volatile
    private var intentionalClose = false

    val isConnected: Boolean
        get() = webSocket != null

    fun connect() {
        registered = false
        intentionalClose = false
        client = buildClient()
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                dispatchMain {
                    listener.onConnected()
                    sendRegister()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleText(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                handleBinary(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                registered = false
                if (intentionalClose) return
                dispatchMain {
                    listener.onDisconnected(reason.ifBlank { "closed ($code)" })
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                registered = false
                if (intentionalClose) return
                Log.e(TAG, "WebSocket failure", t)
                dispatchMain {
                    listener.onDisconnected(t.message ?: "connection failed")
                }
            }
        })
    }

    fun disconnect() {
        intentionalClose = true
        webSocket?.close(1000, "client disconnect")
        webSocket = null
        registered = false
    }

    fun sendControl(type: String, callId: UUID? = null) {
        val payload = JSONObject().put("type", type)
        if (callId != null) {
            payload.put("call_id", callId.toString())
        }
        webSocket?.send(payload.toString())
    }

    fun sendDial(number: String) {
        val payload = JSONObject()
            .put("type", Protocol.DIAL)
            .put("number", number)
        webSocket?.send(payload.toString())
    }

    fun sendDtmf(callId: UUID, digit: String) {
        val payload = JSONObject()
            .put("type", Protocol.DTMF)
            .put("call_id", callId.toString())
            .put("digit", digit)
        webSocket?.send(payload.toString())
    }

    fun sendHold(callId: UUID, held: Boolean) {
        val payload = JSONObject()
            .put("type", Protocol.HOLD)
            .put("call_id", callId.toString())
            .put("held", held)
        webSocket?.send(payload.toString())
    }

    fun sendTransfer(callId: UUID, target: String) {
        val payload = JSONObject()
            .put("type", Protocol.TRANSFER)
            .put("call_id", callId.toString())
            .put("target", target)
        webSocket?.send(payload.toString())
    }

    fun sendPing() {
        sendControl(Protocol.PING)
    }

    fun sendAudio(data: ByteArray) {
        webSocket?.send(data.toByteString(0, data.size))
    }

    private fun sendRegister() {
        val payload = JSONObject()
            .put("type", Protocol.REGISTER)
            .put("device_id", deviceId)
            .put("token", deviceToken)
        if (!fcmToken.isNullOrBlank()) {
            payload.put("fcm_token", fcmToken)
        }
        webSocket?.send(payload.toString())
    }

    private fun dispatchMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun handleText(text: String) {
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        when (json.optString("type")) {
            Protocol.REGISTERED -> {
                registered = true
                val org = json.optString("organization_name", "").trim()
                val grace = json.optInt("reconnect_grace_sec", 10)
                dispatchMain { listener.onRegistered(org.ifBlank { null }, grace) }
            }
            Protocol.ERROR -> dispatchMain {
                listener.onError(json.optString("message", "unknown error"))
            }
            Protocol.INCOMING_CALL -> {
                val callId = parseUuid(json.optString("call_id")) ?: return
                dispatchMain {
                    listener.onIncomingCall(
                        callId,
                        json.optString("caller", "unknown"),
                        json.optString("called", ""),
                    )
                }
            }
            Protocol.DIALING -> {
                val callId = parseUuid(json.optString("call_id")) ?: return
                dispatchMain {
                    listener.onDialing(
                        callId,
                        json.optString("caller", ""),
                        json.optString("number", ""),
                    )
                }
            }
            Protocol.CALL_RINGING -> {
                parseUuid(json.optString("call_id"))?.let { callId ->
                    dispatchMain { listener.onCallRinging(callId) }
                }
            }
            Protocol.CALL_ACCEPTED -> {
                parseUuid(json.optString("call_id"))?.let { callId ->
                    dispatchMain { listener.onCallAccepted(callId) }
                }
            }
            Protocol.CALL_ENDED -> {
                val callId = parseUuid(json.optString("call_id")) ?: return
                dispatchMain {
                    listener.onCallEnded(callId, json.optString("reason", ""))
                }
            }
            Protocol.CALL_RESYNC -> {
                val callId = parseUuid(json.optString("call_id")) ?: return
                dispatchMain {
                    listener.onCallResync(
                        callId,
                        json.optString("caller", ""),
                        json.optString("called", ""),
                        json.optBoolean("outgoing", false),
                        json.optString("state", "active"),
                        json.optBoolean("held", false),
                    )
                }
            }
            Protocol.CALL_HOLD_CHANGED -> {
                val callId = parseUuid(json.optString("call_id")) ?: return
                dispatchMain {
                    listener.onCallHoldChanged(callId, json.optBoolean("held", false))
                }
            }
            Protocol.PONG -> Unit
        }
    }

    private fun handleBinary(data: ByteArray) {
        try {
            val (callId, opus) = Protocol.unpackAudio(data)
            listener.onAudio(callId, opus)
        } catch (e: Exception) {
            Log.w(TAG, "bad audio frame", e)
        }
    }

    private fun parseUuid(raw: String): UUID? {
        return try {
            UUID.fromString(raw)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "DeviceWebSocket"

        private fun buildClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
