package uz.ex.sip2go.service

import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ex.sip2go.R
import uz.ex.sip2go.audio.CallAudioFocus
import uz.ex.sip2go.audio.CallAudioSession
import uz.ex.sip2go.audio.CallAudioRoute
import uz.ex.sip2go.audio.CallAudioRouter
import uz.ex.sip2go.audio.RingbackTonePlayer
import uz.ex.sip2go.contacts.ContactLookup
import uz.ex.sip2go.branding.BrandingClient
import uz.ex.sip2go.branding.BrandingHolder
import uz.ex.sip2go.branding.ServerUrls
import uz.ex.sip2go.data.DeviceSettings
import uz.ex.sip2go.data.SettingsStore
import uz.ex.sip2go.history.CallDirection
import uz.ex.sip2go.history.CallHistoryEntry
import uz.ex.sip2go.history.CallHistoryStore
import uz.ex.sip2go.history.MissedCallBadge
import uz.ex.sip2go.history.CallStatus
import uz.ex.sip2go.network.DeviceWebSocket
import uz.ex.sip2go.network.DeviceWebSocketListener
import uz.ex.sip2go.network.NetworkMonitor
import uz.ex.sip2go.network.Protocol
import uz.ex.sip2go.call.IncomingCallNotifier
import uz.ex.sip2go.push.FcmManager
import uz.ex.sip2go.telecom.CallConnectionRegistry
import uz.ex.sip2go.telecom.TelecomBridge
import uz.ex.sip2go.recording.CallRecorder
import uz.ex.sip2go.recording.CallRecordingResult
import uz.ex.sip2go.recording.CallRecordingStorage
import uz.ex.sip2go.telecom.OngoingCallNotifier
import java.util.UUID

enum class ConnectionState {
    STOPPED,
    IDLE,
    CONNECTING,
    CONNECTED,
    REGISTERED,
    ERROR,
}

data class IncomingCallUi(
    val callId: UUID,
    val caller: String,
    val called: String,
    val contactName: String? = null,
    val contactId: Long? = null,
)

data class ActiveCallUi(
    val callId: UUID,
    val caller: String,
    val called: String,
    val outgoing: Boolean = false,
    val contactName: String? = null,
    val contactId: Long? = null,
)

data class OutgoingCallUi(
    val callId: UUID,
    val caller: String,
    val number: String,
    val ringing: Boolean = false,
    val contactName: String? = null,
    val contactId: Long? = null,
)

