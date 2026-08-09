package uz.ex.sip2go.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uz.ex.sip2go.MainActivity
import uz.ex.sip2go.R
import uz.ex.sip2go.SipTgApp
import uz.ex.sip2go.call.IncomingCallActivity

class DeviceService : LifecycleService() {
    private lateinit var deviceClient: DeviceClient
    private var foregroundReleased = false
    private var foregroundInCall = false
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        deviceClient = (application as SipTgApp).deviceClient
        createChannels()
        observeState()
    }

    override fun onDestroy() {
        if (runningInstance === this) {
            runningInstance = null
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                deviceClient.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELEASE_FOREGROUND -> {
                releaseForegroundKeepAlive()
                return START_NOT_STICKY
            }
            ACTION_WAKE -> {
                foregroundReleased = false
                startAsForeground(inCall = false)
                val callId = intent?.getStringExtra(EXTRA_WAKE_CALL_ID)
                if (!callId.isNullOrBlank()) {
                    deviceClient.onPushIncomingCall(
                        callId,
                        intent.getStringExtra(EXTRA_WAKE_CALLER) ?: getString(R.string.label_unknown),
                        intent.getStringExtra(EXTRA_WAKE_CALLED) ?: "",
                    )
                } else {
                    deviceClient.wakeForIncomingCall()
                }
                return START_STICKY
            }
            ACTION_CONNECT -> {
                foregroundReleased = false
                startAsForeground(inCall = false)
                deviceClient.connectForOutgoing()
                return START_STICKY
            }
            ACTION_ENSURE_IN_CALL -> {
                foregroundReleased = false
                ensureForeground(inCall = true)
                return START_STICKY
            }
        }
        foregroundReleased = false
        startAsForeground(inCall = false)
        if (deviceClient.wantsPersistentConnection()) {
            deviceClient.connectForOutgoing()
        } else {
            deviceClient.startIdle()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun observeState() {
        val settingsStore = (application as SipTgApp).settingsStore
        lifecycleScope.launch {
            combine(
                deviceClient.connectionState,
                deviceClient.incomingCall,
                deviceClient.outgoingCall,
                deviceClient.activeCall,
                settingsStore.settings.map { it.backgroundPushMode },
            ) { state, incoming, outgoing, active, backgroundPush ->
                IdleUiState(
                    state = state,
                    hasIncoming = incoming != null,
                    hasInCall = active != null || outgoing != null || incoming != null,
                    backgroundPush = backgroundPush,
                )
            }.collect { ui ->
                if (ui.hasInCall) {
                    foregroundReleased = false
                    ensureForeground(inCall = true)
                    updateNotification(ui.state, caller = null, inCall = true)
                    return@collect
                }
                if (ui.backgroundPush && ui.state == ConnectionState.IDLE && !deviceClient.isWakingForIncoming()) {
                    releaseForegroundKeepAlive()
                    return@collect
                }
                if (!foregroundReleased && !ui.backgroundPush) {
                    updateNotification(ui.state, caller = null, inCall = false)
                }
            }
        }
    }

    private fun releaseForegroundKeepAlive() {
        if (foregroundReleased) {
            cancelConnectionNotification()
            return
        }
        foregroundReleased = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        cancelConnectionNotification()
        stopSelf()
    }

    private fun cancelConnectionNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private data class IdleUiState(
        val state: ConnectionState,
        val hasIncoming: Boolean,
        val hasInCall: Boolean,
        val backgroundPush: Boolean,
    )

    private fun ensureForeground(inCall: Boolean) {
        if (foregroundStarted && !foregroundReleased && foregroundInCall == inCall) {
            return
        }
        foregroundInCall = inCall
        foregroundStarted = true
        startAsForeground(inCall)
    }

    private fun startAsForeground(inCall: Boolean) {
        val notification = buildNotification(
            title = getString(R.string.notification_connecting),
            text = getString(R.string.notification_channel_name),
            ongoing = true,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    foregroundServiceType(inCall),
                )
            } catch (e: Exception) {
                android.util.Log.e("DeviceService", "startForeground failed", e)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun foregroundServiceType(inCall: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return if (inCall) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
        }
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    }

    private fun updateNotification(state: ConnectionState, caller: String?, inCall: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        val (title, text) = when {
            inCall -> getString(R.string.notification_in_call) to (caller ?: "")
            caller != null -> getString(R.string.notification_incoming, caller) to caller
            state == ConnectionState.REGISTERED -> getString(R.string.notification_connected) to
                getString(R.string.notification_channel_name)
            state == ConnectionState.IDLE -> getString(R.string.notification_idle) to
                getString(R.string.notification_idle_detail)
            else -> getString(R.string.notification_connecting) to getString(R.string.status_connecting)
        }
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(title, text, ongoing = true),
        )
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sip)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setNumber(0)
            .build()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        @Volatile
        private var runningInstance: DeviceService? = null

        private const val CHANNEL_ID = "siptg_connection"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "uz.ex.sip2go.action.STOP"
        private const val ACTION_WAKE = "uz.ex.sip2go.action.WAKE"
        private const val ACTION_CONNECT = "uz.ex.sip2go.action.CONNECT"
        private const val ACTION_ENSURE_IN_CALL = "uz.ex.sip2go.action.ENSURE_IN_CALL"
        private const val ACTION_RELEASE_FOREGROUND = "uz.ex.sip2go.action.RELEASE_FOREGROUND"
        private const val EXTRA_WAKE_CALL_ID = "wake_call_id"
        private const val EXTRA_WAKE_CALLER = "wake_caller"
        private const val EXTRA_WAKE_CALLED = "wake_called"

        /** Must run before AudioRecord — Android 10+ requires microphone FGS type. */
        fun ensureInCallForeground(context: Context) {
            runningInstance?.let { service ->
                service.foregroundReleased = false
                service.ensureForeground(inCall = true)
                return
            }
            val appContext = context.applicationContext
            val intent = Intent(appContext, DeviceService::class.java).apply {
                action = ACTION_ENSURE_IN_CALL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }

        fun startIdle(context: Context) {
            val intent = Intent(context, DeviceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun connect(context: Context) {
            val intent = Intent(context, DeviceService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun wakeForIncoming(
            context: Context,
            callId: String? = null,
            caller: String? = null,
            called: String? = null,
        ) {
            val intent = Intent(context, DeviceService::class.java).apply {
                action = ACTION_WAKE
                callId?.let { putExtra(EXTRA_WAKE_CALL_ID, it) }
                caller?.let { putExtra(EXTRA_WAKE_CALLER, it) }
                called?.let { putExtra(EXTRA_WAKE_CALLED, it) }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("DeviceService", "wakeForIncoming failed", e)
                context.applicationContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DeviceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /** Hide idle foreground notification while keeping push registration active. */
        fun releaseForeground(context: Context) {
            val appContext = context.applicationContext
            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager.cancel(NOTIFICATION_ID)
            val intent = Intent(appContext, DeviceService::class.java).apply {
                action = ACTION_RELEASE_FOREGROUND
            }
            appContext.startService(intent)
        }

        fun dismissConnectionNotification(context: Context) {
            context.applicationContext.getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_ID)
        }

        fun launchIncomingCallUi(context: Context) {
            val intent = Intent(context, IncomingCallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(IncomingCallActivity.EXTRA_INCOMING_CALL, true)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // Blocked on some Android versions without overlay permission.
            }
        }
    }
}
