package com.altomedia.herbalindo.data;

import java.util.List;

/**
 * Abstraksi penyimpanan dokumen (collection + docId + JSON).
 * Implementasi produksi: {@link Db} (SQLite). Implementasi uji: penyimpanan memori.
 */
public interface Store {
    void put(String collection, String docId, String json);

    void delete(String collection, String docId);

    String get(String collection, String docId);

    List<String> all(String collection);

    int count(String collection);

    void putMeta(String key, String value);

    String getMeta(String key, String def);

    long nextSequence(String name);

    void wipeAll();
}