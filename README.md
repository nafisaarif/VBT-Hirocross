
# HiroCross Phone VBT — Android Native

Aplikasi Android native yang memakai sensor accelerometer/linear acceleration untuk mendeteksi gerakan HP naik dan turun.

## Fitur
- Kalibrasi posisi diam
- Sensor accelerometer native
- Deteksi arah naik/turun
- Rep counter
- Mean velocity
- Peak velocity
- Estimasi ROM
- Velocity loss
- Grafik real-time
- Riwayat rep
- Portrait mode

## Cara termudah menghasilkan APK melalui GitHub
1. Buat repository GitHub baru.
2. Upload seluruh isi folder ini.
3. Buka tab Actions.
4. Pilih `Build Android APK`.
5. Jalankan workflow.
6. Setelah selesai, download artifact `HiroCross-Phone-VBT-debug`.
7. Ekstrak artifact lalu instal `app-debug.apk` pada HP.

## Build dengan Android Studio
1. Buka folder proyek di Android Studio.
2. Tunggu Gradle Sync selesai.
3. Pilih Build > Build APK(s).
4. APK berada di:
   app/build/outputs/apk/debug/app-debug.apk

## Instalasi
Aktifkan izin `Install unknown apps` untuk browser atau file manager yang digunakan, lalu buka APK.

## Keselamatan dan validitas
- Gunakan holder yang benar-benar kuat.
- Jangan memasang HP secara longgar pada barbell.
- Uji tanpa beban.
- Integrasi accelerometer menghasilkan drift.
- Hasil perlu divalidasi terhadap encoder/LPT/VBT komersial sebelum dipakai untuk keputusan profesional.
