package uz.ex.sip2go.audio

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log

/** Turns the screen off when the phone is held to the ear during earpiece calls. */
class ProximityScreenGuard(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private var wakeLock: PowerManager.WakeLock? = null

    private val sensorAvailable: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)

    fun setEnabled(enabled: Boolean) {
        if (!sensorAvailable) return
        if (enabled) {
            if (wakeLock != null) return
            try {
                @Suppress("DEPRECATION")
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "siptg:proximity",
                ).apply { acquire() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire proximity wake lock", e)
                wakeLock = null
            }
        } else {
            release()
        }
    }

    fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    companion object {
        private const val TAG = "ProximityScreenGuard"
    }
}
