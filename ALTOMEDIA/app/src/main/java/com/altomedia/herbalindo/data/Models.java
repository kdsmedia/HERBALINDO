package com.altomedia.herbalindo.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import com.altomedia.herbalindo.core.Util;

/** Model dokumen. Serialisasi JSON agar sepadan dengan dokumen Firestore (Bab 12). */
public final class Models {
    private Models() {}

    /* ---------------- users/{userId} ---------------- */
    public static class User {
        public String userId, name, email = "", phone = "", salt, passwordHash;
        public String referralId, referredBy, role = "MEMBER", status = "ACTIVE";
        public boolean verified, fraudFlag;
        public long points;
        public String createdAt, updatedAt;
        public String lastAddress = "";

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("userId", userId); o.put("name", name); o.put("email", email); o.put("phone", phone);
            o.put("salt", salt); o.put("passwordHash", passwordHash);
            o.put("referralId", referralId); o.put("referredBy", referredBy);
            o.put("role", role); o.put("status", status);
            o.put("verified", verified); o.put("fraudFlag", fraudFlag);
            o.put("points", points); o.put("createdAt", createdAt); o.put("updatedAt", updatedAt);
            o.put("lastAddress", lastAddress);
            return o;
        }

        public static User from(String json) {
            User u = new User();
            try {
                JSONObject o = new JSONObject(json);
                u.userId = o.optString("userId");
                u.name = o.optString("name");
                u.email = o.optString("email");
                u.phone = o.optString("phone");
                u.salt = o.optString("salt");
                u.passwordHash = o.optString("passwordHash");
                u.referralId = o.optString("referralId");
                u.referredBy = o.isNull("referredBy") ? null : o.optString("referredBy", null);
                u.role = o.optString("role", "MEMBER");
                u.status = o.optString("status", "ACTIVE");
                u.verified = o.optBoolean("verified");
                u.fraudFlag = o.optBoolean("fraudFlag");
                u.points = o.optLong("points");
                u.createdAt = o.optString("createdAt");
                u.updatedAt = o.optString("updatedAt");
                u.lastAddress = o.optString("lastAddress");
            } catch (JSONException ignored) { }
            return u;
        }

