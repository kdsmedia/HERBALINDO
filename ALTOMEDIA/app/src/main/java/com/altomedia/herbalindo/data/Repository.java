package com.altomedia.herbalindo.data;

import android.content.Context;

import com.altomedia.herbalindo.core.Config;
import com.altomedia.herbalindo.core.PasswordHasher;
import com.altomedia.herbalindo.core.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine aturan bisnis HERBALINDO.
 * Seluruh perubahan poin/saldo hanya melalui kelas ini (setara Cloud Functions),
 * sehingga tidak dapat dimanipulasi dari sisi tampilan (Bab 13).
 */
public class Repository {

    /** Kredensial akun admin bawaan. */
    public static final String ADMIN_EMAIL = "appsidhanie@gmail.com";
    public static final String ADMIN_PHONE = "085813899649";
    public static final String ADMIN_PASSWORD = "Kdsmedia@123";
    private static final String META_ADMIN_VERSION = "admin_credentials_version";
    private static final String META_SETTINGS_VERSION = "settings_version";

    public static class RuleException extends Exception {
        public RuleException(String msg) { super(msg); }
    }

    private final Store db;
    private static Repository instance;

    /** Instans produksi (SQLite di perangkat). */
    public static synchronized Repository get(Context ctx) {
        if (instance == null) instance = new Repository(Db.get(ctx.getApplicationContext()));
        return instance;
    }

    /** Dipakai pengujian dengan penyimpanan pengganti. */
    public static synchronized Repository with(Store store) {
        instance = new Repository(store);
        return instance;
    }

    private Repository(Store store) {
        db = store;
        if (db.count(Config.C_USERS) == 0) seed();
        updateAdminCredentials();
        updateWithdrawDefaults();
    }

    /**
     * Menyelaraskan akun admin bawaan dengan kredensial yang berlaku.
     *
     * {@link #seed()} hanya berjalan saat koleksi pengguna masih kosong, jadi
     * perangkat yang sudah terpasang tidak akan pernah mendapat kredensial
     * admin yang baru. Penanda versi di penyimpanan meta membuat penyesuaian
     * ini berjalan sekali saja, sehingga kata sandi admin yang sudah diganti
     * sendiri oleh pemilik aplikasi tidak ikut ditimpa.
     */
    private void updateAdminCredentials() {
        try {
            if ("2".equals(db.getMeta(META_ADMIN_VERSION, ""))) return;
            Models.User admin = user("USR-ADMIN");
            if (admin != null) {
                String salt = PasswordHasher.newSalt();
                admin.email = ADMIN_EMAIL;
                admin.phone = ADMIN_PHONE;
                admin.salt = salt;
                admin.passwordHash = PasswordHasher.hash(ADMIN_PASSWORD, salt);
                saveUser(admin);
            }
            db.putMeta(META_ADMIN_VERSION, "2");
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menyelaraskan akun admin", e);
        }
    }

    /**
     * Menurunkan batas minimum withdrawal pada perangkat yang sudah terpasang.
     *
     * Nilai lama Rp50.000 lebih tinggi daripada nominal pilihan tertinggi
     * (Rp20.000), sehingga tanpa penyesuaian ini seluruh penarikan akan ditolak
     * oleh aturan minimum. Dijalankan sekali saja dan hanya bila admin belum
     * menyesuaikan sendiri; bila sudah, nilainya dibiarkan.
     */
    private void updateWithdrawDefaults() {
        try {
            if ("1".equals(db.getMeta(META_SETTINGS_VERSION, ""))) return;
            Models.Settings s = settings();
            if (s.minWithdrawRupiah > Config.DEFAULT_MIN_WITHDRAW) {
                s.minWithdrawRupiah = Config.DEFAULT_MIN_WITHDRAW;
                saveSettings(s, null);
            }
            db.putMeta(META_SETTINGS_VERSION, "1");
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menyelaraskan batas withdrawal", e);
        }
    }

    /* ================= SEED ================= */
    private void seed() {
        try {
            String salt = PasswordHasher.newSalt();
            Models.User admin = new Models.User();
            admin.userId = "USR-ADMIN";
            admin.name = "Administrator";
            admin.email = ADMIN_EMAIL;
            admin.phone = ADMIN_PHONE;
            admin.salt = salt;
            admin.passwordHash = PasswordHasher.hash(ADMIN_PASSWORD, salt);
            admin.referralId = "000001";
            admin.role = "ADMIN";
            admin.status = "ACTIVE";
            admin.verified = true;
            admin.points = 0;
            admin.createdAt = Util.nowIso();
            admin.updatedAt = admin.createdAt;
            db.put(Config.C_USERS, admin.userId, admin.toJson().toString());

            saveSettings(new Models.Settings(), null);

            db.put(Config.C_CATEGORIES, "CAT-001", cat("CAT-001", "Herbal Diet").toString());
            db.put(Config.C_CATEGORIES, "CAT-002", cat("CAT-002", "Herbal Keluarga").toString());

            createProduct("Herbal Diet A", "HBA-001", "Herbal Diet",
                "Ramuan herbal diet alami berbahan tumbuhan pilihan, diseduh dan diminum sesuai aturan pakai.",
                "Ekstrak daun kelor, jahe merah, temulawak, daun sirsak, madu murni.",
                "Seduh 1 sachet dengan 150 ml air hangat, diminum 1 kali sehari setelah makan.",
                "Tidak untuk ibu hamil, menyusui, dan anak di bawah 12 tahun. Hentikan pemakaian bila terjadi reaksi alergi. Bukan obat dan tidak untuk menyembuhkan penyakit.",
                100000, 85000, 50, 250, 500);
            createProduct("Herbal Diet B", "HBA-002", "Herbal Diet",
                "Ramuan herbal untuk membantu menjaga pola makan sehat sehari-hari.",
                "Ekstrak kunyit, kayu manis, daun pandan, temulawak, gula aren.",
                "Seduh 1 sachet dengan air hangat, diminum 1-2 kali sehari.",
                "Tidak untuk ibu hamil, menyusui, dan anak di bawah 12 tahun. Bukan obat dan tidak untuk menyembuhkan penyakit.",
                120000, 0, 40, 250, 600);
            createProduct("Herbal Diet C", "HBA-003", "Herbal Diet",
                "Ramuan herbal malam untuk mendukung istirahat dan pencernaan.",
                "Ekstrak daun senna, akar alang-alang, kapulaga, cengkeh.",
                "Seduh 1 sachet dengan 150 ml air panas, diminum sebelum tidur.",
                "Tidak untuk ibu hamil, menyusui, anak di bawah 12 tahun, dan penderita gangguan ginjal. Bukan obat.",
                95000, 79000, 35, 200, 450);
            createProduct("Herbal Diet D", "HBA-005", "Herbal Diet",
                "Ramuan herbal pagi untuk membantu menjaga energi saat menjalani pola makan sehat.",
                "Ekstrak daun stevia, jahe, serai, kayu manis, jeruk nipis.",
                "Seduh 1 sachet dengan 200 ml air hangat, diminum 1 kali sehari sebelum makan pagi.",
                "Tidak untuk ibu hamil, menyusui, dan anak di bawah 12 tahun. Bukan obat dan tidak untuk menyembuhkan penyakit.",
                110000, 89000, 45, 250, 550);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menyiapkan data awal", e);
        }
    }

    private JSONObject cat(String id, String name) throws Exception {
        JSONObject o = new JSONObject();
        o.put("categoryId", id); o.put("name", name); o.put("status", "ACTIVE");
        return o;
    }

    /* ================= KATEGORI ================= */
    public List<JSONObject> allCategories() {
        List<JSONObject> out = new ArrayList<>();
        for (String j : db.all(Config.C_CATEGORIES)) {
            try { out.add(new JSONObject(j)); } catch (Exception ignored) { }
        }
        return out;
    }

    public List<String> categoryNames() {
        List<String> out = new ArrayList<>();
        for (JSONObject o : allCategories()) {
            if (!"INACTIVE".equals(o.optString("status"))) out.add(o.optString("name"));
        }
        return out;
    }

