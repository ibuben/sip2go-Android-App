# sip2go-Android-App

Android-клиент для [шлюза SIP2GO](https://github.com/ibuben/sip2go-gateway): softphone **WSS (TLS) + Opus** с push wake для входящих.

[English](README.md)

Протокол: [docs/PROTOCOL.md в sip2go-gateway](https://github.com/ibuben/sip2go-gateway/blob/main/docs/PROTOCOL.md)

## Требования

- Android 8.0+ (API 26)
- Google Play Services (FCM)
- Android Studio (JDK 17+)
- Работающий шлюз SIP2GO

## Firebase (push wake)

1. Создайте проект в [Firebase Console](https://console.firebase.google.com/)
2. Добавьте Android-приложение с package `uz.ex.sip2go`
3. Скачайте `google-services.json` → положите в `app/google-services.json`  
   (см. `app/google-services.json.example`)

Доставка push со стороны шлюза обычно идёт через Push Relay; приложению нужен только настроенный FCM.

## Сборка

1. Установите [Android Studio](https://developer.android.com/studio)
2. **File → Open** → эта папка репозитория
3. При **Invalid Gradle JDK** выберите **Embedded JDK** (или JDK 17/21)
4. Положите свой `app/google-services.json`
5. Gradle Sync → Run

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Первый запуск

1. В Web UI шлюза создайте устройство (mapping)
2. Отсканируйте QR / откройте provision-ссылку в приложении (или введите `device_id`, `device_token` и URL сервера)
3. Разрешите микрофон и уведомления
4. Включите push wake (и Connect, если нужны исходящие)

| Режим | Назначение |
|-------|------------|
| **Push / Enable** | Входящие будят приложение через FCM; экономия батареи |
| **Connect** | Постоянный WSS — нужен для исходящих звонков |

## Лицензия

Copyright (C) 2026 eXUnity LAB.

Разработано в **eXUnity LAB** в 2026 году.

Репозиторий: [github.com/ibuben/sip2go-Android-App](https://github.com/ibuben/sip2go-Android-App)

Шлюз: [github.com/ibuben/sip2go-gateway](https://github.com/ibuben/sip2go-gateway)

Свободное ПО на условиях [GNU Affero General Public License](LICENSE) v3 или новее.