        public String contact() { return !Util.isBlank(email) ? email : phone; }
    }

    /* ---------------- products/{productId} ---------------- */
    public static class Product {
        public String productId, name, sku = "", category = "", description = "", composition = "",
                usage = "", warning = "", imageUrl = "", status = "ACTIVE";
        public long price, promoPrice, points;
        public int stock, minStock, weight;
        public String createdAt, updatedAt;

        public long effectivePrice() {
            return (promoPrice > 0 && promoPrice < price) ? promoPrice : price;
        }
        public boolean hasPromo() { return promoPrice > 0 && promoPrice < price; }

        /** Produk tayang bila status bukan INACTIVE. */
        public boolean active() { return !"INACTIVE".equals(status); }

        public void setActive(boolean on) { status = on ? "ACTIVE" : "INACTIVE"; }

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("productId", productId); o.put("name", name); o.put("sku", sku);
            o.put("category", category); o.put("description", description);
            o.put("composition", composition); o.put("usage", usage); o.put("warning", warning);
            o.put("imageUrl", imageUrl); o.put("status", status);
            o.put("price", price); o.put("promoPrice", promoPrice); o.put("points", points);
            o.put("stock", stock); o.put("minStock", minStock); o.put("weight", weight);
            o.put("createdAt", createdAt); o.put("updatedAt", updatedAt);
            return o;
        }

        public static Product from(String json) {
            Product p = new Product();
            try {
                JSONObject o = new JSONObject(json);
                p.productId = o.optString("productId"); p.name = o.optString("name");
                p.sku = o.optString("sku"); p.category = o.optString("category");
                p.description = o.optString("description"); p.composition = o.optString("composition");
                p.usage = o.optString("usage"); p.warning = o.optString("warning");
                p.imageUrl = o.optString("imageUrl"); p.status = o.optString("status", "ACTIVE");
                p.price = o.optLong("price"); p.promoPrice = o.optLong("promoPrice");
                p.points = o.optLong("points"); p.stock = o.optInt("stock");
                p.minStock = o.optInt("minStock"); p.weight = o.optInt("weight");
                p.createdAt = o.optString("createdAt"); p.updatedAt = o.optString("updatedAt");
            } catch (JSONException ignored) { }
            return p;
        }
    }

    /* ---------------- orders/{orderId} ---------------- */
    public static class OrderItem {
        public String productId, name;
        public long price, points;
        public int qty, weight;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("productId", productId); o.put("name", name); o.put("price", price);
            o.put("qty", qty); o.put("weight", weight); o.put("points", points);
            return o;
        }
        public static OrderItem from(JSONObject o) {
            OrderItem i = new OrderItem();
            i.productId = o.optString("productId"); i.name = o.optString("name");
            i.price = o.optLong("price"); i.qty = o.optInt("qty");
            i.weight = o.optInt("weight"); i.points = o.optLong("points");
            return i;
        }
        public long subtotal() { return price * qty; }
    }

    public static class Order {
        public String orderId, userId, orderNumber, paymentMethod = "QRIS", paymentStatus = "UNPAID",
                orderStatus = "PENDING", shippingCourier = "", trackingNumber = "",
                shippingAddressText = "", shippingName = "", shippingPhone = "", note = "";
        public List<OrderItem> items = new ArrayList<>();
        public long subtotal, shippingCost, total;
        public String createdAt, updatedAt;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("orderId", orderId); o.put("userId", userId); o.put("orderNumber", orderNumber);
            JSONArray arr = new JSONArray();
            for (OrderItem i : items) arr.put(i.toJson());
            o.put("items", arr);
            o.put("subtotal", subtotal); o.put("shippingCost", shippingCost); o.put("total", total);
            o.put("paymentMethod", paymentMethod); o.put("paymentStatus", paymentStatus);
            o.put("orderStatus", orderStatus); o.put("shippingCourier", shippingCourier);
            o.put("trackingNumber", trackingNumber); o.put("shippingAddressText", shippingAddressText);
            o.put("shippingName", shippingName); o.put("shippingPhone", shippingPhone);
            o.put("note", note); o.put("createdAt", createdAt); o.put("updatedAt", updatedAt);
            return o;
        }

        public static Order from(String json) {
            Order ord = new Order();
            try {
                JSONObject o = new JSONObject(json);
                ord.orderId = o.optString("orderId"); ord.userId = o.optString("userId");
                ord.orderNumber = o.optString("orderNumber");
                JSONArray arr = o.optJSONArray("items");
                if (arr != null) for (int i = 0; i < arr.length(); i++) ord.items.add(OrderItem.from(arr.getJSONObject(i)));
                ord.subtotal = o.optLong("subtotal"); ord.shippingCost = o.optLong("shippingCost");
                ord.total = o.optLong("total");
                ord.paymentMethod = o.optString("paymentMethod", "QRIS");
                ord.paymentStatus = o.optString("paymentStatus", "UNPAID");
                ord.orderStatus = o.optString("orderStatus", "PENDING");
                ord.shippingCourier = o.optString("shippingCourier");
                ord.trackingNumber = o.optString("trackingNumber");
                ord.shippingAddressText = o.optString("shippingAddressText");
                ord.shippingName = o.optString("shippingName");
                ord.shippingPhone = o.optString("shippingPhone");
                ord.note = o.optString("note");
                ord.createdAt = o.optString("createdAt"); ord.updatedAt = o.optString("updatedAt");
            } catch (JSONException ignored) { }
            return ord;
        }
    }

    /* ---------------- points_ledger/{ledgerId} ---------------- */
    public static class Ledger {
        public String ledgerId, userId, type, note = "", refId, createdAt;
        public long amount;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("ledgerId", ledgerId); o.put("userId", userId); o.put("amount", amount);
            o.put("type", type); o.put("note", note); o.put("refId", refId); o.put("createdAt", createdAt);
            return o;
        }
        public static Ledger from(String json) {
            Ledger l = new Ledger();
            try {
                JSONObject o = new JSONObject(json);
                l.ledgerId = o.optString("ledgerId"); l.userId = o.optString("userId");
                l.amount = o.optLong("amount"); l.type = o.optString("type");
                l.note = o.optString("note"); l.refId = o.optString("refId", null);
                l.createdAt = o.optString("createdAt");
            } catch (JSONException ignored) { }
            return l;
        }
    }

    /* ---------------- referrals/{referralId} ---------------- */
    public static class Referral {
        public String referralId, inviterId, invitedUserId, status = "PENDING", qualifyingOrderId, createdAt, verifiedAt;
        public long bonusPoints;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("referralId", referralId); o.put("inviterId", inviterId);
            o.put("invitedUserId", invitedUserId); o.put("status", status);
            o.put("qualifyingOrderId", qualifyingOrderId); o.put("bonusPoints", bonusPoints);
            o.put("createdAt", createdAt); o.put("verifiedAt", verifiedAt);
            return o;
        }
        public static Referral from(String json) {
            Referral r = new Referral();
            try {
                JSONObject o = new JSONObject(json);
                r.referralId = o.optString("referralId"); r.inviterId = o.optString("inviterId");
                r.invitedUserId = o.optString("invitedUserId"); r.status = o.optString("status", "PENDING");
                r.qualifyingOrderId = o.optString("qualifyingOrderId", null);
                r.bonusPoints = o.optLong("bonusPoints");
                r.createdAt = o.optString("createdAt"); r.verifiedAt = o.optString("verifiedAt", null);
            } catch (JSONException ignored) { }
            return r;
        }
    }

    /* ---------------- daily_tasks/{taskId} ---------------- */
    public static class DailyTask {
        public String taskId, userId, date;
        public boolean checkin;
        public int adsWatched;
        public String createdAt, updatedAt;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("taskId", taskId); o.put("userId", userId); o.put("date", date);
            o.put("checkin", checkin); o.put("adsWatched", adsWatched);
            o.put("createdAt", createdAt); o.put("updatedAt", updatedAt);
            return o;
        }
        public static DailyTask from(String json) {
            DailyTask t = new DailyTask();
            try {
                JSONObject o = new JSONObject(json);
                t.taskId = o.optString("taskId"); t.userId = o.optString("userId");
                t.date = o.optString("date"); t.checkin = o.optBoolean("checkin");
                t.adsWatched = o.optInt("adsWatched");
                t.createdAt = o.optString("createdAt"); t.updatedAt = o.optString("updatedAt");
            } catch (JSONException ignored) { }
            return t;
        }
    }

    /* ---------------- withdrawals/{withdrawalId} ---------------- */
    public static class Withdrawal {
        public String withdrawalId, userId, method, destination, status = "PENDING", adminId, note = "", date, createdAt, processedAt;
        public long amountPoints, amountRupiah;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("withdrawalId", withdrawalId); o.put("userId", userId);
            o.put("amountPoints", amountPoints); o.put("amountRupiah", amountRupiah);
            o.put("method", method); o.put("destination", destination); o.put("status", status);
            o.put("adminId", adminId); o.put("note", note); o.put("date", date);
            o.put("createdAt", createdAt); o.put("processedAt", processedAt);
            return o;
        }
        public static Withdrawal from(String json) {
            Withdrawal w = new Withdrawal();
            try {
                JSONObject o = new JSONObject(json);
                w.withdrawalId = o.optString("withdrawalId"); w.userId = o.optString("userId");
                w.amountPoints = o.optLong("amountPoints"); w.amountRupiah = o.optLong("amountRupiah");
                w.method = o.optString("method"); w.destination = o.optString("destination");
                w.status = o.optString("status", "PENDING");
                w.adminId = o.optString("adminId", null); w.note = o.optString("note");
                w.date = o.optString("date"); w.createdAt = o.optString("createdAt");
                w.processedAt = o.optString("processedAt", null);
            } catch (JSONException ignored) { }
            return w;
        }
    }

    /* ---------------- settings/{key} ---------------- */
    public static class Settings {
        public long pointsPerUnit = 10000, rupiahPerUnit = 1000;
        public long checkinPoints = 10, adPoints = 5, adMaxPerDay = 20;
        public long referralBonus = 5000, referralMinOrder = 50000;
        public long minWithdrawRupiah = 50000, maxWithdrawPerDay = 1;
        public boolean requireAdsForWithdraw = true, purchasePointsEnabled = true;
        public long shippingFlat = 15000, freeShippingMin = 0;
        public boolean admobEnabled = true;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("pointsPerUnit", pointsPerUnit); o.put("rupiahPerUnit", rupiahPerUnit);
            o.put("checkinPoints", checkinPoints); o.put("adPoints", adPoints);
            o.put("adMaxPerDay", adMaxPerDay); o.put("referralBonus", referralBonus);
            o.put("referralMinOrder", referralMinOrder); o.put("minWithdrawRupiah", minWithdrawRupiah);
            o.put("maxWithdrawPerDay", maxWithdrawPerDay);
            o.put("requireAdsForWithdraw", requireAdsForWithdraw);
            o.put("purchasePointsEnabled", purchasePointsEnabled);
            o.put("shippingFlat", shippingFlat); o.put("freeShippingMin", freeShippingMin);
            o.put("admobEnabled", admobEnabled);
            return o;
        }
        public static Settings from(String json) {
            Settings s = new Settings();
            if (json == null) return s;
            try {
                JSONObject o = new JSONObject(json);
                s.pointsPerUnit = o.optLong("pointsPerUnit", 10000);
                s.rupiahPerUnit = o.optLong("rupiahPerUnit", 1000);
                s.checkinPoints = o.optLong("checkinPoints", 10);
                s.adPoints = o.optLong("adPoints", 5);
                s.adMaxPerDay = o.optLong("adMaxPerDay", 20);
                s.referralBonus = o.optLong("referralBonus", 5000);
                s.referralMinOrder = o.optLong("referralMinOrder", 50000);
                s.minWithdrawRupiah = o.optLong("minWithdrawRupiah", 50000);
                s.maxWithdrawPerDay = o.optLong("maxWithdrawPerDay", 1);
                s.requireAdsForWithdraw = o.optBoolean("requireAdsForWithdraw", true);
                s.purchasePointsEnabled = o.optBoolean("purchasePointsEnabled", true);
                s.shippingFlat = o.optLong("shippingFlat", 15000);
                s.freeShippingMin = o.optLong("freeShippingMin", 0);
                s.admobEnabled = o.optBoolean("admobEnabled", true);
            } catch (JSONException ignored) { }
            return s;
        }
    }
}