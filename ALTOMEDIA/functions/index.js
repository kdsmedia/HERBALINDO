/**
 * HERBALINDO trusted backend.
 *
 * Every value that a member can redeem — points, balance, referral bonuses, stock and
 * order state — is mutated here only. The Android client never writes these fields;
 * Firestore security rules deny client writes to them outright (see firestore.rules).
 */

const functions = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { logger } = require("firebase-functions");

// All callables are pinned to the same region the Android client uses
// (Config.FUNCTIONS_REGION). A mismatch here makes every call fail with NOT_FOUND.
const REGION = "asia-southeast2";
functions.setGlobalOptions({ region: REGION });

admin.initializeApp();
const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;

const USERS = "users";
const PRODUCTS = "products";
const ORDERS = "orders";
const PAYMENTS = "payments";
const LEDGER = "points_ledger";
const REFERRALS = "referrals";
const AD_REWARDS = "ad_rewards";
const DAILY_TASKS = "daily_tasks";
const WITHDRAWALS = "withdrawals";
const SETTINGS = "settings";
const ADMIN_LOGS = "admin_logs";
const STOCK_MOVEMENTS = "stock_movements";

const DEFAULT_SETTINGS = {
  checkinPoints: 10,
  pointsPerAd: 5,
  maxAdsPerDay: 20,
  referralBonusPoints: 5000,
  minWithdrawalRupiah: 50000,
  pointsPerRupiah: 10000,
  maxWithdrawalsPerDay: 1,
  shippingCostFlat: 15000,
  freeShippingMin: 0,
  referralRequiresPurchase: true,
  referralMinOrderTotal: 0,
};

const MIN_WITHDRAWAL_RUPIAH = 50000;
const REQUIRED_ADS_FOR_WITHDRAWAL = 20;
const POINTS_PER_RUPIAH = 10000;

/** Jakarta (WIB) day key, so daily limits reset at local midnight rather than UTC. */
function dayKey(date = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Jakarta",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  })
    .format(date)
    .replace(/-/g, "");
}

function fail(code, message) {
  throw new functions.HttpsError(code, message);
}

function requireAuth(request) {
  if (!request.auth || !request.auth.uid) {
    fail("unauthenticated", "Silakan masuk terlebih dahulu.");
  }
  return request.auth.uid;
}

async function requireAdmin(uid) {
  const snap = await db.collection(USERS).doc(uid).get();
  if (!snap.exists || snap.data().role !== "ADMIN") {
    fail("permission-denied", "Akses khusus admin.");
  }
  return snap.data();
}

async function loadSettings() {
  const snap = await db.collection(SETTINGS).doc("app").get();
  return { ...DEFAULT_SETTINGS, ...(snap.exists ? snap.data() : {}) };
}

async function requireActiveUser(uid) {
  const snap = await db.collection(USERS).doc(uid).get();
  if (!snap.exists) fail("not-found", "Akun tidak ditemukan.");
  const user = snap.data();
  if (user.status !== "ACTIVE") {
    fail("permission-denied", "Akun Anda tidak aktif. Hubungi admin.");
  }
  return user;
}

async function writeLedger(tx, userId, delta, reason, refId, balanceAfter) {
  tx.set(db.collection(LEDGER).doc(), {
    userId,
    delta,
    reason,
    refId: refId || "",
    balanceAfter,
    createdAt: FieldValue.serverTimestamp(),
  });
}

async function writeAdminLog(adminId, action, target, amount, detail) {
  await db.collection(ADMIN_LOGS).add({
    adminId,
    action,
    target: target || "",
    amount: amount || 0,
    detail: detail || "",
    timestamp: FieldValue.serverTimestamp(),
  });
}

// ---------------------------------------------------------------------------
// Registration
// ---------------------------------------------------------------------------

/** Generates a unique 6-digit referral code, retrying on the rare collision. */
async function generateReferralId() {
  for (let attempt = 0; attempt < 12; attempt++) {
    const candidate = String(Math.floor(100000 + Math.random() * 900000));
    const clash = await db.collection(USERS).where("referralId", "==", candidate).limit(1).get();
    if (clash.empty) return candidate;
  }
  fail("resource-exhausted", "Gagal membuat Referral ID. Coba lagi.");
}

/**
 * Creates the member profile after Firebase Auth registration. The referral code is
 * minted here so it is guaranteed unique and cannot be chosen by the client.
 */
