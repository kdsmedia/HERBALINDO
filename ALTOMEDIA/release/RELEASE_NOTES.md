Rilis perdana aplikasi HERBALINDO — toko produk herbal diet alami dengan sistem member.

## Berkas unduhan

| Berkas | Ukuran | Kegunaan |
| --- | --- | --- |
| `HERBALINDO-1.0.0.aab` | 6,1 MB | Untuk diunggah ke Google Play Console |
| `HERBALINDO-1.0.0.apk` | 3,5 MB | Untuk pemasangan langsung pada perangkat |

Nilai pemeriksaan berkas tersedia pada `SHA256SUMS.txt`.

Paket rilis ditandatangani dengan sertifikat ALTOMEDIA:

```
CN=ALTOMEDIA, OU=Developer, O=ALTOMEDIA, L=Karawang, ST=Jawa Barat, C=ID
SHA-256: bab18b5ef926b5b7ae04ae1192ae282c72f1db4ca4437d74f296550e93e801d5
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

## Persyaratan teknis

| Parameter | Nilai |
| --- | --- |
| Nama paket | `com.altomedia.herbalindo` |
| Version code | 1 |
| Version name | 1.0.0 |
| Minimum SDK | 21 (Android 5.0 Lollipop) |
| Target SDK | 36 |
| Skema tanda tangan | APK Signature Scheme v1 dan v2 |

## Mutu

- 69 pengujian lulus, tanpa kegagalan
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