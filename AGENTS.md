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
export JAVA_HOME=/workspace/tools/jdk-17.0.20.1+1
export ANDROID_HOME=/workspace/android-sdk
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

10. **Jangan menyimpan objek `Models.User` yang dibaca sebelum operasi lain.**
    Poin dan XP ditambahkan ke dokumen terbaru di penyimpanan, bukan ke objek
    di tangan pemanggil. Menulis ulang objek lama (`saveUser(user)`) menimpa
    tambahan itu. `createOrder` pernah menjadi bug ini: alamat pengiriman
    disimpan memakai objek basi, sehingga poin dan XP dari pesanan sebelumnya
    hilang. Selalu baca ulang dengan `user(userId)` sebelum menulis.

11. **Id penanda bisa didefinisikan di `values/ids.xml`, bukan hanya lewat
    `@+id` pada layout.** `Insets` memakai `R.id.sysbar_pad_*` sebagai penanda
    padding. Pemeriksa `tools/verify_project.py` sudah mengenali keduanya.

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
- Level akun dihitung dari **XP**, bukan poin (`level/Levels.java`). Kurva
  `100 * (L-1) * L / 2`: Lv2 di 100 XP, Lv5 di 1.000 XP, Lv10 di 4.500 XP.
- Sumber XP: pembelian 1 XP per Rp1.000 nilai pesanan (`XP_PER_RUPIAH_UNIT`),
  referral terverifikasi 250 XP, check-in 15 XP + 5 XP per hari beruntun
  (maks 30 hari), iklan berhadiah 5 XP, dan keaktifan harian 15 XP.
- XP **tidak pernah berkurang**. Pembatalan pesanan mengembalikan poin dan
  stok, tetapi XP tetap; ini sengaja agar level tidak turun.
- Setiap peristiwa XP ditulis ke koleksi `xp_events` dengan kode
  `type + "|" + refId`, dan `xp()` menghitung ulang dari peristiwa yang unik.
  Cara ini yang mencegah satu pesanan dihitung dua kali ketika `markStatus`
  dipanggil ulang. Jangan mengganti dengan penambahan langsung ke `user.xp`.
- Referral ID: tepat 6 digit angka, unik, tidak dapat diubah member.
- Referral hanya **satu tingkat** (pengundang → yang diundang). Dilarang
  menerapkan bonus berantai.
- Verifikasi akun hanya bergantung pada nilai pembelian: pesanan berstatus
  `PAID` yang totalnya mencapai `settings.verifyMinOrder` (bawaan Rp50.000)
  membuat akun `ACTIVE` berstatus terverifikasi. Pengundang **tidak**
  diperlukan, sehingga member yang mendaftar sendiri pun dapat terverifikasi.
- Bonus referral bagi pengundang baru dibayarkan setelah akun bawahan berstatus
  aktif terverifikasi. Urutan di `markStatus` penting: `refreshVerification`
  dipanggil **sebelum** `qualifyReferral`, jika tidak bonus akan dinilai dari
  status bawahan yang belum mutakhir.
- Verifikasi selalu dihitung ulang dari seluruh pesanan (`refreshVerification`),
  bukan diset sekali, agar refund satu pesanan tidak mencabut status akun yang
  masih dipenuhi pesanan lunas lain. Status akun `SUSPENDED` membuat akun tidak
  terverifikasi; `setUserStatus` memanggil ulang perhitungan ini.
- Kunci pengaturan lama `referralMinOrder` masih dibaca sebagai cadangan
  `verifyMinOrder` agar ambang tersimpan tidak hilang saat pembaruan.
- Withdrawal memerlukan: saldo ≥ batas minimum, jumlah iklan harian terpenuhi
  bila diaktifkan, akun aktif, tidak ditandai curang, dan belum melewati batas
  pengajuan per hari.
- Nominal withdrawal hanya boleh salah satu nilai
  `Config.WITHDRAW_OPTIONS_RUPIAH` (Rp100–Rp20.000). Parameter
  `requestWithdrawal(...)` adalah **rupiah**, bukan poin; poin yang ditahan
  dihitung dengan `rupiahToPoints`.
- **BCA memakai batas bawah Rp50.000** (`Config.WITHDRAW_MIN_BCA`) karena
  transfer bank dikenakan biaya admin; dompet digital mengikuti
  `minWithdrawRupiah` admin (bawaan Rp100). Batas efektif dihitung
  `Config.minWithdrawFor(method, minWithdrawRupiah)` = `max(nilai admin, batas
  metode)`, sehingga admin **tidak dapat** menurunkan batas BCA. Daftar nominal
  `withdrawOptionsFor(user, method)` menyaring dua hal sekaligus: nominal ≤
  saldo member dan nominal ≥ batas metode. Jangan menawarkan pilihan yang tidak
  akan lolos validasi ini. Isian jumlah bebas tidak boleh dikembalikan
  karena nilai yang diajukan harus sama dengan nilai yang disetujui admin.
