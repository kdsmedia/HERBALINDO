/* ============================================================
   HERBALINDO — Store (Firestore-shaped data layer)
   Struktur koleksi meniru Bab 12: users, products, categories,
   orders, payments, points_ledger, referrals, ad_rewards,
   daily_tasks, withdrawals, settings, admin_logs, stock_movements
   ============================================================ */
(() => {
  const DB_KEY = 'herbalindo_db_v1';
  const QRIS_BASE = "00020101021126610014COM.GO-JEK.WWW01189360091439663050810210G9663050810303UMI51440014ID.CO.QRIS.WWW0215ID10254671365660303UMI5204549953033605802ID5917ALTOMEDIA, Grosir6008KARAWANG61054136162070703A016304D21A";

  const uid = (p) => p + '_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  const nowISO = () => new Date().toISOString();
  const dayKey = (d = new Date()) => {
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  };
  const rupiah = (n) => 'Rp' + Math.round(Number(n) || 0).toLocaleString('id-ID');
  const num = (n) => Number(n || 0).toLocaleString('id-ID');

  async function sha(text) {
    const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
    return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, '0')).join('');
  }

  const DEFAULT_SETTINGS = {
    appName: 'HERBALINDO',
    pointsPerRupiah: 10000,       // 10.000 poin = Rp1.000
    rupiahPerUnit: 1000,
    checkinPoints: 10,
    adPoints: 5,
    adMaxPerDay: 20,
    purchasePointsEnabled: true,
    referralBonus: 5000,
    referralMinOrder: 50000,
    minWithdrawRupiah: 50000,
    maxWithdrawPerDay: 1,
    requireAdsForWithdraw: true,
    shippingFlat: 15000,
    freeShippingMin: 0,
    admobEnabled: true,
    admobUnitId: 'ca-app-pub-3940256099942544/6300978111',
    admobTestMode: true,
    promoCodes: []
  };

  const DEFAULT_PRODUCTS = [
    {
      productId: 'PRD-001', name: 'Herbal Diet A', sku: 'HBA-001', category: 'Herbal Diet',
      description: 'Ramuan herbal diet alami berbahan tumbuhan pilihan, diseduh dan diminum sesuai aturan pakai.',
      composition: 'Ekstrak daun kelor, jahe merah, temulawak, daun sirsak, madu murni.',
      usage: 'Seduh 1 sachet dengan 150 ml air hangat, diminum 1 kali sehari setelah makan.',
      warning: 'Tidak untuk ibu hamil, menyusui, dan anak di bawah 12 tahun. Hentikan pemakaian bila terjadi reaksi alergi. Bukan obat dan tidak untuk menyembuhkan penyakit.',
      price: 100000, promoPrice: 85000, stock: 50, minStock: 5, weight: 250,
      imageUrl: '', points: 500, status: 'ACTIVE', createdAt: nowISO(), updatedAt: nowISO()
    },
    {
      productId: 'PRD-002', name: 'Herbal Diet B', sku: 'HBA-002', category: 'Herbal Diet',
      description: 'Ramuan herbal untuk membantu menjaga pola makan sehat sehari-hari.',
      composition: 'Ekstrak kunyit, kayu manis, daun pandan, temulawak, gula aren.',
      usage: 'Seduh 1 sachet dengan air hangat, diminum 1-2 kali sehari.',
      warning: 'Tidak untuk ibu hamil, menyusui, dan anak di bawah 12 tahun. Bukan obat dan tidak untuk menyembuhkan penyakit.',
      price: 120000, promoPrice: 0, stock: 40, minStock: 5, weight: 250,
      imageUrl: '', points: 600, status: 'ACTIVE', createdAt: nowISO(), updatedAt: nowISO()
    },
    {
      productId: 'PRD-003', name: 'Herbal Diet C', sku: 'HBA-003', category: 'Herbal Diet',
      description: 'Ramuan herbal malam untuk mendukung istirahat dan pencernaan.',
      composition: 'Ekstrak daun senna, akar alang-alang, kapulaga, cengkeh.',
      usage: 'Seduh 1 sachet dengan 150 ml air panas, diminum sebelum tidur.',
      warning: 'Tidak untuk ibu hamil, menyusui, anak di bawah 12 tahun, dan penderita gangguan ginjal. Bukan obat.',
      price: 95000, promoPrice: 79000, stock: 35, minStock: 5, weight: 200,
      imageUrl: '', points: 450, status: 'ACTIVE', createdAt: nowISO(), updatedAt: nowISO()
    }
  ];

  const DEFAULT_CATEGORIES = [
    { categoryId: 'CAT-001', name: 'Herbal Diet', status: 'ACTIVE' },
    { categoryId: 'CAT-002', name: 'Herbal Keluarga', status: 'ACTIVE' }
  ];

  let db = null;

  function save() {
    try { localStorage.setItem(DB_KEY, JSON.stringify(db)); }
    catch (e) { console.warn('Simpan gagal', e); }
  }

  function load() {
    try {
      const raw = localStorage.getItem(DB_KEY);
      db = raw ? JSON.parse(raw) : null;
    } catch (e) { db = null; }
    if (!db || !db.users) db = seed();
    return db;
  }

  function seed() {
    const admin = {
      userId: 'USR-ADMIN', name: 'Administrator', email: 'admin@herbalindo.id', phone: '081200000000',
      passwordHash: null, referralId: '000001', referredBy: null, role: 'ADMIN', status: 'ACTIVE',
      verified: true, points: 0, createdAt: nowISO(), updatedAt: nowISO(), fraudFlag: false
    };
    db = {
      users: [admin],
      products: DEFAULT_PRODUCTS,
      categories: DEFAULT_CATEGORIES,
      orders: [], order_items: [], payments: [],
      points_ledger: [], referrals: [], ad_rewards: [], daily_tasks: [],
      withdrawals: [], settings: { ...DEFAULT_SETTINGS }, admin_logs: [], stock_movements: [],
      session: { userId: null },
      counters: { order: 123 }
    };
    save();
    return db;
  }

  /* ------------ session / auth ------------ */
  function genReferralId() {
    for (let i = 0; i < 200; i++) {
      const v = String(Math.floor(100000 + Math.random() * 900000));
      if (!db.users.some(u => u.referralId === v)) return v;
    }
    throw new Error('Gagal membuat Referral ID');
  }

  function logout() { db.session.userId = null; save(); }
  function currentUser() { return db.users.find(u => u.userId === db.session.userId) || null; }

  /* ------------ points & balance ------------ */
  const S = () => db.settings;
  function pointsToRupiah(points) { return (Number(points) || 0) * (S().rupiahPerUnit / S().pointsPerRupiah); }
  function rupiahToPoints(rp) { return Math.round((Number(rp) || 0) * (S().pointsPerRupiah / S().rupiahPerUnit)); }

  function addPoints(userId, amount, type, note, refId) {
    amount = Math.round(Number(amount) || 0);
    if (!amount) return;
    const u = db.users.find(x => x.userId === userId);
    if (!u) return;
    u.points = Math.max(0, Math.round((u.points || 0) + amount));
    u.updatedAt = nowISO();
    db.points_ledger.push({
      ledgerId: uid('PLG'), userId, amount, type, note: note || '', refId: refId || null, createdAt: nowISO()
    });
    save();
    return u.points;
  }

  function ledger(userId, limit) {
    const rows = db.points_ledger.filter(l => l.userId === userId).slice().reverse();
    return limit ? rows.slice(0, limit) : rows;
  }

  /* ------------ products ------------ */
  function activeProducts() { return db.products.filter(p => p.status === 'ACTIVE'); }
  function product(id) { return db.products.find(p => p.productId === id) || null; }
  function priceOf(p) { return Number(p.promoPrice) > 0 && Number(p.promoPrice) < Number(p.price) ? Number(p.promoPrice) : Number(p.price); }
  function saveProduct(data) {
    if (data.productId) {
      const p = product(data.productId);
      if (!p) throw new Error('Produk tidak ditemukan');
      Object.assign(p, data, { updatedAt: nowISO() });
      save();
      return p;
    }
    const p = { ...data, productId: 'PRD-' + Date.now().toString(36).toUpperCase(), createdAt: nowISO(), updatedAt: nowISO() };
    db.products.push(p);
    save();
    return p;
  }
  function adjustStock(productId, delta, reason, actorId) {
    const p = product(productId);
    if (!p) throw new Error('Produk tidak ditemukan');
    p.stock = Math.max(0, Number(p.stock) + Number(delta));
    p.updatedAt = nowISO();
    db.stock_movements.push({ id: uid('STK'), productId, delta: Number(delta), reason, actorId: actorId || null, stockAfter: p.stock, createdAt: nowISO() });
    save();
    return p;
  }

  /* ------------ cart ------------ */
  let cart = [];
  function cartAdd(pid, qty = 1) {
    const p = product(pid);
    if (!p) throw new Error('Produk tidak ditemukan');
    const row = cart.find(c => c.productId === pid);
    const next = (row ? row.qty : 0) + qty;
    if (next > p.stock) throw new Error('Stok tidak mencukupi (tersisa ' + p.stock + ')');
    if (row) row.qty = next; else cart.push({ productId: pid, qty });
    return cart;
  }
  function cartSet(pid, qty) {
    const p = product(pid);
    const row = cart.find(c => c.productId === pid);
    if (!row) return cart;
    if (qty <= 0) { cart = cart.filter(c => c.productId !== pid); return cart; }
    if (qty > p.stock) throw new Error('Stok tidak mencukupi (tersisa ' + p.stock + ')');
    row.qty = qty;
    return cart;
  }
  function cartClear() { cart = []; }
  function cartItems() {
    return cart.map(c => {
      const p = product(c.productId);
      return p ? { ...c, product: p, price: priceOf(p), subtotal: priceOf(p) * c.qty, points: (Number(p.points) || 0) * c.qty } : null;
    }).filter(Boolean);
  }
  function cartTotals() {
    const items = cartItems();
    const subtotal = items.reduce((s, i) => s + i.subtotal, 0);
    const shipping = items.length === 0 ? 0 : (S().freeShippingMin > 0 && subtotal >= S().freeShippingMin ? 0 : Number(S().shippingFlat) || 0);
    return { items, subtotal, shipping, total: subtotal + shipping, points: items.reduce((s, i) => s + i.points, 0) };
  }

  /* ------------ order ------------ */
  const ORDER_FLOW = ['PENDING', 'WAITING_PAYMENT', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'COMPLETED'];
  const ORDER_LABEL = {
    PENDING: 'PENDING', WAITING_PAYMENT: 'MENUNGGU PEMBAYARAN', PAID: 'DIBAYAR', PROCESSING: 'DIPROSES',
    SHIPPED: 'DIKIRIM', DELIVERED: 'DELIVERED', COMPLETED: 'SELESAI', CANCELLED: 'DIBATALKAN', REFUNDED: 'REFUND'
  };

  function createOrder({ user, items, shipping, shippingAddress }) {
    if (!items.length) throw new Error('Keranjang kosong');
    db.counters.order = (db.counters.order || 0) + 1;
    const seq = String(db.counters.order).padStart(6, '0');
    const orderNumber = 'ORD-' + dayKey().replace(/-/g, '') + '-' + seq;
    const subtotal = items.reduce((s, i) => s + i.subtotal, 0);
    const orderId = uid('ORD');
    const order = {
      orderId, userId: user.userId, orderNumber,
      items: items.map(i => ({ productId: i.productId, name: i.product.name, price: i.price, qty: i.qty, weight: i.product.weight, points: i.points })),
      subtotal, shippingCost: shipping, total: subtotal + shipping,
      paymentMethod: 'QRIS', paymentStatus: 'UNPAID', orderStatus: 'PENDING',
      shippingCourier: '', trackingNumber: '', shippingAddress,
      shippingAddressText: shippingAddress.address + ', ' + shippingAddress.city + ' ' + shippingAddress.postal,
      note: '', createdAt: nowISO(), updatedAt: nowISO()
    };
    db.orders.push(order);
    order.items.forEach(i => db.order_items.push({ id: uid('OIT'), orderId, ...i }));
    db.payments.push({ paymentId: uid('PAY'), orderId, method: 'QRIS', amount: order.total, status: 'UNPAID', createdAt: nowISO(), paidAt: null });
    items.forEach(i => adjustStock(i.productId, -i.qty, 'ORDER ' + orderNumber, user.userId));
    markOrderStatus(order, 'WAITING_PAYMENT', user.userId, 'Order dibuat');
    cartClear();
    save();
    return order;
  }

  function markOrderStatus(order, status, actorId, note) {
    const prev = order.orderStatus;
    order.orderStatus = status;
    if (status === 'PAID' || status === 'PROCESSING' || status === 'SHIPPED' || status === 'DELIVERED' || status === 'COMPLETED') order.paymentStatus = 'PAID';
    if (status === 'REFUNDED') order.paymentStatus = 'REFUNDED';
    if (status === 'CANCELLED') order.paymentStatus = 'CANCELLED';
    order.updatedAt = nowISO();
    const pay = db.payments.find(p => p.orderId === order.orderId);
    if (pay) { pay.status = order.paymentStatus; if (order.paymentStatus === 'PAID' && !pay.paidAt) pay.paidAt = nowISO(); }
    if (prev !== status) db.admin_logs.push({ logId: uid('LOG'), adminId: actorId || null, action: 'ORDER_STATUS', target: order.orderNumber, data: prev + ' → ' + status + (note ? ' | ' + note : ''), createdAt: nowISO() });

    if (status === 'PAID') {
      grantPurchasePoints(order, actorId || order.userId);
      qualifyReferral(order);
    }
    if (status === 'REFUNDED' || status === 'CANCELLED') revokeForOrder(order, actorId);
    save();
    return order;
  }

  function grantPurchasePoints(order) {
    if (!S().purchasePointsEnabled) return;
    if (db.points_ledger.some(l => l.refId === order.orderId && l.type === 'PURCHASE')) return;
    const pts = order.items.reduce((s, i) => s + (Number(i.points) || 0), 0);
    if (pts > 0) addPoints(order.userId, pts, 'PURCHASE', 'Pembelian ' + order.orderNumber, order.orderId);
  }

  function qualifyReferral(order) {
    const ref = db.referrals.find(r => r.invitedUserId === order.userId && r.status === 'PENDING');
    if (!ref) return;
    if (Number(order.total) < Number(S().referralMinOrder)) return;
    const buyer = db.users.find(u => u.userId === order.userId);
    if (buyer) buyer.verified = true;
    ref.status = 'VERIFIED';
    ref.qualifyingOrderId = order.orderId;
    ref.bonusPoints = Number(S().referralBonus);
    ref.verifiedAt = nowISO();
    addPoints(ref.inviterId, S().referralBonus, 'REFERRAL', 'Bonus referral ' + (buyer ? buyer.name : ''), ref.referralId);
    addPoints(order.userId, 0, 'REFERRAL_NOTE', 'Pembelian memenuhi syarat referral', ref.referralId);
    db.admin_logs.push({ logId: uid('LOG'), adminId: null, action: 'REFERRAL_VERIFIED', target: ref.referralId, data: 'Bonus ' + S().referralBonus + ' poin', createdAt: nowISO() });
  }

  function revokeForOrder(order, actorId) {
    const pts = db.points_ledger.filter(l => l.refId === order.orderId && l.type === 'PURCHASE');
    const total = pts.reduce((s, l) => s + l.amount, 0);
    if (total > 0) addPoints(order.userId, -total, 'PURCHASE_REVOKE', 'Pembatalan poin ' + order.orderNumber, order.orderId);
    const ref = db.referrals.find(r => r.qualifyingOrderId === order.orderId && r.status === 'VERIFIED');
    if (ref) {
      ref.status = 'CANCELLED';
      if (ref.bonusPoints > 0) addPoints(ref.inviterId, -ref.bonusPoints, 'REFERRAL_REVOKE', 'Bonus dibatalkan (refund ' + order.orderNumber + ')', ref.referralId);
      const buyer = db.users.find(u => u.userId === ref.invitedUserId);
      if (buyer) buyer.verified = false;
      db.admin_logs.push({ logId: uid('LOG'), adminId: actorId || null, action: 'REFERRAL_REVOKED', target: ref.referralId, data: order.orderNumber, createdAt: nowISO() });
    }
    order.items.forEach(i => adjustStock(i.productId, i.qty, 'REFUND ' + order.orderNumber, actorId));
  }

  function order(id) { return db.orders.find(o => o.orderId === id || o.orderNumber === id) || null; }
  function userOrders(userId) { return db.orders.filter(o => o.userId === userId).slice().reverse(); }

  /* ------------ daily tasks: check-in & rewarded ads ------------ */
  function todayTask(userId) {
    const k = dayKey();
    let t = db.daily_tasks.find(x => x.userId === userId && x.date === k);
    if (!t) {
      t = { taskId: uid('DTK'), userId, date: k, checkin: false, adsWatched: 0, createdAt: nowISO(), updatedAt: nowISO() };
      db.daily_tasks.push(t);
      save();
    }
    return t;
  }
  function checkin(userId) {
    const t = todayTask(userId);
    if (t.checkin) throw new Error('Sudah check-in hari ini');
    t.checkin = true; t.updatedAt = nowISO();
    addPoints(userId, S().checkinPoints, 'CHECKIN', 'Check-in ' + t.date, t.taskId);
    save();
    return t;
  }
  function watchAd(userId) {
    const t = todayTask(userId);
    if (t.adsWatched >= S().adMaxPerDay) throw new Error('Batas iklan harian tercapai (' + S().adMaxPerDay + ')');
    t.adsWatched += 1; t.updatedAt = nowISO();
    db.ad_rewards.push({ rewardId: uid('ADR'), userId, date: t.date, points: S().adPoints, provider: 'ADMOB_REWARDED', status: 'CONFIRMED', createdAt: nowISO() });
    const gained = addPoints(userId, S().adPoints, 'AD', 'Rewarded Ads ' + t.adsWatched + '/' + S().adMaxPerDay, t.taskId);
    save();
    return { gained, adsWatched: t.adsWatched, max: S().adMaxPerDay };
  }
  function adsToday(userId) { return todayTask(userId).adsWatched; }

  /* ------------ withdrawal ------------ */
  function withdrawalEligibility(user) {
    const rp = pointsToRupiah(user.points);
    const ads = adsToday(user.userId);
    const need = Number(S().adMaxPerDay);
    const today = dayKey();
    const usedToday = db.withdrawals.filter(w => w.userId === user.userId && w.date === today && w.status !== 'REJECTED').length;
    const checks = [
      { key: 'saldo', ok: rp >= S().minWithdrawRupiah, text: 'Saldo minimal ' + rupiah(S().minWithdrawRupiah) + ' (sekarang ' + rupiah(rp) + ')' },
      { key: 'ads', ok: !S().requireAdsForWithdraw || ads >= need, text: 'Rewarded Ads hari ini ' + ads + '/' + need },
      { key: 'akun', ok: user.status === 'ACTIVE', text: 'Akun aktif' },
      { key: 'fraud', ok: !user.fraudFlag, text: 'Tidak terkena pembatasan fraud' },
      { key: 'limit', ok: usedToday < S().maxWithdrawPerDay, text: 'Maksimal ' + S().maxWithdrawPerDay + ' withdrawal per hari' }
    ];
    return { checks, ok: checks.every(c => c.ok), rupiah: rp, ads, need };
  }

  function requestWithdrawal(user, { amountPoints, method, destination }) {
    const e = withdrawalEligibility(user);
    if (!e.ok) throw new Error('Syarat withdrawal belum terpenuhi');
    amountPoints = Math.round(Number(amountPoints) || 0);
    if (amountPoints <= 0) throw new Error('Jumlah tidak valid');
    if (amountPoints > user.points) throw new Error('Poin tidak cukup');
    const rp = pointsToRupiah(amountPoints);
    if (rp < S().minWithdrawRupiah) throw new Error('Minimum withdrawal ' + rupiah(S().minWithdrawRupiah));
    const w = {
      withdrawalId: 'WD-' + Date.now().toString(36).toUpperCase(), userId: user.userId,
      amountPoints, amountRupiah: rp, method, destination, status: 'PENDING',
      adminId: null, note: '', date: dayKey(), createdAt: nowISO(), processedAt: null
    };
    db.withdrawals.push(w);
    addPoints(user.userId, -amountPoints, 'WITHDRAW_HOLD', 'Pengajuan withdrawal ' + w.withdrawalId, w.withdrawalId);
    save();
    return w;
  }

  function processWithdrawal(id, status, adminId, note) {
    const w = db.withdrawals.find(x => x.withdrawalId === id);
    if (!w) throw new Error('Withdrawal tidak ditemukan');
    if (w.status !== 'PENDING') throw new Error('Withdrawal sudah diproses');
    w.status = status; w.adminId = adminId; w.note = note || ''; w.processedAt = nowISO();
    if (status === 'REJECTED') {
      addPoints(w.userId, w.amountPoints, 'WITHDRAW_REFUND', 'Withdrawal ditolak ' + w.withdrawalId, w.withdrawalId);
    } else if (status === 'PAID') {
      db.points_ledger.push({ ledgerId: uid('PLG'), userId: w.userId, amount: 0, type: 'WITHDRAW_PAID', note: 'Dibayar ' + rupiah(w.amountRupiah) + ' via ' + w.method + ' ' + w.destination, refId: w.withdrawalId, createdAt: nowISO() });
    }
    log(adminId, 'WITHDRAWAL_' + status, w.withdrawalId, rupiah(w.amountRupiah) + ' | ' + w.destination);
    save();
    return w;
  }

  /* ------------ admin ------------ */
  function log(adminId, action, target, data) {
    db.admin_logs.push({ logId: uid('LOG'), adminId: adminId || null, action, target: target || '', data: data || '', createdAt: nowISO() });
    save();
  }
  function adminAdjustBalance(userId, amountPoints, reason, adminId) {
    if (!reason || reason.trim().length < 3) throw new Error('Alasan wajib diisi');
    const u = db.users.find(x => x.userId === userId);
    if (!u) throw new Error('Member tidak ditemukan');
    // Pengurangan tidak boleh melebihi saldo: addPoints memangkasnya menjadi
    // nol sementara ledger tetap mencatat nilai penuh, sehingga riwayat poin
    // tidak lagi cocok dengan saldo sebenarnya.
    if (amountPoints < 0 && -amountPoints > (u.points || 0))
      throw new Error('Saldo tidak cukup, poin tersedia ' + num(u.points || 0));
    addPoints(userId, amountPoints, amountPoints >= 0 ? 'ADMIN_CREDIT' : 'ADMIN_DEBIT', reason, null);
    log(adminId, 'SALDO_ADJUST', userId, (amountPoints >= 0 ? '+' : '') + amountPoints + ' poin | ' + reason);
    return true;
  }
  function setUserStatus(userId, status, adminId) {
    const u = db.users.find(x => x.userId === userId);
    if (!u) throw new Error('Member tidak ditemukan');
    u.status = status; u.updatedAt = nowISO();
    log(adminId, 'MEMBER_STATUS', userId, status);
    save();
  }
  function setFraud(userId, flag, adminId) {
    const u = db.users.find(x => x.userId === userId);
    u.fraudFlag = !!flag; log(adminId, 'MEMBER_FRAUD', userId, flag ? 'DITANDAI' : 'DIBERSIHKAN'); save();
  }
  function saveSettings(patch, adminId) {
    Object.assign(db.settings, patch);
    log(adminId, 'SETTINGS_UPDATE', 'settings', JSON.stringify(patch));
    save();
  }
  function stats() {
    const members = db.users.filter(u => u.role === 'MEMBER');
    return {
      totalMember: members.length,
      activeMember: members.filter(u => u.status === 'ACTIVE').length,
      verifiedMember: members.filter(u => u.verified).length,
      totalPoints: members.reduce((s, u) => s + (u.points || 0), 0),
      totalBalance: members.reduce((s, u) => s + pointsToRupiah(u.points), 0),
      totalProducts: db.products.length,
      totalOrders: db.orders.length,
      orderPending: db.orders.filter(o => ['PENDING', 'WAITING_PAYMENT'].includes(o.orderStatus)).length,
      orderProcessing: db.orders.filter(o => ['PAID', 'PROCESSING', 'SHIPPED'].includes(o.orderStatus)).length,
      withdrawalPending: db.withdrawals.filter(w => w.status === 'PENDING').length,
      totalReferral: db.referrals.length,
      verifiedReferral: db.referrals.filter(r => r.status === 'VERIFIED').length,
      adActivity: db.ad_rewards.length,
      adsToday: db.ad_rewards.filter(r => r.date === dayKey()).length
    };
  }

  /* ------------ referral view ------------ */
  function myReferrals(userId) {
    return db.referrals.filter(r => r.inviterId === userId).map(r => {
      const u = db.users.find(x => x.userId === r.invitedUserId);
      return { ...r, invitedName: u ? u.name : '(terhapus)', invitedVerified: u ? u.verified : false };
    }).reverse();
  }

  /* ------------ QRIS (nominal dinamis + CRC16, sesuai skrip txt) ------------ */
  function crc16(data) {
    let crc = 0xFFFF;
    for (let i = 0; i < data.length; i++) {
      crc ^= data.charCodeAt(i) << 8;
      for (let j = 0; j < 8; j++) {
        if ((crc & 0x8000) !== 0) crc = (crc << 1) ^ 0x1021;
        else crc <<= 1;
      }
    }
    return (crc & 0xFFFF).toString(16).toUpperCase().padStart(4, '0');
  }
  function qrisPayload(amount) {
    const tanpaCRC = QRIS_BASE.split("6304")[0];
    const tag = "54" + String(amount).length.toString().padStart(2, '0') + String(amount);
    const siap = tanpaCRC + tag + "6304";
    return siap + crc16(siap);
  }

  window.Store = {
    load, save, rupiah, num, dayKey, nowISO, uid, sha,
    login: async (i, p) => {
      const h = await sha(p);
      const id = String(i || '').trim().toLowerCase();
      const u = db.users.find(x => (x.email || '').toLowerCase() === id || (x.phone || '') === String(i).trim());
      if (!u) throw new Error('Akun tidak ditemukan');
      if (u.status !== 'ACTIVE') throw new Error('Akun tidak aktif. Hubungi admin.');
      // Akun admin hasil seed belum punya hash: tetapkan saat login pertama.
      if (!u.passwordHash && u.role === 'ADMIN' && String(p) === 'admin123') {
        u.passwordHash = h; u.updatedAt = nowISO(); save();
      }
      if (!u.passwordHash || u.passwordHash !== h) throw new Error('Password salah');
      db.session.userId = u.userId; save(); return u;
    },
    register: async (d) => {
      // hash password dulu, lalu simpan
      const hash = await sha(d.password);
      const name = String(d.name || '').trim();
      if (name.length < 3) throw new Error('Nama minimal 3 karakter');
      if (String(d.password || '').length < 6) throw new Error('Password minimal 6 karakter');
      const c = String(d.contact || '').trim();
      const isEmail = /^\S+@\S+\.\S+$/.test(c);
      const phone = isEmail ? '' : c, email = isEmail ? c : '';
      if (!email && !/^0\d{8,13}$/.test(phone)) throw new Error('Nomor HP tidak valid (contoh 08xxxxxxxxxx)');
      if (db.users.some(u => (email && u.email.toLowerCase() === email.toLowerCase()) || (phone && u.phone === phone)))
        throw new Error('Nomor HP/email sudah terdaftar');
      let inviter = null;
      if (d.referralId && String(d.referralId).trim()) {
        const rid = String(d.referralId).trim();
        if (!/^\d{6}$/.test(rid)) throw new Error('Referral ID harus tepat 6 digit angka');
        inviter = db.users.find(u => u.referralId === rid);
        if (!inviter) throw new Error('Referral ID tidak ditemukan');
        if (inviter.status !== 'ACTIVE') throw new Error('Referral ID tidak aktif');
      }
      const user = {
        userId: uid('USR'), name, email, phone, passwordHash: hash,
        referralId: genReferralId(), referredBy: inviter ? inviter.userId : null,
        role: 'MEMBER', status: 'ACTIVE', verified: false, points: 0,
        createdAt: nowISO(), updatedAt: nowISO(), fraudFlag: false
      };
      db.users.push(user);
      if (inviter) db.referrals.push({
        referralId: uid('REF'), inviterId: inviter.userId, invitedUserId: user.userId,
        status: 'PENDING', qualifyingOrderId: null, bonusPoints: 0, createdAt: nowISO(), verifiedAt: null
      });
      db.session.userId = user.userId; save();
      return user;
    },
    logout, currentUser,
    get db() { return db; },
    get settings() { return db.settings; },
    pointsToRupiah, rupiahToPoints,
    addPoints, ledger,
    activeProducts, product, priceOf, saveProduct, adjustStock,
    cartAdd, cartSet, cartClear, cartItems, cartTotals,
    createOrder, markOrderStatus, order, userOrders,
    ORDER_FLOW, ORDER_LABEL,
    todayTask, checkin, watchAd, adsToday,
    withdrawalEligibility, requestWithdrawal, processWithdrawal,
    log, adminAdjustBalance, setUserStatus, setFraud, saveSettings, stats, myReferrals,
    qrisPayload, crc16,
    resetDemo() { localStorage.removeItem(DB_KEY); db = seed(); }
  };
})();