exports.registerProfile = functions.onCall(async (request) => {
  const uid = requireAuth(request);
  const data = request.data || {};

  const name = String(data.name || "").trim();
  const email = String(data.email || "").trim().toLowerCase();
  const phone = String(data.phone || "").trim();
  const referralInput = String(data.referralId || "").trim();

  if (!name) fail("invalid-argument", "Nama wajib diisi.");
  if (!phone && !email) fail("invalid-argument", "Nomor HP atau email wajib diisi.");
  if (phone && !/^[0-9+]{8,16}$/.test(phone)) fail("invalid-argument", "Nomor HP tidak valid.");

  const userRef = db.collection(USERS).doc(uid);
  const existing = await userRef.get();
  if (existing.exists) {
    return { referralId: existing.data().referralId, alreadyRegistered: true };
  }

  // Resolve the inviter before opening the transaction so a bad code fails early.
  let inviterId = null;
  if (referralInput) {
    if (!/^[0-9]{6}$/.test(referralInput)) {
      fail("invalid-argument", "Referral ID harus tepat 6 digit angka.");
    }
    const inviterSnap = await db.collection(USERS)
      .where("referralId", "==", referralInput)
      .limit(1)
      .get();
    if (inviterSnap.empty) fail("not-found", "Referral ID tidak ditemukan.");
    const inviter = inviterSnap.docs[0];
    if (inviter.id === uid) fail("invalid-argument", "Tidak dapat menggunakan Referral ID sendiri.");
    inviterId = inviter.id;
  }

  const referralId = await generateReferralId();

  await db.runTransaction(async (tx) => {
    tx.set(userRef, {
      name,
      email,
      phone,
      referralId,
      referredBy: inviterId,
      role: "MEMBER",
      status: "ACTIVE",
      verified: false,
      points: 0,
      balance: 0,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    // A pending referral is recorded now and only becomes VERIFIED after a paid order.
    if (inviterId) {
      tx.set(db.collection(REFERRALS).doc(), {
        inviterId,
        invitedUserId: uid,
        status: "PENDING",
        qualifyingOrderId: "",
        bonusPoints: 0,
        createdAt: FieldValue.serverTimestamp(),
        verifiedAt: null,
      });
    }
  });

  return { referralId, referredBy: inviterId };
});

/** Lets the client confirm a referral code exists before the member submits the form. */
exports.validateReferralId = functions.onCall(async (request) => {
  const code = String((request.data && request.data.referralId) || "").trim();
  if (!/^[0-9]{6}$/.test(code)) {
    return { valid: false, reason: "Referral ID harus tepat 6 digit angka." };
  }
  const snap = await db.collection(USERS).where("referralId", "==", code).limit(1).get();
  if (snap.empty) return { valid: false, reason: "Referral ID tidak ditemukan." };
  return { valid: true, name: snap.docs[0].data().name || "" };
});

// ---------------------------------------------------------------------------
// Daily check-in
// ---------------------------------------------------------------------------

exports.dailyCheckIn = functions.onCall(async (request) => {
  const uid = requireAuth(request);
  await requireActiveUser(uid);
  const settings = await loadSettings();
  const key = dayKey();
  const taskRef = db.collection(DAILY_TASKS).doc(`${uid}_${key}`);
  const userRef = db.collection(USERS).doc(uid);
  const points = Number(settings.checkinPoints) || 0;

  const result = await db.runTransaction(async (tx) => {
    const [taskSnap, userSnap] = await Promise.all([tx.get(taskRef), tx.get(userRef)]);
    const task = taskSnap.exists ? taskSnap.data() : {};
    if (task.checkedIn) fail("already-exists", "Anda sudah check-in hari ini.");

    const newPoints = Number(userSnap.data().points || 0) + points;
    tx.update(userRef, { points: newPoints, updatedAt: FieldValue.serverTimestamp() });
    tx.set(
      taskRef,
      {
        userId: uid,
        dayKey: key,
        checkedIn: true,
        adsWatched: Number(task.adsWatched || 0),
        checkinPoints: points,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    await writeLedger(tx, uid, points, "Check-in", key, newPoints);
    return { points: newPoints, awarded: points };
  });

  return result;
});

// ---------------------------------------------------------------------------
// Rewarded ads — the client only records a reward the ad SDK already confirmed
// ---------------------------------------------------------------------------

exports.rewardAd = functions.onCall(async (request) => {
  const uid = requireAuth(request);
  await requireActiveUser(uid);
  const settings = await loadSettings();
  const key = dayKey();
  const taskRef = db.collection(DAILY_TASKS).doc(`${uid}_${key}`);
  const userRef = db.collection(USERS).doc(uid);
  const maxAds = Number(settings.maxAdsPerDay) || 20;
  const reward = Number(settings.pointsPerAd) || 0;

  // One reward document per ad view keeps repeated calls from being silently deduped
  // while still allowing an audit trail of every rewarded view.
  const adUnconfirmed = request.data && request.data.clientConfirmed === true;
  if (!adUnconfirmed) {
    fail("failed-precondition", "Reward iklan tidak terkonfirmasi.");
  }

  return db.runTransaction(async (tx) => {
    const [taskSnap, userSnap] = await Promise.all([tx.get(taskRef), tx.get(userRef)]);
    const task = taskSnap.exists ? taskSnap.data() : {};
    const watched = Number(task.adsWatched || 0);
    if (watched >= maxAds) fail("resource-exhausted", "Batas iklan harian tercapai.");

    const newPoints = Number(userSnap.data().points || 0) + reward;
    const newWatched = watched + 1;
    tx.update(userRef, { points: newPoints, updatedAt: FieldValue.serverTimestamp() });
    tx.set(
      taskRef,
      {
        userId: uid,
        dayKey: key,
        checkedIn: Boolean(task.checkedIn),
        adsWatched: newWatched,
        checkinPoints: Number(task.checkinPoints || 0),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    tx.set(db.collection(AD_REWARDS).doc(), {
      userId: uid,
      dayKey: key,
      points: reward,
      adUnit: String((request.data && request.data.adUnit) || "rewarded"),
      createdAt: FieldValue.serverTimestamp(),
    });
    await writeLedger(tx, uid, reward, "Rewarded Ads", key, newPoints);
    return { points: newPoints, adsWatched: newWatched, maxAds, awarded: reward };
  });
});

// ---------------------------------------------------------------------------
// Orders
// ---------------------------------------------------------------------------

/**
 * Creates an order from the client cart. Prices and points are re-read from the
 * product documents so a tampered client cannot dictate what it pays.
 */
exports.createOrder = functions.onCall(async (request) => {
  const uid = requireAuth(request);
  await requireActiveUser(uid);
  const settings = await loadSettings();

  const data = request.data || {};
  const rawItems = Array.isArray(data.items) ? data.items : [];
  if (rawItems.length === 0) fail("invalid-argument", "Keranjang kosong.");

  const address = data.shippingAddress || {};
  if (!address.recipientName || !address.phone || !address.address) {
    fail("invalid-argument", "Alamat penerima belum lengkap.");
  }

  const orderRef = db.collection(ORDERS).doc();
  const seqRef = db.collection(SETTINGS).doc("orderSequence");
  const paymentRef = db.collection(PAYMENTS).doc();

  const total = await db.runTransaction(async (tx) => {
    const productRefs = rawItems.map((i) => db.collection(PRODUCTS).doc(String(i.productId)));
    const productSnaps = await tx.getAll(...productRefs);

    const items = [];
    let subtotal = 0;
    let pointsEarned = 0;

    productSnaps.forEach((snap, idx) => {
      if (!snap.exists) fail("not-found", `Produk tidak ditemukan: ${rawItems[idx].productId}`);
      const p = snap.data();
      if (p.status !== "ACTIVE") fail("failed-precondition", `${p.name} sedang tidak aktif.`);

      const qty = Math.max(1, Math.floor(Number(rawItems[idx].qty) || 1));
      if (Number(p.stock || 0) < qty) {
        fail("failed-precondition", `Stok ${p.name} tidak mencukupi.`);
      }

      const promoOk =
        p.promoActive === true &&
        Number(p.promoPrice || 0) > 0 &&
        Number(p.promoPrice) < Number(p.price);
      const unitPrice = promoOk ? Number(p.promoPrice) : Number(p.price);

      subtotal += unitPrice * qty;
      pointsEarned += Number(p.points || 0) * qty;
      items.push({
        productId: snap.id,
        name: p.name,
        price: unitPrice,
        qty,
        points: Number(p.points || 0),
      });

      tx.update(snap.ref, { stock: Number(p.stock || 0) - qty, updatedAt: FieldValue.serverTimestamp() });
    });

    let shipping = Number(settings.shippingCostFlat) || 0;
    const freeMin = Number(settings.freeShippingMin) || 0;
    if (freeMin > 0 && subtotal >= freeMin) shipping = 0;

    const grandTotal = subtotal + shipping;

    const seqSnap = await tx.get(seqRef);
    const lastSeq = seqSnap.exists ? Number(seqSnap.data().last || 0) : 0;
    const nextSeq = lastSeq + 1;
    const now = new Date();
    const stamp = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Jakarta",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    })
      .format(now)
      .replace(/-/g, "");
    const orderNumber = `ORD-${stamp}-${String(nextSeq).padStart(6, "0")}`;

    tx.set(seqRef, { last: nextSeq, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(orderRef, {
      userId: uid,
      orderNumber,
      items,
      subtotal,
      shippingCost: shipping,
      total: grandTotal,
      pointsEarned,
      paymentStatus: "UNPAID",
      paymentMethod: "QRIS",
      orderStatus: "WAITING_PAYMENT",
      shippingCourier: "",
      trackingNumber: "",
      shippingAddress: {
        recipientName: String(address.recipientName || ""),
        phone: String(address.phone || ""),
        address: String(address.address || ""),
        city: String(address.city || ""),
        postalCode: String(address.postalCode || ""),
      },
      note: String(data.note || ""),
      qrPayload: "",
      referralBonusApplied: false,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    tx.set(paymentRef, {
      orderId: orderRef.id,
      userId: uid,
      amount: grandTotal,
      method: "QRIS",
      payload: "",
      status: "UNPAID",
      createdAt: FieldValue.serverTimestamp(),
      paidAt: null,
    });

    for (const item of items) {
      tx.set(db.collection(STOCK_MOVEMENTS).doc(), {
        productId: item.productId,
        delta: -item.qty,
        reason: `Order ${orderNumber}`,
        adminId: "",
        stockAfter: 0,
        createdAt: FieldValue.serverTimestamp(),
      });
    }

    return { grandTotal, orderNumber, pointsEarned, orderId: orderRef.id };
  });

  return total;
});

/**
 * Member taps "Saya sudah bayar". Trusted payment confirmation is a manual admin
 * action (confirmPayment) because QRIS settlement is verified outside the app.
 */
exports.submitPaymentProof = functions.onCall(async (request) => {
  const uid = requireAuth(request);
  const orderId = String((request.data && request.data.orderId) || "");
  if (!orderId) fail("invalid-argument", "Order tidak valid.");

  const orderSnap = await db.collection(ORDERS).doc(orderId).get();
  if (!orderSnap.exists) fail("not-found", "Order tidak ditemukan.");
  if (orderSnap.data().userId !== uid) fail("permission-denied", "Order bukan milik Anda.");
  if (orderSnap.data().paymentStatus === "PAID") fail("already-exists", "Order sudah dibayar.");

  await orderSnap.ref.update({
    paymentStatus: "WAITING_CONFIRMATION",
    updatedAt: FieldValue.serverTimestamp(),
  });
  return { ok: true };
});

/**
 * Admin confirms a payment. This is the single point where purchase points and any
 * referral bonus are minted, so those fields stay server-authoritative.
 */
exports.confirmPayment = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);
  const settings = await loadSettings();

  const orderId = String((request.data && request.data.orderId) || "");
  if (!orderId) fail("invalid-argument", "Order tidak valid.");

  const orderRef = db.collection(ORDERS).doc(orderId);

  const outcome = await db.runTransaction(async (tx) => {
    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) fail("not-found", "Order tidak ditemukan.");
    const order = orderSnap.data();
    if (order.paymentStatus === "PAID") fail("already-exists", "Order sudah dibayar.");

    const buyerRef = db.collection(USERS).doc(order.userId);
    const buyerSnap = await tx.get(buyerRef);
    if (!buyerSnap.exists) fail("not-found", "Member tidak ditemukan.");

    const buyer = buyerSnap.data();
    const gained = Number(order.pointsEarned || 0);
    const newBuyerPoints = Number(buyer.points || 0) + gained;

    tx.update(buyerRef, {
      points: newBuyerPoints,
      verified: true,
      updatedAt: FieldValue.serverTimestamp(),
    });
    if (gained > 0) {
      await writeLedger(tx, order.userId, gained, "Pembelian", orderId, newBuyerPoints);
    }

    tx.update(orderRef, {
      paymentStatus: "PAID",
      orderStatus: "PAID",
      updatedAt: FieldValue.serverTimestamp(),
    });

    // --- single-level referral bonus, only for the direct inviter ---
    let referralAwarded = 0;
    let referralInviterId = "";
    if (buyer.referredBy) {
      const refQuery = db.collection(REFERRALS)
        .where("invitedUserId", "==", order.userId)
        .where("inviterId", "==", buyer.referredBy);
      const refSnap = await tx.get(refQuery);
      const refDoc = refSnap.docs.find((d) => d.data().status === "PENDING");

      const meetsOrderFloor =
        Number(order.total || 0) >= Number(settings.referralMinOrderTotal || 0);
      const purchaseRequired = settings.referralRequiresPurchase !== false;

      if (refDoc && meetsOrderFloor && purchaseRequired) {
        const inviterRef = db.collection(USERS).doc(buyer.referredBy);
        const inviterSnap = await tx.get(inviterRef);
        if (inviterSnap.exists) {
          const bonus = Number(settings.referralBonusPoints || 0);
          const inviterPoints = Number(inviterSnap.data().points || 0) + bonus;
          tx.update(inviterRef, {
            points: inviterPoints,
            updatedAt: FieldValue.serverTimestamp(),
          });
          await writeLedger(tx, buyer.referredBy, bonus, "Referral", orderId, inviterPoints);
          tx.update(refDoc.ref, {
            status: "VERIFIED",
            qualifyingOrderId: orderId,
            bonusPoints: bonus,
            verifiedAt: FieldValue.serverTimestamp(),
          });
          referralAwarded = bonus;
          referralInviterId = buyer.referredBy;
        }
      }
    }

    tx.update(orderRef, { referralBonusApplied: referralAwarded > 0 }, { merge: true });

    return { gained, newBuyerPoints, referralAwarded, referralInviterId, orderNumber: order.orderNumber };
  });

  await writeAdminLog(adminId, "CONFIRM_PAYMENT", orderId, outcome.gained, `Order ${outcome.orderNumber}`);
  return outcome;
});

/** Admin moves an order along the fulfilment pipeline and records courier + tracking. */
exports.updateOrderStatus = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const orderId = String(data.orderId || "");
  const status = String(data.status || "");
  const allowed = [
    "PENDING", "WAITING_PAYMENT", "PAID", "PROCESSING",
    "SHIPPED", "DELIVERED", "COMPLETED", "CANCELLED", "REFUNDED",
  ];
  if (!orderId || !allowed.includes(status)) fail("invalid-argument", "Status order tidak valid.");

  const orderRef = db.collection(ORDERS).doc(orderId);
  const update = { orderStatus: status, updatedAt: FieldValue.serverTimestamp() };
  if (data.courier !== undefined) update.shippingCourier = String(data.courier);
  if (data.trackingNumber !== undefined) update.trackingNumber = String(data.trackingNumber);
  if (data.note !== undefined) update.note = String(data.note);

  await orderRef.update(update);
  await writeAdminLog(adminId, "UPDATE_ORDER_STATUS", orderId, 0, `Status -> ${status}`);
  return { ok: true, status };
});

/**
 * Refund handling. When an order is refunded the purchase points are clawed back and
 * the referral bonus is reversed, which is the rule BAB 11.9 requires.
 */
exports.refundOrder = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const orderId = String(data.orderId || "");
  const reason = String(data.reason || "");
  if (!orderId) fail("invalid-argument", "Order tidak valid.");

  const orderRef = db.collection(ORDERS).doc(orderId);

  const outcome = await db.runTransaction(async (tx) => {
    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) fail("not-found", "Order tidak ditemukan.");
    const order = orderSnap.data();
    if (order.orderStatus === "REFUNDED") fail("already-exists", "Order sudah direfund.");

    let clawedBack = 0;

    if (order.paymentStatus === "PAID") {
      const buyerRef = db.collection(USERS).doc(order.userId);
      const buyerSnap = await tx.get(buyerRef);
      if (buyerSnap.exists) {
        const gained = Number(order.pointsEarned || 0);
        const current = Number(buyerSnap.data().points || 0);
        const after = Math.max(0, current - gained);
        clawedBack = current - after;
        tx.update(buyerRef, { points: after, updatedAt: FieldValue.serverTimestamp() });
        if (clawedBack > 0) {
          await writeLedger(tx, order.userId, -clawedBack, "Refund", orderId, after);
        }
      }

      // Reverse a verified referral bonus for this order.
      const refQuery = db.collection(REFERRALS)
        .where("qualifyingOrderId", "==", orderId)
        .where("status", "==", "VERIFIED");
      const refSnap = await tx.get(refQuery);
      for (const doc of refSnap.docs) {
        const ref = doc.data();
        const bonus = Number(ref.bonusPoints || 0);
        if (bonus > 0 && ref.inviterId) {
          const inviterRef = db.collection(USERS).doc(ref.inviterId);
          const inviterSnap = await tx.get(inviterRef);
          if (inviterSnap.exists) {
            const current = Number(inviterSnap.data().points || 0);
            const after = Math.max(0, current - bonus);
            tx.update(inviterRef, { points: after, updatedAt: FieldValue.serverTimestamp() });
            await writeLedger(tx, ref.inviterId, -bonus, "Pembatalan Referral", orderId, after);
          }
        }
        tx.update(doc.ref, { status: "CANCELLED" });
      }
    }

    // Return stock to the catalogue.
    for (const item of order.items || []) {
      const pRef = db.collection(PRODUCTS).doc(item.productId);
      const pSnap = await tx.get(pRef);
      if (pSnap.exists) {
        tx.update(pRef, {
          stock: Number(pSnap.data().stock || 0) + Number(item.qty || 0),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    }

    tx.update(orderRef, {
      orderStatus: "REFUNDED",
      paymentStatus: "REFUNDED",
      note: reason,
      updatedAt: FieldValue.serverTimestamp(),
    });

    return { clawedBack };
  });

  await writeAdminLog(adminId, "REFUND_ORDER", orderId, outcome.clawedBack, reason);
  return outcome;
});

// ---------------------------------------------------------------------------
// Withdrawal
// ---------------------------------------------------------------------------

/**
 * Enforces every withdrawal rule in the spec on the server: minimum balance,
 * 20 rewarded ads that day, active account, and one request per day.
 */
exports.requestWithdrawal = functions.onCall(async (request) => {
  const uid = requireAuth(request);
  await requireActiveUser(uid);
  const settings = await loadSettings();

  const data = request.data || {};
  const amount = Math.floor(Number(data.amount) || 0);
  const method = String(data.method || "");
  const destination = String(data.destination || "");
  const holderName = String(data.holderName || "");

  if (!method || !destination || !holderName) {
    fail("invalid-argument", "Data rekening/e-wallet belum lengkap.");
  }

  const key = dayKey();
  const withdrawalRef = db.collection(WITHDRAWALS).doc();
  const taskRef = db.collection(DAILY_TASKS).doc(`${uid}_${key}`);

  const result = await db.runTransaction(async (tx) => {
    const userRef = db.collection(USERS).doc(uid);
    const [userSnap, taskSnap, todaySnap] = await Promise.all([
      tx.get(userRef),
      tx.get(taskRef),
      tx.get(
        db.collection(WITHDRAWALS)
          .where("userId", "==", uid)
          .where("dayKey", "==", key)
      ),
    ]);

    const user = userSnap.data();
    const pointsBalance = Number(user.points || 0);
    const minPoints = (Number(settings.minWithdrawalRupiah) || MIN_WITHDRAWAL_RUPIAH) *
      (Number(settings.pointsPerRupiah) || POINTS_PER_RUPIAH) / 1000;

    if (pointsBalance < minPoints) {
      fail("failed-precondition", `Saldo minimal belum terpenuhi (butuh ${minPoints} poin).`);
    }

    const adsWatched = taskSnap.exists ? Number(taskSnap.data().adsWatched || 0) : 0;
    if (adsWatched < REQUIRED_ADS_FOR_WITHDRAWAL) {
      fail(
        "failed-precondition",
        `Selesaikan ${REQUIRED_ADS_FOR_WITHDRAWAL} rewarded ads hari ini sebelum menarik saldo.`
      );
    }

    const maxPerDay = Number(settings.maxWithdrawalsPerDay) || 1;
    if (todaySnap.size >= maxPerDay) {
      fail("resource-exhausted", "Maksimal 1 withdrawal per hari.");
    }

    const ratio = (Number(settings.pointsPerRupiah) || POINTS_PER_RUPIAH) / 1000;
    const maxRupiah = Math.floor(pointsBalance / ratio);
    if (amount < (Number(settings.minWithdrawalRupiah) || MIN_WITHDRAWAL_RUPIAH)) {
      fail("invalid-argument", "Minimum withdrawal Rp50.000.");
    }
    if (amount > maxRupiah) {
      fail("invalid-argument", "Saldo poin tidak mencukupi.");
    }

    const pointsToDebit = Math.round(amount * ratio);
    const newPoints = pointsBalance - pointsToDebit;

    tx.set(withdrawalRef, {
      userId: uid,
      amount,
      dayKey: key,
      method,
      destination,
      holderName,
      status: "PENDING",
      adminId: "",
      note: "",
      createdAt: FieldValue.serverTimestamp(),
      processedAt: null,
    });

    // Points leave the balance immediately so the same balance cannot be withdrawn twice.
    tx.update(userRef, { points: newPoints, updatedAt: FieldValue.serverTimestamp() });
    await writeLedger(tx, uid, -pointsToDebit, "Withdrawal", withdrawalRef.id, newPoints);

    return { withdrawalId: withdrawalRef.id, newPoints, amount };
  });

  return result;
});

/** Admin approves, rejects or pays out a withdrawal request. */
exports.processWithdrawal = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const withdrawalId = String(data.withdrawalId || "");
  const status = String(data.status || "");
  const note = String(data.note || "");
  const allowed = ["REVIEW", "APPROVED", "REJECTED", "PAID"];
  if (!withdrawalId || !allowed.includes(status)) {
    fail("invalid-argument", "Status withdrawal tidak valid.");
  }

  const wRef = db.collection(WITHDRAWALS).doc(withdrawalId);

  const outcome = await db.runTransaction(async (tx) => {
    const wSnap = await tx.get(wRef);
    if (!wSnap.exists) fail("not-found", "Withdrawal tidak ditemukan.");
    const w = wSnap.data();
    if (["PAID", "REJECTED"].includes(w.status)) {
      fail("failed-precondition", "Withdrawal ini sudah diproses final.");
    }

    // Rejecting returns the reserved points to the member.
    if (status === "REJECTED" && w.status !== "REJECTED") {
      const ratio = POINTS_PER_RUPIAH / 1000;
      const refundPoints = Math.round(Number(w.amount || 0) * ratio);
      const userRef = db.collection(USERS).doc(w.userId);
      const userSnap = await tx.get(userRef);
      if (userSnap.exists) {
        const after = Number(userSnap.data().points || 0) + refundPoints;
        tx.update(userRef, { points: after, updatedAt: FieldValue.serverTimestamp() });
        await writeLedger(tx, w.userId, refundPoints, "Pengembalian Withdrawal", withdrawalId, after);
      }
    }

    tx.update(wRef, {
      status,
      adminId,
      note,
      processedAt: FieldValue.serverTimestamp(),
    });

    return { status };
  });

  await writeAdminLog(adminId, "PROCESS_WITHDRAWAL", withdrawalId, 0, `Status -> ${status}. ${note}`);
  return outcome;
});

// ---------------------------------------------------------------------------
// Admin: points, balance and stock
// ---------------------------------------------------------------------------

/** Manual points/balance adjustment. Always requires an amount and a reason. */
exports.adjustUserPoints = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const userId = String(data.userId || "");
  const delta = Math.trunc(Number(data.delta) || 0);
  const reason = String(data.reason || "");

  if (!userId) fail("invalid-argument", "Member tidak valid.");
  if (delta === 0) fail("invalid-argument", "Jumlah penyesuaian tidak boleh nol.");
  if (!reason) fail("invalid-argument", "Alasan wajib diisi.");

  const userRef = db.collection(USERS).doc(userId);

  const outcome = await db.runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    if (!snap.exists) fail("not-found", "Member tidak ditemukan.");
    const current = Number(snap.data().points || 0);
    const after = Math.max(0, current + delta);
    tx.update(userRef, { points: after, updatedAt: FieldValue.serverTimestamp() });
    await writeLedger(tx, userId, delta, `Penyesuaian Admin: ${reason}`, adminId, after);
    return { newPoints: after };
  });

  await writeAdminLog(adminId, "ADJUST_POINTS", userId, delta, reason);
  return outcome;
});

