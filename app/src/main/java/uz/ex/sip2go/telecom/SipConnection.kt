package uz.ex.sip2go.telecom

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.util.Log
import uz.ex.sip2go.SipTgApp
import uz.ex.sip2go.audio.CallAudioRoute
import uz.ex.sip2go.audio.findForRoute
import uz.ex.sip2go.audio.supportsCallEndpointApi
import uz.ex.sip2go.audio.toCallAudioRoute
import uz.ex.sip2go.audio.toTelecomRoute
import java.util.UUID
import java.util.concurrent.Executor

class SipConnection(
    private val appContext: Context,
    private var callId: UUID?,
    private val address: String,
    private val isIncoming: Boolean,
) : Connection() {

    @Volatile
    private var ended = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    @Volatile
    private var preferredRoute: CallAudioRoute = CallAudioRoute.EARPIECE

    private var availableEndpoints: List<CallEndpoint> = emptyList()

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        connectionCapabilities = CAPABILITY_MUTE
        setAudioModeIsVoip(true)
        setAddress(
            Uri.fromParts(PhoneAccount.SCHEME_TEL, address, null),
            TelecomManager.PRESENTATION_ALLOWED,
        )
    }

    fun bindCallId(id: UUID) {
        callId = id
        CallConnectionRegistry.registerIncoming(id, this)
    }

    fun setCallerLabel(displayName: String) {
        setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
    }

    fun setPreferredAudioRoute(route: CallAudioRoute) {
        preferredRoute = route
        if (state == STATE_ACTIVE) {
            requestTelecomRoute()
        }
    }

    fun preferredAudioRoute(): CallAudioRoute = preferredRoute

    fun markDialing() {
        if (ended) return
        setDialing()
    }

    fun markRinging() {
        if (ended) return
        setRinging()
    }

    fun markActive() {
        if (ended) return
        setActive()
        scheduleRouteApply()
    }

    fun markDisconnected(cause: DisconnectCause) {
        if (ended) return
        ended = true
        setDisconnected(cause)
        destroy()
        callId?.let { CallConnectionRegistry.remove(it) }
    }

    override fun onAnswer() {
        val id = callId ?: run {
            Log.w(TAG, "onAnswer without callId")
            return
        }
        Log.i(TAG, "Telecom answer callId=$id")
        (appContext.applicationContext as SipTgApp).deviceClient.acceptCallFromTelecom(id)
    }

    override fun onReject() {
        val id = callId
        Log.i(TAG, "Telecom reject callId=$id")
        if (id != null) {
            (appContext.applicationContext as SipTgApp).deviceClient.rejectCallFromTelecom(id)
        }
        markDisconnected(DisconnectCause(DisconnectCause.REJECTED))
    }

    override fun onDisconnect() {
        val id = callId
        Log.i(TAG, "Telecom disconnect callId=$id")
        if (id != null) {
            (appContext.applicationContext as SipTgApp).deviceClient.hangupCallFromTelecom(id)
        }
        markDisconnected(DisconnectCause(DisconnectCause.LOCAL))
    }

    override fun onAbort() {
        onReject()
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        this.availableEndpoints = availableEndpoints.toList()
        if (supportsCallEndpointApi() && state == STATE_ACTIVE) {
            scheduleRouteApply(delayMs = 80L)
        }
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        val id = callId ?: return
        val route = callEndpoint.toCallAudioRoute()
        mainHandler.post {
            (appContext.applicationContext as SipTgApp).deviceClient.onTelecomAudioRouteChanged(id, route)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(state: CallAudioState) {
        if (supportsCallEndpointApi()) {
            return
        }
        val id = callId ?: return
        val route = state.toCallAudioRoute()
        mainHandler.post {
            (appContext.applicationContext as SipTgApp).deviceClient.onTelecomAudioRouteChanged(id, route)
        }
    }

    override fun onStateChanged(state: Int) {
        Log.d(TAG, "Connection state=$state callId=$callId incoming=$isIncoming")
    }

    private fun requestTelecomRoute() {
        if (ended) return
        if (supportsCallEndpointApi()) {
            requestTelecomRouteModern()
        } else {
            requestTelecomRouteLegacy()
        }
    }

    private fun requestTelecomRouteLegacy() {
        @Suppress("DEPRECATION")
        runCatching { setAudioRoute(preferredRoute.toTelecomRoute()) }
            .onFailure { Log.w(TAG, "setAudioRoute failed", it) }
    }

    private fun requestTelecomRouteModern() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        val endpoint = availableEndpoints.findForRoute(preferredRoute) ?: return
        requestCallEndpointChange(
            endpoint,
            mainExecutor,
            object : OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) = Unit

                override fun onError(error: CallEndpointException) {
                    Log.w(TAG, "requestCallEndpointChange failed: ${error.message}")
                }
            },
        )
    }

    private val routeApplyRunnable = Runnable {
        if (!ended && state == STATE_ACTIVE) {
            requestTelecomRoute()
        }
    }

    private fun scheduleRouteApply(delayMs: Long = 100L) {
        if (ended) return
        mainHandler.removeCallbacks(routeApplyRunnable)
        mainHandler.postDelayed(routeApplyRunnable, delayMs)
    }

    companion object {
        private const val TAG = "SipConnection"
    }
}
