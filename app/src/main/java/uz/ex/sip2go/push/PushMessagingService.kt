package uz.ex.sip2go.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.ex.sip2go.R
import uz.ex.sip2go.SipTgApp
import uz.ex.sip2go.service.DeviceService

class PushMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            (application as SipTgApp).fcmManager.refreshAndUpload()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "incoming_call") {
            return
        }
        val callId = data["call_id"] ?: return
        val caller = data["caller"] ?: getString(R.string.label_unknown)
        val called = data["called"] ?: ""
        Log.i(TAG, "Incoming call push: callId=$callId caller=$caller")

        try {
            // Route through DeviceService so foreground starts before any UI work.
            DeviceService.wakeForIncoming(this, callId, caller, called)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start incoming call wake", e)
        }
    }

    companion object {
        private const val TAG = "PushMessagingService"
    }
}