/** Stock correction with a movement record (BAB 11.4). */
exports.adjustStock = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const productId = String(data.productId || "");
  const delta = Math.trunc(Number(data.delta) || 0);
  const reason = String(data.reason || "");

  if (!productId) fail("invalid-argument", "Produk tidak valid.");
  if (delta === 0) fail("invalid-argument", "Jumlah stok tidak boleh nol.");

  const pRef = db.collection(PRODUCTS).doc(productId);

  const outcome = await db.runTransaction(async (tx) => {
    const snap = await tx.get(pRef);
    if (!snap.exists) fail("not-found", "Produk tidak ditemukan.");
    const after = Math.max(0, Number(snap.data().stock || 0) + delta);
    tx.update(pRef, { stock: after, updatedAt: FieldValue.serverTimestamp() });
    tx.set(db.collection(STOCK_MOVEMENTS).doc(), {
      productId,
      delta,
      reason: reason || "Penyesuaian admin",
      adminId,
      stockAfter: after,
      createdAt: FieldValue.serverTimestamp(),
    });
    return { stockAfter: after };
  });

  await writeAdminLog(adminId, "ADJUST_STOCK", productId, delta, reason);
  return outcome;
});

/** Creates or updates a product document (admin only). */
exports.saveProduct = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const productId = data.productId ? String(data.productId) : null;
  const name = String(data.name || "").trim();
  if (!name) fail("invalid-argument", "Nama produk wajib diisi.");

  const payload = {
    name,
    sku: String(data.sku || ""),
    categoryId: String(data.categoryId || ""),
    description: String(data.description || ""),
    composition: String(data.composition || ""),
    usage: String(data.usage || ""),
    warning: String(data.warning || ""),
    price: Math.max(0, Math.floor(Number(data.price) || 0)),
    promoPrice: Math.max(0, Math.floor(Number(data.promoPrice) || 0)),
    stock: Math.max(0, Math.floor(Number(data.stock) || 0)),
    minStock: Math.max(0, Math.floor(Number(data.minStock) || 0)),
    weight: Math.max(0, Math.floor(Number(data.weight) || 0)),
    imageUrl: String(data.imageUrl || ""),
    points: Math.max(0, Math.floor(Number(data.points) || 0)),
    status: data.status === "INACTIVE" ? "INACTIVE" : "ACTIVE",
    promoActive: data.promoActive === true,
    updatedAt: FieldValue.serverTimestamp(),
  };

  const ref = productId ? db.collection(PRODUCTS).doc(productId) : db.collection(PRODUCTS).doc();
  if (productId) {
    await ref.set(payload, { merge: true });
  } else {
    await ref.set({ ...payload, createdAt: FieldValue.serverTimestamp() });
  }

  await writeAdminLog(adminId, productId ? "UPDATE_PRODUCT" : "CREATE_PRODUCT", ref.id, 0, name);
  return { productId: ref.id };
});

