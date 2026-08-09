package uz.ex.sip2go.call

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import uz.ex.sip2go.R
import uz.ex.sip2go.SipTgApp
import uz.ex.sip2go.ui.ActiveCallScreen
import uz.ex.sip2go.ui.IncomingCallScreen
import uz.ex.sip2go.ui.theme.SipTgTheme

/** Full-screen overlay — works over lock screen and other apps when permitted. */
object IncomingCallOverlay {
    private const val TAG = "IncomingCallOverlay"

    private var overlayView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context.applicationContext)
    }

    fun show(context: Context, keepNotification: Boolean = false) {
        val appContext = context.applicationContext
        if (!canDrawOverlays(appContext)) {
            Log.w(TAG, "Overlay permission not granted")
            return
        }
        mainHandler.post {
            hideInternal()
            try {
                val wm = appContext.getSystemService(WindowManager::class.java)
                    ?: return@post
                val owner = OverlayLifecycleOwner().also { it.onCreate() }
                val composeView = ComposeView(appContext).apply {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnDetachedFromWindow,
                    )
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setContent { OverlayContent() }
                }
                wm.addView(composeView, buildLayoutParams(appContext))
                windowManager = wm
                overlayView = composeView
                lifecycleOwner = owner
                Log.i(TAG, "Overlay shown")
                if (!keepNotification) {
                    IncomingCallNotifier.onCallUiVisible(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay", e)
                hideInternal()
            }
        }
    }

    fun hide() {
        mainHandler.post { hideInternal() }
    }

    private fun hideInternal() {
        val view = overlayView
        val wm = windowManager
        overlayView = null
        windowManager = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = context.getString(R.string.overlay_window_title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
    @androidx.compose.runtime.Composable
    private fun OverlayContent() {
        val app = overlayView?.context?.applicationContext as? SipTgApp ?: return
        val incoming by app.deviceClient.incomingCall.collectAsState()
        val active by app.deviceClient.activeCall.collectAsState()
        val callReconnecting by app.deviceClient.callReconnecting.collectAsState()
        val audioRoute by app.deviceClient.audioRoute.collectAsState()
        val micMuted by app.deviceClient.micMuted.collectAsState()
        val callConnectedAt by app.deviceClient.callConnectedAt.collectAsState()

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
                        onToggleMute = { app.deviceClient.toggleMicMute() },
                        onCycleAudio = { app.deviceClient.cycleAudioRoute() },
                        onDtmf = { app.deviceClient.sendDtmf(it) },
                        onHangup = { app.deviceClient.hangupCall() },
                    )
                    incoming != null -> IncomingCallScreen(
                        caller = incoming!!.caller,
                        called = incoming!!.called,
                        contactName = incoming!!.contactName,
                        contactId = incoming!!.contactId,
                        reconnecting = callReconnecting,
                        onAccept = {
                            IncomingCallNotifier.onCallUiVisible(app)
                            app.deviceClient.acceptCall()
                        },
                        onReject = {
                            IncomingCallNotifier.onCallUiVisible(app)
                            app.deviceClient.rejectCall()
                        },
                    )
                }
            }
        }
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = registry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
}
