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
  app/src/test/java/.../RepositoryTest.java   ← aturan bisnis (MemoryStore)
  app/src/test/java/.../StartupLifecycleTest.java ← daur hidup layar (Robolectric)
  app/src/test/java/.../DbStoreTest.java      ← penyimpanan SQLite sungguhan
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

6. **`Activity.getColor(int)` baru ada sejak API 23, sedangkan minSdk 21.**
   Pemakaiannya membuat aplikasi menutup dengan `NoSuchMethodError` di Android
   5.x. Selalu pakai `ContextCompat.getColor(...)`. Android Lint tidak
   melaporkan ini karena `lint.abortOnError` bernilai false — jalankan
   `$GRADLE :app:lintDebug` lalu periksa bagian `NewApi` secara manual.

7. **Layar tidak boleh menyentuh `user` pada `onCreate` sebelum sesi dibaca.**
   `BaseActivity.onResume` mengisi `user` dan memanggil `onSessionReady`.
   `MemberActivity.onCreate` karena itu membaca `Session.current(this)` lebih
   dulu; tab pertama (`show("home")`) langsung memakai data pengguna. Mengandalkan
   `onResume` saja akan melempar `NullPointerException` sebelum layar tampil.

8. **Layar yang tidak memerlukan sesi wajib menimpa `requiresSession()`.**
   `BaseActivity.onResume` mengalihkan layar ke `AuthActivity` ketika sesi tidak
   ada. Layar seperti `AuthActivity` harus mengembalikan `false`, jika tidak
   layar akan menutup diri sendiri dan aplikasi tampak langsung keluar.

9. **Metode `protected` di `BaseActivity` jangan diduplikasi di turunan.**
   Menulis ulang `toAuth()` dengan akses `private` menyebabkan kegagalan
   kompilasi "attempting to assign weaker access privileges".

## Pengujian

Alur bisnis diuji dengan `MemoryStore`; itu tidak mewakili perangkat. Untuk
perubahan yang menyentuh layar, jalur pembukaan, atau penyimpanan, andalkan
`StartupLifecycleTest` dan `DbStoreTest`.

- Robolectric 4.13 hanya mendukung sampai SDK 34, sedangkan `targetSdk` 36.
  Uji yang memakainya **wajib** memakai `@org.robolectric.annotation.Config(sdk = 34)`
  (tulis lengkap; `Config` bentrok dengan `com.altomedia.herbalindo.core.Config`).
- `StartupLifecycleTest` menjalankan Activity sungguhan, sehingga
  `HerbalindoApp.onCreate` ikut berjalan dan banner AdMob benar-benar dimuat.
- Saat menguji layar, ingat `Repository.createOrder` mengosongkan keranjang.
  Layar checkout yang dibuka setelahnya akan selesai sendiri karena keranjang
  kosong — bukan tanda adanya cacat.
- Alur "tambah produk baru" menulis stok dalam dua langkah: `saveProduct`
  dengan `stock = 0`, lalu `adjustStock` sebesar stok awal. Menyetel `stock`
  langsung pada `saveProduct` membuat stok terhitung dua kali. Uji
  `adminDapatMenambahProdukBaruDanStokAwalnyaTercatat` menjaga aturan ini.
- `markStatus` ke `CANCELLED`/`REFUNDED` memakai penanda `revocationDone` pada
  order. Tanpa penanda itu, pemanggilan berulang mengembalikan stok dan
  memotong poin berkali-kali.

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

## Iklan (AdMob)

Tiga format dipakai: banner, rewarded, interstitial.

| Format | ID produksi | Tempat pemasangan |
| --- | --- | --- |
| App ID | `ca-app-pub-6881903056221433~4194258778` | `strings.xml` → `admob_app_id` (dibaca manifest) |
| Banner | `ca-app-pub-6881903056221433/9657593588` | `activity_member.xml` (bawah konten), `activity_product_detail.xml` |
| Rewarded | `ca-app-pub-6881903056221433/4720872385` | `TasksTab` — sumber poin iklan |
| Interstitial | `ca-app-pub-6881903056221433/3693811302` | `CheckoutActivity` setelah pesanan dibuat |

Build **debug** otomatis memakai unit uji resmi Google; build **release** memakai unit
produksi. Pemilihan ada di `Config.adUnit(...)` dan diterapkan lewat
`AdsManager.loadBanner(...)`. Baik `setAdUnitId` maupun `setAdSize` diatur dari
kode: `AdView.loadAd` melempar `IllegalStateException` bila salah satunya belum
terisi, dan kegagalan itu ditangkap agar banner tidak menjatuhkan layar.

Karena itu, `ads:adUnitId` pada layout **diabaikan** untuk banner. Jangan
mengandalkan nilai di XML dan jangan menyalin unit uji ke berkas layout.

Hal yang mudah salah:

1. **Poin iklan tidak boleh diberikan saat iklan mulai tampil.** `onRewarded`
   hanya dipanggil dari `onUserEarnedReward`. Callback `onAdFailedToShow` dan
   `onAdDismissedFullScreenContent` tanpa reward harus memanggil `onFailed`,
   bukan memberi poin.
2. **Jangan tampilkan interstitial saat aplikasi dibuka.** Penayangan langsung
   pada peluncuran melanggar kebijakan AdMob. Interstitial hanya dipasang pada
   jeda alami (setelah checkout selesai).