/** Soft delete keeps historic orders intact (BAB 11.3). */
exports.setProductStatus = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const productId = String(data.productId || "");
  const status = data.status === "INACTIVE" ? "INACTIVE" : "ACTIVE";
  if (!productId) fail("invalid-argument", "Produk tidak valid.");

  await db.collection(PRODUCTS).doc(productId).update({
    status,
    updatedAt: FieldValue.serverTimestamp(),
  });
  await writeAdminLog(adminId, "SET_PRODUCT_STATUS", productId, 0, status);
  return { ok: true };
});

exports.saveSettings = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const numericKeys = [
    "checkinPoints", "pointsPerAd", "maxAdsPerDay", "referralBonusPoints",
    "minWithdrawalRupiah", "pointsPerRupiah", "maxWithdrawalsPerDay",
    "shippingCostFlat", "freeShippingMin", "referralMinOrderTotal",
  ];
  const payload = { updatedAt: FieldValue.serverTimestamp() };
  for (const key of numericKeys) {
    if (data[key] !== undefined) payload[key] = Math.max(0, Math.floor(Number(data[key]) || 0));
  }
  if (data.referralRequiresPurchase !== undefined) {
    payload.referralRequiresPurchase = data.referralRequiresPurchase === true;
  }
  if (data.maintenanceMode !== undefined) {
    payload.maintenanceMode = data.maintenanceMode === true;
  }
  if (data.supportEmail !== undefined) payload.supportEmail = String(data.supportEmail);
  if (data.adminWhatsapp !== undefined) payload.adminWhatsapp = String(data.adminWhatsapp);

  await db.collection(SETTINGS).doc("app").set(payload, { merge: true });
  await writeAdminLog(adminId, "SAVE_SETTINGS", "settings/app", 0, JSON.stringify(payload));
  return { ok: true };
});

