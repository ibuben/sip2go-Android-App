package uz.ex.sip2go

import android.app.Application
import android.content.Context
import uz.ex.sip2go.data.SettingsStore
import uz.ex.sip2go.history.CallHistoryStore
import uz.ex.sip2go.locale.AppLocale
import uz.ex.sip2go.push.FcmManager
import uz.ex.sip2go.service.DeviceClient
import uz.ex.sip2go.telecom.TelecomBridge

class SipTgApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set

    lateinit var callHistoryStore: CallHistoryStore
        private set

    lateinit var deviceClient: DeviceClient
        private set

    lateinit var fcmManager: FcmManager
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.withAppLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        callHistoryStore = CallHistoryStore(this)
        deviceClient = DeviceClient(applicationContext, settingsStore, callHistoryStore)
        fcmManager = FcmManager(settingsStore)
        TelecomBridge.init(this)
    }
}