3. **Iklan yang gagal dimuat tidak boleh menahan alur pengguna.**
   `showInterstitial` selalu menjalankan `onClosed`, dan `showRewarded`
   melaporkan kegagalan lewat `onFailed` sehingga antarmuka dapat menampilkan
   pesan tanpa membeku.
4. **Jangan memakai unit uji pada build rilis.** Unit uji
   `ca-app-pub-3940256099942544/...` hanya sah untuk pengembangan; memakainya di
   produksi meniadakan pendapatan dan dapat menimbulkan pelanggaran.

## Rilis

- Versi saat ini: **1.0.0**, versionCode **1**, minSdk **21**, targetSdk **36**.
- Rilis GitHub: `v1.0.0` pada `github.com/kdsmedia/HERBALINDO`, berisi APK, AAB,
  dan SHA256SUMS.txt.
- Sebelum setiap rilis: naikkan `versionCode`, jalankan unit test dan
  `verify_project.py`, lalu bangun ulang paket.
- Kredensial signing dibaca dari `keystore/keystore.properties`
  (`signing.storeFile`, `signing.storePassword`, `signing.keyAlias`,
  `signing.keyPassword`). Berkas itu diabaikan git; bila hilang, build rilis
  gagal dengan pesan yang jelas, bukan menandatangani dengan kunci salah.
- Sertifikat ALTOMEDIA sah sampai 5 Februari 2054 (alias `kdsmedia`).
  Sidik SHA-256-nya `24bbfec790bf5c02652aba29eb9fcc053eccf90e7b0880fe820449013129fb68`;
  nilai inilah yang harus tertulis di `release/RELEASE_NOTES.md`.

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
memiliki 96 unit test, dan telah menghasilkan APK serta AAB rilis.

Spesifikasi pada Bab 12 memang menyebut Firebase. Apabila di kemudian hari
aplikasi akan dihubungkan ke Firebase, diperlukan proyek Firebase yang nyata
beserta berkas `google-services.json` yang sah, serta penyelarasan aturan
Firestore dengan aturan yang saat ini dijalankan di `Repository`.

## Artefak rilis tidak ikut git; paket di GitHub Release harus diganti manual

`ALTOMEDIA/.gitignore` mengabaikan `*.apk` dan `*.aab`. Jadi `git push` **tidak
pernah** memperbarui paket yang diunduh pengguna. Paket hanya berubah bila aset
GitHub Release diganti lewat API.

Akibatnya pernah terjadi: Release `v1.0.0` dibuat pukul 18:00 dengan APK hasil
build **sebelum** perbaikan pembukaan aplikasi. Semua perbaikan berikutnya
hanya masuk ke git, sementara APK di Release tetap versi lama dan menutup
sendiri saat dibuka di Android 5.x. Setelah mengganti aset, barulah pengguna
menerima build yang benar.

Setiap kali artefak dibangun ulang:

1. Ganti `ALTOMEDIA/release/HERBALINDO-1.0.0.apk` dan `.aab` dengan hasil build.
2. Perbarui `SHA256SUMS.txt`.
3. Unggah ulang ketiga berkas ke Release dengan token bercakupan `repo`.
4. Unduh kembali dari Release dan periksa checksumnya.

## `Activity.getColor` tetap dapat masuk lewat library, dibawa oleh R8

Perbaikan di kode sendiri tidak cukup. R8 pernah memindahkan panggilan
`Activity.getColor` milik kode aplikasi ke dalam kelas paket
`com.google.android.gms.internal.ads` (`be1.a`), sehingga pemeriksaan
`grep ContextCompat` pada dex tidak menemukan apa pun dan bug tampak hilang
padahal masih ada.

Cara memeriksa yang benar adalah mencari langsung rujukan tanpa penjaga versi
di seluruh dex, bukan mencari nama `ContextCompat`:

```
dexdump -d classes.dex | grep -c "Activity;.getColor"   # harus 0
```

Jalur yang aman harus memuat `sget ... Build$VERSION;.SDK_INT` sebelum memilih
antara `Context.getColor` (API 23) dan `Resources.getColor` (API 1).

## Kredensial akun admin tidak boleh hanya ditulis di `seed()`

`seed()` hanya dijalankan ketika koleksi pengguna masih kosong. Mengubah nilai
di dalam `seed()` karena itu **tidak berpengaruh** pada perangkat yang sudah
memakai aplikasi: akun admin lamanya tetap tersimpan di basis data.

Karena itu kredensial admin ditulis sebagai konstanta di `Repository`
(`ADMIN_EMAIL`, `ADMIN_PHONE`, `ADMIN_PASSWORD`) dan diselaraskan oleh
`updateAdminCredentials()`. Penanda `admin_credentials_version` di penyimpanan
meta membuat penyelarasan berjalan sekali per perubahan, sehingga kata sandi
yang sudah diganti sendiri oleh pemilik aplikasi tidak ikut ditimpa.

Saat mengganti kredensial admin, naikkan nilai penanda tersebut.

## Teks di bawah tombol masuk dan daftar telah dihapus

Permintaan pemilik aplikasi: tidak boleh ada teks apa pun di bawah tombol
masuk/daftar. String `role_hint` dan `referral_hint` sudah dihapus dari
`strings.xml` dan `activity_auth.xml`. Jangan menambahkannya kembali.

Slogan aplikasi adalah "Herbal Diet Alami Tanpa Bahan Kimia" (`app_tagline`),
tampil di layar splash dan layar masuk.