exports.setUserStatus = functions.onCall(async (request) => {
  const adminId = requireAuth(request);
  await requireAdmin(adminId);

  const data = request.data || {};
  const userId = String(data.userId || "");
  const status = String(data.status || "");
  if (!userId || !["ACTIVE", "INACTIVE", "SUSPENDED"].includes(status)) {
    fail("invalid-argument", "Status member tidak valid.");
  }
  if (userId === adminId) fail("failed-precondition", "Tidak dapat menonaktifkan akun sendiri.");

  await db.collection(USERS).doc(userId).update({
    status,
    updatedAt: FieldValue.serverTimestamp(),
  });
  await writeAdminLog(adminId, "SET_USER_STATUS", userId, 0, status);
  return { ok: true };
});

/**
 * One-off bootstrap: promotes the calling account to ADMIN when no admin exists yet.
 * Prevents an operator from being locked out on a fresh project.
 */
exports.claimFirstAdmin = functions.onCall(async (request) => {
  const uid = requireAuth(request);
  const admins = await db.collection(USERS).where("role", "==", "ADMIN").limit(1).get();
  if (!admins.empty) fail("already-exists", "Admin sudah tersedia.");
  await db.collection(USERS).doc(uid).update({
    role: "ADMIN",
    updatedAt: FieldValue.serverTimestamp(),
  });
  await writeAdminLog(uid, "CLAIM_ADMIN", uid, 0, "First admin claimed");
  return { ok: true };
});