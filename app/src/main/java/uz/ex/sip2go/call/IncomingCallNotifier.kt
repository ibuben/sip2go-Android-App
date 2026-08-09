package uz.ex.sip2go.call

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uz.ex.sip2go.R
import uz.ex.sip2go.SipTgApp
import uz.ex.sip2go.data.DeviceSettings
import uz.ex.sip2go.audio.CallAudioFocus
import uz.ex.sip2go.service.DeviceService
import java.util.UUID

object IncomingCallNotifier {
    private const val TAG = "IncomingCallNotifier"
    private const val CHANNEL_ALERT_ID = "siptg_incoming_calls_alert"
    private const val NOTIFICATION_ID = 1002
    private const val CALL_REQUEST_CODE = 100
    private val mainHandler = Handler(Looper.getMainLooper())

    const val ACTION_ACCEPT = "uz.ex.sip2go.action.ACCEPT_CALL"
    const val ACTION_REJECT = "uz.ex.sip2go.action.REJECT_CALL"
    const val ACTION_HANGUP = "uz.ex.sip2go.action.HANGUP_CALL"
    const val EXTRA_CALL_ID = "call_id"

    private var ringtonePlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeCallId: UUID? = null
    private var callUiVisible = false
    private var callActivityFinisher: (() -> Unit)? = null

    fun show(
        context: Context,
        callId: UUID,
        caller: String,
        called: String,
        contactName: String?,
        fromBackgroundWake: Boolean = false,
    ) {
        val appContext = context.applicationContext
        activeCallId = callId
        callUiVisible = false
        CallAudioFocus.acquire(appContext)
        createAlertChannel(appContext)
        wakeScreen(appContext)
        startRingtone(appContext)
        startVibration(appContext)

        val callIntent = buildCallIntent(appContext, callId)
        mainHandler.post {
            presentCallUi(
                appContext,
                callIntent,
                callId,
                caller,
                called,
                contactName ?: caller,
                fromBackgroundWake = fromBackgroundWake,
            )
        }
    }

    fun updateCallerLabel(
        context: Context,
        callId: UUID,
        caller: String,
        called: String,
        contactName: String,
    ) {
        if (activeCallId != callId || callUiVisible) return
        val appContext = context.applicationContext
        val callIntent = buildCallIntent(appContext, callId)
        postAlertNotification(appContext, callId, caller, called, contactName, callIntent)
    }

