package uz.ex.sip2go.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

enum class CallAudioRoute {
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
}

class CallAudioRouter(
    context: Context,
    private val onRouteChanged: (CallAudioRoute) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var routePreference = RoutePreference.AUTO
    private var deviceCallback: AudioDeviceCallback? = null

    fun start(initialRoute: CallAudioRoute? = null) {
        registerDeviceCallback()
        if (initialRoute != null) {
            applyRoute(initialRoute)
        } else {
            routePreference = RoutePreference.AUTO
            applyPreferredRoute()
            notifyRoute()
        }
    }

    fun stop() {
        releaseKeepRoute()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
        @Suppress("DEPRECATION")
        runCatching {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
        }
        routePreference = RoutePreference.AUTO
    }

    /** Unregister callbacks without resetting the active communication route. */
    fun releaseKeepRoute() {
        unregisterDeviceCallback()
    }

    /** Toggle between earpiece and speaker; external devices go to speaker first. */
    fun cycleOutputRoute(): CallAudioRoute {
        val next = when (readActualRoute()) {
            CallAudioRoute.SPEAKER -> CallAudioRoute.EARPIECE
            CallAudioRoute.EARPIECE -> CallAudioRoute.SPEAKER
            CallAudioRoute.WIRED_HEADSET,
            CallAudioRoute.BLUETOOTH,
            -> CallAudioRoute.SPEAKER
        }
        applyRoute(next)
        return readActualRoute()
    }

    fun applySpeakerRoute() {
        applyRoute(CallAudioRoute.SPEAKER)
    }

    fun applyRoute(route: CallAudioRoute) {
        when (route) {
            CallAudioRoute.SPEAKER -> {
                routePreference = RoutePreference.PHONE_SPEAKER
                applyPhoneSpeaker()
            }
            CallAudioRoute.EARPIECE -> {
                routePreference = RoutePreference.PHONE_EARPIECE
                applyPhoneEarpiece()
            }
            CallAudioRoute.WIRED_HEADSET,
            CallAudioRoute.BLUETOOTH,
            -> {
                routePreference = RoutePreference.AUTO
                applyAutoRoute()
            }
        }
        notifyRoute()
    }

    fun isSpeakerForced(): Boolean = routePreference == RoutePreference.PHONE_SPEAKER

    fun currentRoute(): CallAudioRoute = readActualRoute()

    fun hasExternalAudioDevice(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return findExternalDevice() != null
        }
        @Suppress("DEPRECATION")
        return audioManager.isBluetoothScoOn ||
            audioManager.isBluetoothA2dpOn ||
            audioManager.isWiredHeadsetOn
    }

    private fun applyPreferredRoute() {
        when (routePreference) {
            RoutePreference.PHONE_SPEAKER -> applyPhoneSpeaker()
            RoutePreference.PHONE_EARPIECE -> applyPhoneEarpiece()
            RoutePreference.AUTO -> applyAutoRoute()
        }
    }

    private fun applyAutoRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
            routeToPreferredDevice()
            return
        }
        applyLegacyAutoRoute()
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyAutoRoute() {
        audioManager.isSpeakerphoneOn = false
        val hasExternal = audioManager.isWiredHeadsetOn ||
            audioManager.isBluetoothScoOn ||
            audioManager.isBluetoothA2dpOn
        if (hasExternal) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }

    private fun applyPhoneSpeaker() {
        disableLegacyBluetoothSco()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speaker != null) {
                runCatching { audioManager.setCommunicationDevice(speaker) }
            }
        }
        setSpeakerphoneLegacy(true)
    }

    private fun applyPhoneEarpiece() {
        disableLegacyBluetoothSco()
        setSpeakerphoneLegacy(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            if (earpiece != null) {
                runCatching { audioManager.setCommunicationDevice(earpiece) }
            } else {
                runCatching { audioManager.clearCommunicationDevice() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun disableLegacyBluetoothSco() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphoneLegacy(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    private fun routeToPreferredDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        findExternalDevice()?.let { device ->
            runCatching { audioManager.setCommunicationDevice(device) }
                .onFailure { Log.w(TAG, "setCommunicationDevice failed", it) }
            return
        }
        audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }?.let { device ->
            runCatching { audioManager.setCommunicationDevice(device) }
        }
    }

    private fun findExternalDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        }
        @Suppress("DEPRECATION")
        return when {
            audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn -> null
            audioManager.isWiredHeadsetOn -> null
            else -> null
        }
    }

    private fun readActualRoute(): CallAudioRoute {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (audioManager.communicationDevice?.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CallAudioRoute.SPEAKER
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> CallAudioRoute.WIRED_HEADSET
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                -> CallAudioRoute.BLUETOOTH
                else -> CallAudioRoute.EARPIECE
            }
        }
        @Suppress("DEPRECATION")
        return when {
            audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn -> CallAudioRoute.BLUETOOTH
            audioManager.isWiredHeadsetOn -> CallAudioRoute.WIRED_HEADSET
            audioManager.isSpeakerphoneOn -> CallAudioRoute.SPEAKER
            else -> CallAudioRoute.EARPIECE
        }
    }

    private fun registerDeviceCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        if (deviceCallback != null) {
            return
        }
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                onDevicesChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                onDevicesChanged()
            }
        }.also { callback ->
            audioManager.registerAudioDeviceCallback(callback, mainHandler)
        }
    }

    private fun unregisterDeviceCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        deviceCallback?.let { callback ->
            runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
        }
        deviceCallback = null
    }

    private fun onDevicesChanged() {
        if (routePreference == RoutePreference.AUTO) {
            applyAutoRoute()
        } else {
            applyPreferredRoute()
        }
        notifyRoute()
    }

    private fun notifyRoute() {
        onRouteChanged(readActualRoute())
    }

    private enum class RoutePreference {
        AUTO,
        PHONE_SPEAKER,
        PHONE_EARPIECE,
    }

    companion object {
        private const val TAG = "CallAudioRouter"
    }
}
