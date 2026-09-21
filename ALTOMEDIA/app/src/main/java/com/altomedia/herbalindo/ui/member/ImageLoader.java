package com.altomedia.herbalindo.ui.member;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pemuatan gambar produk dari URL.
 *
 * Gambar diunduh di luar thread tampilan lalu dipasang kembali di thread
 * tampilan. Hasilnya disimpan pada cache agar menggulir daftar tidak mengunduh
 * ulang gambar yang sama. Setiap permintaan menandai ImageView dengan URL-nya,
 * sehingga hasil unduhan yang sudah tidak relevan — misalnya karena tampilan
 * dipakai ulang untuk produk lain — tidak dipasang ke gambar yang salah.
 */
public final class ImageLoader {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private ImageLoader() { }

    /**
     * Memasang gambar dari {@code url} ke {@code view}.
     *
     * Bila URL kosong atau tidak berbentuk http/https, {@code placeholder}
     * dipasang kembali sehingga produk tanpa gambar tetap menampilkan lambang
     * bawaan, bukan sisa gambar produk sebelumnya.
     */
    public static void load(ImageView view, String url, int placeholder) {
        if (view == null) return;
        String key = url == null ? "" : url.trim();

        if (!com.altomedia.herbalindo.core.Util.isHttpUrl(key)) {
            view.setTag(null);
            view.setImageResource(placeholder);
            return;
        }

        // Baris daftar dipakai ulang untuk produk berbeda; tandai URL yang
        // sedang diminta agar hasil unduhan lama tidak terpasang ke produk baru.
        if (!key.equals(view.getTag())) view.setImageResource(placeholder);
        view.setTag(key);

        Bitmap cached = CACHE.get(key);
        if (cached != null) { view.setImageBitmap(cached); return; }

        POOL.execute(() -> {
            Bitmap bmp = download(key);
            if (bmp == null) return;
            CACHE.put(key, bmp);
            MAIN.post(() -> {
                if (key.equals(view.getTag())) view.setImageBitmap(bmp);
            });
        });
    }

    /**
     * Mengunduh gambar berukuran wajar.
     *
     * Ukuran diperkecil saat pembacaan berkas, bukan setelahnya, agar gambar
     * besar tidak menghabiskan memori perangkat kelas bawah.
     */
    private static Bitmap download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "HERBALINDO/1.0");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = conn.getInputStream()) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "HERBALINDO/1.0");
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
