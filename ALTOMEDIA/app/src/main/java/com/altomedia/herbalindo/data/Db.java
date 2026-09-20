package com.altomedia.herbalindo.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.altomedia.herbalindo.core.Config;

/**
 * Penyimpanan lokal berbentuk dokumen (collection + docId + JSON) sehingga
 * struktur data identik dengan skema Firestore pada Bab 12 dan dapat
 * disinkronkan ke Firestore tanpa mengubah model.
 */
public class Db extends SQLiteOpenHelper implements Store {

    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private Db(Context ctx) {
        super(ctx, Config.DB_NAME, null, Config.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE docs (" +
                "collection TEXT NOT NULL," +
                "doc_id TEXT NOT NULL," +
                "data TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY (collection, doc_id))");
        db.execSQL("CREATE INDEX idx_docs_collection ON docs(collection)");
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Versi awal: belum ada migrasi. Tabel dibuat ulang agar konsisten.
        db.execSQL("DROP TABLE IF EXISTS docs");
        db.execSQL("DROP TABLE IF EXISTS meta");
        onCreate(db);
    }

    @Override
    public void put(String collection, String docId, String json) {
        ContentValues cv = new ContentValues();
        cv.put("collection", collection);
        cv.put("doc_id", docId);
        cv.put("data", json);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("docs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void delete(String collection, String docId) {
        getWritableDatabase().delete("docs", "collection = ? AND doc_id = ?", new String[]{collection, docId});
    }

    @Override
    public String get(String collection, String docId) {
        Cursor c = getReadableDatabase().query("docs", new String[]{"data"},
                "collection = ? AND doc_id = ?", new String[]{collection, docId}, null, null, null);
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally { c.close(); }
    }

    /** Mengembalikan seluruh dokumen pada koleksi, terurut naik berdasarkan updated_at. */
    @Override
    public java.util.List<String> all(String collection) {
        return query(collection, null, null, "updated_at ASC", null);
    }

    public java.util.List<String> query(String collection, String where, String[] args, String orderBy, String limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        Cursor c = getReadableDatabase().query("docs", new String[]{"data"},
                where == null ? "collection = ?" : "collection = ? AND " + where,
                where == null ? new String[]{collection} : merge(collection, args),
                null, null, orderBy, limit);
        try {
            while (c.moveToNext()) out.add(c.getString(0));
        } finally { c.close(); }
        return out;
    }

    private static String[] merge(String first, String[] rest) {
        if (rest == null || rest.length == 0) return new String[]{first};
        String[] out = new String[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    @Override
    public int count(String collection) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM docs WHERE collection = ?", new String[]{collection});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    @Override
    public void putMeta(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("k", key);
        cv.put("v", value);
        getWritableDatabase().insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public String getMeta(String key, String def) {
        Cursor c = getReadableDatabase().query("meta", new String[]{"v"}, "k = ?", new String[]{key}, null, null, null);
        try { return c.moveToFirst() ? c.getString(0) : def; } finally { c.close(); }
    }

    @Override
    public long nextSequence(String name) {
        long current = Long.parseLong(getMeta("seq_" + name, "0")) + 1;
        putMeta("seq_" + name, String.valueOf(current));
        return current;
    }

    @Override
    public void wipeAll() {
        getWritableDatabase().delete("docs", null, null);
        getWritableDatabase().delete("meta", null, null);
    }
}