    /** Hide heads-up banner once full-screen call UI is on screen. */
    fun onCallUiVisible(context: Context) {
        if (callUiVisible) return
        callUiVisible = true
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            Log.i(TAG, "Call UI visible on lock screen — keeping notification")
            return
        }
        cancelAlertNotification(context)
        Log.i(TAG, "Call UI visible — heads-up notification dismissed")
    }

    /** Stop ringtone/vibration/heads-up — keep call screen visible (e.g. on accept). */
    fun stopAlert(context: Context) {
        val appContext = context.applicationContext
        stopRingtone()
        stopVibration(appContext)
        releaseWakeLock()
        cancelAlertNotification(appContext)
    }

    /** Tear down incoming/active call UI — use on reject, hangup, or call ended. */
    fun dismiss(context: Context) {
        val appContext = context.applicationContext
        activeCallId = null
        callUiVisible = false
        stopAlert(appContext)
        CallAudioFocus.release(appContext)
        IncomingCallOverlay.hide()
        callActivityFinisher?.invoke()
        callActivityFinisher = null
    }

    fun registerCallActivity(onFinish: () -> Unit) {
        callActivityFinisher = onFinish
    }

    fun unregisterCallActivity(onFinish: () -> Unit) {
        if (callActivityFinisher === onFinish) {
            callActivityFinisher = null
        }
    }

    private fun cancelAlertNotification(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    fun isShowing(callId: UUID): Boolean = activeCallId == callId

    private fun presentCallUi(
        context: Context,
        callIntent: Intent,
        callId: UUID,
        caller: String,
        called: String,
        label: String,
        fromBackgroundWake: Boolean = false,
    ) {
        val hasOverlay = IncomingCallOverlay.canDrawOverlays(context)
        val locked = isLockScreenActive(context)
        Log.i(TAG, "Present call UI: overlay=$hasOverlay locked=$locked bgWake=$fromBackgroundWake")

        try {
            if (fromBackgroundWake) {
                // FCM wake — notification + full-screen intent only; overlay/activity crash from background.
                postAlertNotification(context, callId, caller, called, label, callIntent, lockScreen = true)
                launchCallActivity(context, callIntent)
                return
            }

            if (locked) {
                postAlertNotification(context, callId, caller, called, label, callIntent, lockScreen = true)
                launchCallActivity(context, callIntent)
                DeviceService.launchIncomingCallUi(context)
                if (hasOverlay) {
                    IncomingCallOverlay.show(context, keepNotification = true)
                }
                return
            }

            if (hasOverlay) {
                IncomingCallOverlay.show(context)
                onCallUiVisible(context)
                return
            }

            postAlertNotification(context, callId, caller, called, label, callIntent)
            launchCallActivity(context, callIntent)
            DeviceService.launchIncomingCallUi(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to present call UI, falling back to notification", e)
            postAlertNotification(context, callId, caller, called, label, callIntent, lockScreen = true)
        }
    }

    private fun isLockScreenActive(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return keyguard?.isKeyguardLocked == true || power?.isInteractive == false
    }

    private fun buildCallIntent(context: Context, callId: UUID): Intent {
        return Intent(context, IncomingCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(IncomingCallActivity.EXTRA_INCOMING_CALL, true)
            putExtra(EXTRA_CALL_ID, callId.toString())
        }
    }

    private fun callPendingIntent(context: Context, intent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            context,
            CALL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun launchCallActivity(context: Context, intent: Intent) {
        val pendingIntent = callPendingIntent(context, intent)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    // Incoming calls must open when the app is not visible.
                    pendingIntentBackgroundActivityStartMode =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        } else {
                            @Suppress("DEPRECATION")
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }
                }
                pendingIntent.send(
                    context,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options.toBundle(),
                )
            } else {
                pendingIntent.send()
            }
            Log.i(TAG, "Launched call activity via PendingIntent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch call activity via PendingIntent", e)
        }
    }

    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ALERT_ID,
            context.getString(R.string.notification_channel_calls),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_calls_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
            setBypassDnd(true)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun wakeScreen(context: Context) {
        releaseWakeLock()
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "siptg:incoming_call",
        ).apply {
            acquire(60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun startRingtone(context: Context) {
        stopRingtone()
        val uri = resolveRingtoneUri(context) ?: return
        try {
            ringtonePlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
            stopRingtone()
        }
    }

    private fun stopRingtone() {
        ringtonePlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        ringtonePlayer = null
    }

    private fun resolveRingtoneUri(context: Context): Uri? {
        val customUri = try {
            val app = context.applicationContext as? SipTgApp ?: return RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_RINGTONE,
            )
            runBlocking {
                app.settingsStore.settings.first().ringtoneUri
            }
        } catch (_: Exception) {
            null
        }
        return when {
            customUri == DeviceSettings.SILENT_RINGTONE -> null
            customUri.isNullOrBlank() -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            else -> Uri.parse(customUri)
        }
    }

    private fun startVibration(context: Context) {
        val pattern = longArrayOf(0, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Vibrator::class.java)
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)?.cancel()
        }
    }

    /** Heads-up + full-screen intent — only when overlay/activity is not already shown. */
    private fun postAlertNotification(
        context: Context,
        callId: UUID,
        caller: String,
        called: String,
        label: String,
        callIntent: Intent,
        lockScreen: Boolean = false,
    ) {
        val fullScreenIntent = callPendingIntent(context, callIntent)

        val acceptIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, IncomingCallReceiver::class.java).apply {
                action = ACTION_ACCEPT
                putExtra(EXTRA_CALL_ID, callId.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val rejectIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, IncomingCallReceiver::class.java).apply {
                action = ACTION_REJECT
                putExtra(EXTRA_CALL_ID, callId.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_stat_sip)
            .setContentTitle(context.getString(R.string.notification_incoming, label))
            .setContentText(if (label != caller) caller else called)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callerPerson = Person.Builder().setName(label).build()
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    callerPerson,
                    rejectIntent,
                    acceptIntent,
                ),
            )
        } else if (!lockScreen) {
            builder
                .addAction(R.drawable.ic_stat_sip, context.getString(R.string.action_accept), acceptIntent)
                .addAction(R.drawable.ic_stat_sip, context.getString(R.string.action_reject), rejectIntent)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, builder.build())
    }
}