class DeviceClient(
    private val appContext: android.content.Context,
    private val settingsStore: SettingsStore,
    private val callHistoryStore: CallHistoryStore,
) : DeviceWebSocketListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fcmManager = FcmManager(settingsStore)
    private val networkMonitor = NetworkMonitor(appContext) { onNetworkAvailable() }

    private val _connectionState = MutableStateFlow(ConnectionState.STOPPED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _incomingCall = MutableStateFlow<IncomingCallUi?>(null)
    val incomingCall: StateFlow<IncomingCallUi?> = _incomingCall.asStateFlow()

    private val _outgoingCall = MutableStateFlow<OutgoingCallUi?>(null)
    val outgoingCall: StateFlow<OutgoingCallUi?> = _outgoingCall.asStateFlow()

    private val _activeCall = MutableStateFlow<ActiveCallUi?>(null)
    val activeCall: StateFlow<ActiveCallUi?> = _activeCall.asStateFlow()

    private val _callReconnecting = MutableStateFlow(false)
    val callReconnecting: StateFlow<Boolean> = _callReconnecting.asStateFlow()

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    private val _audioRoute = MutableStateFlow(CallAudioRoute.EARPIECE)
    val audioRoute: StateFlow<CallAudioRoute> = _audioRoute.asStateFlow()

    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    private val _callConnectedAt = MutableStateFlow<Long?>(null)
    val callConnectedAt: StateFlow<Long?> = _callConnectedAt.asStateFlow()

    private val _callRecording = MutableStateFlow(false)
    val callRecording: StateFlow<Boolean> = _callRecording.asStateFlow()

    private val _callHeld = MutableStateFlow(false)
    val callHeld: StateFlow<Boolean> = _callHeld.asStateFlow()

    private var callRecorder: CallRecorder? = null
    private var finishedRecording: CallRecordingResult? = null

    private var webSocket: DeviceWebSocket? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var idleJob: Job? = null
    private var shouldRun = false
    private var keepConnected = false
    private var appInForeground = false
    private var currentFcmToken: String? = null
    private var audioSession: CallAudioSession? = null
    private var ringbackPlayer: RingbackTonePlayer? = null
    private var ringbackRouter: CallAudioRouter? = null
    private var ringbackAudioMode = AudioManager.MODE_NORMAL
    private var pendingDialNumber: String = ""
    private var queuedDialNumber: String? = null
    private var reconnectAttempt = 0
    private var pendingHangupCallId: UUID? = null
    @Volatile
    private var connectInFlight = false
    private var callRecoveryJob: Job? = null
    private var userAudioRoute: CallAudioRoute? = null
    @Volatile
    private var routeSettlingUntilMs = 0L

    private fun msg(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) appContext.getString(resId) else appContext.getString(resId, *args)

    private data class PendingHistory(
        val callId: UUID,
        val direction: CallDirection,
        val number: String,
        val localEndpoint: String,
        val contactName: String? = null,
        val contactId: Long? = null,
        val startedAt: Long = System.currentTimeMillis(),
    )

    private val pendingHistory = mutableMapOf<UUID, PendingHistory>()
    private val callAcceptedAt = mutableMapOf<UUID, Long>()

    fun applyBackgroundPushMode(enabled: Boolean) {
        scope.launch {
            if (enabled) {
                if (_activeCall.value != null || _incomingCall.value != null || _outgoingCall.value != null) {
                    _statusMessage.value = msg(R.string.status_finish_call_before_push)
                    return@launch
                }
                disconnectToIdle()
                keepConnected = false
                connectInFlight = false
                reconnectAttempt = 0
                _statusMessage.value = msg(R.string.status_ready_push_wake)
                val settings = settingsStore.settings.first()
                if (settings.isComplete) {
                    currentFcmToken = fcmManager.refreshAndUpload()
                }
                DeviceService.releaseForeground(appContext)
            } else {
                shouldRun = true
                keepConnected = appInForeground
                DeviceService.startIdle(appContext)
                if (appInForeground) {
                    connectForOutgoing()
                }
            }
        }
    }

    fun startIdle() {
        shouldRun = false
        keepConnected = false
        disconnectToIdle()
        scope.launch {
            val settings = settingsStore.settings.first()
            _statusMessage.value = if (settings.backgroundPushMode) {
                msg(R.string.status_ready_push_wake)
            } else {
                msg(R.string.status_idle_plain)
            }
            if (settings.backgroundPushMode) {
                DeviceService.releaseForeground(appContext)
            }
            if (!settings.isComplete) {
                _connectionState.value = ConnectionState.ERROR
                _statusMessage.value = msg(R.string.status_fill_credentials)
                return@launch
            }
            currentFcmToken = fcmManager.refreshAndUpload()
        }
    }

    fun wantsPersistentConnection(): Boolean = keepConnected || appInForeground

    fun connectForOutgoing() {
        keepConnected = true
        shouldRun = true
        startNetworkMonitor()
        idleJob?.cancel()
        when (_connectionState.value) {
            ConnectionState.REGISTERED -> {
                if (webSocket != null) {
                    _statusMessage.value = msg(R.string.status_online_ready)
                    return
                }
                connectNow()
            }
            ConnectionState.CONNECTING, ConnectionState.CONNECTED -> {
                if (webSocket == null && reconnectJob?.isActive != true) {
                    connectNow()
                }
            }
            else -> connectNow()
        }
    }

    /** Keep WebSocket online while the main UI is visible. */
    fun setAppInForeground(inForeground: Boolean) {
        appInForeground = inForeground
        if (inForeground) {
            idleJob?.cancel()
            scope.launch {
                val backgroundPush = settingsStore.settings.first().backgroundPushMode
                if (backgroundPush && _connectionState.value == ConnectionState.IDLE && !hasCallUi()) {
                    return@launch
                }
                keepConnected = true
                if (_connectionState.value != ConnectionState.STOPPED) {
                    shouldRun = true
                    connectForOutgoing()
                }
            }
        } else if (!hasCallUi()) {
            scheduleForegroundRelease()
        }
    }

    /** FCM wake — show call UI immediately, connect WebSocket in parallel. */
    fun onPushIncomingCall(callIdStr: String, caller: String, called: String) {
        val callId = runCatching { UUID.fromString(callIdStr) }.getOrNull() ?: return
        scope.launch {
            Log.i(TAG, "Push incoming call $callId from $caller")
            if (_incomingCall.value?.callId == callId) {
                wakeForIncomingCall()
                return@launch
            }
            _outgoingCall.value = null
            _activeCall.value = null
            _incomingCall.value = IncomingCallUi(callId, caller, called)
            _statusMessage.value = msg(R.string.status_incoming_from, caller)
            rememberPendingHistory(
                callId = callId,
                direction = CallDirection.INCOMING,
                number = caller,
                localEndpoint = called,
            )
            IncomingCallNotifier.show(appContext, callId, caller, called, null, fromBackgroundWake = true)
            TelecomBridge.notifyIncoming(callId, caller, called)
            wakeForIncomingCall()
            val match = withContext(Dispatchers.IO) {
                ContactLookup.lookup(appContext, caller)
            }
            if (_incomingCall.value?.callId != callId || match == null) return@launch
            _incomingCall.value = IncomingCallUi(
                callId,
                caller,
                called,
                match.name,
                match.contactId,
            )
            updatePendingContact(callId, match.name, match.contactId)
            IncomingCallNotifier.updateCallerLabel(appContext, callId, caller, called, match.name)
            TelecomBridge.updateCallerLabel(callId, match.name)
        }
    }

    fun wakeForIncomingCall() {
        keepConnected = false
        shouldRun = true
        idleJob?.cancel()
        reconnectJob?.cancel()
        // Prevent observeState from stopping the service while connectNow() is still async.
        if (_connectionState.value == ConnectionState.IDLE ||
            _connectionState.value == ConnectionState.STOPPED
        ) {
            _connectionState.value = ConnectionState.CONNECTING
            _statusMessage.value = msg(R.string.status_waking_incoming)
        }
        startNetworkMonitor()
        connectNow()
    }

    /** True while FCM wake is connecting — don't release foreground service yet. */
    fun isWakingForIncoming(): Boolean =
        shouldRun && !keepConnected && _connectionState.value != ConnectionState.STOPPED

    fun stop() {
        shouldRun = false
        keepConnected = false
        stopNetworkMonitor()
        stopCallRecoveryLoop()
        reconnectJob?.cancel()
        pingJob?.cancel()
        idleJob?.cancel()
        stopAudio()
        scope.launch { discardCallRecording() }
        stopRingback()
        CallAudioFocus.release(appContext)
        IncomingCallNotifier.dismiss(appContext)
        OngoingCallNotifier.dismiss(appContext)
        TelecomBridge.clearConnections()
        webSocket?.disconnect()
        webSocket = null
        reconnectAttempt = 0
        _callReconnecting.value = false
        pendingHangupCallId = null
        _connectionState.value = ConnectionState.STOPPED
        _statusMessage.value = msg(R.string.status_stopped)
        _incomingCall.value = null
        _outgoingCall.value = null
        _activeCall.value = null
    }

    /** Place outgoing call via Android Telecom when available. */
    fun dialViaTelecom(number: String) {
        scope.launch {
            dialWhenReadyViaTelecom(number)
        }
    }

    suspend fun dialWhenReadyViaTelecom(number: String) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) {
            _statusMessage.value = msg(R.string.status_enter_number)
            return
        }
        if (_activeCall.value != null || _incomingCall.value != null || _outgoingCall.value != null) {
            _statusMessage.value = msg(R.string.status_already_in_call)
            return
        }
        connectForOutgoing()
        if (_connectionState.value != ConnectionState.REGISTERED) {
            _statusMessage.value = msg(R.string.status_connecting)
            if (!awaitRegistered(DIAL_CONNECT_TIMEOUT_MS)) {
                _statusMessage.value = msg(R.string.status_could_not_connect)
                return
            }
        }
        if (TelecomBridge.placeOutgoing(trimmed)) {
            return
        }
        dial(trimmed)
    }

    fun acceptCallFromTelecom(callId: UUID) {
        if (_incomingCall.value?.callId != callId) return
        acceptCall()
    }

    fun rejectCallFromTelecom(callId: UUID) {
        if (_incomingCall.value?.callId != callId) return
        IncomingCallNotifier.dismiss(appContext)
        if (_connectionState.value == ConnectionState.REGISTERED) {
            webSocket?.sendControl(Protocol.REJECT, callId)
        }
        _incomingCall.value = null
        scheduleGoIdle()
    }

    fun hangupCallFromTelecom(callId: UUID) {
        if (_activeCall.value?.callId != callId &&
            _outgoingCall.value?.callId != callId &&
            _incomingCall.value?.callId != callId
        ) {
            return
        }
        pendingHangupCallId = callId
        stopRingback()
        webSocket?.sendControl(Protocol.HANGUP, callId)
        scope.launch { completeCallEnd(callId, "hangup") }
        IncomingCallNotifier.dismiss(appContext)
        stopAudio()
        _incomingCall.value = null
        _outgoingCall.value = null
        _activeCall.value = null
        _callReconnecting.value = false
        if (shouldRun && _connectionState.value != ConnectionState.REGISTERED) {
            reconnectAttempt = 0
            scheduleReconnect()
        }
        scheduleGoIdle()
    }

    fun dial(number: String) {
        if (_activeCall.value != null || _incomingCall.value != null || _outgoingCall.value != null) {
            _statusMessage.value = msg(R.string.status_already_in_call)
            return
        }
        if (_connectionState.value != ConnectionState.REGISTERED) {
            _statusMessage.value = msg(R.string.status_connect_first)
            return
        }
        val trimmed = number.trim()
        if (trimmed.isEmpty()) {
            _statusMessage.value = msg(R.string.status_enter_number)
            return
        }
        webSocket?.sendDial(trimmed)
        pendingDialNumber = trimmed
        scope.launch { callHistoryStore.saveLastDialed(trimmed) }
        _statusMessage.value = msg(R.string.status_dialing, trimmed)
    }

    /** Connect if needed and dial once the WebSocket is registered. */
    suspend fun dialWhenReady(number: String) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) {
            _statusMessage.value = msg(R.string.status_enter_number)
            return
        }
        if (_activeCall.value != null || _incomingCall.value != null || _outgoingCall.value != null) {
            _statusMessage.value = msg(R.string.status_already_in_call)
            return
        }
        connectForOutgoing()
        if (_connectionState.value != ConnectionState.REGISTERED) {
            _statusMessage.value = msg(R.string.status_connecting)
            if (!awaitRegistered(DIAL_CONNECT_TIMEOUT_MS)) {
                _statusMessage.value = msg(R.string.status_could_not_connect)
                return
            }
        }
        dial(trimmed)
    }

    private suspend fun awaitRegistered(timeoutMs: Long): Boolean {
        if (_connectionState.value == ConnectionState.REGISTERED) {
            return true
        }
        return try {
            withTimeout(timeoutMs) {
                connectionState.first { state ->
                    state == ConnectionState.REGISTERED ||
                        state == ConnectionState.ERROR ||
                        state == ConnectionState.STOPPED
                }
            }
            _connectionState.value == ConnectionState.REGISTERED
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    fun cancelOutgoing() {
        hangupCall()
    }

    fun acceptCall() {
        scope.launch {
            val call = _incomingCall.value ?: return@launch
            IncomingCallNotifier.stopAlert(appContext)
            if (_connectionState.value != ConnectionState.REGISTERED) {
                _statusMessage.value = msg(R.string.status_connecting)
                if (!awaitRegistered(PUSH_ACCEPT_TIMEOUT_MS)) {
                    _statusMessage.value = msg(R.string.status_could_not_connect)
                    return@launch
                }
            }
            webSocket?.sendControl(Protocol.ACCEPT, call.callId)
        }
    }

    fun rejectCall() {
        val call = _incomingCall.value ?: return
        IncomingCallNotifier.dismiss(appContext)
        if (_connectionState.value == ConnectionState.REGISTERED) {
            webSocket?.sendControl(Protocol.REJECT, call.callId)
        }
        TelecomBridge.notifyEnded(call.callId, "rejected")
        _incomingCall.value = null
        scheduleGoIdle()
    }

    fun hangupCall() {
        val callId = _activeCall.value?.callId
            ?: _outgoingCall.value?.callId
            ?: _incomingCall.value?.callId
            ?: return
        pendingHangupCallId = callId
        stopRingback()
        clearUserAudioRoute()
        webSocket?.sendControl(Protocol.HANGUP, callId)
        scope.launch { completeCallEnd(callId, "hangup") }
        IncomingCallNotifier.dismiss(appContext)
        stopAudio()
        OngoingCallNotifier.dismiss(appContext)
        TelecomBridge.notifyEnded(callId, "hangup")
        _incomingCall.value = null
        _outgoingCall.value = null
        _activeCall.value = null
        _callReconnecting.value = false
        if (shouldRun && _connectionState.value != ConnectionState.REGISTERED) {
            reconnectAttempt = 0
            scheduleReconnect()
        }
        scheduleGoIdle()
    }

    fun cycleAudioRoute() {
        val newRoute = when {
            audioSession != null -> audioSession!!.cycleOutputRoute()
            ringbackRouter != null -> ringbackRouter!!.cycleOutputRoute()
            else -> return
        }
        rememberUserAudioRoute(newRoute)
        syncAudioRouteState()
        syncTelecomAudioRoute(newRoute)
    }

    fun onTelecomAudioRouteChanged(callId: UUID, route: CallAudioRoute) {
        if (_activeCall.value?.callId != callId) return
        val preferred = userAudioRoute
        if (System.currentTimeMillis() < routeSettlingUntilMs) {
            if (preferred != null && preferred != route) {
                audioSession?.applyRoute(preferred)
            }
            publishAudioRoute(preferred ?: route)
            return
        }
        if (preferred != null && preferred != route) {
            audioSession?.applyRoute(preferred)
            syncTelecomAudioRoute(preferred)
            publishAudioRoute(preferred)
            return
        }
        publishAudioRoute(route)
    }

    fun toggleMicMute() {
        val session = audioSession ?: return
        session.setMuted(!session.isMuted())
        _micMuted.value = session.isMuted()
    }

    fun toggleCallRecording() {
        if (_activeCall.value == null) return
        if (_callRecording.value) {
            callRecorder?.pause()
            _callRecording.value = false
            return
        }
        val callId = _activeCall.value?.callId ?: return
        if (callRecorder == null) {
            scope.launch { startCallRecording(callId) }
        } else {
            callRecorder?.resume()
            _callRecording.value = true
        }
    }

    fun toggleHold() {
        val callId = _activeCall.value?.callId ?: return
        val newHeld = !_callHeld.value
        webSocket?.sendHold(callId, newHeld)
    }

    fun blindTransfer(target: String) {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) {
            _statusMessage.value = msg(R.string.status_enter_number)
            return
        }
        val callId = _activeCall.value?.callId ?: return
        webSocket?.sendTransfer(callId, trimmed)
        _statusMessage.value = msg(R.string.status_transferring, trimmed)
    }

    fun hasExternalAudioDevice(): Boolean {
        return audioSession?.hasExternalAudioDevice()
            ?: ringbackRouter?.hasExternalAudioDevice()
            ?: false
    }

    fun sendDtmf(digit: String) {
        if (digit.length != 1 || digit !in "0123456789*#") {
            return
        }
        val callId = _activeCall.value?.callId ?: return
        audioSession?.playDtmfTone(digit[0])
        webSocket?.sendDtmf(callId, digit)
    }

    private fun connectNow() {
        if (connectInFlight) {
            return
        }
        if (!networkMonitor.hasInternet()) {
            _statusMessage.value = msg(R.string.status_waiting_network)
            scheduleReconnect()
            return
        }
        connectInFlight = true
        scope.launch {
            try {
                val settings = settingsStore.settings.first()
                if (!settings.isComplete) {
                    _connectionState.value = ConnectionState.ERROR
                    _statusMessage.value = msg(R.string.status_fill_credentials)
                    shouldRun = false
                    connectInFlight = false
                    return@launch
                }
                if (currentFcmToken.isNullOrBlank()) {
                    currentFcmToken = fcmManager.refreshAndUpload()
                } else {
                    launch(Dispatchers.IO) {
                        fcmManager.refreshAndUpload()
                    }
                }
                connect(settings, currentFcmToken)
            } catch (e: Exception) {
                Log.e(TAG, "connectNow failed", e)
                connectInFlight = false
                scheduleReconnect()
            }
        }
    }

    private fun connect(settings: DeviceSettings, fcmToken: String?) {
        scope.launch { refreshBranding(settings.wsUrl) }
        if (_connectionState.value == ConnectionState.REGISTERED && webSocket != null) {
            connectInFlight = false
            return
        }
        if (_connectionState.value == ConnectionState.CONNECTING && webSocket != null) {
            connectInFlight = false
            return
        }
        reconnectJob?.cancel()
        pingJob?.cancel()
        idleJob?.cancel()
        webSocket?.disconnect()
        webSocket = null

        _connectionState.value = ConnectionState.CONNECTING
        if (_callReconnecting.value) {
            _statusMessage.value = msg(R.string.status_reconnecting)
        } else {
            _statusMessage.value = msg(R.string.status_connecting_to, settings.wsUrl)
        }

        webSocket = DeviceWebSocket(
            wsUrl = settings.wsUrl,
            deviceId = settings.deviceId,
            deviceToken = settings.deviceToken,
            fcmToken = fcmToken,
            listener = this,
        ).also { it.connect() }
    }

    private fun scheduleReconnect() {
        if (!shouldRun) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = if (_callReconnecting.value || hasCallUi()) {
                CALL_RECOVERY_INTERVAL_MS
            } else {
                minOf(
                    RECONNECT_DELAY_MS * (1L shl reconnectAttempt.coerceAtMost(4)),
                    RECONNECT_MAX_DELAY_MS,
                )
            }
            delay(delayMs)
            if (!shouldRun) return@launch
            if (_connectionState.value == ConnectionState.REGISTERED && webSocket != null) {
                return@launch
            }
            connectNow()
        }
    }

    private fun startCallRecoveryLoop() {
        callRecoveryJob?.cancel()
        callRecoveryJob = scope.launch {
            while (_callReconnecting.value && shouldRun) {
                if (_connectionState.value == ConnectionState.REGISTERED && webSocket != null) {
                    break
                }
                if (networkMonitor.hasInternet()) {
                    if (!connectInFlight) {
                        reconnectAttempt = 0
                        connectNow()
                    }
                } else {
                    _statusMessage.value = msg(R.string.status_waiting_network)
                }
                delay(CALL_RECOVERY_INTERVAL_MS)
            }
        }
    }

    private fun stopCallRecoveryLoop() {
        callRecoveryJob?.cancel()
        callRecoveryJob = null
    }

    private fun onNetworkAvailable() {
        if (!shouldRun) return
        if (_connectionState.value == ConnectionState.REGISTERED && webSocket != null) {
            return
        }
        Log.i(TAG, "Network available — reconnecting WebSocket")
        reconnectAttempt = 0
        connectInFlight = false
        reconnectJob?.cancel()
        connectNow()
    }

    private fun startNetworkMonitor() {
        networkMonitor.start()
    }

    private fun stopNetworkMonitor() {
        networkMonitor.stop()
    }

    private fun scheduleGoIdle() {
        if (keepConnected || !shouldRun || appInForeground) {
            return
        }
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_DISCONNECT_MS)
            if (
                shouldRun &&
                !keepConnected &&
                _activeCall.value == null &&
                _incomingCall.value == null &&
                _outgoingCall.value == null
            ) {
                goIdle()
            }
        }
    }

    private fun scheduleForegroundRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            val backgroundPush = settingsStore.settings.first().backgroundPushMode
            if (backgroundPush) {
                keepConnected = false
                if (_connectionState.value == ConnectionState.IDLE) {
                    DeviceService.releaseForeground(appContext)
                } else {
                    goIdle()
                }
                return@launch
            }
            delay(IDLE_DISCONNECT_MS)
            if (
                appInForeground ||
                _activeCall.value != null ||
                _incomingCall.value != null ||
                _outgoingCall.value != null ||
                queuedDialNumber != null
            ) {
                return@launch
            }
            keepConnected = false
            if (shouldRun && _connectionState.value == ConnectionState.REGISTERED) {
                goIdle()
            }
        }
    }

    private fun goIdle() {
        disconnectToIdle()
        scope.launch {
            val backgroundPush = settingsStore.settings.first().backgroundPushMode
            _statusMessage.value = if (backgroundPush) {
                msg(R.string.status_ready_push_wake)
            } else {
                msg(R.string.status_idle_plain)
            }
            if (backgroundPush) {
                val settings = settingsStore.settings.first()
                if (settings.isComplete) {
                    currentFcmToken = fcmManager.refreshAndUpload()
                }
                DeviceService.releaseForeground(appContext)
            }
        }
    }

    private fun disconnectToIdle() {
        idleJob?.cancel()
        reconnectJob?.cancel()
        pingJob?.cancel()
        stopCallRecoveryLoop()
        stopNetworkMonitor()
        webSocket?.disconnect()
        webSocket = null
        shouldRun = false
        connectInFlight = false
        _connectionState.value = ConnectionState.IDLE
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (shouldRun && _connectionState.value == ConnectionState.REGISTERED) {
                delay(PING_INTERVAL_MS)
                webSocket?.sendPing()
            }
        }
    }

    private suspend fun startCallRecording(callId: UUID) {
        if (_activeCall.value?.callId != callId) return
        if (callRecorder != null) {
            callRecorder?.resume()
            _callRecording.value = true
            return
        }
        val file = withContext(Dispatchers.IO) {
            CallRecordingStorage.prepareFile(appContext, callId)
        }
        withContext(Dispatchers.IO) {
            CallRecordingStorage.enforceQuota(
                appContext,
                protectedPaths = setOf(file.absolutePath),
            )
        }
        if (_activeCall.value?.callId != callId) return
        val recorder = CallRecorder(file).also { it.start() }
        callRecorder = recorder
        audioSession?.setRecorder(recorder)
        _callRecording.value = true
        Log.i(TAG, "Call recording started: ${file.name}")
    }

    private suspend fun finishCallRecording(): CallRecordingResult? {
        if (finishedRecording != null) {
            return finishedRecording
        }
        val recorder = callRecorder ?: return null
        callRecorder = null
        audioSession?.setRecorder(null)
        _callRecording.value = false
        val result = withContext(Dispatchers.IO) { recorder.stop() }
        finishedRecording = result
        if (result != null) {
            Log.i(TAG, "Call recording saved: ${result.path} (${result.durationSec}s)")
        } else {
            Log.w(TAG, "Call recording produced no file")
        }
        return result
    }

    private suspend fun discardCallRecording() {
        withContext(Dispatchers.IO) {
            callRecorder?.stop()?.let { CallRecordingStorage.deleteFile(it.path) }
        }
        callRecorder = null
        finishedRecording = null
        audioSession?.setRecorder(null)
        _callRecording.value = false
    }

    private fun startAudio(callId: UUID, preferredRoute: CallAudioRoute = capturePreferredAudioRoute()) {
        CallAudioFocus.acquire(appContext)
        DeviceService.ensureInCallForeground(appContext)
        stopAudio(resetRouteUi = false)
        stopRingback(releaseAudio = false)
        audioSession = CallAudioSession(
            context = appContext,
            callId = callId,
            onSendAudio = { payload -> webSocket?.sendAudio(payload) },
            onRouteChanged = { route -> scope.launch { publishAudioRoute(route) } },
        ).also { session ->
            rememberUserAudioRoute(preferredRoute)
            try {
                session.start(preferredRoute)
                callRecorder?.let { session.setRecorder(it) }
                publishAudioRoute(session.currentRoute())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start call audio", e)
            }
        }
    }

    private fun capturePreferredAudioRoute(): CallAudioRoute {
        userAudioRoute?.let { return it }
        ringbackRouter?.let { router ->
            if (router.isSpeakerForced()) {
                return CallAudioRoute.SPEAKER
            }
            return router.currentRoute()
        }
        CallConnectionRegistry.get(
            _activeCall.value?.callId
                ?: _outgoingCall.value?.callId
                ?: _incomingCall.value?.callId
                ?: return _audioRoute.value,
        )?.let { connection ->
            return connection.preferredAudioRoute()
        }
        return _audioRoute.value
    }

    private fun publishAudioRoute(route: CallAudioRoute) {
        val displayRoute = if (System.currentTimeMillis() < routeSettlingUntilMs) {
            userAudioRoute ?: route
        } else {
            route
        }
        _audioRoute.value = displayRoute
        _speakerOn.value = displayRoute == CallAudioRoute.SPEAKER
    }

    private fun beginRouteSettling(durationMs: Long = ROUTE_SETTLING_MS) {
        routeSettlingUntilMs = System.currentTimeMillis() + durationMs
        userAudioRoute?.let { publishAudioRoute(it) }
    }

    private fun rememberUserAudioRoute(route: CallAudioRoute) {
        userAudioRoute = route
    }

    private fun applyUserAudioRoute(
        route: CallAudioRoute,
        session: CallAudioSession? = audioSession,
    ) {
        rememberUserAudioRoute(route)
        session?.applyRoute(route)
        syncAudioRouteState(session)
    }

    private fun syncAudioRouteState(session: CallAudioSession? = audioSession) {
        val route = session?.currentRoute() ?: userAudioRoute ?: CallAudioRoute.EARPIECE
        publishAudioRoute(route)
    }

    private fun syncTelecomAudioRoute(route: CallAudioRoute) {
        val callId = _activeCall.value?.callId
            ?: _outgoingCall.value?.callId
            ?: _incomingCall.value?.callId
            ?: return
        CallConnectionRegistry.get(callId)?.setPreferredAudioRoute(route)
    }

    private fun enforcePreferredAudioRoute(callId: UUID) {
        if (_activeCall.value?.callId != callId) return
        val preferred = userAudioRoute ?: return
        audioSession?.applyRoute(preferred)
        syncTelecomAudioRoute(preferred)
        publishAudioRoute(preferred)
    }

    private fun scheduleRouteEnforcement(callId: UUID) {
        scope.launch {
            delay(ROUTE_ENFORCE_DELAY_MS)
            enforcePreferredAudioRoute(callId)
            delay(ROUTE_SETTLING_MS - ROUTE_ENFORCE_DELAY_MS)
            routeSettlingUntilMs = 0L
            enforcePreferredAudioRoute(callId)
        }
    }

    private fun clearUserAudioRoute() {
        userAudioRoute = null
    }

    private fun applyCallHold(held: Boolean) {
        _callHeld.value = held
        audioSession?.setHeld(held)
    }

    private fun stopAudio(resetRouteUi: Boolean = true) {
        audioSession?.setRecorder(null)
        audioSession?.stop()
        audioSession = null
        _micMuted.value = false
        _callHeld.value = false
        if (resetRouteUi) {
            _speakerOn.value = false
            _audioRoute.value = CallAudioRoute.EARPIECE
            _callConnectedAt.value = null
        }
    }

    private fun startRingback() {
        if (ringbackPlayer != null) {
            return
        }
        CallAudioFocus.acquire(appContext)
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        ringbackAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        ringbackRouter = CallAudioRouter(appContext) { route ->
            scope.launch { publishAudioRoute(route) }
        }.also { router ->
            router.start()
            userAudioRoute?.let { router.applyRoute(it) }
            syncAudioRouteStateFromRingback(router)
        }
        ringbackPlayer = RingbackTonePlayer().also { it.start(scope) }
    }

    private fun syncAudioRouteStateFromRingback(router: CallAudioRouter) {
        publishAudioRoute(router.currentRoute())
    }

    private fun stopRingback(releaseAudio: Boolean = true) {
        ringbackPlayer?.stop()
        ringbackPlayer = null
        ringbackRouter?.let { router ->
            if (releaseAudio) {
                router.stop()
            } else {
                router.releaseKeepRoute()
            }
        }
        ringbackRouter = null
        if (releaseAudio) {
            appContext.getSystemService(AudioManager::class.java).mode = ringbackAudioMode
        }
    }

    override fun onConnected() {
        connectInFlight = false
        _connectionState.value = ConnectionState.CONNECTED
        _statusMessage.value = msg(R.string.status_connected_registering)
    }

    override fun onRegistered(organizationName: String?) {
        scope.launch {
            if (!organizationName.isNullOrBlank()) {
                applyOrganizationName(organizationName)
            } else {
                refreshBranding(settingsStore.settings.first().wsUrl)
            }
        }
        connectInFlight = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        stopCallRecoveryLoop()
        pendingHangupCallId?.let { callId ->
            pendingHangupCallId = null
            webSocket?.sendControl(Protocol.HANGUP, callId)
            _callReconnecting.value = false
            _connectionState.value = ConnectionState.REGISTERED
            _statusMessage.value = msg(R.string.status_online_ready)
            startPingLoop()
            return
        }
        _connectionState.value = ConnectionState.REGISTERED
        _statusMessage.value = if (keepConnected) {
            msg(R.string.status_online_ready)
        } else {
            msg(R.string.status_connecting_incoming)
        }
        startPingLoop()
        if (_callReconnecting.value || hasCallUi()) {
            scheduleResyncFallback()
        }
        queuedDialNumber?.let { number ->
            queuedDialNumber = null
            if (_outgoingCall.value == null && _activeCall.value == null) {
                dial(number)
            }
        }
    }

    private suspend fun refreshBranding(wsUrl: String) {
        val base = ServerUrls.httpBaseFromWs(wsUrl) ?: return
        val name = BrandingClient.fetch(base) ?: return
        applyOrganizationName(name)
    }

    private suspend fun applyOrganizationName(raw: String?) {
        val normalized = BrandingClient.normalize(raw)
        BrandingHolder.update(normalized)
        if (hasCallUi()) return
        val current = settingsStore.settings.first()
        if (current.organizationName != normalized) {
            settingsStore.save(current.copy(organizationName = normalized))
        }
    }

    private fun scheduleResyncFallback() {
        scope.launch {
            delay(RESYNC_WAIT_MS)
            if (!_callReconnecting.value) return@launch
            Log.w(TAG, "call_resync not received — keeping call UI, clearing reconnect flag")
            _callReconnecting.value = false
        }
    }

    override fun onDisconnected(reason: String) {
        connectInFlight = false
        pingJob?.cancel()
        webSocket = null
        val hadCall = hasCallUi()
        if (hadCall && shouldRun) {
            _callReconnecting.value = true
            reconnectAttempt++
            _connectionState.value = ConnectionState.CONNECTING
            _statusMessage.value = msg(R.string.status_reconnecting)
            startNetworkMonitor()
            startCallRecoveryLoop()
            scheduleReconnect()
            return
        }
        stopAudio()
        if (_activeCall.value != null) {
            _incomingCall.value = null
            _outgoingCall.value = null
            _activeCall.value = null
        }
        _callReconnecting.value = false
        if (shouldRun) {
            reconnectAttempt++
            _connectionState.value = ConnectionState.CONNECTING
            _statusMessage.value = msg(R.string.status_disconnected, reason)
            scheduleReconnect()
        } else if (_connectionState.value != ConnectionState.STOPPED) {
            reconnectAttempt = 0
            _connectionState.value = ConnectionState.IDLE
            _statusMessage.value = msg(R.string.status_ready_push_wake)
        }
    }

    override fun onCallResync(
        callId: UUID,
        caller: String,
        called: String,
        outgoing: Boolean,
        state: String,
        held: Boolean,
    ) {
        connectInFlight = false
        stopCallRecoveryLoop()
        _callReconnecting.value = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        when (state) {
            "active" -> {
                val preferredRoute = capturePreferredAudioRoute()
                rememberUserAudioRoute(preferredRoute)
                _incomingCall.value = null
                _outgoingCall.value = null
                _activeCall.value = ActiveCallUi(
                    callId = callId,
                    caller = caller,
                    called = called,
                    outgoing = outgoing,
                )
                _statusMessage.value = if (outgoing) {
                    msg(R.string.status_connected_to, called)
                } else {
                    msg(R.string.status_in_call_with, caller)
                }
                beginRouteSettling()
                CallConnectionRegistry.get(callId)?.setPreferredAudioRoute(preferredRoute)
                startAudio(callId, preferredRoute)
                TelecomBridge.notifyActive(callId)
                CallConnectionRegistry.get(callId)?.setPreferredAudioRoute(preferredRoute)
                scheduleRouteEnforcement(callId)
                applyCallHold(held)
            }
            "incoming" -> {
                _outgoingCall.value = null
                _activeCall.value = null
                _incomingCall.value = IncomingCallUi(callId, caller, called)
                _statusMessage.value = msg(R.string.status_incoming_from, caller)
            }
            "ringing", "outgoing" -> {
                _incomingCall.value = null
                _activeCall.value = null
                _outgoingCall.value = OutgoingCallUi(
                    callId = callId,
                    caller = caller,
                    number = called,
                    ringing = state == "ringing",
                )
                _statusMessage.value = if (state == "ringing") {
                    msg(R.string.outgoing_ringing)
                } else {
                    msg(R.string.status_calling, called)
                }
                startRingback()
            }
        }
        enrichResyncContacts(callId, caller, called, outgoing, state)
    }

    private fun enrichResyncContacts(
        callId: UUID,
        caller: String,
        called: String,
        outgoing: Boolean,
        state: String,
    ) {
        val lookupNumber = if (outgoing) called else caller
        scope.launch {
            val match = withContext(Dispatchers.IO) {
                ContactLookup.lookup(appContext, lookupNumber)
            } ?: return@launch
            when (state) {
                "active" -> {
                    if (_activeCall.value?.callId != callId) return@launch
                    _activeCall.value = _activeCall.value!!.copy(
                        contactName = match.name,
                        contactId = match.contactId,
                    )
                }
                "incoming" -> {
                    if (_incomingCall.value?.callId != callId) return@launch
                    _incomingCall.value = IncomingCallUi(
                        callId, caller, called, match.name, match.contactId,
                    )
                }
                "ringing", "outgoing" -> {
                    if (_outgoingCall.value?.callId != callId) return@launch
                    _outgoingCall.value = _outgoingCall.value!!.copy(
                        contactName = match.name,
                        contactId = match.contactId,
                    )
                }
            }
        }
    }

    private fun hasCallUi(): Boolean =
        _activeCall.value != null || _incomingCall.value != null || _outgoingCall.value != null

    override fun onCallHoldChanged(callId: UUID, held: Boolean) {
        if (_activeCall.value?.callId != callId) return
        applyCallHold(held)
        _statusMessage.value = if (held) {
            msg(R.string.status_on_hold)
        } else {
            val active = _activeCall.value ?: return
            if (active.outgoing) {
                msg(R.string.status_connected_to, active.called)
            } else {
                msg(R.string.status_in_call_with, active.caller)
            }
        }
    }

    override fun onError(message: String) {
        Log.e(TAG, "server error: $message")
        _statusMessage.value = message
        if (message.contains("invalid credentials", ignoreCase = true)) {
            _connectionState.value = ConnectionState.ERROR
            shouldRun = false
            keepConnected = false
        }
    }

    override fun onDialing(callId: UUID, caller: String, number: String) {
        _incomingCall.value = null
        _outgoingCall.value = OutgoingCallUi(callId, caller, number)
        _statusMessage.value = msg(R.string.status_calling, number)
        startRingback()
        rememberPendingHistory(
            callId = callId,
            direction = CallDirection.OUTGOING,
            number = number,
            localEndpoint = caller,
        )
        TelecomBridge.notifyOutgoingDialing(callId, number)
        runCatching { OngoingCallNotifier.show(appContext, callId, number) }
        scope.launch {
            val match = withContext(Dispatchers.IO) {
                ContactLookup.lookup(appContext, number)
            }
            if (_outgoingCall.value?.callId == callId && match != null) {
                _outgoingCall.value = OutgoingCallUi(
                    callId,
                    caller,
                    number,
                    contactName = match.name,
                    contactId = match.contactId,
                )
                updatePendingContact(callId, match.name, match.contactId)
                OngoingCallNotifier.show(appContext, callId, match.name)
            }
        }
    }

    override fun onCallRinging(callId: UUID) {
        _outgoingCall.value = _outgoingCall.value?.takeIf { it.callId == callId }?.copy(ringing = true)
        _statusMessage.value = msg(R.string.outgoing_ringing)
        startRingback()
        TelecomBridge.notifyRinging(callId)
    }

    override fun onIncomingCall(callId: UUID, caller: String, called: String) {
        stopRingback()
        _outgoingCall.value = null
        _activeCall.value = null
        val alreadyShowing = _incomingCall.value?.callId == callId
        if (!alreadyShowing) {
            _incomingCall.value = IncomingCallUi(callId, caller, called)
            _statusMessage.value = msg(R.string.status_incoming_from, caller)
            rememberPendingHistory(
                callId = callId,
                direction = CallDirection.INCOMING,
                number = caller,
                localEndpoint = called,
            )
            IncomingCallNotifier.show(appContext, callId, caller, called, null)
            TelecomBridge.notifyIncoming(callId, caller, called)
        }
        scope.launch {
            val match = withContext(Dispatchers.IO) {
                ContactLookup.lookup(appContext, caller)
            }
            if (_incomingCall.value?.callId != callId) return@launch
            if (match != null) {
                _incomingCall.value = IncomingCallUi(
                    callId,
                    caller,
                    called,
                    match.name,
                    match.contactId,
                )
                updatePendingContact(callId, match.name, match.contactId)
                _statusMessage.value = msg(R.string.status_incoming_from, match.name)
                IncomingCallNotifier.updateCallerLabel(appContext, callId, caller, called, match.name)
                TelecomBridge.updateCallerLabel(callId, match.name)
            }
        }
    }

    override fun onCallAccepted(callId: UUID) {
        try {
            onCallAcceptedInternal(callId)
        } catch (e: Exception) {
            Log.e(TAG, "onCallAccepted failed callId=$callId", e)
        }
    }

    private fun onCallAcceptedInternal(callId: UUID) {
        val preferredRoute = capturePreferredAudioRoute()
        rememberUserAudioRoute(preferredRoute)
        IncomingCallNotifier.stopAlert(appContext)
        val incoming = _incomingCall.value
        val outgoing = _outgoingCall.value
        val contactName = incoming?.contactName ?: outgoing?.contactName
        val contactId = incoming?.contactId ?: outgoing?.contactId
        val active = when {
            incoming?.callId == callId ->
                ActiveCallUi(
                    callId,
                    incoming.caller,
                    incoming.called,
                    false,
                    incoming.contactName,
                    incoming.contactId,
                )
            outgoing?.callId == callId ->
                ActiveCallUi(
                    callId,
                    outgoing.caller,
                    outgoing.number,
                    true,
                    outgoing.contactName,
                    outgoing.contactId,
                )
            else ->
                ActiveCallUi(
                    callId,
                    outgoing?.caller ?: incoming?.caller ?: "",
                    outgoing?.number ?: incoming?.called ?: pendingDialNumber,
                    outgoing != null || incoming == null,
                    contactName,
                    contactId,
                )
        }
        beginRouteSettling()
        _activeCall.value = active
        _incomingCall.value = null
        _outgoingCall.value = null
        pendingDialNumber = ""
        _statusMessage.value = if (active.outgoing) {
            msg(R.string.status_connected_to, active.called)
        } else {
            msg(R.string.status_in_call_with, active.caller)
        }
        val connectedAt = System.currentTimeMillis()
        callAcceptedAt[callId] = connectedAt
        _callConnectedAt.value = connectedAt
        CallConnectionRegistry.get(callId)?.setPreferredAudioRoute(preferredRoute)
        startAudio(callId, preferredRoute)
        TelecomBridge.notifyActive(callId)
        CallConnectionRegistry.get(callId)?.setPreferredAudioRoute(preferredRoute)
        scheduleRouteEnforcement(callId)
        val label = when {
            active.contactName != null -> active.contactName
            active.outgoing -> active.called
            else -> active.caller
        }
        runCatching {
            OngoingCallNotifier.show(appContext, callId, label)
        }.onFailure { e ->
            Log.w(TAG, "OngoingCallNotifier.show failed", e)
        }
        scope.launch {
            if (settingsStore.settings.first().recordCallsByDefault) {
                startCallRecording(callId)
            }
        }
    }

    override fun onCallEnded(callId: UUID, reason: String) {
        IncomingCallNotifier.dismiss(appContext)
        OngoingCallNotifier.dismiss(appContext)
        TelecomBridge.notifyEnded(callId, reason)
        stopRingback()
        clearUserAudioRoute()
        CallAudioFocus.release(appContext)
        connectInFlight = false
        stopCallRecoveryLoop()
        _callReconnecting.value = false
        pendingHangupCallId = null
        val shouldStopAudio = _activeCall.value?.callId == callId
        scope.launch { completeCallEnd(callId, reason) }
        if (_incomingCall.value?.callId == callId) {
            _incomingCall.value = null
        }
        if (_outgoingCall.value?.callId == callId) {
            _outgoingCall.value = null
        }
        if (shouldStopAudio) {
            _activeCall.value = null
            stopAudio()
        }
        _callHeld.value = false
        _statusMessage.value = when (reason) {
            "remote_hangup" -> msg(R.string.status_call_ended_remote)
            "rejected" -> msg(R.string.status_call_rejected)
            "hangup" -> msg(R.string.status_call_ended)
            "transferred" -> msg(R.string.status_call_transferred)
            "busy" -> msg(R.string.status_line_busy)
            "declined" -> msg(R.string.status_call_declined)
            "cancelled" -> msg(R.string.status_call_cancelled)
            "failed" -> msg(R.string.status_call_failed)
            "reconnect_timeout" -> msg(R.string.status_call_reconnect_timeout)
            else -> if (reason.isNotBlank()) {
                msg(R.string.status_call_ended_reason, reason)
            } else {
                msg(R.string.status_call_ended)
            }
        }
        if (shouldRun && (_connectionState.value != ConnectionState.REGISTERED || webSocket == null)) {
            reconnectAttempt = 0
            scheduleReconnect()
        }
        scheduleGoIdle()
    }

    private fun rememberPendingHistory(
        callId: UUID,
        direction: CallDirection,
        number: String,
        localEndpoint: String,
        contactName: String? = null,
        contactId: Long? = null,
    ) {
        pendingHistory[callId] = PendingHistory(
            callId = callId,
            direction = direction,
            number = number,
            localEndpoint = localEndpoint,
            contactName = contactName,
            contactId = contactId,
        )
    }

    private fun updatePendingContact(callId: UUID, contactName: String, contactId: Long) {
        pendingHistory[callId]?.let { pending ->
            pendingHistory[callId] = pending.copy(
                contactName = contactName,
                contactId = contactId,
            )
        }
    }

    private suspend fun completeCallEnd(callId: UUID, reason: String) {
        val recording = finishCallRecording()
        finalizeHistory(callId, reason, recording)
    }

    private suspend fun finalizeHistory(
        callId: UUID,
        reason: String,
        recording: CallRecordingResult? = null,
    ) {
        finishedRecording = null
        val pending = pendingHistory.remove(callId) ?: return
        val acceptedAt = callAcceptedAt.remove(callId)
        val status = mapHistoryStatus(pending.direction, reason, acceptedAt != null)
        val durationSec = acceptedAt?.let {
            ((System.currentTimeMillis() - it) / 1000L).toInt().coerceAtLeast(0)
        } ?: 0
        val resolvedRecording = recording ?: CallRecordingStorage.findNewestForCall(
            appContext,
            callId.toString(),
        )?.let { file ->
            CallRecordingResult(
                path = file.absolutePath,
                durationSec = CallRecordingStorage.estimateDurationSec(file),
            )
        }
        callHistoryStore.addEntry(
            CallHistoryEntry(
                id = callId.toString(),
                number = pending.number,
                localEndpoint = pending.localEndpoint,
                contactName = pending.contactName,
                contactId = pending.contactId,
                direction = pending.direction,
                status = status,
                startedAt = pending.startedAt,
                durationSec = durationSec,
                recordingPath = resolvedRecording?.path,
                recordingDurationSec = resolvedRecording?.durationSec ?: 0,
            ),
        )
        if (resolvedRecording != null) {
            _statusMessage.value = msg(
                R.string.status_recording_saved,
                formatRecordingDuration(resolvedRecording.durationSec),
            )
        }
        val count = callHistoryStore.unreadMissedCount.first()
        val settings = settingsStore.settings.first()
        MissedCallBadge.update(appContext, count, settings.missedCallNotifications)
    }

    private fun formatRecordingDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "${s}s"
    }

    private fun mapHistoryStatus(
        direction: CallDirection,
        reason: String,
        wasAccepted: Boolean,
    ): CallStatus {
        if (wasAccepted) return CallStatus.COMPLETED
        return when (reason) {
            "busy" -> CallStatus.BUSY
            "failed", "reconnect_timeout" -> CallStatus.FAILED
            "cancelled" -> CallStatus.CANCELLED
            "rejected", "declined" -> if (direction == CallDirection.INCOMING) {
                CallStatus.MISSED
            } else {
                CallStatus.REJECTED
            }
            "hangup", "remote_hangup" -> if (direction == CallDirection.INCOMING) {
                CallStatus.MISSED
            } else {
                CallStatus.CANCELLED
            }
            else -> if (direction == CallDirection.INCOMING) CallStatus.MISSED else CallStatus.CANCELLED
        }
    }

    override fun onAudio(callId: UUID, opus: ByteArray) {
        if (_activeCall.value?.callId == callId) {
            audioSession?.playIncoming(opus)
        }
    }

    companion object {
        private const val TAG = "DeviceClient"
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
        private const val PING_INTERVAL_MS = 30_000L
        private const val IDLE_DISCONNECT_MS = 5_000L
        private const val DIAL_CONNECT_TIMEOUT_MS = 20_000L
        private const val PUSH_ACCEPT_TIMEOUT_MS = 30_000L
        private const val RESYNC_WAIT_MS = 2_000L
        private const val CALL_RECOVERY_INTERVAL_MS = 1_000L
        private const val ROUTE_SETTLING_MS = 500L
        private const val ROUTE_ENFORCE_DELAY_MS = 80L
    }
}
