package uz.ex.sip2go.history

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import uz.ex.sip2go.MainActivity
import uz.ex.sip2go.R

object MissedCallBadge {
    private const val CHANNEL_ID = "siptg_missed_calls"
    private const val NOTIFICATION_ID = 1003

    fun update(context: Context, count: Int, enabled: Boolean = true) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        if (!enabled || count <= 0) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel(context, manager)
        val openHistory = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_HISTORY, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sip)
            .setContentTitle(context.getString(R.string.missed_calls_badge_title, count))
            .setContentText(context.getString(R.string.missed_calls_badge_text))
            .setContentIntent(openHistory)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_missed),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_missed_desc)
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
