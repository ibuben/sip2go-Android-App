package uz.ex.sip2go

import android.Manifest
import android.media.RingtoneManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ex.sip2go.R
import uz.ex.sip2go.call.IncomingCallNotifier
import uz.ex.sip2go.call.IncomingCallOverlay
import uz.ex.sip2go.branding.BrandingHolder
import uz.ex.sip2go.data.AppLanguage
import uz.ex.sip2go.data.DeviceSettings
import uz.ex.sip2go.locale.AppLocale
import uz.ex.sip2go.service.ConnectionState
import uz.ex.sip2go.service.DeviceService
import uz.ex.sip2go.telecom.TelecomBridge
import uz.ex.sip2go.telecom.TelecomStatus
import uz.ex.sip2go.ui.OngoingCallScreen
import uz.ex.sip2go.history.MissedCallBadge
import uz.ex.sip2go.ui.PhoneAppScreen
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import uz.ex.sip2go.provision.ProvisionClaimResult
import uz.ex.sip2go.provision.ProvisionClient
import uz.ex.sip2go.provision.QrProvisionParser
import uz.ex.sip2go.ui.theme.SipTgTheme

class MainActivity : ComponentActivity() {
    private val app by lazy { application as SipTgApp }
    private val openHistoryRequest = mutableStateOf(false)
    private var ringtonePickerCallback: ((String?) -> Unit)? = null
    private var applyProvisionClaim: ((ProvisionClaimResult) -> Unit)? = null
    private var pendingProvisionRaw: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.withAppLocale(newBase))
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            ringtonePickerCallback = null
            return@registerForActivityResult
        }
        val data = result.data
        val picked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        val value = picked?.toString() ?: DeviceSettings.SILENT_RINGTONE
        ringtonePickerCallback?.invoke(value)
        ringtonePickerCallback = null
    }

    private fun launchRingtonePicker(currentUri: String?, onPicked: (String?) -> Unit) {
        ringtonePickerCallback = onPicked
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                when (currentUri) {
                    DeviceSettings.SILENT_RINGTONE -> null
                    null, "" -> null
                    else -> currentUri.toUri()
                },
            )
        }
        ringtonePickerLauncher.launch(intent)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchQrScannerInternal()
        } else {
            Toast.makeText(
                this,
                getString(R.string.settings_qr_camera_denied),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        lifecycleScope.launch {
            handleProvisionQr(contents)
        }
    }

    private fun launchQrScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchQrScannerInternal()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchQrScannerInternal() {
        qrScanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(getString(R.string.settings_qr_prompt))
                setBeepEnabled(false)
                setOrientationLocked(false)
            },
        )
    }

    private suspend fun handleProvisionQr(raw: String) {
        val payload = QrProvisionParser.parse(raw)
        if (payload == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.settings_qr_invalid),
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }
        val claimResult = ProvisionClient.claim(payload.server, payload.code)
        claimResult.onSuccess { claim ->
            withContext(Dispatchers.Main) {
                applyProvisionClaim?.invoke(claim)
            }
        }.onFailure { error ->
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.settings_qr_claim_failed, error.message ?: "?"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            maybeRequestOverlayPermission()
            maybeRequestFullScreenIntent()
            startDeviceService(pendingOutgoingConnect)
            pendingDialAfterConnect?.let { number ->
                pendingDialAfterConnect = null
                lifecycleScope.launch {
                    app.deviceClient.dialViaTelecom(number)
                }
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_mic_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        configureIncomingCallUi()
        super.onCreate(savedInstanceState)
        openHistoryRequest.value = intent.getBooleanExtra(EXTRA_OPEN_HISTORY, false)
        handleIncomingIntent(intent)
        handleProvisionIntent(intent)

        setContent {
            val scope = rememberCoroutineScope()
            val savedSettings by app.settingsStore.settings.collectAsState(
                initial = DeviceSettings(),
            )
            var wsUrl by rememberSaveable { mutableStateOf("") }
            var deviceId by rememberSaveable { mutableStateOf("") }
            var deviceToken by rememberSaveable { mutableStateOf("") }
            var backgroundPushMode by rememberSaveable { mutableStateOf(false) }
            var missedCallNotifications by rememberSaveable { mutableStateOf(true) }
            var recordCallsByDefault by rememberSaveable { mutableStateOf(false) }
            var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
            var appLanguageTag by rememberSaveable { mutableStateOf(AppLanguage.SYSTEM.tag) }
            var dialNumber by rememberSaveable { mutableStateOf("") }
            LaunchedEffect(savedSettings) {
                if (wsUrl.isEmpty() && deviceId.isEmpty()) {
                    wsUrl = savedSettings.wsUrl
                    deviceId = savedSettings.deviceId
                    deviceToken = savedSettings.deviceToken
                }
                backgroundPushMode = savedSettings.backgroundPushMode
                missedCallNotifications = savedSettings.missedCallNotifications
                recordCallsByDefault = savedSettings.recordCallsByDefault
                ringtoneUri = savedSettings.ringtoneUri
                appLanguageTag = savedSettings.appLanguage.tag
                BrandingHolder.update(savedSettings.organizationName)
            }
            val appLanguage = AppLanguage.fromTag(appLanguageTag)
            val draftSettings = DeviceSettings(
                wsUrl = wsUrl,
                deviceId = deviceId,
                deviceToken = deviceToken,
                organizationName = savedSettings.organizationName,
                backgroundPushMode = backgroundPushMode,
                missedCallNotifications = missedCallNotifications,
                recordCallsByDefault = recordCallsByDefault,
                ringtoneUri = ringtoneUri,
                appLanguage = appLanguage,
            )

            applyProvisionClaim = { claim ->
                wsUrl = claim.wsUrl
                deviceId = claim.deviceId
                deviceToken = claim.deviceToken
                BrandingHolder.update(claim.organizationName)
                scope.launch {
                    val updated = DeviceSettings(
                        wsUrl = claim.wsUrl,
                        deviceId = claim.deviceId,
                        deviceToken = claim.deviceToken,
                        organizationName = claim.organizationName,
                        backgroundPushMode = backgroundPushMode,
                        missedCallNotifications = missedCallNotifications,
                        recordCallsByDefault = recordCallsByDefault,
                        ringtoneUri = ringtoneUri,
                        appLanguage = appLanguage,
                    )
                    app.settingsStore.save(updated)
                    val label = claim.name.ifBlank { claim.endpointNumber }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.settings_qr_success, label),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            LaunchedEffect(Unit) {
                pendingProvisionRaw?.let { raw ->
                    pendingProvisionRaw = null
                    handleProvisionQr(raw)
                }
            }

            val connectionState by app.deviceClient.connectionState.collectAsState()
            val statusMessage by app.deviceClient.statusMessage.collectAsState()
            val incomingCall by app.deviceClient.incomingCall.collectAsState()
            val outgoingCall by app.deviceClient.outgoingCall.collectAsState()
            val activeCall by app.deviceClient.activeCall.collectAsState()
            val callReconnecting by app.deviceClient.callReconnecting.collectAsState()
            val audioRoute by app.deviceClient.audioRoute.collectAsState()
            val micMuted by app.deviceClient.micMuted.collectAsState()
            val callRecording by app.deviceClient.callRecording.collectAsState()
            val callHeld by app.deviceClient.callHeld.collectAsState()
            val callConnectedAt by app.deviceClient.callConnectedAt.collectAsState()
            val callHistory by app.callHistoryStore.history.collectAsState(initial = emptyList())
            val lastDialedNumber by app.callHistoryStore.lastDialedNumber.collectAsState(initial = null)
            val unreadMissedCount by app.callHistoryStore.unreadMissedCount.collectAsState(initial = 0)
            val openHistoryTab by openHistoryRequest
            var telecomStatus by remember { mutableStateOf(TelecomStatus.Unavailable) }
            LaunchedEffect(activeCall, incomingCall, outgoingCall, connectionState) {
                telecomStatus = withContext(Dispatchers.IO) {
                    runCatching {
                        TelecomBridge.ensureRegistered()
                        TelecomBridge.status()
                    }.getOrElse { TelecomStatus.Unavailable }
                }
            }
            val serviceRunning = connectionState != ConnectionState.STOPPED
            val isIdle = connectionState == ConnectionState.IDLE

            LaunchedEffect(incomingCall?.callId) {
                if (incomingCall != null) {
                    IncomingCallNotifier.onCallUiVisible(this@MainActivity)
                }
            }

            LaunchedEffect(outgoingCall?.callId) {
                if (outgoingCall != null) {
                    dialNumber = ""
                }
            }

            LaunchedEffect(unreadMissedCount, savedSettings.missedCallNotifications) {
                MissedCallBadge.update(
                    this@MainActivity,
                    unreadMissedCount,
                    savedSettings.missedCallNotifications,
                )
            }

            SipTgTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            activeCall != null || outgoingCall != null || incomingCall != null -> {
                                val active = activeCall
                                val outgoing = outgoingCall
                                val incoming = incomingCall
                                val callId = active?.callId ?: outgoing?.callId ?: incoming!!.callId
                                key(callId) {
                                    OngoingCallScreen(
                                        connected = active != null,
                                        caller = active?.caller
                                            ?: outgoing?.caller
                                            ?: incoming?.caller
                                            ?: "",
                                        called = active?.called
                                            ?: outgoing?.number
                                            ?: incoming?.called
                                            ?: "",
                                        contactName = active?.contactName
                                            ?: outgoing?.contactName
                                            ?: incoming?.contactName,
                                        contactId = active?.contactId
                                            ?: outgoing?.contactId
                                            ?: incoming?.contactId,
                                        outgoing = when {
                                            active != null -> active.outgoing
                                            outgoing != null -> true
                                            else -> false
                                        },
                                        ringing = outgoing?.ringing ?: false,
                                        incomingRinging = incoming != null && active == null,
                                        reconnecting = callReconnecting,
                                        callConnectedAt = callConnectedAt,
                                        audioRoute = audioRoute,
                                        micMuted = micMuted,
                                        callRecording = callRecording,
                                        callHeld = callHeld,
                                        onToggleMute = { app.deviceClient.toggleMicMute() },
                                        onToggleRecording = { app.deviceClient.toggleCallRecording() },
                                        onToggleHold = { app.deviceClient.toggleHold() },
                                        onTransfer = { app.deviceClient.blindTransfer(it) },
                                        onCycleAudio = { app.deviceClient.cycleAudioRoute() },
                                        onDtmf = { app.deviceClient.sendDtmf(it) },
                                        onHangup = { app.deviceClient.hangupCall() },
                                        onCancel = { app.deviceClient.cancelOutgoing() },
                                        onAccept = { app.deviceClient.acceptCall() },
                                        onReject = { app.deviceClient.rejectCall() },
                                    )
                                }
                            }
                            else -> {
                                val context = LocalContext.current
                                val overlayOk = IncomingCallOverlay.canDrawOverlays(context)
                                val fullScreenOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    context.getSystemService(NotificationManager::class.java)
                                        .canUseFullScreenIntent()
                                } else {
                                    true
                                }
                                PhoneAppScreen(
                                    settings = draftSettings,
                                    connectionState = connectionState,
                                    statusMessage = statusMessage,
                                    serviceRunning = serviceRunning,
                                    isIdle = isIdle,
                                    dialNumber = dialNumber,
                                    callHistory = callHistory,
                                    lastDialedNumber = lastDialedNumber,
                                    overlayPermissionGranted = overlayOk,
                                    fullScreenIntentGranted = fullScreenOk,
                                    onDialNumberChange = { dialNumber = it },
                                    onCall = { number ->
                                        dialNumber = ""
                                        scope.launch {
                                            app.settingsStore.save(draftSettings)
                                            if (connectionState == ConnectionState.STOPPED) {
                                                pendingDialAfterConnect = number
                                                requestPermissionsAndConnect(forOutgoing = true)
                                                return@launch
                                            }
                                            DeviceService.connect(this@MainActivity)
                                            app.deviceClient.dialViaTelecom(number)
                                        }
                                    },
                                    onOpenOverlaySettings = {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                "package:$packageName".toUri(),
                                            ),
                                        )
                                    },
                                    onOpenFullScreenSettings = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            startActivity(
                                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                    data = "package:$packageName".toUri()
                                                },
                                            )
                                        }
                                    },
                                    onSettingsChange = { updated ->
                                        val previousPush = backgroundPushMode
                                        wsUrl = updated.wsUrl
                                        deviceId = updated.deviceId
                                        deviceToken = updated.deviceToken
                                        backgroundPushMode = updated.backgroundPushMode
                                        missedCallNotifications = updated.missedCallNotifications
                                        recordCallsByDefault = updated.recordCallsByDefault
                                        ringtoneUri = updated.ringtoneUri
                                        appLanguageTag = updated.appLanguage.tag
                                        if (updated.backgroundPushMode != previousPush) {
                                            scope.launch {
                                                app.settingsStore.save(
                                                    app.settingsStore.settings.first().copy(
                                                        wsUrl = updated.wsUrl,
                                                        deviceId = updated.deviceId,
                                                        deviceToken = updated.deviceToken,
                                                        backgroundPushMode = updated.backgroundPushMode,
                                                        missedCallNotifications = updated.missedCallNotifications,
                                                        recordCallsByDefault = updated.recordCallsByDefault,
                                                        ringtoneUri = updated.ringtoneUri,
                                                    ),
                                                )
                                                app.deviceClient.applyBackgroundPushMode(
                                                    updated.backgroundPushMode,
                                                )
                                            }
                                        }
                                    },
                                    onSaveSettings = {
                                        scope.launch {
                                            app.settingsStore.save(draftSettings)
                                            MissedCallBadge.update(
                                                this@MainActivity,
                                                unreadMissedCount,
                                                draftSettings.missedCallNotifications,
                                            )
                                            Toast.makeText(this@MainActivity, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onToggleService = {
                                        if (serviceRunning && connectionState == ConnectionState.REGISTERED) {
                                            DeviceService.stop(this@MainActivity)
                                        } else if (serviceRunning && isIdle && !draftSettings.backgroundPushMode) {
                                            scope.launch {
                                                app.settingsStore.save(draftSettings)
                                                requestPermissionsAndConnect(forOutgoing = true)
                                            }
                                        } else if (serviceRunning) {
                                            DeviceService.stop(this@MainActivity)
                                        } else {
                                            scope.launch {
                                                app.settingsStore.save(draftSettings)
                                                requestPermissionsAndConnect(forOutgoing = false)
                                            }
                                        }
                                    },
                                    onPickRingtone = {
                                        launchRingtonePicker(ringtoneUri) { picked ->
                                            ringtoneUri = picked
                                            scope.launch {
                                                app.settingsStore.save(
                                                    app.settingsStore.settings.first().copy(
                                                        ringtoneUri = picked,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    onResetRingtone = {
                                        ringtoneUri = null
                                        scope.launch {
                                            app.settingsStore.save(
                                                app.settingsStore.settings.first().copy(
                                                    ringtoneUri = null,
                                                ),
                                            )
                                        }
                                    },
                                    onScanQr = { launchQrScanner() },
                                    onLanguageChange = { language ->
                                        if (language.tag == appLanguageTag) {
                                            return@PhoneAppScreen
                                        }
                                        appLanguageTag = language.tag
                                        scope.launch {
                                            app.settingsStore.save(draftSettings.copy(appLanguage = language))
                                            recreate()
                                        }
                                    },
                                    telecomStatus = telecomStatus,
                                    onOpenTelecomSettings = {
                                        TelecomBridge.openPhoneAccountSettings(this@MainActivity)
                                    },
                                    openHistoryTab = openHistoryTab,
                                    onHistorySeen = {
                                        openHistoryRequest.value = false
                                        scope.launch {
                                            app.callHistoryStore.markHistorySeen()
                                        }
                                    },
                                    onDeleteHistoryEntries = { ids ->
                                        scope.launch {
                                            app.callHistoryStore.deleteEntries(ids)
                                            val count = app.callHistoryStore.unreadMissedCount.first()
                                            MissedCallBadge.update(
                                                this@MainActivity,
                                                count,
                                                draftSettings.missedCallNotifications,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        uz.ex.sip2go.recording.CallRecordingPlayer.stop()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val settings = app.settingsStore.settings.first()
            if (!settings.isComplete) {
                return@launch
            }
            if (app.deviceClient.connectionState.value == ConnectionState.STOPPED) {
                requestPermissionsAndConnect(forOutgoing = true)
            } else {
                app.deviceClient.setAppInForeground(true)
                val state = app.deviceClient.connectionState.value
                val inCall = app.deviceClient.isCallSessionActive()
                if (state != ConnectionState.REGISTERED && !inCall) {
                    val backgroundPush = settings.backgroundPushMode
                    if (!(backgroundPush && state == ConnectionState.IDLE)) {
                        DeviceService.connect(this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onStop() {
        if (!app.deviceClient.isCallSessionActive()) {
            app.deviceClient.setAppInForeground(false)
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_HISTORY, false)) {
            openHistoryRequest.value = true
        }
        extractProvisionRaw(intent)?.let { raw ->
            lifecycleScope.launch { handleProvisionQr(raw) }
        }
    }

    private fun handleProvisionIntent(intent: Intent?) {
        pendingProvisionRaw = extractProvisionRaw(intent)
    }

    private fun extractProvisionRaw(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val raw = intent.data?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return raw.takeIf { QrProvisionParser.parse(it) != null }
    }

    private fun configureIncomingCallUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_INCOMING_CALL, false) != true) {
            return
        }
        configureIncomingCallUi()
    }

    private fun requestPermissionsAndConnect(forOutgoing: Boolean) {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        pendingOutgoingConnect = forOutgoing
        if (missing.isEmpty()) {
            maybeRequestOverlayPermission()
            maybeRequestFullScreenIntent()
            TelecomBridge.ensureRegistered()
            startDeviceService(forOutgoing)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun maybeRequestOverlayPermission() {
        if (IncomingCallOverlay.canDrawOverlays(this)) {
            return
        }
        Toast.makeText(
            this,
            getString(R.string.overlay_permission_hint),
            Toast.LENGTH_LONG,
        ).show()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri(),
            ),
        )
    }

    private fun maybeRequestFullScreenIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.canUseFullScreenIntent()) {
            return
        }
        Toast.makeText(
            this,
            getString(R.string.full_screen_intent_hint),
            Toast.LENGTH_LONG,
        ).show()
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = "package:$packageName".toUri()
            },
        )
    }

    private var pendingOutgoingConnect = false
    private var pendingDialAfterConnect: String? = null

    private fun startDeviceService(forOutgoing: Boolean = pendingOutgoingConnect) {
        if (forOutgoing) {
            DeviceService.connect(this)
            app.deviceClient.setAppInForeground(true)
        } else {
            DeviceService.startIdle(this)
        }
    }

    companion object {
        const val EXTRA_INCOMING_CALL = "incoming_call"
        const val EXTRA_OPEN_HISTORY = "open_history"
    }
}
