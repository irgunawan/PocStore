# POC STORE

Toko aplikasi POC Indonesia sederhana untuk perangkat radio Android.
Terhubung ke GitHub `irgunawan` untuk mengunduh aplikasi, dan punya pembaruan OTA sendiri.

- Package: `com.pocindonesia.pocstore`
- Label: **POC STORE**
- `minSdk 21` (Android 5.0/5.1) · `targetSdk 34`
- Navigasi **hanya atas/bawah** (DPAD), ENTER/DPAD_CENTER memilih, F5 = naik
- Unduh aplikasi lewat `DownloadManager`, otomatis membuka installer setelah selesai
- Katalog diambil dari GitHub API `irgunawan/PocDial` dengan URL cadangan
- Tombol UPDATE mengecek `https://ota.irgun.web.id/check.php?pkg=com.pocindonesia.pocstore`

## Aplikasi di katalog

| Aplikasi | Sumber |
|---|---|
| POC DIAL 1.0 | GitHub `irgunawan/PocDial` (raw APK) |
| PTTin 1.7.0 | OTA `ota.irgun.web.id` |
| POC STORE 1.1 | OTA (self-update) |

## Build

Butuh Android SDK platform 34 + build-tools 34.0.0 dan JDK 17.

```bash
export ANDROID_HOME=/path/to/sdk
export KEYSTORE=/path/to/release.jks
export KEYPASS_FILE=/path/to/keypass.txt
./build.sh
```

APK siap pakai: [`apk/POC-STORE-v1.1.apk`](apk/POC-STORE-v1.1.apk).
