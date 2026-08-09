package uz.ex.sip2go.telecom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import uz.ex.sip2go.R
import uz.ex.sip2go.branding.BrandingHolder
import java.util.UUID

object TelecomBridge {
    private const val TAG = "TelecomBridge"
    private const val PHONE_ACCOUNT_ID = "sip2go_voip"

    private lateinit var appContext: Context
    private var phoneAccountHandle: PhoneAccountHandle? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun status(): TelecomStatus {
        if (!::appContext.isInitialized) return TelecomStatus.Unavailable
        return try {
            val telecomManager = appContext.getSystemService(TelecomManager::class.java)
                ?: return TelecomStatus.Unavailable
            val handle = accountHandle() ?: return TelecomStatus.Unavailable
            val account = telecomManager.getPhoneAccount(handle) ?: return TelecomStatus.Unavailable
            if (account.isEnabled) TelecomStatus.Ready else TelecomStatus.Disabled
        } catch (e: Exception) {
            Log.w(TAG, "status() failed", e)
            TelecomStatus.Unavailable
        }
    }

    fun isAvailable(): Boolean = status() != TelecomStatus.Unavailable

    fun isReady(): Boolean = status() == TelecomStatus.Ready

    fun ensureRegistered(): Boolean {
        if (!::appContext.isInitialized) return false
        val telecomManager = appContext.getSystemService(TelecomManager::class.java) ?: return false
        return ensureRegistered(telecomManager)
    }

    fun openPhoneAccountSettings(context: Context) {
        val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open phone account settings", e)
            val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }

    private fun accountHandle(): PhoneAccountHandle? {
        phoneAccountHandle?.let { return it }
        val component = ComponentName(appContext, SipConnectionService::class.java)
        return PhoneAccountHandle(component, PHONE_ACCOUNT_ID).also { phoneAccountHandle = it }
    }

    private fun ensureRegistered(telecomManager: TelecomManager): Boolean {
        val handle = accountHandle() ?: return false
        return try {
            if (telecomManager.getPhoneAccount(handle) == null) {
                val label = BrandingHolder.organizationName.ifBlank {
                    appContext.getString(R.string.phone_account_label)
                }
                val account = PhoneAccount.builder(handle, label)
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .setShortDescription(label)
                    .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL))
                    .build()
                telecomManager.registerPhoneAccount(account)
                Log.i(TAG, "PhoneAccount registered")
            }
            telecomManager.getPhoneAccount(handle) != null
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to register PhoneAccount — MANAGE_OWN_CALLS?", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register PhoneAccount", e)
            false
        }
    }

    private fun requireReady(telecomManager: TelecomManager): PhoneAccountHandle? {
        if (!ensureRegistered(telecomManager)) return null
        val handle = accountHandle() ?: return null
        if (telecomManager.getPhoneAccount(handle)?.isEnabled != true) {
            Log.w(TAG, "PhoneAccount not enabled — open system call account settings")
            return null
        }
        return handle
    }

    fun notifyIncoming(
        callId: UUID,
        caller: String,
        called: String,
        displayName: String? = null,
    ): Boolean {
        if (CallConnectionRegistry.get(callId) != null) {
            return true
        }
        val telecomManager = appContext.getSystemService(TelecomManager::class.java) ?: return false
        val handle = requireReady(telecomManager) ?: return false

        return try {
            val extras = Bundle().apply {
                putString(TelecomExtras.CALL_ID, callId.toString())
                putString(TelecomExtras.CALLER, caller)
                putString(TelecomExtras.CALLED, called)
                displayName?.let { putString(TelecomExtras.DISPLAY_NAME, it) }
            }
            telecomManager.addNewIncomingCall(handle, extras)
            Log.i(TAG, "addNewIncomingCall callId=$callId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "addNewIncomingCall failed", e)
            false
        }
    }

    fun placeOutgoing(number: String): Boolean {
        val telecomManager = appContext.getSystemService(TelecomManager::class.java) ?: return false
        val handle = requireReady(telecomManager) ?: return false

        return try {
            val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null)
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                putString(TelecomExtras.DIAL_NUMBER, number)
            }
            telecomManager.placeCall(uri, extras)
            Log.i(TAG, "placeCall number=$number")
            true
        } catch (e: Exception) {
            Log.e(TAG, "placeCall failed", e)
            false
        }
    }

    fun notifyOutgoingDialing(callId: UUID, number: String) {
        CallConnectionRegistry.linkOutgoingCallId(callId, number)
        CallConnectionRegistry.get(callId)?.markDialing()
    }

    fun notifyRinging(callId: UUID) {
        CallConnectionRegistry.get(callId)?.markRinging()
    }

    fun notifyActive(callId: UUID) {
        CallConnectionRegistry.get(callId)?.markActive()
    }

    fun notifyEnded(callId: UUID, reason: String) {
        val connection = CallConnectionRegistry.get(callId) ?: return
        connection.markDisconnected(DisconnectCauseMapper.fromReason(reason))
    }

    fun updateCallerLabel(callId: UUID, displayName: String) {
        CallConnectionRegistry.get(callId)?.setCallerLabel(displayName)
    }

    fun clearConnections() {
        CallConnectionRegistry.clear()
    }
}