    public void saveCategory(String name, String adminId) throws RuleException {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < 3) throw new RuleException("Nama kategori minimal 3 karakter");
        for (JSONObject o : allCategories()) {
            if (trimmed.equalsIgnoreCase(o.optString("name")))
                throw new RuleException("Kategori " + trimmed + " sudah ada");
        }
        try {
            String id = Util.id("CAT");
            db.put(Config.C_CATEGORIES, id, cat(id, trimmed).toString());
            log(adminId, "CATEGORY_CREATE", id, trimmed);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private Models.Product createProduct(String name, String sku, String category, String desc,
                                         String comp, String usage, String warn,
                                         long price, long promo, int stock, int weight, long points) throws Exception {
        Models.Product p = new Models.Product();
        p.productId = "PRD-" + sku;
        p.name = name; p.sku = sku; p.category = category; p.description = desc;
        p.composition = comp; p.usage = usage; p.warning = warn;
        p.price = price; p.promoPrice = promo; p.stock = stock; p.weight = weight; p.points = points;
        p.status = "ACTIVE";
        p.createdAt = Util.nowIso(); p.updatedAt = p.createdAt;
        db.put(Config.C_PRODUCTS, p.productId, p.toJson().toString());
        return p;
    }

    /* ================= SETTINGS ================= */
    public Models.Settings settings() {
        return Models.Settings.from(db.get(Config.C_SETTINGS, "global"));
    }

    public void saveSettings(Models.Settings s, String adminId) {
        try {
            db.put(Config.C_SETTINGS, "global", s.toJson().toString());
            if (adminId != null) log(adminId, "SETTINGS_UPDATE", "settings", s.toJson().toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /* ================= KONVERSI ================= */
    public long pointsToRupiah(long points) {
        Models.Settings s = settings();
        if (s.pointsPerUnit <= 0) return 0;
        return (points * s.rupiahPerUnit) / s.pointsPerUnit;
    }

    public long rupiahToPoints(long rupiah) {
        Models.Settings s = settings();
        if (s.rupiahPerUnit <= 0) return 0;
        return (rupiah * s.pointsPerUnit) / s.rupiahPerUnit;
    }

    /* ================= AUTH ================= */
    public Models.User login(String identifier, String password) throws RuleException {
        if (Util.isBlank(identifier) || Util.isBlank(password)) throw new RuleException("Isi email/HP dan password");
        String id = identifier.trim().toLowerCase();
        Models.User found = null;
        for (Models.User u : allUsers()) {
            if ((!Util.isBlank(u.email) && u.email.toLowerCase().equals(id))
                    || (!Util.isBlank(u.phone) && u.phone.equals(identifier.trim()))) {
                found = u; break;
            }
        }
        if (found == null) throw new RuleException("Akun tidak ditemukan");
        if (!"ACTIVE".equals(found.status)) throw new RuleException("Akun tidak aktif. Hubungi admin.");
        if (!PasswordHasher.verify(password, found.salt, found.passwordHash)) throw new RuleException("Password salah");
        return found;
    }

    public Models.User register(String name, String contact, String password, String referralCode) throws RuleException {
        name = name == null ? "" : name.trim();
        if (name.length() < 3) throw new RuleException("Nama minimal 3 karakter");
        if (password == null || password.length() < 6) throw new RuleException("Password minimal 6 karakter");
        String c = contact == null ? "" : contact.trim();
        boolean emailForm = Util.isEmail(c);
        String email = emailForm ? c : "";
        String phone = emailForm ? "" : c;
        if (!emailForm && !Util.isPhone(phone)) throw new RuleException("Nomor HP tidak valid (contoh 08xxxxxxxxxx)");

        for (Models.User u : allUsers()) {
            boolean dupEmail = !email.isEmpty() && u.email != null && u.email.toLowerCase().equals(email.toLowerCase());
            boolean dupPhone = !phone.isEmpty() && phone.equals(u.phone);
            if (dupEmail || dupPhone) throw new RuleException("Nomor HP/email sudah terdaftar");
        }

        Models.User inviter = null;
        if (!Util.isBlank(referralCode)) {
            String rid = referralCode.trim();
            if (!rid.matches("^\\d{6}$")) throw new RuleException("Referral ID harus tepat 6 digit angka");
            inviter = findByReferralId(rid);
            if (inviter == null) throw new RuleException("Referral ID tidak ditemukan");
            if (!"ACTIVE".equals(inviter.status)) throw new RuleException("Referral ID tidak aktif");
        }

        try {
            String salt = PasswordHasher.newSalt();
            Models.User u = new Models.User();
            u.userId = Util.id("USR");
            u.name = name; u.email = email; u.phone = phone;
            u.salt = salt;
            u.passwordHash = PasswordHasher.hash(password, salt);
            u.referralId = generateReferralId();
            u.referredBy = inviter == null ? null : inviter.userId;
            u.role = "MEMBER"; u.status = "ACTIVE"; u.verified = false; u.points = 0;
            u.createdAt = Util.nowIso(); u.updatedAt = u.createdAt;
            saveUser(u);

            if (inviter != null) {
                Models.Referral r = new Models.Referral();
                r.referralId = Util.id("REF");
                r.inviterId = inviter.userId;
                r.invitedUserId = u.userId;
                r.status = "PENDING";
                r.createdAt = Util.nowIso();
                db.put(Config.C_REFERRALS, r.referralId, r.toJson().toString());
            }
            return u;
        } catch (Exception e) {
            throw new RuleException("Gagal mendaftar: " + e.getMessage());
        }
    }

    private String generateReferralId() {
        for (int i = 0; i < 500; i++) {
            String v = String.valueOf(100000 + (int) (Math.random() * 900000));
            if (findByReferralId(v) == null) return v;
        }
        throw new IllegalStateException("Ruang Referral ID habis");
    }

    public Models.User findByReferralId(String rid) {
        for (Models.User u : allUsers()) if (rid.equals(u.referralId)) return u;
        return null;
    }

    public Models.User user(String userId) {
        String json = db.get(Config.C_USERS, userId);
        return json == null ? null : Models.User.from(json);
    }

    public List<Models.User> allUsers() {
        List<Models.User> out = new ArrayList<>();
        for (String j : db.all(Config.C_USERS)) out.add(Models.User.from(j));
        return out;
    }

    public List<Models.User> members() {
        List<Models.User> out = new ArrayList<>();
        for (Models.User u : allUsers()) if ("MEMBER".equals(u.role)) out.add(u);
        return out;
    }

    public void saveUser(Models.User u) {
        try {
            u.updatedAt = Util.nowIso();
            db.put(Config.C_USERS, u.userId, u.toJson().toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /**
     * Mengganti kata sandi pengguna. Kata sandi lama wajib benar agar pengguna
     * yang menemukan perangkat dalam keadaan terbuka tidak dapat mengambil alih
     * akun. Salt baru dibuat setiap perubahan sehingga hash lama tidak berlaku.
     */
    public void changePassword(String userId, String oldPassword, String newPassword, String confirm) throws RuleException {
        Models.User u = user(userId);
        if (u == null) throw new RuleException("Akun tidak ditemukan");
        if (Util.isBlank(oldPassword)) throw new RuleException("Isi password lama");
        if (!PasswordHasher.verify(oldPassword, u.salt, u.passwordHash)) throw new RuleException("Password lama salah");
        if (newPassword == null || newPassword.length() < 6) throw new RuleException("Password baru minimal 6 karakter");
        if (!newPassword.equals(confirm)) throw new RuleException("Konfirmasi password tidak sama");
        if (newPassword.equals(oldPassword)) throw new RuleException("Password baru harus berbeda dari password lama");
        String salt = PasswordHasher.newSalt();
        u.salt = salt;
        u.passwordHash = PasswordHasher.hash(newPassword, salt);
        saveUser(u);
        log(u.userId, "UBAH_PASSWORD", u.userId, "Password akun diperbarui");
    }

    /* ================= PRODUK ================= */
    public List<Models.Product> allProducts() {
        List<Models.Product> out = new ArrayList<>();
        for (String j : db.all(Config.C_PRODUCTS)) out.add(Models.Product.from(j));
        return out;
    }

    public List<Models.Product> activeProducts() {
        List<Models.Product> out = new ArrayList<>();
        for (Models.Product p : allProducts()) if ("ACTIVE".equals(p.status)) out.add(p);
        return out;
    }

    public Models.Product product(String productId) {
        String j = db.get(Config.C_PRODUCTS, productId);
        return j == null ? null : Models.Product.from(j);
    }

    public void saveProduct(Models.Product p, String adminId) {
        try {
            boolean isNew = db.get(Config.C_PRODUCTS, p.productId) == null;
            if (isNew) p.createdAt = Util.nowIso();
            p.updatedAt = Util.nowIso();
            db.put(Config.C_PRODUCTS, p.productId, p.toJson().toString());
            if (adminId != null) log(adminId, isNew ? "PRODUCT_CREATE" : "PRODUCT_UPDATE", p.productId, p.name);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public void adjustStock(String productId, int delta, String reason, String actorId) {
        Models.Product p = product(productId);
        if (p == null) throw new IllegalStateException("Produk tidak ditemukan");
        p.stock = Math.max(0, p.stock + delta);
        p.updatedAt = Util.nowIso();
        try {
            db.put(Config.C_PRODUCTS, p.productId, p.toJson().toString());
            JSONObject m = new JSONObject();
            m.put("id", Util.id("STK")); m.put("productId", productId);
            // Urutan riwayat ditentukan nomor urut, bukan waktu, karena
            // beberapa perubahan dapat terjadi pada detik yang sama.
            m.put("seq", db.all(Config.C_STOCK_MOVEMENTS).size() + 1);
            // SKU disimpan agar riwayat tetap terbaca walau produk sudah diubah.
            m.put("sku", p.sku); m.put("name", p.name); m.put("delta", delta);
            m.put("reason", reason); m.put("actorId", actorId); m.put("stockAfter", p.stock);
            m.put("createdAt", Util.nowIso());
            db.put(Config.C_STOCK_MOVEMENTS, m.getString("id"), m.toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /**
     * Riwayat pergerakan stok, terbaru lebih dahulu.
     *
     * Urutan memakai nomor urut penyimpanan, bukan urutan penyimpanan maupun
     * waktu, karena keduanya tidak dapat diandalkan saat beberapa perubahan
     * terjadi pada detik yang sama.
     */
    public List<JSONObject> stockMovements() {
        List<JSONObject> out = new ArrayList<>();
        for (String j : db.all(Config.C_STOCK_MOVEMENTS)) {
            try { out.add(new JSONObject(j)); } catch (Exception ignored) { }
        }
        java.util.Collections.sort(out, (a, b) -> {
            long sa = a.optLong("seq", 0), sb = b.optLong("seq", 0);
            if (sa != sb) return Long.compare(sb, sa);
            return b.optString("createdAt").compareTo(a.optString("createdAt"));
        });
        return out;
    }

    /* ================= POIN ================= */
    public long addPoints(String userId, long amount, String type, String note, String refId) {
        if (amount == 0) return user(userId).points;
        Models.User u = user(userId);
        if (u == null) return 0;
        u.points = Math.max(0, u.points + amount);
        saveUser(u);
        try {
            Models.Ledger l = new Models.Ledger();
            l.ledgerId = Util.id("PLG");
            l.userId = userId; l.amount = amount; l.type = type;
            l.note = note == null ? "" : note; l.refId = refId;
            l.createdAt = Util.nowIso();
            db.put(Config.C_POINTS_LEDGER, l.ledgerId, l.toJson().toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
        return u.points;
    }

    public List<Models.Ledger> ledger(String userId, int limit) {
        List<Models.Ledger> all = new ArrayList<>();
        for (String j : db.all(Config.C_POINTS_LEDGER)) {
            Models.Ledger l = Models.Ledger.from(j);
            if (userId == null || userId.equals(l.userId)) all.add(l);
        }
        List<Models.Ledger> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            out.add(all.get(i));
            if (limit > 0 && out.size() >= limit) break;
        }
        return out;
    }

    /* ================= XP & LEVEL AKUN ================= */

    /**
     * Menambah XP anggota. XP tidak pernah berkurang, jadi jumlahnya selalu
     * naik. Peristiwa dengan {@code refId} dan {@code type} yang sama diabaikan
     * agar satu pesanan atau satu tautan referral tidak pernah dihitung dua
     * kali, sebagaimana pencatatan pada buku besar poin.
     */
    public long addXp(String userId, long amount, String type, String note, String refId) {
        Models.User u = user(userId);
        if (u == null) return 0;
        if (amount <= 0) return u.xp;
        for (String j : db.all(Config.C_XP_EVENTS)) {
            Models.XpEvent e = Models.XpEvent.from(j);
            if (userId.equals(e.userId) && type != null && type.equals(e.type)
                    && refId != null && refId.equals(e.refId)) return u.xp;
        }
        u.xp = u.xp + amount;
        saveUser(u);
        try {
            Models.XpEvent e = new Models.XpEvent();
            e.eventId = Util.id("XPE");
            e.userId = userId; e.amount = amount; e.type = type;
            e.note = note == null ? "" : note; e.refId = refId;
            e.date = Util.todayKey(); e.createdAt = Util.nowIso();
            db.put(Config.C_XP_EVENTS, e.eventId, e.toJson().toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
        return u.xp;
    }

    public long xp(String userId) {
        Models.User u = user(userId);
        return u == null ? 0 : u.xp;
    }

    /** Level akun saat ini. */
    public int level(String userId) {
        return com.altomedia.herbalindo.level.Levels.levelFor(xp(userId));
    }

    /** Riwayat perolehan XP, terbaru lebih dahulu. */
    public List<Models.XpEvent> xpEvents(String userId, int limit) {
        List<Models.XpEvent> all = new ArrayList<>();
        for (String j : db.all(Config.C_XP_EVENTS)) {
            Models.XpEvent e = Models.XpEvent.from(j);
            if (userId == null || userId.equals(e.userId)) all.add(e);
        }
        List<Models.XpEvent> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            out.add(all.get(i));
            if (limit > 0 && out.size() >= limit) break;
        }
        return out;
    }

    /**
     * Jumlah hari check-in beruntun yang berakhir hari ini atau kemarin.
     * Bila hari ini belum check-in, rantai yang dihitung berakhir kemarin
     * sehingga hasilnya dipakai untuk menentukan XP check-in hari ini.
     */
    public long checkinStreak(String userId) {
        java.util.Set<String> days = new java.util.TreeSet<>();
        for (String j : db.all(Config.C_POINTS_LEDGER)) {
            Models.Ledger l = Models.Ledger.from(j);
            if (!"CHECKIN".equals(l.type) || !userId.equals(l.userId)) continue;
            if (l.createdAt != null && l.createdAt.length() >= 10) days.add(l.createdAt.substring(0, 10));
        }
        if (days.isEmpty()) return 0;
        java.util.Calendar c = java.util.Calendar.getInstance();
        if (!days.contains(Util.todayKey())) c.add(java.util.Calendar.DAY_OF_YEAR, -1);
        long streak = 0;
        while (days.contains(Util.dayKey(c.getTime()))) {
            streak++;
            c.add(java.util.Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    /**
     * XP keaktifan harian: diberikan sekali untuk setiap hari ketika anggota
     * membuka aplikasi. Dipanggil saat layar member aktif sehingga hari tanpa
     * aktivitas tidak pernah mendapat XP.
     */
    public long grantDailyActive(String userId) {
        Models.User u = user(userId);
        if (u == null || !"MEMBER".equals(u.role)) return u == null ? 0 : u.xp;
        String date = Util.todayKey();
        for (String j : db.all(Config.C_XP_EVENTS)) {
            Models.XpEvent e = Models.XpEvent.from(j);
            if (!userId.equals(e.userId) || !"DAILY_ACTIVE".equals(e.type)) continue;
            if (date.equals(e.date)) return u.xp;
        }
        return addXp(userId, Config.XP_DAILY_CHECKIN, "DAILY_ACTIVE",
                "Aktif " + date, "DAILY-" + date);
    }

    /* ================= DAILY TASK ================= */
    public Models.DailyTask todayTask(String userId) {
        String date = Util.todayKey();
        for (String j : db.all(Config.C_DAILY_TASKS)) {
            Models.DailyTask t = Models.DailyTask.from(j);
            if (userId.equals(t.userId) && date.equals(t.date)) return t;
        }
        Models.DailyTask t = new Models.DailyTask();
        t.taskId = Util.id("DTK");
        t.userId = userId; t.date = date;
        t.checkin = false; t.adsWatched = 0;
        t.createdAt = Util.nowIso(); t.updatedAt = t.createdAt;
        try { db.put(Config.C_DAILY_TASKS, t.taskId, t.toJson().toString()); }
        catch (Exception e) { throw new IllegalStateException("Gagal menyimpan tugas harian", e); }
        return t;
    }

    private void saveTask(Models.DailyTask t) {
        try {
            t.updatedAt = Util.nowIso();
            db.put(Config.C_DAILY_TASKS, t.taskId, t.toJson().toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public Models.DailyTask checkin(String userId) throws RuleException {
        Models.DailyTask t = todayTask(userId);
        if (t.checkin) throw new RuleException("Sudah check-in hari ini");
        long streak = checkinStreak(userId) + 1;
        t.checkin = true;
        saveTask(t);
        addPoints(userId, settings().checkinPoints, "CHECKIN", "Check-in " + t.date, t.taskId);
        // Keaktifan harian: XP bertambah untuk hari beruntun (Bab level akun).
        addXp(userId, com.altomedia.herbalindo.level.Levels.xpForCheckin(streak),
                "CHECKIN", "Check-in hari ke-" + streak, t.taskId);
        return t;
    }

    public long watchAd(String userId) throws RuleException {
        Models.Settings s = settings();
        Models.DailyTask t = todayTask(userId);
        if (t.adsWatched >= s.adMaxPerDay) throw new RuleException("Batas iklan harian tercapai (" + s.adMaxPerDay + ")");
        t.adsWatched += 1;
        saveTask(t);
        try {
            JSONObject r = new JSONObject();
            r.put("rewardId", Util.id("ADR")); r.put("userId", userId); r.put("date", t.date);
            r.put("points", s.adPoints); r.put("provider", "ADMOB_REWARDED");
            r.put("status", "CONFIRMED"); r.put("createdAt", Util.nowIso());
            db.put(Config.C_AD_REWARDS, r.getString("rewardId"), r.toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
        long total = addPoints(userId, s.adPoints, "AD",
                "Rewarded Ads " + t.adsWatched + "/" + s.adMaxPerDay, t.taskId);
        // Iklan berhadiah juga menyumbang XP keaktifan, sekali per hari per iklan.
        addXp(userId, Config.XP_AD, "AD",
                "Iklan berhadiah hari ini ke-" + t.adsWatched, t.taskId + "-" + t.adsWatched);
        return total;
    }

    public int adsToday(String userId) { return todayTask(userId).adsWatched; }

    public List<JSONObject> adRewards() {
        List<JSONObject> out = new ArrayList<>();
        for (String j : db.all(Config.C_AD_REWARDS)) {
            try { out.add(new JSONObject(j)); } catch (Exception ignored) { }
        }
        return out;
    }

    /* ================= CART ================= */
    public static class CartLine {
        public Models.Product product;
        public int qty;
        public long subtotal() { return product.effectivePrice() * qty; }
        public long points() { return product.points * qty; }
    }

    private final List<CartLine> cart = new ArrayList<>();

    public List<CartLine> cart() { return cart; }

    public void cartAdd(String productId, int qty) throws RuleException {
        Models.Product p = product(productId);
        if (p == null || !"ACTIVE".equals(p.status)) throw new RuleException("Produk tidak tersedia");
        CartLine line = null;
        for (CartLine c : cart) if (c.product.productId.equals(productId)) line = c;
        int next = (line == null ? 0 : line.qty) + qty;
        if (next > p.stock) throw new RuleException("Stok tidak mencukupi (tersisa " + p.stock + ")");
        if (line == null) { line = new CartLine(); line.product = p; cart.add(line); }
        line.qty = next;
    }

    public void cartSet(String productId, int qty) throws RuleException {
        CartLine line = null;
        for (CartLine c : cart) if (c.product.productId.equals(productId)) line = c;
        if (line == null) return;
        if (qty <= 0) { cart.remove(line); return; }
        if (qty > line.product.stock) throw new RuleException("Stok tidak mencukupi (tersisa " + line.product.stock + ")");
        line.qty = qty;
    }

    public void cartClear() { cart.clear(); }

    public long cartSubtotal() { long s = 0; for (CartLine c : cart) s += c.subtotal(); return s; }

    public long cartPoints() { long s = 0; for (CartLine c : cart) s += c.points(); return s; }

    public long cartShipping() {
        if (cart.isEmpty()) return 0;
        Models.Settings s = settings();
        if (s.freeShippingMin > 0 && cartSubtotal() >= s.freeShippingMin) return 0;
        return s.shippingFlat;
    }

    public long cartTotal() { return cartSubtotal() + cartShipping(); }

    /* ================= ORDER ================= */
    public Models.Order createOrder(Models.User user, String name, String phone, String address,
                                    String city, String postal, String note) throws RuleException {
        if (cart.isEmpty()) throw new RuleException("Keranjang kosong");
        if (Util.isBlank(name) || Util.isBlank(phone) || Util.isBlank(address) || Util.isBlank(city))
            throw new RuleException("Lengkapi nama, HP, alamat, dan kota");
        try {
            long seq = db.nextSequence("order");
            Models.Order o = new Models.Order();
            o.orderId = Util.id("ORD");
            o.userId = user.userId;
            o.orderNumber = "ORD-" + Util.orderDay() + "-" + String.format(java.util.Locale.US, "%06d", seq);
            o.items = new ArrayList<>();
            for (CartLine c : cart) {
                Models.OrderItem i = new Models.OrderItem();
                i.productId = c.product.productId; i.name = c.product.name;
                i.price = c.product.effectivePrice(); i.qty = c.qty;
                i.weight = c.product.weight; i.points = c.product.points * c.qty;
                o.items.add(i);
            }
            o.subtotal = cartSubtotal(); o.shippingCost = cartShipping(); o.total = cartTotal();
            o.paymentMethod = "QRIS"; o.paymentStatus = "UNPAID";
            o.shippingName = name; o.shippingPhone = phone;
            o.shippingAddressText = Util.isBlank(postal) ? address + ", " + city
                    : address + ", " + city + " " + postal;
            o.note = note == null ? "" : note;
            o.createdAt = Util.nowIso(); o.updatedAt = o.createdAt;
            db.put(Config.C_ORDERS, o.orderId, o.toJson().toString());

            for (Models.OrderItem i : o.items) adjustStock(i.productId, -i.qty, "ORDER " + o.orderNumber, user.userId);

            JSONObject pay = new JSONObject();
            pay.put("paymentId", Util.id("PAY")); pay.put("orderId", o.orderId);
            pay.put("method", "QRIS"); pay.put("amount", o.total);
            pay.put("status", "UNPAID"); pay.put("createdAt", Util.nowIso()); pay.put("paidAt", JSONObject.NULL);
            db.put(Config.C_PAYMENTS, pay.getString("paymentId"), pay.toString());

            // Simpan pada dokumen terbaru, bukan objek pemanggil yang mungkin
            // sudah basi: saldo poin dan XP yang bertambah di tempat lain akan
            // hilang bila dokumen lama ditulis ulang di sini.
            Models.User fresh = user(user.userId);
            if (fresh == null) fresh = user;
            fresh.lastAddress = address + "|" + city + "|" + postal;
            saveUser(fresh);

            markStatus(o, "WAITING_PAYMENT", user.userId, "Order dibuat");
            cartClear();
            return o;
        } catch (Exception e) {
            throw new RuleException("Gagal membuat order: " + e.getMessage());
        }
    }

    /** Menyimpan perubahan pada order yang sudah ada (mis. resi pengiriman). */
    public void saveOrder(Models.Order o) throws RuleException {
        if (o == null || Util.isBlank(o.orderId)) throw new RuleException("Order tidak valid");
        try {
            o.updatedAt = Util.nowIso();
            db.put(Config.C_ORDERS, o.orderId, o.toJson().toString());
        } catch (Exception e) {
            throw new RuleException("Gagal menyimpan order: " + e.getMessage());
        }
    }

    /**
     * Mencatat data pembayaran yang diisi pembeli.
     *
     * Admin memerlukan keterangan ini untuk mencocokkan pesanan dengan mutasi
     * yang benar-benar masuk. Nominal dinyatakan oleh pembeli, jadi kebenarannya
     * tetap diverifikasi admin, bukan dipercaya begitu saja. Karena itu status
     * pembayaran tidak langsung menjadi PAID melainkan VERIFYING.
     */
    public Models.Order submitPayment(String orderId, String buyerName, long paidAmount,
                                      String paidFrom, String paidNote) throws RuleException {
        Models.Order o = order(orderId);
        if (o == null) throw new RuleException("Pesanan tidak ditemukan");
        if ("PAID".equals(o.paymentStatus)) throw new RuleException("Pembayaran sudah terverifikasi");
        if (Util.isBlank(buyerName) || buyerName.trim().length() < 3)
            throw new RuleException("Nama pengirim minimal 3 karakter");
        if (paidAmount <= 0) throw new RuleException("Nominal transfer harus lebih dari 0");
        try {
            o.buyerName = buyerName.trim();
            o.paidAmount = paidAmount;
            o.paidFrom = paidFrom == null ? "" : paidFrom.trim();
            o.paidNote = paidNote == null ? "" : paidNote.trim();
            o.updatedAt = Util.nowIso();
            o.paymentStatus = "VERIFYING";
            db.put(Config.C_ORDERS, o.orderId, o.toJson().toString());
            syncPaymentDoc(o);
            log(o.userId, "PAYMENT_SUBMIT", o.orderNumber, Util.rupiah(paidAmount) + " dari " + o.buyerName);
            return o;
        } catch (Exception e) {
            throw new RuleException("Gagal menyimpan data pembayaran: " + e.getMessage());
        }
    }
    public Models.Order order(String orderId) {
        String j = db.get(Config.C_ORDERS, orderId);
        return j == null ? null : Models.Order.from(j);
    }

    public Models.Order orderByNumber(String number) {
        for (Models.Order o : allOrders()) if (number.equals(o.orderNumber)) return o;
        return null;
    }

    public List<Models.Order> allOrders() {
        List<Models.Order> out = new ArrayList<>();
        for (String j : db.all(Config.C_ORDERS)) out.add(Models.Order.from(j));
        return out;
    }

    public List<Models.Order> userOrders(String userId) {
        List<Models.Order> out = new ArrayList<>();
        for (Models.Order o : allOrders()) if (userId.equals(o.userId)) out.add(o);
        java.util.Collections.reverse(out);
        return out;
    }

    public void markStatus(Models.Order o, String status, String actorId, String note) {
        String prev = o.orderStatus;

        // Pembatalan/pengembalian hanya boleh diproses sekali. Bila status
        // akhir sudah sama dan pembatalan sudah pernah dijalankan, permintaan
        // ulang cukup diabaikan agar stok dan poin tidak digandakan.
        if (prev.equals(status) && ("REFUNDED".equals(status) || "CANCELLED".equals(status))
                && o.revocationDone) return;

        o.orderStatus = status;
        if ("PAID".equals(status) || "PROCESSING".equals(status) || "SHIPPED".equals(status)
                || "DELIVERED".equals(status) || "COMPLETED".equals(status)) o.paymentStatus = "PAID";
        if ("REFUNDED".equals(status)) o.paymentStatus = "REFUNDED";
        if ("CANCELLED".equals(status)) o.paymentStatus = "CANCELLED";

        boolean needsRevoke = ("REFUNDED".equals(status) || "CANCELLED".equals(status))
                && !o.revocationDone;
        if (needsRevoke) {
            o.revocationDone = true;
            o.revokedAt = Util.nowIso();
        }

        o.updatedAt = Util.nowIso();
        try {
            db.put(Config.C_ORDERS, o.orderId, o.toJson().toString());
            syncPaymentDoc(o);
        } catch (Exception e) { throw new IllegalStateException(e); }

        if (!prev.equals(status)) log(actorId, "ORDER_STATUS", o.orderNumber, prev + " -> " + status
                + (Util.isBlank(note) ? "" : " | " + note));

        if ("PAID".equals(status)) {
            grantPurchasePoints(o);
            qualifyReferral(o);
        }
        if (needsRevoke) revokeForOrder(o, actorId);
    }

    /** Menyelaraskan dokumen pembayaran dengan status pembayaran pada order. */
    private void syncPaymentDoc(Models.Order o) throws Exception {
        for (String j : db.all(Config.C_PAYMENTS)) {
            JSONObject pay = new JSONObject(j);
            if (o.orderId.equals(pay.optString("orderId"))) {
                pay.put("status", o.paymentStatus);
                if ("PAID".equals(o.paymentStatus) && pay.isNull("paidAt")) pay.put("paidAt", Util.nowIso());
                db.put(Config.C_PAYMENTS, pay.getString("paymentId"), pay.toString());
            }
        }
    }

    private void grantPurchasePoints(Models.Order o) {
        Models.Settings s = settings();
        if (!s.purchasePointsEnabled) return;
        for (Models.Ledger l : ledger(null, 0)) {
            if (o.orderId.equals(l.refId) && "PURCHASE".equals(l.type)) return;
        }
        long pts = 0;
        for (Models.OrderItem i : o.items) pts += i.points;
        if (pts > 0) addPoints(o.userId, pts, "PURCHASE", "Pembelian " + o.orderNumber, o.orderId);
        // Jalur level tercepat: nilai pesanan menentukan XP (Bab level akun).
        addXp(o.userId, com.altomedia.herbalindo.level.Levels.xpForPurchase(o.total),
                "PURCHASE", "Pembelian " + o.orderNumber, o.orderId);
    }

    /** Referral 1 tingkat: bonus hanya untuk pengundang langsung (Bab 3). */
    private void qualifyReferral(Models.Order o) {
        Models.Referral target = null;
        for (Models.Referral r : allReferrals()) {
            if (o.userId.equals(r.invitedUserId) && "PENDING".equals(r.status)) { target = r; break; }
        }
        if (target == null) return;
        Models.Settings s = settings();
        if (o.total < s.referralMinOrder) return;

        Models.User buyer = user(o.userId);
        if (buyer != null) { buyer.verified = true; saveUser(buyer); }
        target.status = "VERIFIED";
        target.qualifyingOrderId = o.orderId;
        target.bonusPoints = s.referralBonus;
        target.verifiedAt = Util.nowIso();
        try { db.put(Config.C_REFERRALS, target.referralId, target.toJson().toString()); }
        catch (Exception e) { throw new IllegalStateException(e); }
        addPoints(target.inviterId, s.referralBonus, "REFERRAL",
                "Bonus referral " + (buyer == null ? "" : buyer.name), target.referralId);
        addXp(target.inviterId, Config.XP_REFERRAL, "REFERRAL",
                "Undangan terverifikasi " + (buyer == null ? "" : buyer.name), target.referralId);
        log(null, "REFERRAL_VERIFIED", target.referralId, "Bonus " + s.referralBonus + " poin");
    }

    private void revokeForOrder(Models.Order o, String actorId) {
        long total = 0;
        for (Models.Ledger l : ledger(null, 0)) {
            if (o.orderId.equals(l.refId) && "PURCHASE".equals(l.type)) total += l.amount;
        }
        if (total > 0) addPoints(o.userId, -total, "PURCHASE_REVOKE", "Pembatalan poin " + o.orderNumber, o.orderId);

        for (Models.Referral r : allReferrals()) {
            if (o.orderId.equals(r.qualifyingOrderId) && "VERIFIED".equals(r.status)) {
                r.status = "CANCELLED";
                try { db.put(Config.C_REFERRALS, r.referralId, r.toJson().toString()); }
                catch (Exception e) { throw new IllegalStateException(e); }
                if (r.bonusPoints > 0) addPoints(r.inviterId, -r.bonusPoints, "REFERRAL_REVOKE",
                        "Bonus dibatalkan (refund " + o.orderNumber + ")", r.referralId);
                Models.User buyer = user(r.invitedUserId);
                if (buyer != null) { buyer.verified = false; saveUser(buyer); }
                log(actorId, "REFERRAL_REVOKED", r.referralId, o.orderNumber);
            }
        }
        for (Models.OrderItem i : o.items) adjustStock(i.productId, i.qty, "REFUND " + o.orderNumber, actorId);
    }

    /* ================= REFERRAL ================= */
    public List<Models.Referral> allReferrals() {
        List<Models.Referral> out = new ArrayList<>();
        for (String j : db.all(Config.C_REFERRALS)) out.add(Models.Referral.from(j));
        return out;
    }

    public List<Models.Referral> myReferrals(String inviterId) {
        List<Models.Referral> out = new ArrayList<>();
        for (Models.Referral r : allReferrals()) if (inviterId.equals(r.inviterId)) out.add(r);
        java.util.Collections.reverse(out);
        return out;
    }

    /** Jumlah referral member ini yang sudah terverifikasi (Bab 7.5). */
    public int verifiedReferralCount(String inviterId) {
        int n = 0;
        for (Models.Referral r : allReferrals()) {
            if (inviterId.equals(r.inviterId) && "VERIFIED".equals(r.status)) n++;
        }
        return n;
    }

    /** Poin pembelian yang diperoleh member pada tanggal tertentu (Bab 7.5). */
    public long purchasePointsOn(String userId, String date) {
        long total = 0;
        for (Models.Ledger l : ledger(userId, 0)) {
            if (!"PURCHASE".equals(l.type)) continue;
            if (l.createdAt != null && l.createdAt.startsWith(date)) total += l.amount;
        }
        return total;
    }

    /** Total nilai pesanan member yang tidak dibatalkan (Bab 7.5). */
    public long purchaseTotal(String userId) {
        long total = 0;
        for (Models.Order o : allOrders()) {
            if (!userId.equals(o.userId)) continue;
            if ("CANCELLED".equals(o.orderStatus) || "REFUNDED".equals(o.orderStatus)) continue;
            total += o.total;
        }
        return total;
    }

    /* ================= WITHDRAWAL ================= */
    public static class Eligibility {
        public boolean ok;
        public long saldo, ads, need;
        public List<String[]> checks = new ArrayList<>(); // {ok("1"/"0"), text}
    }

    public Eligibility eligibility(Models.User user) {
        // Ambil ulang dari penyimpanan: objek yang dipegang pemanggil bisa
        // sudah kedaluwarsa, misalnya setelah penandaan fraud.
        Models.User u = user == null ? null : user(user.userId);
        if (u == null) throw new IllegalStateException("Member tidak ditemukan");
        Models.Settings s = settings();
        Eligibility e = new Eligibility();
        e.saldo = pointsToRupiah(u.points);
        e.ads = adsToday(u.userId);
        e.need = s.adMaxPerDay;
        long usedToday = 0;
        for (Models.Withdrawal w : allWithdrawals()) {
            if (u.userId.equals(w.userId) && Util.todayKey().equals(w.date) && !"REJECTED".equals(w.status)) usedToday++;
        }
        e.checks.add(new String[]{e.saldo >= s.minWithdrawRupiah ? "1" : "0",
                "Saldo minimal " + Util.rupiah(s.minWithdrawRupiah) + " (sekarang " + Util.rupiah(e.saldo)
                        + "); BCA minimal " + Util.rupiah(Config.minWithdrawFor("BCA", s.minWithdrawRupiah))});
        e.checks.add(new String[]{(!s.requireAdsForWithdraw || e.ads >= e.need) ? "1" : "0",
                "Rewarded Ads hari ini " + e.ads + "/" + e.need});
        e.checks.add(new String[]{"ACTIVE".equals(u.status) ? "1" : "0", "Akun aktif"});
        e.checks.add(new String[]{!u.fraudFlag ? "1" : "0", "Tidak terkena pembatasan fraud"});
        e.checks.add(new String[]{usedToday < s.maxWithdrawPerDay ? "1" : "0",
                "Maksimal " + s.maxWithdrawPerDay + " withdrawal per hari"});
        e.ok = true;
        for (String[] c : e.checks) if ("0".equals(c[0])) e.ok = false;
        return e;
    }

    /**
     * Mengajukan pencairan saldo sebesar salah satu nominal pada
     * {@link Config#WITHDRAW_OPTIONS_RUPIAH}. Jumlah dalam rupiah, bukan poin,
     * karena member memilih nominal yang tertera pada antarmuka. Poin yang
     * dipotong dihitung dari nominal tersebut sehingga pembulatan konversi
     * tidak pernah membuat saldo terpotong lebih besar daripada yang diminta.
     */
    public Models.Withdrawal requestWithdrawal(Models.User u, long amountRupiah, String method,
                                               String accountName, String destination) throws RuleException {
        Models.Settings s = settings();
        Eligibility e = eligibility(u);
        if (!e.ok) throw new RuleException("Syarat withdrawal belum terpenuhi");
        if (!isWithdrawOption(amountRupiah))
            throw new RuleException("Pilih nominal pencairan: " + daftarNominal());
        if (!isWithdrawMethod(method)) throw new RuleException("Pilih metode: " + daftarMetode());
        method = method.trim();
        if (Util.isBlank(accountName) || accountName.trim().length() < 3)
            throw new RuleException("Nama pemilik rekening minimal 3 karakter");
        if (Util.isBlank(destination)) throw new RuleException(
                Config.isEwallet(method) ? "Nomor HP dompet digital wajib diisi"
                                         : "Nomor rekening wajib diisi");
        if (Config.isEwallet(method) && !Util.isPhone(destination.trim()))
            throw new RuleException("Nomor HP dompet digital tidak valid (contoh 08xxxxxxxxxx)");
        if ("BCA".equals(method) && !destination.trim().matches("^\\d{6,20}$"))
            throw new RuleException("Nomor rekening BCA harus 6-20 digit angka");
        if (amountRupiah < Config.minWithdrawFor(method, s.minWithdrawRupiah))
            throw new RuleException("Minimum withdrawal " + method + " "
                    + Util.rupiah(Config.minWithdrawFor(method, s.minWithdrawRupiah)));
        if (amountRupiah > e.saldo)
            throw new RuleException("Saldo tidak cukup, saldo tersedia " + Util.rupiah(e.saldo));
        long amountPoints = rupiahToPoints(amountRupiah);
        if (amountPoints > u.points) throw new RuleException("Poin tidak cukup");

        try {
            Models.Withdrawal w = new Models.Withdrawal();
            w.withdrawalId = "WD-" + System.currentTimeMillis();
            w.userId = u.userId;
            w.amountPoints = amountPoints; w.amountRupiah = amountRupiah;
            w.method = method; w.accountName = accountName.trim(); w.destination = destination.trim();
            w.status = "PENDING"; w.date = Util.todayKey();
            w.createdAt = Util.nowIso();
            db.put(Config.C_WITHDRAWALS, w.withdrawalId, w.toJson().toString());
            addPoints(u.userId, -amountPoints, "WITHDRAW_HOLD", "Pengajuan withdrawal " + w.withdrawalId, w.withdrawalId);
            return w;
        } catch (Exception ex) {
            throw new RuleException("Gagal mengajukan withdrawal: " + ex.getMessage());
        }
    }

    /** Nominal pencairan hanya boleh salah satu dari daftar pilihan tetap. */
    public static boolean isWithdrawOption(long rupiah) {
        for (long v : Config.WITHDRAW_OPTIONS_RUPIAH) if (v == rupiah) return true;
        return false;
    }

    /** Nominal pencairan yang boleh dipilih, hanya yang terjangkau saldo member. */
    public List<Long> withdrawOptionsFor(Models.User u) {
        return withdrawOptionsFor(u, null);
    }

    /**
     * Nominal pencairan untuk sebuah metode. Bila {@code method} diisi, batas
     * bawah metode itu ikut diterapkan sehingga pilihan yang tidak akan lolos
     * validasi tidak ditawarkan kepada member.
     */
    public List<Long> withdrawOptionsFor(Models.User u, String method) {
        if (u == null) return new ArrayList<>();
        Models.User fresh = user(u.userId);
        long saldo = pointsToRupiah((fresh == null ? u : fresh).points);
        long min = Config.minWithdrawFor(method, settings().minWithdrawRupiah);
        List<Long> out = new ArrayList<>();
        for (long v : Config.WITHDRAW_OPTIONS_RUPIAH) if (v <= saldo && v >= min) out.add(v);
        return out;
    }

    /** Daftar nominal pencairan untuk pesan bantuan, dipisah koma. */
    private static String daftarNominal() {
        StringBuilder sb = new StringBuilder();
        for (long v : Config.WITHDRAW_OPTIONS_RUPIAH) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Util.rupiah(v));
        }
        return sb.toString();
    }

    /** Daftar metode pencairan untuk pesan bantuan, dipisah koma tanpa kelas Android. */
    private static String daftarMetode() {
        StringBuilder sb = new StringBuilder();
        for (String m : Config.WITHDRAW_METHODS) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(m);
        }
        return sb.toString();
    }

    /** Metode pencairan hanya boleh salah satu dari daftar dropdown. */
    public static boolean isWithdrawMethod(String method) {
        if (Util.isBlank(method)) return false;
        for (String m : Config.WITHDRAW_METHODS) if (m.equals(method.trim())) return true;
        return false;
    }

    public List<Models.Withdrawal> allWithdrawals() {
        List<Models.Withdrawal> out = new ArrayList<>();
        for (String j : db.all(Config.C_WITHDRAWALS)) out.add(Models.Withdrawal.from(j));
        return out;
    }

    public void processWithdrawal(String id, String status, String adminId, String note) throws RuleException {
        Models.Withdrawal w = null;
        for (Models.Withdrawal x : allWithdrawals()) if (id.equals(x.withdrawalId)) w = x;
        if (w == null) throw new RuleException("Withdrawal tidak ditemukan");
        if (!"PENDING".equals(w.status)) throw new RuleException("Withdrawal sudah diproses");
        w.status = status; w.adminId = adminId; w.note = note == null ? "" : note;
        w.processedAt = Util.nowIso();
        try { db.put(Config.C_WITHDRAWALS, w.withdrawalId, w.toJson().toString()); }
        catch (Exception e) { throw new IllegalStateException(e); }
        if ("REJECTED".equals(status)) {
            addPoints(w.userId, w.amountPoints, "WITHDRAW_REFUND", "Withdrawal ditolak " + w.withdrawalId, w.withdrawalId);
        } else if ("PAID".equals(status)) {
            try {
                Models.Ledger l = new Models.Ledger();
                l.ledgerId = Util.id("PLG"); l.userId = w.userId; l.amount = 0;
                l.type = "WITHDRAW_PAID";
                l.note = "Dibayar " + Util.rupiah(w.amountRupiah) + " via " + w.method + " " + w.destination;
                l.refId = w.withdrawalId; l.createdAt = Util.nowIso();
                db.put(Config.C_POINTS_LEDGER, l.ledgerId, l.toJson().toString());
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        log(adminId, "WITHDRAWAL_" + status, w.withdrawalId, Util.rupiah(w.amountRupiah) + " | " + w.destination);
    }

    /* ================= ADMIN ================= */
    public void log(String adminId, String action, String target, String data) {
        try {
            JSONObject o = new JSONObject();
            o.put("logId", Util.id("LOG"));
            o.put("adminId", adminId); o.put("action", action);
            o.put("target", target == null ? "" : target);
            o.put("data", data == null ? "" : data);
            o.put("createdAt", Util.nowIso());
            db.put(Config.C_ADMIN_LOGS, o.getString("logId"), o.toString());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public List<JSONObject> adminLogs(int limit) {
        List<JSONObject> out = new ArrayList<>();
        List<String> js = db.all(Config.C_ADMIN_LOGS);
        for (int i = js.size() - 1; i >= 0; i--) {
            try { out.add(new JSONObject(js.get(i))); } catch (Exception ignored) { }
            if (limit > 0 && out.size() >= limit) break;
        }
        return out;
    }

    public void adminAdjustBalance(String userId, long amount, String reason, String adminId) throws RuleException {
        if (Util.isBlank(reason) || reason.trim().length() < 3) throw new RuleException("Alasan wajib diisi");
        if (user(userId) == null) throw new RuleException("Member tidak ditemukan");
        addPoints(userId, amount, amount >= 0 ? "ADMIN_CREDIT" : "ADMIN_DEBIT", reason, null);
        log(adminId, "SALDO_ADJUST", userId, (amount >= 0 ? "+" : "") + amount + " poin | " + reason);
    }

    /**
     * Menetapkan saldo poin member ke nilai tertentu.
     *
     * Berbeda dari {@link #adminAdjustBalance} yang menambah atau mengurangi,
     * cara ini langsung menentukan hasil akhir sehingga admin dapat mengoreksi
     * saldo tanpa menghitung selisihnya. Selisihnya tetap dicatat pada ledger
     * agar riwayat poin tetap utuh dan dapat diaudit.
     */
    public void adminSetPoints(String userId, long targetPoints, String reason, String adminId) throws RuleException {
        if (targetPoints < 0) throw new RuleException("Poin tidak boleh negatif");
        if (Util.isBlank(reason) || reason.trim().length() < 3) throw new RuleException("Alasan wajib diisi");
        Models.User u = user(userId);
        if (u == null) throw new RuleException("Member tidak ditemukan");
        long delta = targetPoints - u.points;
        if (delta == 0) throw new RuleException("Poin sudah bernilai " + Util.num(targetPoints));
        addPoints(userId, delta, delta >= 0 ? "ADMIN_SET" : "ADMIN_SET", reason, null);
        log(adminId, "POIN_SET", userId,
                Util.num(u.points) + " → " + Util.num(targetPoints) + " poin | " + reason);
    }

    public void setUserStatus(String userId, String status, String adminId) throws RuleException {
        Models.User u = user(userId);
        if (u == null) throw new RuleException("Member tidak ditemukan");
        u.status = status;
        saveUser(u);
        log(adminId, "MEMBER_STATUS", userId, status);
    }

    public void setFraud(String userId, boolean flag, String adminId) throws RuleException {
        Models.User u = user(userId);
        if (u == null) throw new RuleException("Member tidak ditemukan");
        u.fraudFlag = flag;
        saveUser(u);
        log(adminId, "MEMBER_FRAUD", userId, flag ? "DITANDAI" : "DIBERSIHKAN");
    }

    /* ================= KELOLA MEMBER (ADMIN) ================= */

    /**
     * Mencari member berdasarkan Referral ID, User ID, nomor HP, atau email.
     *
     * Pencarian mengabaikan huruf besar/kecil dan spasi di ujung, karena admin
     * sering menempelkan data dari sumber lain. Hanya akun berperan MEMBER yang
     * dikembalikan agar akun admin tidak dapat diubah dari daftar member.
     */
    public List<Models.User> searchMembers(String query) {
        List<Models.User> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.US);
        if (q.isEmpty()) return members();
        for (Models.User u : members()) {
            if (matches(u.userId, q) || matches(u.referralId, q) || matches(u.email, q)
                    || matches(u.phone, q) || matches(u.name, q)) out.add(u);
        }
        return out;
    }

    private static boolean matches(String value, String query) {
        return value != null && value.toLowerCase(java.util.Locale.US).contains(query);
    }

    /**
     * Menyunting data pokok member dari panel admin.
     *
     * Nomor HP dan email wajib tetap unik antar akun: bila duplikat dibiarkan,
     * satu nomor dapat dipakai dua akun dan proses masuk menjadi tidak jelas
     * akun mana yang dibuka. Kata sandi hanya diganti bila diisi.
     */
    public void adminUpdateMember(String userId, String name, String email, String phone,
                                  String newPassword, String adminId) throws RuleException {
        Models.User u = user(userId);
        if (u == null) throw new RuleException("Member tidak ditemukan");
        String n = name == null ? "" : name.trim();
        if (n.length() < 3) throw new RuleException("Nama minimal 3 karakter");

        String e = email == null ? "" : email.trim();
        String p = phone == null ? "" : phone.trim();
        if (!e.isEmpty() && !Util.isEmail(e)) throw new RuleException("Format email tidak valid");
        if (!p.isEmpty() && !Util.isPhone(p)) throw new RuleException("Nomor HP tidak valid (contoh 08xxxxxxxxxx)");
        if (e.isEmpty() && p.isEmpty()) throw new RuleException("Email atau nomor HP wajib diisi");

        for (Models.User other : allUsers()) {
            if (other.userId.equals(userId)) continue;
            if (!e.isEmpty() && other.email != null && other.email.toLowerCase(java.util.Locale.US)
                    .equals(e.toLowerCase(java.util.Locale.US)))
                throw new RuleException("Email sudah dipakai akun lain");
            if (!p.isEmpty() && p.equals(other.phone))
                throw new RuleException("Nomor HP sudah dipakai akun lain");
        }

        StringBuilder changes = new StringBuilder();
        if (!n.equals(u.name)) changes.append("nama; ");
        if (!e.equals(u.email)) changes.append("email; ");
        if (!p.equals(u.phone)) changes.append("HP; ");

        u.name = n;
        u.email = e;
        u.phone = p;
        if (!Util.isBlank(newPassword)) {
            if (newPassword.length() < 6) throw new RuleException("Password minimal 6 karakter");
            String salt = PasswordHasher.newSalt();
            u.salt = salt;
            u.passwordHash = PasswordHasher.hash(newPassword, salt);
            changes.append("password; ");
        }
        saveUser(u);
        log(adminId, "MEMBER_EDIT", userId,
                changes.length() == 0 ? "tanpa perubahan" : changes.toString().trim());
    }

    /**
     * Menghapus akun member beserta seluruh data yang menjadi miliknya.
     *
     * Data yang dihapus meliputi keranjang tersimpan, ledger poin, reward iklan,
     * tugas harian, event XP, pengajuan withdrawal, order beserta itemnya,
     * pembayaran, dan catatan referral — baik sebagai pengundang maupun yang
     * diundang. Tanpa pembersihan ini, sisa dokumen akan merujuk ke akun yang
     * sudah tidak ada dan membuat laporan admin salah hitung.
     */
    public void adminDeleteMember(String userId, String reason, String adminId) throws RuleException {
        Models.User u = user(userId);
        if (u == null) throw new RuleException("Member tidak ditemukan");
        if ("ADMIN".equals(u.role)) throw new RuleException("Akun admin tidak dapat dihapus");
        if (Util.isBlank(reason) || reason.trim().length() < 3) throw new RuleException("Alasan wajib diisi");

        // Order milik member dihapus lebih dulu; item pesanan tersimpan di
        // dalam dokumen order, jadi cukup dokumen ordernya.
        for (Models.Order o : allOrders()) {
            if (!userId.equals(o.userId)) continue;
            db.delete(Config.C_ORDERS, o.orderId);
        }
        for (String id : db.all(Config.C_PAYMENTS)) removeIfOwner(Config.C_PAYMENTS, id, "orderId", orderIdsOf(userId));
        for (String id : db.all(Config.C_POINTS_LEDGER)) removeIfOwner(Config.C_POINTS_LEDGER, id, "userId", userId);
        for (String id : db.all(Config.C_AD_REWARDS)) removeIfOwner(Config.C_AD_REWARDS, id, "userId", userId);
        for (String id : db.all(Config.C_DAILY_TASKS)) removeIfOwner(Config.C_DAILY_TASKS, id, "userId", userId);
        for (String id : db.all(Config.C_XP_EVENTS)) removeIfOwner(Config.C_XP_EVENTS, id, "userId", userId);
        for (Models.Withdrawal w : allWithdrawals()) {
            if (userId.equals(w.userId)) db.delete(Config.C_WITHDRAWALS, w.withdrawalId);
        }
        for (Models.Referral r : allReferrals()) {
            if (userId.equals(r.inviterId) || userId.equals(r.invitedUserId)) {
                db.delete(Config.C_REFERRALS, r.referralId);
            }
        }

        db.delete(Config.C_USERS, userId);
        log(adminId, "MEMBER_DELETE", userId, u.name + " | " + u.contact() + " | " + reason.trim());
    }

    /** Daftar orderId milik seorang member, dipakai untuk menelusuri pembayaran. */
    private java.util.Set<String> orderIdsOf(String userId) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Models.Order o : allOrders()) if (userId.equals(o.userId)) ids.add(o.orderId);
        return ids;
    }

    /** Menghapus dokumen bila kolom penanda pada JSON-nya cocok dengan nilai. */
    private void removeIfOwner(String collection, String docId, String field, String value) {
        try {
            JSONObject o = new JSONObject(db.get(collection, docId));
            if (value != null && value.equals(o.optString(field))) db.delete(collection, docId);
        } catch (Exception ignored) { }
    }

    /** Menghapus dokumen bila kolom penanda pada JSON-nya ada di dalam himpunan. */
    private void removeIfOwner(String collection, String docId, String field, java.util.Set<String> values) {
        try {
            JSONObject o = new JSONObject(db.get(collection, docId));
            if (values.contains(o.optString(field))) db.delete(collection, docId);
        } catch (Exception ignored) { }
    }

    /* ================= DETEKSI FRAUD (BAB 13.4) ================= */

    /** Satu temuan pemeriksaan anti-fraud beserta alasan yang dapat dibaca admin. */
    public static class FraudFinding {
        public String userId, userName, code, detail;
        public boolean blocking;

        FraudFinding(String userId, String userName, String code, String detail, boolean blocking) {
            this.userId = userId; this.userName = userName;
            this.code = code; this.detail = detail; this.blocking = blocking;
        }
    }

    /**
     * Memeriksa pola yang disebut Bab 13.4: akun duplikat, referral
     * mencurigakan, transaksi berulang, pembatalan setelah bonus, dan
     * penyalahgunaan reward iklan.
     *
     * Temuan bersifat laporan untuk admin; hanya pola yang sudah pasti
     * merugikan (bonus referral dicairkan lalu pesanannya dibatalkan) yang
     * bersifat memblokir pencairan.
     */
    public List<FraudFinding> fraudFindings() {
        List<FraudFinding> out = new ArrayList<>();
        List<Models.User> users = members();

        detectDuplicateAccounts(users, out);
        detectReferralPola(users, out);
        detectTransaksiBerulang(users, out);
        detectBatalSetelahBonus(users, out);
        detectRewardBerlebih(users, out);

        return out;
    }

    /** Akun berbeda yang memakai nama sama persis. */
    private void detectDuplicateAccounts(List<Models.User> users, List<FraudFinding> out) {
        java.util.Map<String, List<Models.User>> byName = new java.util.HashMap<>();
        for (Models.User u : users) {
            String key = u.name == null ? "" : u.name.trim().toLowerCase(java.util.Locale.US);
            if (key.length() < 3) continue;
            byName.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
        }
        for (java.util.Map.Entry<String, List<Models.User>> e : byName.entrySet()) {
            if (e.getValue().size() < 2) continue;
            StringBuilder ids = new StringBuilder();
            for (Models.User u : e.getValue()) {
                if (ids.length() > 0) ids.append(", ");
                ids.append(u.phone.isEmpty() ? u.email : u.phone);
            }
            for (Models.User u : e.getValue()) {
                out.add(new FraudFinding(u.userId, u.name, "AKUN_DUPLIKAT",
                        e.getValue().size() + " akun memakai nama sama: " + ids, false));
            }
        }
    }

    /** Pengundang yang mengumpulkan banyak referral dalam waktu singkat. */
    private void detectReferralPola(List<Models.User> users, List<FraudFinding> out) {
        final int batasSehari = 5;
        java.util.Map<String, Integer> perHari = new java.util.HashMap<>();
        for (Models.Referral r : allReferrals()) {
            String hari = r.createdAt == null || r.createdAt.length() < 10 ? "" : r.createdAt.substring(0, 10);
            String key = r.inviterId + "|" + hari;
            perHari.put(key, perHari.getOrDefault(key, 0) + 1);
        }
        for (java.util.Map.Entry<String, Integer> e : perHari.entrySet()) {
            if (e.getValue() < batasSehari) continue;
            String inviterId = e.getKey().split("\\|")[0];
            Models.User u = user(inviterId);
            if (u == null) continue;
            out.add(new FraudFinding(u.userId, u.name, "REFERRAL_MENCURIGAKAN",
                    e.getValue() + " referral masuk pada satu hari (" + e.getKey().split("\\|")[1] + ")", false));
        }
    }

    /** Pembeli yang membuat banyak pesanan pada hari yang sama. */
    private void detectTransaksiBerulang(List<Models.User> users, List<FraudFinding> out) {
        final int batasSehari = 8;
        java.util.Map<String, Integer> perHari = new java.util.HashMap<>();
        for (Models.Order o : allOrders()) {
            String hari = o.createdAt == null || o.createdAt.length() < 10 ? "" : o.createdAt.substring(0, 10);
            String key = o.userId + "|" + hari;
            perHari.put(key, perHari.getOrDefault(key, 0) + 1);
        }
        for (java.util.Map.Entry<String, Integer> e : perHari.entrySet()) {
            if (e.getValue() < batasSehari) continue;
            String userId = e.getKey().split("\\|")[0];
            Models.User u = user(userId);
            if (u == null) continue;
            out.add(new FraudFinding(u.userId, u.name, "TRANSAKSI_BERULANG",
                    e.getValue() + " pesanan pada satu hari (" + e.getKey().split("\\|")[1] + ")", false));
        }
    }

    /** Bonus referral yang sudah masuk lalu pesanannya dibatalkan. */
    private void detectBatalSetelahBonus(List<Models.User> users, List<FraudFinding> out) {
        for (Models.Referral r : allReferrals()) {
            if (!"CANCELLED".equals(r.status) || r.bonusPoints <= 0) continue;
            Models.User u = user(r.inviterId);
            if (u == null) continue;
            out.add(new FraudFinding(u.userId, u.name, "BATAL_SETELAH_BONUS",
                    "Bonus referral " + Util.num(r.bonusPoints) + " poin ditarik kembali setelah pesanan dibatalkan", true));
        }
    }

    /** Reward iklan yang tidak wajar, misalnya tercatat melebihi batas harian. */
    private void detectRewardBerlebih(List<Models.User> users, List<FraudFinding> out) {
        Models.Settings s = settings();
        java.util.Map<String, Integer> perHari = new java.util.HashMap<>();
        for (JSONObject r : adRewards()) {
            String key = r.optString("userId") + "|" + r.optString("date");
            perHari.put(key, perHari.getOrDefault(key, 0) + 1);
        }
        for (java.util.Map.Entry<String, Integer> e : perHari.entrySet()) {
            if (e.getValue() <= s.adMaxPerDay) continue;
            String userId = e.getKey().split("\\|")[0];
            Models.User u = user(userId);
            if (u == null) continue;
            out.add(new FraudFinding(u.userId, u.name, "REWARD_BERLEBIH",
                    e.getValue() + " reward iklan pada " + e.getKey().split("\\|")[1]
                            + " (batas " + s.adMaxPerDay + ")", true));
        }
    }

    /** Menandai seluruh member yang muncul pada temuan yang bersifat memblokir. */
    public int applyFraudFindings(String adminId) {
        int ditandai = 0;
        for (FraudFinding f : fraudFindings()) {
            if (!f.blocking) continue;
            Models.User u = user(f.userId);
            if (u == null || u.fraudFlag) continue;
            u.fraudFlag = true;
            saveUser(u);
            log(adminId, "MEMBER_FRAUD", f.userId, "OTOMATIS: " + f.code + " | " + f.detail);
            ditandai++;
        }
        return ditandai;
    }

    public static class Stats {
        public int totalMember, activeMember, verifiedMember, totalProducts, totalOrders,
                orderPending, orderProcessing, withdrawalPending, totalReferral, verifiedReferral, adActivity, adsToday,
                lowStock;
        public long totalPoints, totalBalance, withdrawalPendingRupiah;
    }

    public Stats stats() {
        Stats s = new Stats();
        for (Models.User u : members()) {
            s.totalMember++;
            if ("ACTIVE".equals(u.status)) s.activeMember++;
            if (u.verified) s.verifiedMember++;
            s.totalPoints += u.points;
            s.totalBalance += pointsToRupiah(u.points);
        }
        s.totalProducts = allProducts().size();
        for (Models.Product p : allProducts()) if (p.stock <= p.minStock) s.lowStock++;
        for (Models.Order o : allOrders()) {
            s.totalOrders++;
            if ("PENDING".equals(o.orderStatus) || "WAITING_PAYMENT".equals(o.orderStatus)) s.orderPending++;
            if ("PAID".equals(o.orderStatus) || "PROCESSING".equals(o.orderStatus) || "SHIPPED".equals(o.orderStatus)) s.orderProcessing++;
        }
        for (Models.Withdrawal w : allWithdrawals()) if ("PENDING".equals(w.status)) {
            s.withdrawalPending++;
            s.withdrawalPendingRupiah += w.amountRupiah;
        }
        for (Models.Referral r : allReferrals()) {
            s.totalReferral++;
            if ("VERIFIED".equals(r.status)) s.verifiedReferral++;
        }
        List<JSONObject> ads = adRewards();
        s.adActivity = ads.size();
        for (JSONObject a : ads) if (Util.todayKey().equals(a.optString("date"))) s.adsToday++;
        return s;
    }

    /** Akses penyimpanan (dipakai pengujian). */
    public Store store() { return db; }

}