package uz.ex.sip2go.telecom

import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.ex.sip2go.R
import uz.ex.sip2go.SipTgApp
import java.util.UUID

class SipConnectionService : ConnectionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val extras = request?.extras ?: Bundle()
        val callId = extras.getString(TelecomExtras.CALL_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        val caller = extras.getString(TelecomExtras.CALLER) ?: getString(R.string.label_unknown)
        val displayName = extras.getString(TelecomExtras.DISPLAY_NAME) ?: caller

        if (callId == null) {
            Log.e(TAG, "Incoming connection missing callId")
            return Connection.createFailedConnection(
                android.telecom.DisconnectCause(android.telecom.DisconnectCause.ERROR),
            )
        }

        Log.i(TAG, "Create incoming connection callId=$callId caller=$caller")
        val connection = SipConnection(applicationContext, callId, caller, isIncoming = true)
        connection.setCallerLabel(displayName)
        connection.markRinging()
        CallConnectionRegistry.registerIncoming(callId, connection)
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val number = request?.address?.schemeSpecificPart
            ?: request?.extras?.getString(TelecomExtras.DIAL_NUMBER)
            ?: run {
                Log.e(TAG, "Outgoing connection missing number")
                return Connection.createFailedConnection(
                    android.telecom.DisconnectCause(android.telecom.DisconnectCause.ERROR),
                )
            }

        Log.i(TAG, "Create outgoing connection number=$number")
        val connection = SipConnection(applicationContext, callId = null, address = number, isIncoming = false)
        connection.markDialing()
        CallConnectionRegistry.registerOutgoing(number, connection)

        val app = applicationContext as SipTgApp
        scope.launch {
            app.deviceClient.dialWhenReady(number)
        }
        return connection
    }

    companion object {
        private const val TAG = "SipConnectionService"
    }
}
