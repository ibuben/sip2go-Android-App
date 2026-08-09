# sip2go-Android-App

Android client for [SIP2GO gateway](https://github.com/ibuben/sip2go-gateway): **WSS (TLS) + Opus** softphone with push wake for incoming calls.

[Русский](README_ru.md)

Protocol: [docs/PROTOCOL.md in sip2go-gateway](https://github.com/ibuben/sip2go-gateway/blob/main/docs/PROTOCOL.md)

## Requirements

- Android 8.0+ (API 26)
- Google Play Services (FCM)
- Android Studio (JDK 17+)
- A running SIP2GO gateway

## Firebase (push wake)

1. Create a project in [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app with package `uz.ex.sip2go`
3. Download `google-services.json` → place at `app/google-services.json`  
   (see `app/google-services.json.example`)

Push delivery from the gateway is usually via the vendor Push Relay; the app only needs FCM configured.

## Build

1. Install [Android Studio](https://developer.android.com/studio)
2. **File → Open** → this repository folder
3. If **Invalid Gradle JDK** appears: use **Embedded JDK** (or JDK 17/21)
4. Place your `app/google-services.json`
5. Gradle Sync → Run

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## First setup

1. In the gateway Web UI, create a device mapping
2. Scan the QR / open the provision link in the app (or paste `device_id` + `device_token` + server URL)
3. Allow microphone and notifications
4. Enable push wake (and Connect if you need outbound calls)

| Mode | Purpose |
|------|---------|
| **Push / Enable** | Incoming calls wake the app via FCM; saves battery |
| **Connect** | Persistent WSS — required for outbound dialing |

## License

Copyright (C) 2026 eXUnity LAB.

Developed by **eXUnity LAB** in 2026.

Source repository: [github.com/ibuben/sip2go-Android-App](https://github.com/ibuben/sip2go-Android-App)

Gateway: [github.com/ibuben/sip2go-gateway](https://github.com/ibuben/sip2go-gateway)

This program is free software under the [GNU Affero General Public License](LICENSE) v3 or later.