- `minWithdrawRupiah` wajib ≤ `WITHDRAW_OPTIONS_RUPIAH` tertinggi, jika tidak
  seluruh pengajuan akan ditolak aturan minimum. `SettingsSection` menolak nilai
  yang lebih besar, dan `Repository.updateWithdrawDefaults()` menurunkan nilai
  bawaan lama (50000) sekali saja pada perangkat yang sudah terpasang
  (penanda `settings_version`).
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

## Dialog AlertDialog: tombol positif menutup dialog sebelum validasi berjalan

`setPositiveButton(...)` pada `AlertDialog` menutup dialog lebih dahulu, baru
memanggil pendengarnya. Bila pendengar itu memvalidasi isian dan menolaknya,
dialog sudah telanjur hilang: admin hanya melihat formulir yang menghilang dan
menyangka datanya tersimpan, padahal tidak ada yang berubah. Inilah penyebab
keluhan "penyesuaian saldo tidak berfungsi" pada panel admin.

Karena itu dialog yang memvalidasi isian **wajib** memakai pola berikut, bukan
`setPositiveButton` dengan aksi langsung:

```java
AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(a)
        .setPositiveButton("Simpan", null)   // pendengar diisi setelah show
        .create();
dialog.show();
Ui.submit(dialog, () -> {
    if (isianSalah) return "Pesan kesalahan";   // dialog tetap terbuka
    repo.aksi(...);
    return null;                                 // dialog ditutup
});
```

`Ui.submit(...)` mengembalikan pesan kesalahan lewat toast dan hanya menutup
dialog ketika handler mengembalikan `null`. Jangan menulis ulang logika ini per
dialog. `Ui.form(...)` sudah memakai pola yang sama untuk formulir kata sandi.

## Pengurangan saldo tidak boleh melebihi saldo

`addPoints` memangkas nilai menjadi nol (`Math.max(0, points + amount)`)
sementara baris ledger mencatat `amount` apa adanya. Tanpa penjagaan, admin yang
mengurangi melebihi saldo membuat saldo menjadi nol tetapi riwayat poin
menuliskan pengurangan penuh, sehingga saldo tidak lagi cocok dengan
penjumlahannya. `adminAdjustBalance` sekarang menolak pengurangan yang melebihi
saldo dan aturan yang sama diterapkan pada `js/store.js`.

## Rilis

- Versi saat ini: **1.0.6**, versionCode **7**, minSdk **21**, targetSdk **36**.
- Paket terbaru `HERBALINDO-1.0.6.apk`/`.aab` beserta `SHA256SUMS.txt` ada di
  `ALTOMEDIA/release/`. Objek GitHub Release untuk versi terbaru belum dibuat:
  `GITHUB_TOKEN` yang tersedia tidak bercakupan `repo` (HTTP 403 "Resource not
  accessible by integration" saat `POST /releases`). Buat rilis dengan token
  bercakupan `repo`, lalu lampirkan APK, AAB, dan SHA256SUMS.txt.
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
memiliki 205 unit test, dan telah menghasilkan APK serta AAB rilis.

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

1. Ganti `ALTOMEDIA/release/HERBALINDO-<versi>.apk` dan `.aab` dengan hasil build.
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

## Tombol memakai `app:icon`, bukan `android:icon`

Seluruh tombol adalah `MaterialButton` (lewat `Widget.Herbalindo.Button`).
Atribut ikonnya `app:icon` pada namespace `res-auto`; `android:icon` diabaikan
sehingga tombol tampil tanpa ikon. Sebaliknya `android:insetLeft` dan
`android:insetRight` memang milik namespace `android` dan build gagal bila
ditulis sebagai `app:`.

Ikon berupa vector drawable dua dimensi di `res/drawable/ic_*.xml` dengan
`fillColor="#FF000000"` agar mengikuti warna teks tombol.

## Saldo: `bal_dest` isian, `bal_dest_label` wadahnya

Tab Saldo pernah menjatuhkan aplikasi karena `findViewById(R.id.bal_dest)`
di-cast ke `TextInputLayout`, padahal id itu milik `TextInputEditText`. Wadah
`TextInputLayout` untuk isian tujuan memakai id `bal_dest_label`. Jangan
menyatukan kembali kedua id tersebut.

## Katalog produk adalah grid dua kolom

`ProductsTab` menyusun empat kartu ringkas dalam dua kolom memakai
`item_product_card.xml` dan pembantu `Grid` berbasis `LinearLayout`. Setiap sel
memakai bobot 1; sisa baris terakhir diisi penyeimbang kosong agar kartu tidak
melebar.

Data awal memuat empat produk (`HBA-001` sampai `HBA-003` dan `HBA-005`).
`HBA-004` sengaja dibiarkan kosong karena dipakai pengujian penambahan produk
baru. Menambah produk awal baru berarti menyesuaikan hitungan pada
`RepositoryTest.seedingIsIdempotentAcrossRestarts` dan
`RepositoryTest.dashboardStatsReflectReality`.