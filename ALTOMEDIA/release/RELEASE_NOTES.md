Pembaruan HERBALINDO 1.0.1 — toko produk herbal diet alami dengan sistem member.

## Berkas unduhan

| Berkas | Ukuran | Kegunaan |
| --- | --- | --- |
| `HERBALINDO-1.0.1.aab` | 6,2 MB | Untuk diunggah ke Google Play Console |
| `HERBALINDO-1.0.1.apk` | 3,5 MB | Untuk pemasangan langsung pada perangkat |

Nilai pemeriksaan berkas tersedia pada `SHA256SUMS.txt`.

## Perbaikan pada rilis ini

- Pencairan saldo memakai nominal tetap Rp100, Rp200, Rp500, Rp1.000,
  Rp2.000, Rp5.000, Rp10.000, dan Rp20.000. Jumlah tidak lagi diketik bebas,
  sehingga nilai yang diajukan member selalu sama dengan nilai yang disetujui
  dan ditransfer admin
- Batas minimum pencairan diturunkan menjadi Rp100 agar seluruh nominal dapat
  dipakai. Perangkat yang sudah terpasang menyesuaikan nilainya sekali saja,
  dan admin tidak dapat menyetel minimum di atas nominal tertinggi
- Poin yang dipotong dihitung dari nominal rupiah, sehingga pembulatan
  konversi tidak lagi membuat saldo terpotong lebih besar daripada jumlah yang
  diminta; pilihan yang tidak terjangkau saldo juga tidak ditawarkan
- Level akun: setiap member kini punya level dengan badge di header, kartu
  kemajuan di Beranda, baris level dan riwayat XP di Profil, serta keterangan
  di tab Tugas. Naik level dipercepat oleh pembelian, undangan teman yang
  terverifikasi, dan keaktifan harian; XP tidak pernah berkurang sehingga
  level tidak turun saat pesanan dibatalkan
- Header tidak lagi terlalu tinggi atau tertimpa bilah status: tinggi header
  dipangkas, teks dibuat satu baris, dan inset bilah sistem ditangani di satu
  tempat untuk seluruh layar
- Halaman Saldo tidak lagi keluar sendiri saat dibuka. Penyebabnya kode mengambil
  wadah isian nomor tujuan dengan tipe tampilan yang keliru, sehingga Android
  melempar kesalahan tipe saat tab Saldo ditampilkan
- Header aplikasi kini tahan pada layar sempit: nama pengguna dipotong rapi satu
  baris, tombol keranjang memiliki ukuran tetap, dan tinggi header lebih ringkas
- Seluruh tombol memakai ikon dua dimensi yang sesuai dengan aksinya, termasuk
  tombol tambah dan kurang jumlah yang kini hanya berupa ikon
- Katalog produk disusun empat kartu ringkas dalam dua kolom agar tampilan lebih
  rapi; kartu pada baris terakhir tidak lagi melebar sendiri
- Perbaikan data: pembuatan pesanan tidak lagi menulis ulang dokumen pengguna
  yang sudah basi, sehingga poin dan XP yang bertambah sebelumnya tidak hilang

Paket rilis ditandatangani dengan sertifikat ALTOMEDIA:

```
CN=ALTOMEDIA, OU=Developer, O=ALTOMEDIA, L=Karawang, ST=Jawa Barat, C=ID
SHA-256: 24bbfec790bf5c02652aba29eb9fcc053eccf90e7b0880fe820449013129fb68
```

## Fitur utama

- Katalog produk herbal diet alami lengkap dengan komposisi, aturan pakai, dan peringatan pemakaian
- Keranjang belanja dan pembayaran QRIS dengan nominal otomatis serta nomor pesanan sebagai referensi
- Sistem member dengan satu halaman masuk untuk seluruh peran pengguna
- Poin harian, check-in, dan iklan berhadiah dengan batas harian
- Referral satu tingkat tanpa struktur berjenjang
- Saldo rupiah dan pengajuan pencairan beserta status kelayakan yang transparan
- Riwayat pesanan, mutasi poin, dan riwayat pencairan
- Ubah password akun dengan verifikasi password lama
- Panel admin: ringkasan, pesanan, produk dan stok, member, pencairan, pengaturan, serta audit log
- Admin dapat menambah produk baru lengkap dengan URL gambar, harga, komisi poin, deskripsi, dan stok awal
- Admin dapat menyetujui pencairan dan memverifikasi pembayaran transfer dengan mencocokkan nominal
- Pembeli dapat mengirim konfirmasi transfer berisi nama pengirim, nominal, dan catatan
- Pembatalan dan pengembalian pesanan diproses sekali saja sehingga stok serta poin tidak berubah dua kali
- Harga promo hanya berlaku di dalam periode promo dan saat promonya dinyalakan
- Admin dapat menambah kategori produk dengan validasi nama ganda dan nama terlalu pendek
- Riwayat pergerakan stok per produk tampil terbaru lebih dahulu lengkap dengan SKU dan pelakunya
- Deteksi fraud otomatis: akun duplikat, referral mencurigakan, transaksi berulang, pembatalan setelah bonus, dan reward iklan berlebih
- Level akun naik dari tiga jalur: pembelian (1 XP per Rp1.000 nilai pesanan),
  undangan teman yang terverifikasi (250 XP), dan keaktifan harian (15 XP
  check-in ditambah 5 XP per hari beruntun serta 5 XP per iklan berhadiah).
  XP tidak pernah berkurang, sehingga level tidak turun saat pesanan dibatalkan
- Penyesuaian header: badge level tampil di header, tinggi header dipangkas,
  dan inset bilah sistem ditangani agar header tidak tertimpa bilah status
- Perbaikan data: pembuatan pesanan tidak lagi menulis ulang dokumen pengguna
  yang sudah basi, sehingga poin dan XP tidak hilang saat checkout

## Persyaratan teknis

| Parameter | Nilai |
| --- | --- |
| Nama paket | `com.altomedia.herbalindo` |
| Version code | 2 |
| Version name | 1.0.1 |
| Minimum SDK | 21 (Android 5.0 Lollipop) |
| Target SDK | 36 |
| Skema tanda tangan | APK Signature Scheme v1, v2, v3, dan v4 |

## Mutu

- 136 pengujian lulus, tanpa kegagalan
- Pengujian tahap pembukaan menjalankan daur hidup layar yang sesungguhnya pada
  API 21 dan API 33, mencakup layar masuk, delapan layar member, dan panel admin
- Pengujian penyimpanan memakai basis data SQLite yang sama seperti aplikasi

## Dokumen paket rilis

Seluruh dokumen berada pada folder `ALTOMEDIA`:

- `PRIVACY_POLICY.txt` — kebijakan privasi
- `TERMS_OF_SERVICE.txt` — syarat dan ketentuan
- `PLAY_STORE_LISTING_GUIDE.txt` — panduan pengisian listing Play Store
- `UPLOAD_GUIDE.txt` — panduan teknis unggah rilis dan listing
- `RELEASE_NOTES.txt` — catatan rilis
- `BLOG_ARTICLE.txt` — artikel blog
- `store-assets/` — ikon, feature graphic, dan tangkapan layar