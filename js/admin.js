/* ============================================================
   HERBALINDO — Panel Admin
   ============================================================ */
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const el = (tag, cls, html) => { const n = document.createElement(tag); if (cls) n.className = cls; if (html != null) n.innerHTML = html; return n; };
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const rp = (n) => Store.rupiah(n);
  const nm = (n) => Store.num(n);
  const dt = (iso) => iso ? new Date(iso).toLocaleString('id-ID') : '-';

  let cur = 'dash';

  let toastTimer;
  function toast(msg, ms = 2200) {
    const old = $('#a-toast'); if (old) old.remove();
    const t = el('div', 'toast', esc(msg)); t.id = 'a-toast';
    t.style.bottom = '24px';
    document.body.appendChild(t);
    clearTimeout(toastTimer); toastTimer = setTimeout(() => t.remove(), ms);
  }

  function overlay(node, onClose) {
    const ov = el('div', 'overlay'); ov.id = 'a-overlay';
    const sh = el('div', 'sheet'); sh.appendChild(el('div', 'grab'));
    sh.appendChild(node);
    ov.appendChild(sh);
    ov.onclick = (e) => { if (e.target === ov && onClose) onClose(); else if (e.target === ov) ov.remove(); };
    document.getElementById('modal-root').appendChild(ov);
    return sh;
  }
  const closeOv = () => { const o = $('#a-overlay'); if (o) o.remove(); };

  const MENU = [
    { v: 'dash', i: '📊', t: 'Dashboard' }, { v: 'member', i: '👥', t: 'Member' },
    { v: 'produk', i: '📦', t: 'Produk' }, { v: 'stok', i: '🏷️', t: 'Stok' },
    { v: 'order', i: '🧾', t: 'Pesanan' }, { v: 'referral', i: '🤝', t: 'Referral' },
    { v: 'poin', i: '⭐', t: 'Poin & Saldo' }, { v: 'wd', i: '💸', t: 'Withdrawal' },
    { v: 'setting', i: '⚙️', t: 'Pengaturan' }, { v: 'log', i: '🗒️', t: 'Audit Log' }
  ];

  function render(view) {
    const admin = Store.currentUser();
    if (!admin || admin.role !== 'ADMIN') { toast('Sesi admin berakhir'); return; }
    cur = view || cur;
    $('#a-title').textContent = (MENU.find(m => m.v === cur) || {}).t || 'Dashboard';
    $$('.a-side button').forEach(b => b.classList.toggle('on', b.dataset.av === cur));
    if (cur === 'keluar') { Store.logout(); location.reload(); return; }
    const v = $('#a-view');
    v.innerHTML = '';
    const fn = views[cur];
    if (fn) fn(v, admin);
    else views.dash(v, admin);
  }

  const views = {};

  /* ---------- Dashboard ---------- */
  views.dash = (v) => {
    const s = Store.stats();
    const cards = [
      ['Total Member', nm(s.totalMember), 'Member Aktif ' + nm(s.activeMember)],
      ['Member Terverifikasi', nm(s.verifiedMember), 'Punya referral verified'],
      ['Total Poin', nm(s.totalPoints), '≈ ' + rp(s.totalBalance)],
      ['Total Produk', nm(s.totalProducts), 'Katalog'],
      ['Total Order', nm(s.totalOrders), 'Order Pending ' + nm(s.orderPending)],
      ['Order Diproses', nm(s.orderProcessing), 'PAID/PROCESSING/SHIPPED'],
      ['Withdrawal Pending', nm(s.withdrawalPending), 'Butuh review'],
      ['Referral', nm(s.totalReferral), 'Verified ' + nm(s.verifiedReferral)],
      ['Aktivitas Iklan', nm(s.adActivity), 'Hari ini ' + nm(s.adsToday)],
      ['Total Saldo', rp(s.totalBalance), 'Konversi 10.000 poin = Rp1.000']
    ];
    v.innerHTML = `<div class="a-grid a4">${cards.map(c => `
      <div class="a-stat"><div class="k">${esc(c[0])}</div><div class="v">${c[1]}</div><div class="k">${esc(c[2])}</div></div>`).join('')}</div>`;
    const menu = el('div', 'a-menu');
    menu.style.marginTop = '12px';
    MENU.forEach(m => {
      const b = el('button', null, `<span class="e">${m.i}</span>${esc(m.t)}`);
      b.type = 'button'; b.onclick = () => render(m.v);
      menu.appendChild(b);
    });
    v.appendChild(menu);
  };

  /* ---------- Member ---------- */
  views.member = (v, admin) => {
    v.innerHTML = `
      <div class="a-card"><h3>Manajemen Member</h3>
        <input class="input" id="m-q" placeholder="Cari nama / HP / email / Referral ID">
      </div>
      <div id="m-list"></div>`;
    const draw = () => {
      const q = ($('#m-q').value || '').toLowerCase().trim();
      const list = Store.db.users.filter(u => u.role === 'MEMBER').filter(u =>
        !q || u.name.toLowerCase().includes(q) || (u.email || '').toLowerCase().includes(q) || (u.phone || '').includes(q) || (u.referralId || '').includes(q));
      const box = $('#m-list');
      box.innerHTML = '';
      if (!list.length) { box.innerHTML = '<div class="a-card">Tidak ada member.</div>'; return; }
      const card = el('div', 'a-card');
      card.innerHTML = `<div class="scrollx"><table class="a-table"><thead><tr>
        <th>Nama</th><th>Kontak</th><th>Referral ID</th><th>Poin</th><th>Saldo</th><th>Status</th><th></th>
      </tr></thead><tbody></tbody></table></div>`;
      const tb = card.querySelector('tbody');
      list.forEach(u => {
        const tr = el('tr', null, `
          <td>${esc(u.name)}<div class="small" style="color:#9FB6A8">${u.verified ? 'VERIFIED' : 'belum verified'}${u.fraudFlag ? ' · FRAUD' : ''}</div></td>
          <td>${esc(u.phone || '-')}<div style="color:#9FB6A8">${esc(u.email || '-')}</div></td>
          <td class="mono">${esc(u.referralId)}</td>
          <td>${nm(u.points)}</td>
          <td>${rp(Store.pointsToRupiah(u.points))}</td>
          <td><span class="pill a">${esc(u.status)}</span></td>
          <td><button class="btn sm ghost" type="button">Kelola</button></td>`);
        tr.querySelector('button').onclick = () => memberSheet(u.userId, admin, draw);
        tb.appendChild(tr);
      });
      box.appendChild(card);
    };
    $('#m-q').oninput = draw;
    draw();
  };

  function memberSheet(userId, admin, refresh) {
    const u = Store.db.users.find(x => x.userId === userId);
    const body = el('div');
    const refs = Store.myReferrals(userId);
    const orders = Store.userOrders(userId);
    const wds = Store.db.withdrawals.filter(w => w.userId === userId);
    const refBy = u.referredBy ? Store.db.users.find(x => x.userId === u.referredBy) : null;
    body.innerHTML = `
      <div class="a-card">
        <h3>${esc(u.name)}</h3>
        <div class="scrollx"><table class="a-table">
          <tr><td>Kontak</td><td>${esc(u.phone || '-')} / ${esc(u.email || '-')}</td></tr>
          <tr><td>Referral ID</td><td class="mono">${esc(u.referralId)}</td></tr>
          <tr><td>Diundang oleh</td><td>${refBy ? esc(refBy.name) + ' (' + esc(refBy.referralId) + ')' : '-'}</td></tr>
          <tr><td>Poin</td><td>${nm(u.points)} (${rp(Store.pointsToRupiah(u.points))})</td></tr>
          <tr><td>Status</td><td>${esc(u.status)} · ${u.verified ? 'VERIFIED' : 'belum verified'} · ${u.fraudFlag ? 'FRAUD FLAG' : 'normal'}</td></tr>
          <tr><td>Referral</td><td>${refs.length} total · ${refs.filter(r => r.status === 'VERIFIED').length} verified</td></tr>
          <tr><td>Pembelian</td><td>${orders.length} order · ${rp(orders.reduce((s, o) => s + o.total, 0))}</td></tr>
          <tr><td>Withdrawal</td><td>${wds.length} pengajuan</td></tr>
          <tr><td>Terdaftar</td><td>${dt(u.createdAt)}</td></tr>
        </table></div>
      </div>
      <div class="a-card"><h3>Edit Saldo / Poin</h3>
        <div class="form-row"><label class="lbl">Jumlah poin (boleh negatif)</label>
          <input class="input" id="ms-amt" inputmode="numeric" placeholder="5000 atau -5000"></div>
        <div class="form-row"><label class="lbl">Alasan (wajib, masuk audit log)</label>
          <input class="input" id="ms-why" placeholder="Bonus manual / koreksi"></div>
        <button class="btn" id="ms-save" type="button">SIMPAN PERUBAHAN</button>
      </div>
      <div class="a-card"><h3>Status Akun</h3>
        <div class="sheet-btns">
          <button class="btn ghost" id="ms-on" type="button">Aktifkan</button>
          <button class="btn danger" id="ms-off" type="button">Nonaktifkan</button>
        </div>
        <div class="sheet-btns">
          <button class="btn outline" id="ms-fraud" type="button">Tandai Fraud</button>
          <button class="btn outline" id="ms-clear" type="button">Bersihkan Fraud</button>
        </div>
      </div>
      <div class="a-card"><h3>Audit Log Member</h3><div id="ms-log"></div></div>
      <button class="btn outline" id="ms-close" type="button">Tutup</button>
    `;
    const lg = body.querySelector('#ms-log');
    const logs = Store.db.admin_logs.filter(l => l.target === userId).slice().reverse().slice(0, 12);
    lg.innerHTML = logs.length ? `<div class="scrollx"><table class="a-table">${logs.map(l => `<tr><td>${esc(l.action)}</td><td>${esc(l.data)}</td><td>${dt(l.createdAt)}</td></tr>`).join('')}</table></div>` : '<div class="small" style="color:#9FB6A8">Belum ada catatan.</div>';

    overlay(body, closeOv);
    body.querySelector('#ms-save').onclick = () => {
      try {
        Store.adminAdjustBalance(userId, Number($('#ms-amt').value), $('#ms-why').value, admin.userId);
        toast('Saldo/poin diperbarui'); closeOv(); refresh && refresh(); render(cur);
      } catch (e) { toast(e.message, 3000); }
    };
    body.querySelector('#ms-on').onclick = () => { Store.setUserStatus(userId, 'ACTIVE', admin.userId); toast('Akun diaktifkan'); closeOv(); refresh && refresh(); };
    body.querySelector('#ms-off').onclick = () => { Store.setUserStatus(userId, 'INACTIVE', admin.userId); toast('Akun dinonaktifkan'); closeOv(); refresh && refresh(); };
    body.querySelector('#ms-fraud').onclick = () => { Store.setFraud(userId, true, admin.userId); toast('Ditandai fraud (withdrawal diblokir)'); closeOv(); refresh && refresh(); };
    body.querySelector('#ms-clear').onclick = () => { Store.setFraud(userId, false, admin.userId); toast('Flag fraud dibersihkan'); closeOv(); refresh && refresh(); };
    body.querySelector('#ms-close').onclick = closeOv;
  }

  /* ---------- Produk ---------- */
  views.produk = (v, admin) => {
    v.innerHTML = `<div class="a-card"><h3>Katalog Produk</h3>
      <button class="btn" id="p-add" type="button">+ TAMBAH PRODUK</button></div>
      <div id="p-list"></div>`;
    const draw = () => {
      const box = $('#p-list'); box.innerHTML = '';
      Store.db.products.forEach(p => {
        const card = el('div', 'a-card');
        card.innerHTML = `
          <h3>${esc(p.name)} ${p.status === 'ACTIVE' ? '<span class="pill a">AKTIF</span>' : '<span class="pill grey">NONAKTIF</span>'}</h3>
          <div class="scrollx"><table class="a-table">
            <tr><td>SKU</td><td>${esc(p.sku || '-')}</td><td>Kategori</td><td>${esc(p.category || '-')}</td></tr>
            <tr><td>Harga</td><td>${rp(p.price)}</td><td>Promo</td><td>${Number(p.promoPrice) > 0 ? rp(p.promoPrice) : '-'}</td></tr>
            <tr><td>Stok</td><td>${nm(p.stock)} ${p.stock <= (p.minStock || 0) ? '<span class="pill red">MIN</span>' : ''}</td><td>Berat</td><td>${nm(p.weight)} gr</td></tr>
            <tr><td>Poin</td><td>+${nm(p.points)}</td><td>Diperbarui</td><td>${dt(p.updatedAt)}</td></tr>
          </table></div>
          <div class="sheet-btns">
            <button class="btn sm ghost" data-a="edit" type="button">Edit</button>
            <button class="btn sm outline" data-a="toggle" type="button">${p.status === 'ACTIVE' ? 'Nonaktifkan' : 'Aktifkan'}</button>
          </div>`;
        card.querySelector('[data-a="edit"]').onclick = () => productSheet(p.productId, admin, draw);
        card.querySelector('[data-a="toggle"]').onclick = () => {
          Store.saveProduct({ productId: p.productId, status: p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' });
          Store.log(admin.userId, 'PRODUCT_STATUS', p.productId, p.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE');
          toast('Status produk diubah'); draw();
        };
        box.appendChild(card);
      });
      if (!Store.db.products.length) box.innerHTML = '<div class="a-card">Belum ada produk.</div>';
    };
    $('#p-add').onclick = () => productSheet(null, admin, draw);
    draw();
  };

  function productSheet(productId, admin, refresh) {
    const isNew = !productId;
    const p = isNew ? {
      name: '', sku: '', category: 'Herbal Diet', description: '', composition: '', usage: '', warning: '',
      price: 0, promoPrice: 0, stock: 0, minStock: 5, weight: 250, imageUrl: '', points: 0, status: 'ACTIVE'
    } : { ...Store.product(productId) };
    const body = el('div');
    const F = (k, label, type, ph) => `<div class="form-row"><label class="lbl">${esc(label)}</label>
      <input class="input" data-k="${k}" type="${type || 'text'}" value="${esc(p[k] != null ? p[k] : '')}" placeholder="${esc(ph || '')}"></div>`;
    const T = (k, label) => `<div class="form-row"><label class="lbl">${esc(label)}</label>
      <textarea class="input" data-k="${k}" rows="3">${esc(p[k] || '')}</textarea></div>`;
    body.innerHTML = `<h3>${isNew ? 'Tambah Produk' : 'Edit Produk'}</h3>
      ${F('name', 'Nama Produk')}
      <div class="grid" style="display:grid;grid-template-columns:1fr 1fr;gap:9px">
        <div>${F('sku', 'SKU')}</div><div>${F('category', 'Kategori')}</div>
        <div>${F('price', 'Harga', 'number')}</div><div>${F('promoPrice', 'Harga Promo (0 = tidak ada)', 'number')}</div>
        <div>${F('stock', 'Stok', 'number')}</div><div>${F('minStock', 'Stok Minimum', 'number')}</div>
        <div>${F('weight', 'Berat (gram)', 'number')}</div><div>${F('points', 'Poin Pembelian', 'number')}</div>
      </div>
      ${F('imageUrl', 'URL Foto')}
      <div class="form-row"><label class="lbl">Status</label>
        <select class="input" data-k="status">
          <option value="ACTIVE" ${p.status === 'ACTIVE' ? 'selected' : ''}>ACTIVE</option>
          <option value="INACTIVE" ${p.status === 'INACTIVE' ? 'selected' : ''}>INACTIVE</option>
        </select></div>
      ${T('description', 'Deskripsi')}
      ${T('composition', 'Komposisi')}
      ${T('usage', 'Aturan Penggunaan')}
      ${T('warning', 'Peringatan')}
      <div class="small" style="color:#9FB6A8">Jangan gunakan klaim berlebihan (turun X kg, membakar lemak, 100% tanpa efek samping, menyembuhkan penyakit).</div>
      <div class="sheet-btns">
        <button class="btn outline" id="pp-cancel" type="button">Batal</button>
        <button class="btn" id="pp-save" type="button">SIMPAN</button>
      </div>`;
    overlay(body, closeOv);
    body.querySelector('#pp-cancel').onclick = closeOv;
    body.querySelector('#pp-save').onclick = () => {
      const data = {};
      $$('[data-k]', body).forEach(i => { data[i.dataset.k] = i.value; });
      data.price = Number(data.price); data.promoPrice = Number(data.promoPrice);
      data.stock = Number(data.stock); data.minStock = Number(data.minStock);
      data.weight = Number(data.weight); data.points = Number(data.points);
      if (!data.name.trim()) { toast('Nama produk wajib diisi'); return; }
      try {
        if (isNew) {
          const np = Store.saveProduct(data);
          Store.log(admin.userId, 'PRODUCT_CREATE', np.productId, np.name);
        } else {
          Store.saveProduct({ ...data, productId });
          Store.log(admin.userId, 'PRODUCT_UPDATE', productId, data.name);
        }
        toast('Produk disimpan'); closeOv(); refresh && refresh();
      } catch (e) { toast(e.message, 3000); }
    };
  }

  /* ---------- Stok ---------- */
  views.stok = (v, admin) => {
    v.innerHTML = `<div class="a-card"><h3>Kelola Stok</h3><div id="st-list"></div></div>
      <div class="a-card"><h3>Riwayat Pergerakan Stok</h3><div id="st-log"></div></div>`;
    const list = $('#st-list');
    Store.db.products.forEach(p => {
      const row = el('div', 'row', `
        <div class="ic">📦</div>
        <div class="main"><div class="t">${esc(p.name)}</div>
          <div class="s">Stok ${nm(p.stock)} · min ${nm(p.minStock || 0)} ${p.stock <= (p.minStock || 0) ? '· <span class="danger">PERLU RESTOCK</span>' : ''}</div></div>
        <button class="btn sm" type="button">Atur</button>`);
      row.querySelector('button').onclick = () => stockSheet(p.productId, admin, () => render('stok'));
      list.appendChild(row);
    });
    const lg = $('#st-log');
    const moves = Store.db.stock_movements.slice().reverse().slice(0, 40);
    lg.innerHTML = moves.length ? `<div class="scrollx"><table class="a-table"><thead><tr><th>Produk</th><th>Δ</th><th>Alasan</th><th>Sisa</th><th>Waktu</th></tr></thead><tbody>${
      moves.map(m => {
        const p = Store.product(m.productId);
        return `<tr><td>${esc(p ? p.name : m.productId)}</td><td>${m.delta > 0 ? '+' : ''}${nm(m.delta)}</td><td>${esc(m.reason)}</td><td>${nm(m.stockAfter)}</td><td>${dt(m.createdAt)}</td></tr>`;
      }).join('')}</tbody></table></div>` : '<div class="small" style="color:#9FB6A8">Belum ada pergerakan stok.</div>';
  };

  function stockSheet(productId, admin, refresh) {
    const p = Store.product(productId);
    const body = el('div');
    body.innerHTML = `<h3>${esc(p.name)}</h3>
      <div class="a-card"><div>Stok saat ini: <b>${nm(p.stock)}</b></div>
      <div class="form-row" style="margin-top:10px"><label class="lbl">Tambah stok</label>
        <input class="input" id="sk-add" type="number" placeholder="jumlah" value="10"></div>
      <button class="btn" id="sk-plus" type="button">TAMBAH STOK</button>
      <div class="form-row" style="margin-top:16px"><label class="lbl">Kurangi stok</label>
        <input class="input" id="sk-sub" type="number" placeholder="jumlah" value="1"></div>
      <button class="btn danger" id="sk-minus" type="button">KURANGI STOK</button>
      <div class="form-row" style="margin-top:16px"><label class="lbl">Stok minimum</label>
        <input class="input" id="sk-min" type="number" value="${p.minStock || 0}"></div>
      <button class="btn ghost" id="sk-min-save" type="button">SIMPAN STOK MINIMUM</button>
      <button class="btn outline" id="sk-close" style="margin-top:16px" type="button">Tutup</button></div>`;
    overlay(body, closeOv);
    body.querySelector('#sk-plus').onclick = () => {
      const n = Number($('#sk-add').value) || 0;
      if (n <= 0) return toast('Jumlah tidak valid');
      Store.adjustStock(productId, n, 'Admin tambah stok', admin.userId);
      Store.log(admin.userId, 'STOCK_ADD', productId, '+' + n); toast('Stok ditambah'); closeOv(); refresh && refresh();
    };
    body.querySelector('#sk-minus').onclick = () => {
      const n = Number($('#sk-sub').value) || 0;
      if (n <= 0 || n > p.stock) return toast('Jumlah tidak valid');
      Store.adjustStock(productId, -n, 'Admin kurangi stok', admin.userId);
      Store.log(admin.userId, 'STOCK_SUB', productId, '-' + n); toast('Stok dikurangi'); closeOv(); refresh && refresh();
    };
    body.querySelector('#sk-min-save').onclick = () => {
      Store.saveProduct({ productId, minStock: Number($('#sk-min').value) || 0 });
      Store.log(admin.userId, 'STOCK_MIN', productId, $('#sk-min').value); toast('Stok minimum disimpan'); closeOv(); refresh && refresh();
    };
    body.querySelector('#sk-close').onclick = closeOv;
  }

  /* ---------- Order ---------- */
  const ORDER_TABS = [
    ['SEMUA', null], ['PENDING', 'PENDING'], ['MENUNGGU PEMBAYARAN', 'WAITING_PAYMENT'],
    ['DIBAYAR', 'PAID'], ['DIPROSES', 'PROCESSING'], ['DIKIRIM', 'SHIPPED'],
    ['SELESAI', 'COMPLETED'], ['DIBATALKAN', 'CANCELLED'], ['REFUND', 'REFUNDED']
  ];
  let orderFilter = null;

  views.order = (v, admin) => {
    v.innerHTML = `<div class="a-card"><h3>Order Management</h3>
      <div class="chips" id="o-tabs" style="flex-wrap:wrap"></div></div>
      <div id="o-list"></div>`;
    const tabs = $('#o-tabs');
    ORDER_TABS.forEach(([label, val]) => {
      const b = el('button', 'chip' + (orderFilter === val ? ' on' : ''), esc(label));
      b.type = 'button'; b.onclick = () => { orderFilter = val; render('order'); };
      tabs.appendChild(b);
    });
    const list = $('#o-list');
    const rows = Store.db.orders.filter(o => !orderFilter || o.orderStatus === orderFilter).slice().reverse();
    if (!rows.length) { list.innerHTML = '<div class="a-card">Tidak ada order pada filter ini.</div>'; return; }
    const card = el('div', 'a-card');
    card.innerHTML = `<div class="scrollx"><table class="a-table"><thead><tr>
      <th>Order</th><th>Member</th><th>Total</th><th>Bayar</th><th>Status</th><th>Resi</th><th></th></tr></thead><tbody></tbody></table></div>`;
    const tb = card.querySelector('tbody');
    rows.forEach(o => {
      const u = Store.db.users.find(x => x.userId === o.userId);
      const tr = el('tr', null, `
        <td class="mono">${esc(o.orderNumber)}<div style="color:#9FB6A8">${dt(o.createdAt)}</div></td>
        <td>${esc(u ? u.name : '(terhapus)')}</td>
        <td>${rp(o.total)}</td>
        <td>${esc(o.paymentStatus)}</td>
        <td><span class="pill a">${Store.ORDER_LABEL[o.orderStatus]}</span></td>
        <td>${o.trackingNumber ? esc(o.shippingCourier) + ' ' + esc(o.trackingNumber) : '-'}</td>
        <td><button class="btn sm ghost" type="button">Kelola</button></td>`);
      tr.querySelector('button').onclick = () => orderSheet(o.orderId, admin, () => render('order'));
      tb.appendChild(tr);
    });
    list.appendChild(card);
  };

  function orderSheet(orderId, admin, refresh) {
    const o = Store.order(orderId);
    const u = Store.db.users.find(x => x.userId === o.userId);
    const body = el('div');
    const nextMap = { PENDING: 'WAITING_PAYMENT', WAITING_PAYMENT: 'PAID', PAID: 'PROCESSING', PROCESSING: 'SHIPPED', SHIPPED: 'DELIVERED', DELIVERED: 'COMPLETED' };
    const next = nextMap[o.orderStatus];
    body.innerHTML = `
      <h3>${esc(o.orderNumber)}</h3>
      <div class="a-card">
        <div class="scrollx"><table class="a-table">
          <tr><td>Member</td><td>${esc(u ? u.name : '-')} ${u ? '(' + esc(u.referralId) + ')' : ''}</td></tr>
          <tr><td>Total</td><td>${rp(o.total)} (subtotal ${rp(o.subtotal)} + ongkir ${rp(o.shippingCost)})</td></tr>
          <tr><td>Bayar</td><td>${esc(o.paymentMethod)} · ${esc(o.paymentStatus)}</td></tr>
          <tr><td>Status</td><td>${Store.ORDER_LABEL[o.orderStatus]}</td></tr>
          <tr><td>Alamat</td><td>${esc(o.shippingAddress.name)} · ${esc(o.shippingAddress.phone)}<br>${esc(o.shippingAddressText)}</td></tr>
        </table></div>
        <div style="margin-top:8px">${o.items.map(i => `<div class="kv"><span class="k">${esc(i.name)} × ${i.qty}</span><span class="v">${rp(i.price * i.qty)}</span></div>`).join('')}</div>
      </div>
      <div class="a-card"><h3>Pengiriman</h3>
        <div class="form-row"><label class="lbl">Kurir</label><input class="input" id="os-courier" value="${esc(o.shippingCourier || '')}" placeholder="JNE / J&T / SiCepat"></div>
        <div class="form-row"><label class="lbl">Nomor Resi</label><input class="input" id="os-resi" value="${esc(o.trackingNumber || '')}"></div>
        <button class="btn" id="os-ship" type="button">SIMPAN &amp; TANDAI DIKIRIM</button>
      </div>
      <div class="a-card"><h3>Ubah Status</h3>
        <div class="sheet-btns">
          ${next ? `<button class="btn" id="os-next" type="button">→ ${Store.ORDER_LABEL[next]}</button>` : ''}
          <button class="btn ghost" id="os-paid" type="button">Tandai DIBAYAR</button>
        </div>
        <div class="sheet-btns">
          <button class="btn outline" id="os-cancel" type="button">Batalkan</button>
          <button class="btn danger" id="os-refund" type="button">Refund</button>
        </div>
        <div class="small" style="color:#9FB6A8;margin-top:8px">Diubah oleh: ${esc(admin.name)} · dicatat di audit log</div>
      </div>
      <button class="btn outline" id="os-close" type="button">Tutup</button>`;
    overlay(body, closeOv);
    const act = (fn) => { try { fn(); refresh && refresh(); closeOv(); } catch (e) { toast(e.message, 3000); } };
    if (body.querySelector('#os-next')) body.querySelector('#os-next').onclick = () => act(() => {
      Store.markOrderStatus(Store.order(orderId), next, admin.userId, 'Admin ' + admin.name); toast('Status: ' + Store.ORDER_LABEL[next]);
    });
    body.querySelector('#os-paid').onclick = () => act(() => {
      Store.markOrderStatus(Store.order(orderId), 'PAID', admin.userId, 'Pembayaran diverifikasi admin');
      toast('Order dibayar. Poin pembelian & referral diproses.');
    });
    body.querySelector('#os-ship').onclick = () => act(() => {
      const c = $('#os-courier', body).value.trim(), r = $('#os-resi', body).value.trim();
      if (!r) throw new Error('Nomor resi wajib diisi');
      const ord = Store.order(orderId);
      ord.shippingCourier = c; ord.trackingNumber = r;
      Store.markOrderStatus(ord, 'SHIPPED', admin.userId, 'Dikirim via ' + c + ' ' + r);
      toast('Order ditandai DIKIRIM');
    });
    body.querySelector('#os-cancel').onclick = () => act(() => {
      if (!confirm('Batalkan order ini? Poin/bonus terkait akan dibatalkan.')) return;
      Store.markOrderStatus(Store.order(orderId), 'CANCELLED', admin.userId, 'Dibatalkan admin');
      toast('Order dibatalkan');
    });
    body.querySelector('#os-refund').onclick = () => act(() => {
      if (!confirm('Proses refund? Poin pembelian dan bonus referral akan ditarik kembali.')) return;
      Store.markOrderStatus(Store.order(orderId), 'REFUNDED', admin.userId, 'Refund admin');
      toast('Order di-refund');
    });
    body.querySelector('#os-close').onclick = closeOv;
  }

  /* ---------- Referral ---------- */
  views.referral = (v) => {
    const refs = Store.db.referrals.slice().reverse();
    const rows = refs.map(r => {
      const inv = Store.db.users.find(u => u.userId === r.inviterId);
      const invd = Store.db.users.find(u => u.userId === r.invitedUserId);
      return `<tr>
        <td>${esc(inv ? inv.name : '-')}<div style="color:#9FB6A8">${esc(inv ? inv.referralId : '')}</div></td>
        <td>${esc(invd ? invd.name : '-')}<div style="color:#9FB6A8">${esc(invd ? invd.referralId : '')}</div></td>
        <td>${r.status === 'VERIFIED' ? '<span class="pill a">VERIFIED</span>' : r.status === 'PENDING' ? '<span class="pill warn">PENDING</span>' : '<span class="pill red">BATAL</span>'}</td>
        <td>${r.qualifyingOrderId ? esc((Store.order(r.qualifyingOrderId) || {}).orderNumber || '-') : '-'}</td>
        <td>${nm(r.bonusPoints)}</td>
        <td>${dt(r.createdAt)}</td></tr>`;
    }).join('');
    v.innerHTML = `<div class="a-card"><h3>Referral (1 tingkat)</h3>
      ${rows ? `<div class="scrollx"><table class="a-table"><thead><tr><th>Pengundang</th><th>Diundang</th><th>Status</th><th>Order syarat</th><th>Bonus</th><th>Dibuat</th></tr></thead><tbody>${rows}</tbody></table></div>` : '<div>Belum ada referral.</div>'}
      <div class="small" style="color:#9FB6A8;margin-top:8px">Tidak ada bonus berantai: hanya pengundang langsung yang menerima bonus.</div>
    </div>`;
  };

  /* ---------- Poin & Saldo ---------- */
  views.poin = (v, admin) => {
    v.innerHTML = `<div class="a-card"><h3>Poin &amp; Saldo</h3>
      <div class="grid" style="display:grid;grid-template-columns:1fr 1fr;gap:9px">
        <div class="a-stat"><div class="k">Total Poin Beredar</div><div class="v">${nm(Store.stats().totalPoints)}</div></div>
        <div class="a-stat"><div class="k">Setara Saldo</div><div class="v">${rp(Store.stats().totalBalance)}</div></div>
      </div></div>
      <div class="a-card"><h3>Penyesuaian Poin Member</h3>
        <div class="form-row"><label class="lbl">Member</label><select class="input" id="pn-user"></select></div>
        <div class="form-row"><label class="lbl">Jumlah poin (+/-)</label><input class="input" id="pn-amt" inputmode="numeric" placeholder="1000"></div>
        <div class="form-row"><label class="lbl">Alasan</label><input class="input" id="pn-why" placeholder="Bonus event / koreksi"></div>
        <button class="btn" id="pn-save" type="button">SIMPAN</button>
      </div>
      <div class="a-card"><h3>Ledger Poin Global</h3><div id="pn-ledger"></div></div>`;
    const sel = $('#pn-user');
    Store.db.users.filter(u => u.role === 'MEMBER').forEach(u => {
      const o = el('option', null, esc(u.name + ' — ' + u.referralId + ' (' + nm(u.points) + ' poin)'));
      o.value = u.userId; sel.appendChild(o);
    });
    $('#pn-save').onclick = () => {
      const uid = sel.value, amt = Number($('#pn-amt').value);
      try {
        Store.adminAdjustBalance(uid, amt, $('#pn-why').value, admin.userId);
        toast('Poin disesuaikan & dicatat di audit log'); render('poin');
      } catch (e) { toast(e.message, 3000); }
    };
    const lg = $('#pn-ledger');
    const rows = Store.db.points_ledger.slice().reverse().slice(0, 60);
    lg.innerHTML = rows.length ? `<div class="scrollx"><table class="a-table"><thead><tr><th>Member</th><th>Δ Poin</th><th>Tipe</th><th>Keterangan</th><th>Waktu</th></tr></thead><tbody>${
      rows.map(l => {
        const u = Store.db.users.find(x => x.userId === l.userId);
        return `<tr><td>${esc(u ? u.name : l.userId)}</td><td>${l.amount >= 0 ? '+' : ''}${nm(l.amount)}</td><td>${esc(l.type)}</td><td>${esc(l.note)}</td><td>${dt(l.createdAt)}</td></tr>`;
      }).join('')}</tbody></table></div>` : '<div class="small" style="color:#9FB6A8">Belum ada mutasi poin.</div>';
  };

  /* ---------- Withdrawal ---------- */
  let wdFilter = 'PENDING';
  views.wd = (v, admin) => {
    v.innerHTML = `<div class="a-card"><h3>Withdrawal</h3><div class="chips" id="wd-tabs"></div></div><div id="wd-list"></div>`;
    const tabs = $('#wd-tabs');
    ['PENDING', 'APPROVED', 'PAID', 'REJECTED', 'SEMUA'].forEach(f => {
      const b = el('button', 'chip' + (wdFilter === f ? ' on' : ''), esc(f));
      b.type = 'button'; b.onclick = () => { wdFilter = f; render('wd'); };
      tabs.appendChild(b);
    });
    const rows = Store.db.withdrawals.filter(w => wdFilter === 'SEMUA' || w.status === wdFilter).reverse();
    const box = $('#wd-list');
    if (!rows.length) { box.innerHTML = '<div class="a-card">Tidak ada data withdrawal.</div>'; return; }
    const card = el('div', 'a-card');
    card.innerHTML = `<div class="scrollx"><table class="a-table"><thead><tr>
      <th>ID</th><th>Member</th><th>Jumlah</th><th>Tujuan</th><th>Status</th><th>Diajukan</th><th></th></tr></thead><tbody></tbody></table></div>`;
    const tb = card.querySelector('tbody');
    rows.forEach(w => {
      const u = Store.db.users.find(x => x.userId === w.userId);
      const tr = el('tr', null, `
        <td class="mono">${esc(w.withdrawalId)}</td>
        <td>${esc(u ? u.name : '-')}<div style="color:#9FB6A8">${esc(u ? u.referralId : '')}</div></td>
        <td>${rp(w.amountRupiah)}<div style="color:#9FB6A8">${nm(w.amountPoints)} poin</div></td>
        <td>${esc(w.method)}<div style="color:#9FB6A8">${esc(w.destination)}</div></td>
        <td><span class="pill a">${esc(w.status)}</span></td>
        <td>${dt(w.createdAt)}</td>
        <td>${w.status === 'PENDING' ? '<button class="btn sm ghost" type="button">Proses</button>' : '<span style="color:#9FB6A8">' + esc(w.note || '-') + '</span>'}</td>`);
      if (w.status === 'PENDING') {
        tr.querySelector('button').onclick = () => wdSheet(w, admin, () => render('wd'));
      }
      tb.appendChild(tr);
    });
    box.appendChild(card);
  };

  function wdSheet(w, admin, refresh) {
    const u = Store.db.users.find(x => x.userId === w.userId);
    const body = el('div');
    const e = Store.withdrawalEligibility(u);
    body.innerHTML = `<h3>Proses ${esc(w.withdrawalId)}</h3>
      <div class="a-card">
        <div class="scrollx"><table class="a-table">
          <tr><td>Member</td><td>${esc(u.name)} (${esc(u.referralId)})</td></tr>
          <tr><td>Jumlah</td><td>${rp(w.amountRupiah)} (${nm(w.amountPoints)} poin)</td></tr>
          <tr><td>Tujuan</td><td>${esc(w.method)} · ${esc(w.destination)}</td></tr>
          <tr><td>Ads hari ini</td><td>${e.ads}/${e.need}</td></tr>
          <tr><td>Saldo tersisa</td><td>${rp(Store.pointsToRupiah(u.points))}</td></tr>
        </table></div>
      </div>
      <div class="a-card"><h3>Catatan Admin</h3>
        <input class="input" id="ws-note" placeholder="Catatan (opsional)">
        <div class="sheet-btns">
          <button class="btn" id="ws-approve" type="button">APPROVE</button>
          <button class="btn gold" id="ws-paid" type="button">TANDAI PAID</button>
        </div>
        <div class="sheet-btns">
          <button class="btn danger" id="ws-reject" type="button">REJECT (poin dikembalikan)</button>
        </div>
        <div class="small" style="color:#9FB6A8;margin-top:8px">Semua tindakan dicatat pada audit log.</div>
      </div>
      <button class="btn outline" id="ws-close" type="button">Tutup</button>`;
    overlay(body, closeOv);
    const doIt = (status) => {
      try {
        Store.processWithdrawal(w.withdrawalId, status, admin.userId, $('#ws-note', body).value);
        toast('Withdrawal ' + status); closeOv(); refresh && refresh();
      } catch (e) { toast(e.message, 3000); }
    };
    body.querySelector('#ws-approve').onclick = () => doIt('APPROVED');
    body.querySelector('#ws-paid').onclick = () => doIt('PAID');
    body.querySelector('#ws-reject').onclick = () => doIt('REJECTED');
    body.querySelector('#ws-close').onclick = closeOv;
  }

  /* ---------- Pengaturan ---------- */
  views.setting = (v, admin) => {
    const s = Store.settings;
    const F = (k, label, note) => `<div class="form-row"><label class="lbl">${esc(label)}${note ? ' <span style="color:#7C9486">· ' + esc(note) + '</span>' : ''}</label>
      <input class="input" data-s="${k}" type="number" value="${s[k]}"></div>`;
    v.innerHTML = `<div class="a-card"><h3>Pengaturan Poin</h3>
      ${F('checkinPoints', 'Poin check-in')}
      ${F('adPoints', 'Poin per iklan')}
      ${F('adMaxPerDay', 'Maksimum iklan harian')}
      ${F('referralBonus', 'Bonus referral (poin)')}
      ${F('referralMinOrder', 'Minimum nilai order untuk verifikasi referral (Rp)')}
    </div>
    <div class="a-card"><h3>Konversi &amp; Withdrawal</h3>
      ${F('pointsPerRupiah', 'Poin per satuan Rupiah', '10.000 poin = Rp1.000')}
      ${F('rupiahPerUnit', 'Rupiah per satuan', 'Rp1.000')}
      ${F('minWithdrawRupiah', 'Minimum withdrawal (Rp)')}
      ${F('maxWithdrawPerDay', 'Maksimum withdrawal per hari')}
      <div class="form-row"><label class="lbl">Wajib 20 rewarded ads untuk withdrawal</label>
        <select class="input" data-s="requireAdsForWithdraw">
          <option value="true" ${s.requireAdsForWithdraw ? 'selected' : ''}>YA</option>
          <option value="false" ${!s.requireAdsForWithdraw ? 'selected' : ''}>TIDAK</option>
        </select></div>
    </div>
    <div class="a-card"><h3>Toko &amp; Ongkir</h3>
      ${F('shippingFlat', 'Ongkos kirim flat (Rp)')}
      ${F('freeShippingMin', 'Gratis ongkir mulai (Rp, 0 = mati)')}
    </div>
    <div class="a-card"><h3>Monetisasi</h3>
      <div class="form-row"><label class="lbl">AdMob banner aktif</label>
        <select class="input" data-s="admobEnabled">
          <option value="true" ${s.admobEnabled ? 'selected' : ''}>YA</option>
          <option value="false" ${!s.admobEnabled ? 'selected' : ''}>TIDAK</option>
        </select></div>
      <div class="form-row"><label class="lbl">AdMob Unit ID (banner)</label>
        <input class="input" data-s="admobUnitId" type="text" value="${esc(s.admobUnitId || '')}"></div>
      <div class="form-row"><label class="lbl">Mode uji iklan</label>
        <select class="input" data-s="admobTestMode">
          <option value="true" ${s.admobTestMode ? 'selected' : ''}>YA (test ads)</option>
          <option value="false" ${!s.admobTestMode ? 'selected' : ''}>TIDAK</option>
        </select></div>
    </div>
    <button class="btn" id="set-save" type="button">SIMPAN PENGATURAN</button>
    <button class="btn danger" id="set-reset" style="margin-top:10px" type="button">RESET DATA DEMO</button>`;
    $('#set-save').onclick = () => {
      const patch = {};
      $$('[data-s]', v).forEach(i => {
        const k = i.dataset.s;
        let val = i.value;
        if (i.type === 'number') val = Number(val);
        else if (val === 'true' || val === 'false') val = val === 'true';
        patch[k] = val;
      });
      Store.saveSettings(patch, admin.userId);
      toast('Pengaturan disimpan & dicatat di audit log');
      render('setting');
    };
    $('#set-reset').onclick = () => {
      if (!confirm('Reset seluruh data demo? Semua member/order akan hilang.')) return;
      Store.resetDemo();
      toast('Data direset'); location.reload();
    };
  };

  /* ---------- Audit Log ---------- */
  views.log = (v) => {
    const logs = Store.db.admin_logs.slice().reverse().slice(0, 200);
    v.innerHTML = `<div class="a-card"><h3>Audit Log</h3>
      ${logs.length ? `<div class="scrollx"><table class="a-table"><thead><tr><th>Waktu</th><th>Admin</th><th>Aksi</th><th>Target</th><th>Data</th></tr></thead><tbody>${
        logs.map(l => {
          const a = Store.db.users.find(u => u.userId === l.adminId);
          return `<tr><td>${dt(l.createdAt)}</td><td>${esc(a ? a.name : 'SISTEM')}</td><td>${esc(l.action)}</td><td class="mono">${esc(l.target)}</td><td>${esc(l.data)}</td></tr>`;
        }).join('')}</tbody></table></div>` : '<div style="color:#9FB6A8">Belum ada aktivitas.</div>'}
    </div>`;
  };

  /* ---------- boot ---------- */
  function boot() {
    $$('.a-side button').forEach(b => { b.onclick = () => render(b.dataset.av); });
    $('#a-logout').onclick = () => { Store.logout(); location.reload(); };
    const u = Store.currentUser();
    if (u && u.role === 'ADMIN') render('dash');
  }
  document.addEventListener('DOMContentLoaded', boot);

  window.Admin = { render };
})();