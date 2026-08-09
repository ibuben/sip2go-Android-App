package uz.ex.sip2go.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import uz.ex.sip2go.MainActivity
import uz.ex.sip2go.R
import uz.ex.sip2go.call.IncomingCallNotifier
import uz.ex.sip2go.call.IncomingCallReceiver
import java.util.UUID

/** Tap-to-return notification while a call is in progress. */
object OngoingCallNotifier {
    private const val TAG = "OngoingCallNotifier"
    private const val CHANNEL_ID = "siptg_ongoing_call"
    private const val NOTIFICATION_ID = 1003

    fun show(context: Context, callId: UUID, label: String) {
        val appContext = context.applicationContext
        createChannel(appContext)

        val openIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hangupIntent = PendingIntent.getBroadcast(
            appContext,
            1,
            Intent(appContext, IncomingCallReceiver::class.java).apply {
                action = IncomingCallNotifier.ACTION_HANGUP
                putExtra(IncomingCallNotifier.EXTRA_CALL_ID, callId.toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val safeLabel = label.ifBlank { appContext.getString(R.string.notification_in_call) }
        // CallStyle.notify() requires an associated foreground service on Android 14+.
        // DeviceService already runs microphone FGS; use a standard call notification here.
        val notification = buildNotification(appContext, safeLabel, openIntent, hangupIntent)
        try {
            appContext.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "Ongoing call notification shown for $safeLabel")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post ongoing call notification", e)
        }
    }

    private fun buildNotification(
        context: Context,
        label: String,
        openIntent: PendingIntent,
        hangupIntent: PendingIntent,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sip)
            .setContentTitle(context.getString(R.string.notification_in_call))
            .setContentText(label)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_sip, context.getString(R.string.action_hangup), hangupIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun dismiss(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_ongoing),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_ongoing_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
