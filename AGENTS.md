# HERBALINDO — Catatan Repositori

Panduan kerja untuk agen yang menangani repositori ini.

## Ringkasan

Aplikasi Android native **HERBALINDO** (toko herbal diet + sistem member,
referral 1 tingkat, poin, tugas harian, rewarded ads, saldo rupiah, withdrawal,
panel admin). Bukan PWA.

Spesifikasi lengkap: `HERBALINDO.txt` (Bab 1–15) dan `README.md`.

## Lokasi dan struktur

- Proyek Android: **`ALTOMEDIA/`** (modul tunggal `:app`).
- Nama paket: `com.altomedia.herbalindo`
- Bahasa: **Java** (bukan Kotlin).

Struktur penting:

```
ALTOMEDIA/
  app/src/main/java/com/altomedia/herbalindo/
    core/       Config, Util, Ui, Session, PasswordHasher, QrisGenerator
    data/       Models, Repository, Store, MemoryStore, Db
    ads/        AdsManager (AdMob banner + rewarded)
    ui/         SplashActivity, AuthActivity, BaseActivity
      member/   MemberActivity + tab + Cart/Checkout/Payment/OrderDetail
      admin/    AdminActivity + 7 Section
  app/src/test/java/.../RepositoryTest.java   ← 48 unit test
  tools/        verify_project.py, assetkit.py, gen_assets.py
  store-assets/ ikon 512, feature graphic, screenshots
  release/      APK, AAB, SHA256SUMS.txt, RELEASE_NOTES.md
```

## Perintah penting

Build memerlukan `JAVA_HOME` dan `ANDROID_HOME`:

```bash
export JAVA_HOME=/workspace/tools/jdk-17.0.13+11
export ANDROID_HOME=/workspace/tools/android-sdk
GRADLE=/workspace/tools/gradle-8.14.3/bin/gradle
```

- Unit test: `$GRADLE :app:testDebugUnitTest`
- Pemeriksa konsistensi: `python3 tools/verify_project.py`
- APK rilis: `$GRADLE :app:assembleRelease`
- AAB rilis: `$GRADLE :app:bundleRelease`
- Keduanya: `$GRADLE :app:assembleRelease :app:bundleRelease`

Tidak tersedia emulator atau KVM di lingkungan ini. Karena itu tidak ada
instrumented test; verifikasi dilakukan melalui unit test JVM, pemeriksa
konsistensi statis, dan pemeriksaan isi APK.

## Gaya kode

- Komentar dan pesan pengguna dalam **Bahasa Indonesia**.
- Komentar hanya untuk hal yang tidak jelas dari kode: invariant, alasan
  keputusan, atau perilaku tak terduga. Jangan mengulang isi kode.
- Logika bisnis berada di `Repository`; UI hanya memanggil dan menampilkan.
- Kesalahan aturan bisnis dilempar sebagai `Repository.RuleException`, lalu
  ditampilkan dengan `Ui.error(...)`.

## Hal yang mudah salah

1. **Path keystore.** `app/build.gradle` memakai
   `rootProject.file("keystore/ALTOMEDIA.jks")`. Bila diubah menjadi `file(...)`,
   Gradle akan mencarinya di dalam folder `app/` dan build rilis gagal dengan
   pesan "Keystore file not found".

2. **Referensi sumber daya.** Kesalahan ketik pada `R.id.*`, `R.layout.*`, atau
   `R.drawable.*` tidak terdeteksi oleh kompilasi Java pada tahap awal, tetapi
   menyebabkan Force Close saat runtime. Jalankan
   `python3 tools/verify_project.py` setelah mengubah layout.

3. **Nilai konfigurasi jangan di-hardcode di UI.** Batas iklan, poin, bonus
   referral, dan ongkir semuanya dapat diubah Admin melalui menu Pengaturan
   (`Models.Settings`). Menulis angka tetap di teks UI akan membuat tampilan
   tidak sesuai dengan aturan yang berlaku.

4. **Berkas rahasia.** `keystore/ALTOMEDIA.jks` dan `local.properties` sudah
   dikecualikan oleh `.gitignore` dan tidak boleh di-commit. Jangan melampirkan
   berkas keystore pada GitHub Release.

5. **`android.R.*` bukan sumber daya aplikasi.** Pada `OrdersSection` dan `Ui`
   memang ada rujukan seperti `android.R.layout.simple_spinner_dropdown_item`
   dan `android.R.id.content`. Ini sah; jangan diubah menjadi `R.*`.

## Aturan bisnis inti

- Konversi poin: 10.000 poin = Rp1.000 (`Config.POINTS_PER_UNIT` /
  `RUPIAH_PER_UNIT`, dan `Models.Settings`).
- Referral ID: tepat 6 digit angka, unik, tidak dapat diubah member.
- Referral hanya **satu tingkat** (pengundang → yang diundang). Dilarang
  menerapkan bonus berantai.
- Withdrawal memerlukan: saldo ≥ batas minimum, jumlah iklan harian terpenuhi
  bila diaktifkan, akun aktif, tidak ditandai curang, dan belum melewati batas
  pengajuan per hari.
- Poin iklan hanya diberikan setelah reward AdMob benar-benar diterima
  (`onUserEarnedReward`), bukan saat iklan mulai tampil.
- Password disimpan sebagai hash bersalt (`PasswordHasher`), bukan teks asli.

## Rilis

- Versi saat ini: **1.0.0**, versionCode **1**, minSdk **21**, targetSdk **36**.
- Rilis GitHub: `v1.0.0` pada `github.com/kdsmedia/HERBALINDO`, berisi APK, AAB,
  dan SHA256SUMS.txt.
- Sebelum setiap rilis: naikkan `versionCode`, jalankan unit test dan
  `verify_project.py`, lalu bangun ulang paket.

### Catatan token

`GITHUB_TOKEN` yang tersedia hanya memiliki izin baca-tulis berkas repositori;
token ini **tidak dapat** membuat GitHub Release (HTTP 403 "Resource not
accessible by integration"). Untuk membuat rilis beserta lampirannya,
diperlukan personal access token dengan cakupan `repo`.

## Repositori ini pernah memiliki dua implementasi paralel

Cabang `origin/main` pernah memuat implementasi alternatif berbasis **Kotlin +
Firebase** (Firestore, Cloud Functions, `google-services.json`) yang menempati
jalur berkas yang sama. Implementasi tersebut dibangun ulang dan berhasil
dikompilasi, tetapi **tidak dapat dijalankan** karena `google-services.json`
masih berisi nilai contoh (`REPLACE_WITH_YOUR_FIREBASE_PROJECT_ID` dan
`REPLACE_WITH_YOUR_API_KEY`).

Merge telah diselesaikan dengan mempertahankan implementasi **Java** sebagai
kode utama, karena implementasi inilah yang benar-benar dapat dijalankan,
memiliki 48 unit test, dan telah menghasilkan APK serta AAB rilis.

Spesifikasi pada Bab 12 memang menyebut Firebase. Apabila di kemudian hari
aplikasi akan dihubungkan ke Firebase, diperlukan proyek Firebase yang nyata
beserta berkas `google-services.json` yang sah, serta penyelarasan aturan
Firestore dengan aturan yang saat ini dijalankan di `Repository`.