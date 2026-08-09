package uz.ex.sip2go.call

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import uz.ex.sip2go.SipTgApp
import uz.ex.sip2go.locale.AppLocale
import uz.ex.sip2go.ui.ActiveCallScreen
import uz.ex.sip2go.ui.IncomingCallScreen
import uz.ex.sip2go.ui.theme.SipTgTheme

/** Full-screen incoming/active call UI — shown over lock screen and other apps. */
class IncomingCallActivity : ComponentActivity() {
    private val app by lazy { application as SipTgApp }
    private val finishCallUi = { finish() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.withAppLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        configureLockScreen()
        super.onCreate(savedInstanceState)
        dismissKeyguardIfNeeded()

        val keyguard = getSystemService(KeyguardManager::class.java)
        val locked = keyguard?.isKeyguardLocked == true
        if (!locked) {
            IncomingCallNotifier.onCallUiVisible(this)
        }
        IncomingCallNotifier.registerCallActivity(finishCallUi)

        setContent {
            val incoming by app.deviceClient.incomingCall.collectAsState()
            val active by app.deviceClient.activeCall.collectAsState()
            val callReconnecting by app.deviceClient.callReconnecting.collectAsState()
            val audioRoute by app.deviceClient.audioRoute.collectAsState()
            val micMuted by app.deviceClient.micMuted.collectAsState()
            val callRecording by app.deviceClient.callRecording.collectAsState()
            val callHeld by app.deviceClient.callHeld.collectAsState()
            val callConnectedAt by app.deviceClient.callConnectedAt.collectAsState()

            LaunchedEffect(incoming, active) {
                if (incoming == null && active == null) {
                    kotlinx.coroutines.delay(1500)
                    if (app.deviceClient.incomingCall.value == null &&
                        app.deviceClient.activeCall.value == null
                    ) {
                        finish()
                    }
                }
            }

            SipTgTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        active != null -> ActiveCallScreen(
                            caller = active!!.caller,
                            called = active!!.called,
                            contactName = active!!.contactName,
                            contactId = active!!.contactId,
                            outgoing = active!!.outgoing,
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
                            onHangup = {
                                app.deviceClient.hangupCall()
                                finish()
                            },
                        )
                        incoming != null -> IncomingCallScreen(
                            caller = incoming!!.caller,
                            called = incoming!!.called,
                            contactName = incoming!!.contactName,
                            contactId = incoming!!.contactId,
                            reconnecting = callReconnecting,
                            onAccept = {
                                IncomingCallNotifier.onCallUiVisible(this@IncomingCallActivity)
                                app.deviceClient.acceptCall()
                            },
                            onReject = {
                                IncomingCallNotifier.onCallUiVisible(this@IncomingCallActivity)
                                app.deviceClient.rejectCall()
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        IncomingCallNotifier.unregisterCallActivity(finishCallUi)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreen()
    }

    private fun configureLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
    }

    private fun dismissKeyguardIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguard = getSystemService(KeyguardManager::class.java) ?: return
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        IncomingCallNotifier.onCallUiVisible(this@IncomingCallActivity)
                    }
                },
            )
        }
    }

    companion object {
        const val EXTRA_INCOMING_CALL = "incoming_call"
    }
}
