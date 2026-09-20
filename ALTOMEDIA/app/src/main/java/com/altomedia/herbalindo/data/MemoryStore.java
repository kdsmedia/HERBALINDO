package com.altomedia.herbalindo.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Penyimpanan dalam memori untuk pengujian aturan bisnis (tanpa Android). */
public class MemoryStore implements Store {

    private final Map<String, Map<String, String>> cols = new LinkedHashMap<>();
    private final Map<String, String> meta = new LinkedHashMap<>();

    private Map<String, String> col(String c) {
        Map<String, String> m = cols.get(c);
        if (m == null) { m = new LinkedHashMap<>(); cols.put(c, m); }
        return m;
    }

    @Override public void put(String collection, String docId, String json) { col(collection).put(docId, json); }

    @Override public void delete(String collection, String docId) { col(collection).remove(docId); }

    @Override public String get(String collection, String docId) { return col(collection).get(docId); }

    @Override public List<String> all(String collection) { return new ArrayList<>(col(collection).values()); }

    @Override public int count(String collection) { return col(collection).size(); }

    @Override public void putMeta(String key, String value) { meta.put(key, value); }

    @Override public String getMeta(String key, String def) { return meta.containsKey(key) ? meta.get(key) : def; }

    @Override public long nextSequence(String name) {
        long current = Long.parseLong(getMeta("seq_" + name, "0")) + 1;
        putMeta("seq_" + name, String.valueOf(current));
        return current;
    }

    @Override public void wipeAll() { cols.clear(); meta.clear(); }
}