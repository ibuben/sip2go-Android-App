package uz.ex.sip2go.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.ex.sip2go.SipTgApp
import java.util.UUID

class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val callId = intent.getStringExtra(IncomingCallNotifier.EXTRA_CALL_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: return
        val client = (context.applicationContext as SipTgApp).deviceClient
        when (intent.action) {
            IncomingCallNotifier.ACTION_ACCEPT -> client.acceptCall()
            IncomingCallNotifier.ACTION_REJECT -> {
                client.rejectCall()
                IncomingCallNotifier.dismiss(context)
            }
            IncomingCallNotifier.ACTION_HANGUP -> {
                client.hangupCall()
                uz.ex.sip2go.telecom.OngoingCallNotifier.dismiss(context)
            }
        }
    }
}
