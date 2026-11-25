# POC STORE

Toko aplikasi POC Indonesia sederhana untuk perangkat radio Android.
Terhubung ke GitHub `irgunawan` untuk mengunduh aplikasi, dan punya pembaruan OTA sendiri.

- Package: `com.pocindonesia.pocstore`
- Label: **POC STORE**
- `minSdk 21` (Android 5.0/5.1) · `targetSdk 34`
- Navigasi **hanya atas/bawah** (DPAD), ENTER/DPAD_CENTER memilih, F5 = naik
- Unduh aplikasi lewat `DownloadManager` ke direktori aplikasi sendiri, dibuka lewat `ApkProvider`
- Katalog aplikasi dari OTA `ota.irgun.web.id/check.php` (cadangan: portal `api/pocstore`)
- Katalog perangkat radio merujuk ke repo **`irgunawan/list-poc-model`**
  (`https://portal.irgun.web.id/files/list-poc-model.xlsx`), tanpa daftar model hardcode
- Tombol UPDATE mengecek `https://ota.irgun.web.id/check.php?pkg=com.pocindonesia.pocstore`

## Perbaikan v2.0 — install otomatis di Hytera P50 Pro

Gejala: menekan aplikasi di POC STORE hanya memunculkan notifikasi unduhan;
installer tidak pernah terbuka dan berkas tidak terlihat di folder Download.

Penjelasan:
- Sejak v1.6 APK memang sengaja ditulis ke `Android/data/com.pocindonesia.pocstore/files/Download`
  (lewat `ApkProvider`), bukan ke folder Download publik — itu mengatasi kegagalan
  tulis di PNC360S. Jadi "tidak ada di folder Download" bukan kerusakan.
- Penyebab installer tidak jalan: `DownloadReceiver` selalu membuat notifikasi untuk
  Android 8+ **walaupun aplikasi sedang dibuka**. Flag `isActive()` ada tetapi tidak dipakai.
  Ditambah, izin *Install unknown apps* tidak dicek sebelum memanggil installer,
  sehingga kegagalan terjadi tanpa pesan.

Yang diubah di v2.0:
- `DownloadReceiver`: kalau POC STORE sedang di depan, installer dipanggil langsung;
  kalau di background, notifikasi membuka `InstallActivity` (activity transparan milik
  sendiri) yang mengecek izin di foreground sebelum melanjutkan.
- `Installer`: cek `canRequestPackageInstalls()` (API 26+), arahkan ke Settings bila
  izin belum aktif, dan kembalikan status ke receiver.
- Tombol **PASANG UNDUHAN TERAKHIR** ditambahkan sebagai jalur pemulihan.
- Manifest: `InstallActivity` + `versionCode 10` / `versionName 2.0`.
- Ditandatangani keystore yang sama dengan v1.9 (`CN=PTTin, OU=Irgun`), jadi OTA aman.

Catatan: pada Android 8+ pengguna harus mengizinkan POC STORE memasang aplikasi
sekali saja (Settings → Install unknown apps), lalu pilih PASANG UNDUHAN TERAKHIR.
Di Android 5 tidak ada gate ini.

## Aplikasi di katalog

| Aplikasi | Sumber |
|---|---|
| POC DIAL 1.0 | GitHub `irgunawan/PocDial` (raw APK) |
| PTTin 1.7.0 | OTA `ota.irgun.web.id` |
| POC STORE 1.2 | OTA (self-update) |

## Build

Butuh Android SDK platform 34 + build-tools 34.0.0 dan JDK 17.

```bash
export ANDROID_HOME=/path/to/sdk
export KEYSTORE=/path/to/release.jks
export KEYPASS_FILE=/path/to/keypass.txt
./build.sh
```

APK siap pakai: [`apk/POC-STORE-v2.0.apk`](apk/POC-STORE-v2.0.apk).

Arsip lama (`v1.0`–`v1.9`) sengaja disimpan. Semua rilis memakai keystore yang sama
(`CN=PTTin, OU=Irgun`), jadi OTA dari versi lama tetap bisa memasang versi baru.
