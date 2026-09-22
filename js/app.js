/* ============================================================
   HERBALINDO — Aplikasi Member (Android/WebView front-end)
   ============================================================ */
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const el = (tag, cls, html) => { const n = document.createElement(tag); if (cls) n.className = cls; if (html != null) n.innerHTML = html; return n; };
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const rp = (n) => Store.rupiah(n);
  const nm = (n) => Store.num(n);

  const state = { tab: 'home', lastRender: 0 };

  /* ---------------- Toast ---------------- */
  let toastTimer;
  function toast(msg, ms = 2200) {
    const old = $('#toast'); if (old) old.remove();
    const t = el('div', 'toast', esc(msg)); t.id = 'toast';
    document.body.appendChild(t);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.remove(), ms);
  }

  /* ---------------- Modal / Sheet ---------------- */
  function sheet(title, contentNode, buttons) {
    closeSheet();
    const ov = el('div', 'overlay'); ov.id = 'overlay';
    const sh = el('div', 'sheet');
    sh.appendChild(el('div', 'grab'));
    if (title) sh.appendChild(el('h3', null, esc(title)));
    sh.appendChild(contentNode);
    if (buttons && buttons.length) {
      const wrap = el('div', 'sheet-btns');
      buttons.forEach(b => {
        const btn = el('button', 'btn ' + (b.cls || ''), esc(b.label));
        btn.type = 'button';
        btn.onclick = () => { b.onClick ? b.onClick() : closeSheet(); };
        wrap.appendChild(btn);
      });
      sh.appendChild(wrap);
    }
    ov.appendChild(sh);
    ov.onclick = (e) => { if (e.target === ov) closeSheet(); };
    document.getElementById('modal-root').appendChild(ov);
    return sh;
  }
  function closeSheet() { const o = $('#overlay'); if (o) o.remove(); }

  /* ---------------- AdMob ---------------- */
  const AdMob = (() => {
    const creatives = [
      { t: 'Herbal Diet C — Promo Rp79.000', s: 'Stok terbatas · Gratis ongkir min. Rp150.000', cta: 'produk' },
      { t: 'Tonton iklan, dapat +5 poin', s: 'Maksimal 20 iklan per hari', cta: 'tugas' },
      { t: 'Ajak teman pakai Referral ID', s: 'Bonus poin setelah pembelian pertama valid', cta: 'profil' },
      { t: 'Kumpulkan poin jadi saldo', s: '10.000 poin = Rp1.000', cta: 'saldo' },
      { t: 'Tarik saldo mulai Rp50.000', s: 'Selesaikan 20 iklan hari ini', cta: 'saldo' }
    ];
    let idx = 0, rotTimer = null, rewardCb = null, mode = 'banner';

    function node() { return $('#admob'); }

    function show(kind = 'banner') {
      const n = node(); if (!n) return;
      const s = Store.settings;
      if (!s.admobEnabled) { n.classList.add('hidden'); return; }
      mode = kind;
      n.classList.remove('hidden');
      n.classList.toggle('reward', kind === 'reward');
      if (kind === 'reward') n.classList.remove('hidden');
      render();
      if (!rotTimer && kind === 'banner') rotTimer = setInterval(() => { idx = (idx + 1) % creatives.length; render(); }, 45000);
      if (kind === 'reward' && rotTimer) { clearInterval(rotTimer); rotTimer = null; }
    }
    function render() {
      const n = node(); if (!n) return;
      const s = Store.settings;
      const c = creatives[idx % creatives.length];
      $('#admob-t').textContent = mode === 'reward' ? 'Rewarded Ads — +' + s.adPoints + ' poin' : c.t;
      $('#admob-s').textContent = mode === 'reward'
        ? 'Tonton sampai selesai untuk mendapatkan poin'
        : c.s + (s.admobTestMode ? ' · TEST' : '');
      $('#admob-tag').textContent = mode === 'reward' ? 'REWARD' : 'AD';
      $('#admob-x').classList.toggle('hidden', mode === 'reward');
    }
    function hide() { const n = node(); if (n) n.classList.add('hidden'); if (rotTimer) { clearInterval(rotTimer); rotTimer = null; } mode = 'banner'; }

    function rewardUser() {
      const s = Store.settings;
      let left = 5;
      const body = el('div');
      body.innerHTML = `
        <div class="qrbox" style="border:none;box-shadow:none">
          <div style="font-size:38px">📺</div>
          <div id="ad-count" style="font-size:26px;font-weight:800;color:var(--green)">${left}s</div>
          <div class="small muted center">Iklan berjalan… harap tunggu sampai selesai.<br>Provider iklan akan mengonfirmasi reward.</div>
          <div class="bar" style="width:100%;margin-top:6px"><i id="ad-bar" style="width:0%"></i></div>
        </div>`;
      sheet('Rewarded Ads', body, []);
      const total = left;
      const timer = setInterval(() => {
        left -= 1;
        const c = $('#ad-count'), b = $('#ad-bar');
        if (c) c.textContent = Math.max(left, 0) + 's';
        if (b) b.style.width = ((total - left) / total * 100) + '%';
        if (left <= 0) {
          clearInterval(timer);
          closeSheet();
          try {
            const r = Store.watchAd(Store.currentUser().userId);
            toast('Reward dikonfirmasi: +' + r.gained + ' poin (' + r.adsWatched + '/' + r.max + ')');
          } catch (e) { toast(e.message, 3000); }
          renderTab(state.tab, true);
        }
      }, 1000);
    }
    return { show, hide, rewardUser, creatives, next: () => { idx = (idx + 1) % creatives.length; render(); } };
  })();
  window.AdMob = AdMob;

  /* ---------------- Pull to refresh ---------------- */
  function initPullToRefresh() {
    const ptr = $('#ptr'), txt = $('#ptr-text');
    let startY = 0, pulling = false, dist = 0;
    const send = async () => {
      ptr.classList.add('loading'); txt.textContent = 'Memperbarui…';
      ptr.style.height = '46px';
      await new Promise(r => setTimeout(r, 600));
      renderTab(state.tab, true);
      AdMob.next();
      ptr.classList.remove('loading');
      txt.textContent = 'Data diperbarui';
      setTimeout(() => { ptr.style.height = '0px'; txt.textContent = 'Tarik untuk memperbarui'; }, 450);
    };
    const onStart = (y) => { if (window.scrollY <= 0 && !pulling) { startY = y; pulling = true; dist = 0; } };
    const onMove = (y) => {
      if (!pulling) return;
      dist = y - startY;
      if (dist > 0 && window.scrollY <= 0) {
        const h = Math.min(dist * 0.5, 70);
        ptr.style.height = h + 'px';
        txt.textContent = h > 44 ? 'Lepaskan untuk memperbarui' : 'Tarik untuk memperbarui';
      }
    };
    const onEnd = () => {
      if (!pulling) return;
      pulling = false;
      if (dist * 0.5 > 44) send(); else ptr.style.height = '0px';
      dist = 0;
    };
    window.addEventListener('touchstart', e => onStart(e.touches[0].clientY), { passive: true });
    window.addEventListener('touchmove', e => onMove(e.touches[0].clientY), { passive: true });
    window.addEventListener('touchend', onEnd, { passive: true });
    window.addEventListener('pointerdown', e => { if (e.pointerType !== 'touch') onStart(e.clientY); });
    window.addEventListener('pointermove', e => { if (e.pointerType !== 'touch') onMove(e.clientY); });
    window.addEventListener('pointerup', () => onEnd());
    // tombol refresh manual (aksesibilitas / device tanpa gesture)
    document.addEventListener('keydown', e => { if (e.key === 'F5') { e.preventDefault(); send(); } });
  }

  /* ---------------- Auth ---------------- */
  function initAuth() {
    const tLogin = $('#tab-login'), tReg = $('#tab-register');
    const fLogin = $('#form-login'), fReg = $('#form-register');
    tLogin.onclick = () => { tLogin.classList.add('on'); tReg.classList.remove('on'); fLogin.classList.remove('hidden'); fReg.classList.add('hidden'); };
    tReg.onclick = () => { tReg.classList.add('on'); tLogin.classList.remove('on'); fReg.classList.remove('hidden'); fLogin.classList.add('hidden'); };

    fLogin.onsubmit = async (e) => {
      e.preventDefault();
      try {
        const u = await Store.login($('#login-id').value, $('#login-pass').value);
        toast('Selamat datang, ' + u.name);
        enterApp(u);
      } catch (err) { toast(err.message, 3000); }
    };

    fReg.onsubmit = async (e) => {
      e.preventDefault();
      try {
        const u = await Store.register({
          name: $('#reg-name').value, contact: $('#reg-contact').value,
          password: $('#reg-pass').value, referralId: $('#reg-ref').value
        });
        toast('Pendaftaran berhasil. Referral ID Anda: ' + u.referralId, 3500);
        enterApp(u);
      } catch (err) { toast(err.message, 3000); }
    };
  }

  function showAuth() {
    $('#auth-screen').classList.remove('hidden');
    $('#app').classList.add('hidden');
    $('#admin-screen').classList.add('hidden');
    AdMob.hide();
    document.title = 'HERBALINDO — Masuk';
  }

  function enterApp(user) {
    if (user.role === 'ADMIN') {
      $('#auth-screen').classList.add('hidden');
      $('#app').classList.add('hidden');
      $('#admin-screen').classList.remove('hidden');
      AdMob.hide();
      document.title = 'HERBALINDO — Admin';
      window.Admin.render('dash');
      return;
    }
    $('#auth-screen').classList.add('hidden');
    $('#admin-screen').classList.add('hidden');
    $('#app').classList.remove('hidden');
    document.title = 'HERBALINDO';
    state.tab = 'home';
    renderTab('home');
    AdMob.show('banner');
  }

  /* ---------------- Tab navigation ---------------- */
  function initTabs() {
    $$('#tabbar button').forEach(b => {
      b.onclick = () => renderTab(b.dataset.tab);
    });
    $('#cartbar-btn').onclick = () => renderTab('keranjang');
  }

  function renderTab(tab, silent) {
    const u = Store.currentUser();
    if (!u) { showAuth(); return; }
    if (u.role === 'ADMIN') { enterApp(u); return; }
    state.tab = tab;
    $$('#tabbar button').forEach(b => b.classList.toggle('on', b.dataset.tab === tab));
    $('#page-title').textContent = ({ home: 'Home', produk: 'Produk', tugas: 'Daily Task', saldo: 'Saldo', profil: 'Profil', keranjang: 'Keranjang', checkout: 'Checkout', order: 'Pesanan Saya', referral: 'Referral Saya' })[tab] || 'Home';
    $('#top-ref').textContent = 'REF ' + u.referralId;
    const view = $('#view');
    if (!silent) view.innerHTML = '';
    const fn = views[tab] || views.home;
    fn(view, u);
    updateCartBar();
    if (tab === 'tugas') AdMob.show('reward'); else AdMob.show('banner');
    window.scrollTo({ top: 0 });
  }

  function updateCartBar() {
    const t = Store.cartTotals();
    const bar = $('#cartbar');
    if (t.items.length === 0 || ['keranjang', 'checkout'].includes(state.tab)) { bar.classList.add('hidden'); return; }
    bar.classList.remove('hidden');
    $('#cartbar-t').textContent = t.items.reduce((s, i) => s + i.qty, 0) + ' item · ' + rp(t.total);
  }

  /* ---------------- Reusable renderers ---------------- */
  function productCard(p) {
    const price = Store.priceOf(p);
    const promo = Number(p.promoPrice) > 0 && Number(p.promoPrice) < Number(p.price);
    const img = p.imageUrl
      ? `<img class="thumb" src="${esc(p.imageUrl)}" alt="${esc(p.name)}" loading="lazy" onerror="this.replaceWith(Object.assign(document.createElement('div'),{className:'thumb ph',textContent:'🌿'}))">`
      : `<div class="thumb ph">🌿</div>`;
    const node = el('div', 'prod', `
      ${img}
      <div class="body">
        <div class="nm">${esc(p.name)}</div>
        <div class="pr">${rp(price)}${promo ? `<s>${rp(p.price)}</s>` : ''}</div>
        <div class="poin">+${nm(p.points)} Poin</div>
        <div class="small muted">${p.stock > 0 ? 'Stok ' + p.stock : '<span class="danger">Stok habis</span>'}</div>
        <button class="btn sm" ${p.stock <= 0 ? 'disabled' : ''}>BELI</button>
      </div>`);
    node.querySelector('.nm').onclick = () => productDetail(p.productId);
    node.querySelector('.thumb').onclick = () => productDetail(p.productId);
    node.querySelector('.btn').onclick = () => {
      try { Store.cartAdd(p.productId, 1); toast(p.name + ' ditambahkan ke keranjang'); updateCartBar(); }
      catch (e) { toast(e.message); }
    };
    return node;
  }

  function stat(v, k) { return `<div class="stat"><div class="v">${v}</div><div class="k">${esc(k)}</div></div>`; }
  function kv(k, v) { return `<div class="kv"><span class="k">${esc(k)}</span><span class="v">${v}</span></div>`; }
  function statusPill(s) {
    const map = { PENDING: 'grey', WAITING_PAYMENT: 'warn', PAID: 'gold', PROCESSING: 'gold', SHIPPED: 'gold', DELIVERED: '', COMPLETED: '', CANCELLED: 'red', REFUNDED: 'red' };
    return `<span class="pill ${map[s] || 'grey'}">${Store.ORDER_LABEL[s] || s}</span>`;
  }

  /* ---------------- Views ---------------- */
  const views = {};

  views.home = (view, u) => {
    const t = Store.todayTask(u.userId);
    const ads = Store.adsToday(u.userId);
    const maxAd = Store.settings.adMaxPerDay;
    const refs = Store.myReferrals(u.userId);
    const verified = refs.filter(r => r.status === 'VERIFIED').length;
    const orders = Store.userOrders(u.userId);
    const wdPend = Store.db.withdrawals.filter(w => w.userId === u.userId && w.status === 'PENDING').length;
    const pct = Math.min(100, Math.round(ads / maxAd * 100));

    view.innerHTML = `
      <div class="hero">
        <div class="lbl">SALDO SAYA</div>
        <div class="amt">${rp(Store.pointsToRupiah(u.points))}</div>
        <div class="pts">${nm(u.points)} POIN · 10.000 poin = Rp1.000</div>
        <div class="rid">REFERRAL ID <b class="mono">${esc(u.referralId)}</b>
          <button class="btn sm" style="width:auto;padding:4px 10px;background:rgba(255,255,255,.2)" id="h-copy" type="button">SALIN</button>
        </div>
      </div>

      <div class="section-title">Daily Task<span class="small muted">${t.date}</span></div>
      <div class="card">
        <div class="row" style="box-shadow:none;margin:0;padding:6px 0">
          <div class="ic">📅</div>
          <div class="main"><div class="t">Check-in Harian</div><div class="s">+${Store.settings.checkinPoints} poin per hari</div></div>
          <button class="btn sm ${t.checkin ? 'ghost' : ''}" id="h-checkin" type="button" ${t.checkin ? 'disabled' : ''}>${t.checkin ? '✅ Selesai' : 'Check-in'}</button>
        </div>
        <div class="row" style="box-shadow:none;margin:8px 0 0;padding:6px 0">
          <div class="ic">📺</div>
          <div class="main"><div class="t">Rewarded Ads</div><div class="s">${ads}/${maxAd} · +${Store.settings.adPoints} poin/iklan</div>
            <div class="bar" style="margin-top:6px"><i style="width:${pct}%"></i></div>
          </div>
          <button class="btn sm" id="h-ad" type="button" ${ads >= maxAd ? 'disabled' : ''}>Tonton</button>
        </div>
        <div class="row" style="box-shadow:none;margin:8px 0 0;padding:6px 0">
          <div class="ic">👥</div>
          <div class="main"><div class="t">Referral</div><div class="s">${verified} Verified · ${refs.length} total</div></div>
          <button class="btn sm ghost" id="h-ref" type="button">Bagikan</button>
        </div>
      </div>

      ${wdPend ? `<div class="card beige small">💸 Ada <b>${wdPend}</b> pengajuan withdrawal menunggu review admin.</div>` : ''}

      <div class="section-title">Produk Unggulan<a href="#" id="h-all">Lihat semua</a></div>
      <div class="grid c2" id="h-produk"></div>

      <div class="section-title">Pesanan Terakhir<a href="#" id="h-order">Riwayat</a></div>
      <div id="h-orders"></div>
    `;

    const grid = $('#h-produk', view);
    Store.activeProducts().slice(0, 4).forEach(p => grid.appendChild(productCard(p)));
    if (!grid.children.length) grid.innerHTML = '<div class="empty">Belum ada produk</div>';

    const oc = $('#h-orders', view);
    if (!orders.length) oc.innerHTML = '<div class="empty"><span class="e">🧾</span>Belum ada pesanan</div>';
    orders.slice(0, 3).forEach(o => oc.appendChild(orderRow(o)));

    $('#h-copy', view).onclick = () => copy(u.referralId);
    $('#h-all', view).onclick = (e) => { e.preventDefault(); renderTab('produk'); };
    $('#h-order', view).onclick = (e) => { e.preventDefault(); renderTab('order'); };
    $('#h-ref', view).onclick = () => shareReferral(u);
    $('#h-checkin', view).onclick = () => {
      try { Store.checkin(u.userId); toast('Check-in berhasil: +' + Store.settings.checkinPoints + ' poin'); renderTab('home'); }
      catch (e) { toast(e.message); }
    };
    $('#h-ad', view).onclick = () => AdMob.rewardUser();
  };

  function orderRow(o) {
    const n = el('div', 'row tap', `
      <div class="ic">🧾</div>
      <div class="main">
        <div class="t">${esc(o.orderNumber)}</div>
        <div class="s">${o.items.length} produk · ${new Date(o.createdAt).toLocaleString('id-ID')}</div>
      </div>
      <div class="rt">${rp(o.total)}<div style="margin-top:4px">${statusPill(o.orderStatus)}</div></div>`);
    n.onclick = () => openOrderDetail(o.orderId);
    return n;
  }

  views.produk = (view, u) => {
    view.innerHTML = `
      <div class="form-row">
        <input class="input" id="p-search" placeholder="🔍 Cari produk herbal…" type="search">
      </div>
      <div class="chips" id="p-cat"></div>
      <div class="grid c2" id="p-grid"></div>
    `;
    const cats = ['Semua', ...Store.db.categories.filter(c => c.status === 'ACTIVE').map(c => c.name)];
    let cat = 'Semua';
    const chips = $('#p-cat', view);
    cats.forEach((c, i) => {
      const b = el('button', 'chip' + (i === 0 ? ' on' : ''), esc(c));
      b.type = 'button';
      b.onclick = () => { cat = c; $$('.chip', chips).forEach(x => x.classList.remove('on')); b.classList.add('on'); draw(); };
      chips.appendChild(b);
    });
    const grid = $('#p-grid', view);
    function draw() {
      const q = $('#p-search', view).value.toLowerCase().trim();
      grid.innerHTML = '';
      const list = Store.activeProducts().filter(p =>
        (cat === 'Semua' || p.category === cat) &&
        (!q || p.name.toLowerCase().includes(q) || (p.description || '').toLowerCase().includes(q) || (p.sku || '').toLowerCase().includes(q)));
      if (!list.length) { grid.innerHTML = '<div class="empty" style="grid-column:1/-1"><span class="e">🔍</span>Produk tidak ditemukan</div>'; return; }
      list.forEach(p => grid.appendChild(productCard(p)));
    }
    $('#p-search', view).oninput = draw;
    draw();
  };

  function productDetail(pid) {
    const p = Store.product(pid);
    if (!p) return;
    const price = Store.priceOf(p);
    const promo = Number(p.promoPrice) > 0 && Number(p.promoPrice) < Number(p.price);
    const body = el('div');
    body.innerHTML = `
      ${p.imageUrl ? `<img src="${esc(p.imageUrl)}" style="width:100%;border-radius:14px;margin-bottom:10px" alt="">` : ''}
      <div class="pill">${esc(p.category || 'Herbal')}</div>
      <h3 style="margin:8px 0 4px">${esc(p.name)}</h3>
      <div style="font-size:20px;font-weight:800;color:var(--green-dark)">${rp(price)} ${promo ? `<s class="muted" style="font-size:13px;font-weight:500">${rp(p.price)}</s>` : ''}</div>
      <div class="poin small" style="font-weight:700;margin:4px 0 10px">+${nm(p.points)} Poin</div>
      ${kv('SKU', esc(p.sku || '-'))}
      ${kv('Berat', (p.weight || 0) + ' gram')}
      ${kv('Stok', p.stock > 0 ? p.stock : '<span class="danger">Habis</span>')}
      <div class="section-title">Deskripsi</div><div class="small">${esc(p.description || '-')}</div>
      <div class="section-title">Komposisi</div><div class="small">${esc(p.composition || '-')}</div>
      <div class="section-title">Aturan Penggunaan</div><div class="small">${esc(p.usage || '-')}</div>
      <div class="section-title">Peringatan</div><div class="hint-box small">${esc(p.warning || '-')}</div>
      <div class="small muted" style="margin-top:10px">Informasi sesuai label produk. Produk herbal bukan obat dan tidak untuk menyembuhkan penyakit.</div>
    `;
    sheet(p.name, body, [
      { label: 'Tambah Keranjang', cls: 'ghost', onClick: () => { try { Store.cartAdd(pid, 1); closeSheet(); toast('Ditambahkan'); updateCartBar(); } catch (e) { toast(e.message); } } },
      { label: 'Beli Sekarang', onClick: () => { try { Store.cartAdd(pid, 1); closeSheet(); renderTab('keranjang'); } catch (e) { toast(e.message); } } }
    ]);
  }

  views.keranjang = (view) => {
    const t = Store.cartTotals();
    if (!t.items.length) {
      view.innerHTML = '<div class="empty"><span class="e">🛒</span>Keranjang masih kosong<button class="btn ghost" id="k-go" style="margin-top:14px" type="button">Lihat Produk</button></div>';
      $('#k-go', view).onclick = () => renderTab('produk');
      return;
    }
    view.innerHTML = `
      <div id="k-list"></div>
      <div class="card">
        ${kv('Subtotal', rp(t.subtotal))}
        ${kv('Ongkos Kirim', t.shipping === 0 ? 'GRATIS' : rp(t.shipping))}
        ${kv('Poin didapat', '+' + nm(t.points) + ' poin')}
        <div class="total-row"><span>TOTAL</span><span>${rp(t.total)}</span></div>
      </div>
      <button class="btn" id="k-checkout" type="button">CHECKOUT</button>
      <button class="btn outline" id="k-clear" style="margin-top:9px" type="button">Kosongkan Keranjang</button>
      <div style="height:10px"></div>`;
    const list = $('#k-list', view);
    t.items.forEach(i => {
      const row = el('div', 'row', `
        <div class="ic">🌿</div>
        <div class="main"><div class="t">${esc(i.product.name)}</div>
          <div class="s">${rp(i.price)} · +${nm(i.points)} poin</div></div>
        <div class="stepper"><button type="button" data-m="-">−</button><span>${i.qty}</span><button type="button" data-m="+">+</button></div>`);
      row.querySelector('[data-m="-"]').onclick = () => { Store.cartSet(i.productId, i.qty - 1); renderTab('keranjang'); };
      row.querySelector('[data-m="+"]').onclick = () => { try { Store.cartSet(i.productId, i.qty + 1); renderTab('keranjang'); } catch (e) { toast(e.message); } };
      list.appendChild(row);
    });
    $('#k-checkout', view).onclick = () => renderTab('checkout');
    $('#k-clear', view).onclick = () => { Store.cartClear(); toast('Keranjang dikosongkan'); renderTab('keranjang'); };
  };

  views.checkout = (view, u) => {
    const t = Store.cartTotals();
    if (!t.items.length) { renderTab('keranjang'); return; }
    const last = u.lastAddress || {};
    view.innerHTML = `
      <div class="card">
        <div class="section-title" style="margin-top:0">Alamat Penerima</div>
        <div class="form-row"><label class="lbl">Nama Penerima</label>
          <input class="input" id="c-name" value="${esc(last.name || u.name)}"></div>
        <div class="form-row"><label class="lbl">Nomor HP</label>
          <input class="input" id="c-phone" inputmode="tel" value="${esc(last.phone || u.phone || '')}"></div>
        <div class="form-row"><label class="lbl">Alamat Lengkap</label>
          <textarea class="input" id="c-address" rows="3" placeholder="Jalan, nomor rumah, RT/RW, kelurahan, kecamatan">${esc(last.address || '')}</textarea></div>
        <div class="grid c2">
          <div class="form-row"><label class="lbl">Kota/Kabupaten</label><input class="input" id="c-city" value="${esc(last.city || '')}"></div>
          <div class="form-row"><label class="lbl">Kode Pos</label><input class="input" id="c-postal" inputmode="numeric" value="${esc(last.postal || '')}"></div>
        </div>
        <div class="form-row" style="margin-bottom:0"><label class="lbl">Catatan (opsional)</label>
          <input class="input" id="c-note" placeholder="Contoh: kirim siang hari"></div>
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0">Rincian Pesanan</div>
        <div id="c-items"></div>
        ${kv('Subtotal', rp(t.subtotal))}
        ${kv('Ongkos Kirim', rp(t.shipping))}
        <div class="total-row"><span>TOTAL BAYAR</span><span>${rp(t.total)}</span></div>
      </div>

      <div class="card">
        <div class="section-title" style="margin-top:0">Metode Pembayaran</div>
        <div class="row" style="box-shadow:none;margin:0;padding:4px 0">
          <div class="ic">🏦</div>
          <div class="main"><div class="t">QRIS</div><div class="s">Scan / simpan QR, bayar dari e-wallet atau m-banking</div></div>
          <span class="pill">TERPILIH</span>
        </div>
        <div class="hint-box small" style="margin-top:8px">Pembayaran diverifikasi admin. Pesanan diproses setelah status <b>DIBAYAR</b>. Poin pembelian (+${nm(t.points)}) dan bonus referral masuk setelah pembayaran berhasil.</div>
      </div>

      <button class="btn" id="c-submit" type="button">BUAT PESANAN &amp; BAYAR</button>
      <div style="height:12px"></div>`;

    const il = $('#c-items', view);
    t.items.forEach(i => {
      const n = el('div', 'kv', `<span class="k">${esc(i.product.name)} × ${i.qty}</span><span class="v">${rp(i.subtotal)}</span>`);
      il.appendChild(n);
    });

    $('#c-submit', view).onclick = () => {
      const addr = {
        name: $('#c-name', view).value.trim(), phone: $('#c-phone', view).value.trim(),
        address: $('#c-address', view).value.trim(), city: $('#c-city', view).value.trim(),
        postal: $('#c-postal', view).value.trim(), note: $('#c-note', view).value.trim()
      };
      if (!addr.name || !addr.phone || !addr.address || !addr.city) { toast('Lengkapi nama, HP, alamat, dan kota'); return; }
      try {
        const order = Store.createOrder({ user: u, items: t.items, shipping: t.shipping, shippingAddress: addr });
        const user = Store.db.users.find(x => x.userId === u.userId);
        user.lastAddress = addr; Store.save();
        toast('Pesanan ' + order.orderNumber + ' dibuat');
        renderTab('order');
        qrisSheet(order);
      } catch (e) { toast(e.message, 3200); }
    };
  };

  /* -------- QRIS pembayaran (nominal dinamis + CRC16) -------- */
  function qrisSheet(order) {
    const payload = Store.qrisPayload(order.total);
    const body = el('div');
    body.innerHTML = `
      <div class="qrbox" id="capture-area">
        <div style="font-weight:700">${esc(Store.settings.appName)}</div>
        <div class="small muted">${esc(order.orderNumber)}</div>
        <div id="qrcode"></div>
        <div style="font-size:19px;font-weight:800;color:var(--green-dark)" id="qr-total">${rp(order.total)}</div>
        <div class="small muted" id="qr-tr">TRX-ID: ${Math.floor(Date.now() / 1000)}</div>
        <div class="small" id="qr-timer" style="color:var(--warn);font-weight:700"></div>
      </div>
      <input type="hidden" id="qris-payload" value="${esc(payload)}">
      <div class="small muted" style="margin-top:10px">QRIS berlaku 180 detik. Simpan/screenshot QR bila perlu. Setelah membayar, tekan <b>Saya Sudah Bayar</b> — admin akan memverifikasi.</div>`;
    sheet('Pembayaran QRIS', body, [
      { label: 'Unduh QR', cls: 'outline', onClick: downloadQR },
      { label: 'Saya Sudah Bayar', onClick: () => { closeSheet(); toast('Menunggu verifikasi admin'); renderTab('order', true); } }
    ]);

    const box = $('#qrcode', body);
    let painted = false;
    try {
      if (window.QRCode) { new QRCode(box, { text: payload, width: 200, height: 200, correctLevel: QRCode.CorrectLevel.M }); painted = true; }
    } catch (e) { }
    if (!painted) {
      // fallback ringan: render QR via gambar publik bila lib belum siap
      const img = el('img'); img.width = 200; img.height = 200;
      img.src = 'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(payload);
      box.appendChild(img);
    }

    let time = 180;
    const tEl = $('#qr-timer', body);
    const timer = setInterval(() => {
      const m = Math.floor(time / 60), s = time % 60;
      if (tEl) tEl.textContent = 'Selesaikan dalam ' + m + ':' + (s < 10 ? '0' : '') + s;
      if (time-- <= 0) { clearInterval(timer); if (tEl) tEl.textContent = 'Waktu habis, buat pesanan ulang.'; }
    }, 1000);
    $('#overlay').addEventListener('remove', () => clearInterval(timer));
  }

  function downloadQR() {
    const area = $('#capture-area');
    if (!area) return;
    const canvas = area.querySelector('canvas');
    const img = area.querySelector('img');
    const a = document.createElement('a');
    a.download = 'QRIS-HERBALINDO-' + Date.now() + '.png';
    if (canvas) { a.href = canvas.toDataURL(); a.click(); toast('QR tersimpan'); }
    else if (img) { window.open(img.src, '_blank'); toast('QR dibuka di tab baru'); }
    else toast('QR belum siap');
  }

  /* -------- Order detail -------- */
  function openOrderDetail(oid) {
    const o = Store.order(oid);
    if (!o) return;
    const wrap = el('div');
    const stepNames = Store.ORDER_FLOW;
    const curIdx = stepNames.indexOf(o.orderStatus);
    const steps = stepNames.map((s, i) => `
      <div class="step ${i < curIdx ? 'done' : i === curIdx ? 'now' : ''}">
        <div class="dot">${i < curIdx || i === curIdx ? '' : ''}${i < stepNames.length - 1 ? '<i class="ln"></i>' : ''}</div>
        <div class="tx"><b>${Store.ORDER_LABEL[s]}</b></div>
      </div>`).join('');
    wrap.innerHTML = `
      <div class="card tight beige">
        <div class="kv"><span class="k">Nomor Order</span><span class="v mono">${esc(o.orderNumber)}</span></div>
        <div class="kv"><span class="k">Status</span><span class="v">${statusPill(o.orderStatus)}</span></div>
        <div class="kv"><span class="k">Pembayaran</span><span class="v">QRIS · ${esc(o.paymentStatus)}</span></div>
        <div class="kv"><span class="k">Waktu</span><span class="v">${new Date(o.createdAt).toLocaleString('id-ID')}</span></div>
        ${o.trackingNumber ? kv('Kurir / Resi', esc(o.shippingCourier) + ' · ' + esc(o.trackingNumber)) : ''}
      </div>
      <div class="section-title">Produk</div>
      ${o.items.map(i => kv(i.name + ' × ' + i.qty, rp(i.price * i.qty))).join('')}
      ${kv('Subtotal', rp(o.subtotal))}
      ${kv('Ongkir', rp(o.shippingCost))}
      <div class="total-row"><span>TOTAL</span><span>${rp(o.total)}</span></div>
      <div class="section-title">Alamat Pengiriman</div>
      <div class="small">${esc(o.shippingAddress.name)} · ${esc(o.shippingAddress.phone)}<br>${esc(o.shippingAddressText)}</div>
      ${o.note ? `<div class="small muted" style="margin-top:6px">Catatan: ${esc(o.note)}</div>` : ''}
      <div class="section-title">Status Pesanan</div>
      <div class="steps">${steps}</div>
      ${['CANCELLED', 'REFUNDED'].includes(o.orderStatus) ? `<div class="hint-box small">Status akhir: ${Store.ORDER_LABEL[o.orderStatus]}</div>` : ''}
    `;
    const btns = [];
    if (['PENDING', 'WAITING_PAYMENT'].includes(o.orderStatus)) {
      btns.push({ label: 'Bayar Sekarang', onClick: () => { closeSheet(); setTimeout(() => qrisSheet(o), 150); } });
    }
    btns.push({ label: 'Tutup', cls: 'outline' });
    sheet('Detail Pesanan', wrap, btns);
  }

  views.order = (view, u) => {
    const orders = Store.userOrders(u.userId);
    view.innerHTML = orders.length ? '<div id="o-list"></div>' : '<div class="empty"><span class="e">🧾</span>Belum ada pesanan</div>';
    const l = $('#o-list', view);
    if (l) orders.forEach(o => l.appendChild(orderRow(o)));
  };

  views.tugas = (view, u) => {
    const t = Store.todayTask(u.userId);
    const ads = Store.adsToday(u.userId), maxAd = Store.settings.adMaxPerDay;
    const refs = Store.myReferrals(u.userId);
    const verified = refs.filter(r => r.status === 'VERIFIED').length;
    const purchasePts = Store.ledger(u.userId).filter(l => l.type === 'PURCHASE').reduce((s, l) => s + l.amount, 0);
    const pct = Math.round(ads / maxAd * 100);
    view.innerHTML = `
      <div class="card beige small">Daily Task tidak memaksa Anda online sepanjang hari. Kerjakan kapan saja saat senggang.</div>
      <div class="task">
        <div class="hd"><span class="ic">📅</span><div class="main"><div class="t">Check-in Harian</div><div class="s">+${Store.settings.checkinPoints} poin</div></div></div>
        <div class="row" style="box-shadow:none;margin:10px 0 0;padding:0">
          <div class="main small">${t.checkin ? '✅ Selesai hari ini' : 'Belum check-in hari ini'}</div>
          <button class="btn sm ${t.checkin ? 'ghost' : ''}" id="t-check" type="button" ${t.checkin ? 'disabled' : ''}>${t.checkin ? '✅ Selesai' : 'Check-in'}</button>
        </div>
      </div>
      <div class="task">
        <div class="hd"><span class="ic">📺</span><div class="main"><div class="t">Rewarded Ads</div><div class="s">${ads}/${maxAd} · +${Store.settings.adPoints} poin per iklan</div></div></div>
        <div class="bar" style="margin:10px 0"><i style="width:${pct}%"></i></div>
        <button class="btn ${ads >= maxAd ? 'ghost' : ''}" id="t-ad" type="button" ${ads >= maxAd ? 'disabled' : ''}>${ads >= maxAd ? '20/20 Selesai' : 'Tonton Iklan (+' + Store.settings.adPoints + ')'}</button>
      </div>
      <div class="task">
        <div class="hd"><span class="ic">👥</span><div class="main"><div class="t">Referral</div><div class="s">${verified} Verified · bonus +${nm(Store.settings.referralBonus)} poin</div></div></div>
        <div class="row" style="box-shadow:none;margin:10px 0 0;padding:0">
          <div class="main"><div class="t mono">${esc(u.referralId)}</div><div class="s">Referral ID 6 digit</div></div>
          <button class="btn sm ghost" id="t-share" type="button">BAGIKAN</button>
        </div>
      </div>
      <div class="task">
        <div class="hd"><span class="ic">🛍️</span><div class="main"><div class="t">Pembelian</div><div class="s">Poin dari produk yang dibeli</div></div></div>
        <div class="row" style="box-shadow:none;margin:10px 0 0;padding:0">
          <div class="main"><div class="t">+${nm(purchasePts)} Poin</div><div class="s">Total poin pembelian</div></div>
          <button class="btn sm" id="t-buy" type="button">Belanja</button>
        </div>
      </div>
      <div class="section-title">Riwayat Poin</div>
      <div id="t-ledger"></div>`;
    const lg = $('#t-ledger', view);
    const rows = Store.ledger(u.userId, 20);
    if (!rows.length) lg.innerHTML = '<div class="empty">Belum ada aktivitas poin</div>';
    rows.forEach(l => {
      const r = el('div', 'row', `
        <div class="ic">${l.amount >= 0 ? '➕' : '➖'}</div>
        <div class="main"><div class="t">${esc(l.note || l.type)}</div><div class="s">${new Date(l.createdAt).toLocaleString('id-ID')}</div></div>
        <div class="rt ${l.amount >= 0 ? '' : 'danger'}">${l.amount >= 0 ? '+' : ''}${nm(l.amount)}</div>`);
      lg.appendChild(r);
    });
    $('#t-check', view).onclick = () => { try { Store.checkin(u.userId); toast('Check-in berhasil'); renderTab('tugas'); } catch (e) { toast(e.message); } };
    $('#t-ad', view).onclick = () => AdMob.rewardUser();
    $('#t-share', view).onclick = () => shareReferral(u);
    $('#t-buy', view).onclick = () => renderTab('produk');
  };

  views.saldo = (view, u) => {
    const e = Store.withdrawalEligibility(u);
    const ads = Store.adsToday(u.userId), maxAd = Store.settings.adMaxPerDay;
    const ledger = Store.ledger(u.userId, 30);
    const wds = Store.db.withdrawals.filter(w => w.userId === u.userId).reverse();
    view.innerHTML = `
      <div class="hero">
        <div class="lbl">SALDO SAYA</div>
        <div class="amt">${rp(e.rupiah)}</div>
        <div class="pts">${nm(u.points)} POIN</div>
        <div class="small" style="opacity:.85;margin-top:8px">Konversi: 10.000 poin = Rp1.000</div>
      </div>

      <div class="card" style="margin-top:14px">
        <div class="section-title" style="margin-top:0">Syarat Withdrawal<span class="small ${e.ok ? 'gold' : 'muted'}">${e.ok ? 'SEMUA TERPENUHI' : 'BELUM TERPENUHI'}</span></div>
        ${e.checks.map(c => `<div class="kv"><span class="k">${c.ok ? '✅' : '⛔'} ${esc(c.text)}</span><span class="v"></span></div>`).join('')}
        <div class="kv"><span class="k">📺 Ads Hari Ini</span><span class="v">${ads}/${maxAd}</span></div>
        <div class="bar" style="margin-top:8px"><i style="width:${Math.round(ads / maxAd * 100)}%"></i></div>
      </div>

      <button class="btn" id="s-wd" type="button" ${e.ok ? '' : 'disabled'}>TARIK SALDO</button>
      <button class="btn outline" id="s-ad" style="margin-top:9px" type="button" ${ads >= maxAd ? 'disabled' : ''}>Tonton Iklan (+${Store.settings.adPoints} poin)</button>

      <div class="section-title">Riwayat Saldo &amp; Poin</div>
      <div id="s-ledger"></div>
      <div class="section-title">Riwayat Withdrawal</div>
      <div id="s-wd-list"></div>`;

    const lg = $('#s-ledger', view);
    if (!ledger.length) lg.innerHTML = '<div class="empty">Belum ada mutasi</div>';
    ledger.forEach(l => lg.appendChild(el('div', 'row', `
      <div class="ic">${l.amount >= 0 ? '➕' : '➖'}</div>
      <div class="main"><div class="t">${esc(l.note || l.type)}</div>
        <div class="s">${new Date(l.createdAt).toLocaleString('id-ID')} · ${esc(l.type)}</div></div>
      <div class="rt ${l.amount >= 0 ? '' : 'danger'}">${l.amount >= 0 ? '+' : ''}${nm(l.amount)}<div class="small muted">${rp(Store.pointsToRupiah(Math.abs(l.amount)))}</div></div>`)));

    const wl = $('#s-wd-list', view);
    if (!wds.length) wl.innerHTML = '<div class="empty">Belum ada pengajuan</div>';
    wds.forEach(w => wl.appendChild(el('div', 'row', `
      <div class="ic">💸</div>
      <div class="main"><div class="t">${rp(w.amountRupiah)}</div>
        <div class="s">${esc(w.method)} · ${esc(w.destination)}<br>${new Date(w.createdAt).toLocaleString('id-ID')}</div></div>
      <div class="rt">${w.status === 'PAID' ? '<span class="pill">PAID</span>' : w.status === 'REJECTED' ? '<span class="pill red">REJECTED</span>' : w.status === 'APPROVED' ? '<span class="pill gold">APPROVED</span>' : '<span class="pill warn">PENDING</span>'}</div>`)));

    $('#s-ad', view).onclick = () => AdMob.rewardUser();
    $('#s-wd', view).onclick = () => {
      const ee = Store.withdrawalEligibility(Store.currentUser());
      if (!ee.ok) { toast('Syarat withdrawal belum terpenuhi'); return; }
      withdrawalSheet(u);
    };
  };

  function withdrawalSheet(u) {
    const maxPoints = u.points;
    const body = el('div');
    body.innerHTML = `
      <div class="card tight beige">
        ${kv('Saldo tersedia', rp(Store.pointsToRupiah(maxPoints)))}
        ${kv('Minimum', rp(Store.settings.minWithdrawRupiah))}
      </div>
      <div class="form-row"><label class="lbl">Nominal (Rp)</label>
        <input class="input" id="w-rp" inputmode="numeric" placeholder="50000" value="${Store.settings.minWithdrawRupiah}"></div>
      <div class="form-row"><label class="lbl">Metode</label>
        <select class="input" id="w-method">
          <option>DANA</option><option>OVO</option><option>GoPay</option><option>ShopeePay</option><option>BCA</option><option>BRI</option><option>Mandiri</option>
        </select></div>
      <div class="form-row"><label class="lbl">Nama Penerima</label>
        <input class="input" id="w-name" value="${esc(u.name)}"></div>
      <div class="form-row"><label class="lbl">Nomor Rekening / E-Wallet</label>
        <input class="input" id="w-dest" inputmode="numeric" placeholder="08xxxxxxxxxx / no rekening"></div>
      <div class="small muted" id="w-info"></div>`;
    const info = () => {
      const rpVal = Number($('#w-rp', body).value) || 0;
      $('#w-info', body).textContent = 'Setara ' + nm(Store.rupiahToPoints(rpVal)) + ' poin akan dipotong dari akun Anda (ditahan sampai admin memproses).';
    };
    sheet('Tarik Saldo', body, [
      { label: 'Batal', cls: 'outline' },
      {
        label: 'Ajukan Withdrawal', onClick: () => {
          try {
            const rpVal = Number($('#w-rp', body).value) || 0;
            const dest = $('#w-dest', body).value.trim();
            if (!dest) throw new Error('Nomor rekening/e-wallet wajib diisi');
            const w = Store.requestWithdrawal(Store.currentUser(), {
              amountPoints: Store.rupiahToPoints(rpVal),
              method: $('#w-method', body).value,
              destination: $('#w-name', body).value.trim() + ' — ' + dest
            });
            closeSheet(); toast('Pengajuan ' + w.withdrawalId + ' dikirim. Status: PENDING');
            renderTab('saldo');
          } catch (e) { toast(e.message, 3000); }
        }
      }
    ]);
    body.querySelector('#w-rp').oninput = info;
    info();
  }

  views.referral = (view, u) => {
    const refs = Store.myReferrals(u.userId);
    const verified = refs.filter(r => r.status === 'VERIFIED');
    const bonus = verified.reduce((s, r) => s + (r.bonusPoints || 0), 0);
    view.innerHTML = `
      <div class="hero center">
        <div class="lbl">REFERRAL SAYA</div>
        <div class="amt mono">${esc(u.referralId)}</div>
        <div class="small" style="opacity:.85">Bagikan kode ini. Bonus masuk setelah teman membeli produk dan pembayaran berhasil.</div>
        <div class="grid c2" style="margin-top:12px">
          <button class="btn sm" id="r-copy" style="background:rgba(255,255,255,.2)" type="button">SALIN</button>
          <button class="btn sm gold" id="r-share" type="button">BAGIKAN</button>
        </div>
      </div>
      <div class="grid c2" style="margin-top:12px">
        ${stat(verified.length, 'Verified Referral')}
        ${stat(nm(bonus), 'Bonus Diperoleh (poin)')}
      </div>
      <div class="hint-box small" style="margin-top:12px">Referral hanya <b>1 tingkat</b>. Anda tidak mendapat bonus dari referral milik orang yang Anda undang.</div>
      <div class="section-title">Daftar Referral</div>
      <div id="r-list"></div>`;
    const l = $('#r-list', view);
    if (!refs.length) l.innerHTML = '<div class="empty"><span class="e">👥</span>Belum ada referral</div>';
    refs.forEach(r => l.appendChild(el('div', 'row', `
      <div class="ic">${r.status === 'VERIFIED' ? '✅' : r.status === 'PENDING' ? '⏳' : '🚫'}</div>
      <div class="main"><div class="t">${esc(r.invitedName)}</div>
        <div class="s">${new Date(r.createdAt).toLocaleDateString('id-ID')} · ${r.qualifyingOrderId ? 'order memenuhi syarat' : 'menunggu pembelian'}</div></div>
      <div class="rt">${r.status === 'VERIFIED' ? '<span class="pill">VERIFIED</span>' : r.status === 'PENDING' ? '<span class="pill warn">PENDING</span>' : '<span class="pill red">BATAL</span>'}
        <div class="small muted">${r.bonusPoints ? '+' + nm(r.bonusPoints) : ''}</div></div>`)));
    $('#r-copy', view).onclick = () => copy(u.referralId);
    $('#r-share', view).onclick = () => shareReferral(u);
  };

  views.profil = (view, u) => {
    const refs = Store.myReferrals(u.userId);
    const orders = Store.userOrders(u.userId);
    const refLink = location.origin + location.pathname + '?ref=' + u.referralId;
    view.innerHTML = `
      <div class="card center">
        <div style="width:64px;height:64px;border-radius:50%;background:var(--green-soft);display:flex;align-items:center;justify-content:center;font-size:28px;margin:0 auto 10px">👤</div>
        <div style="font-weight:800;font-size:17px">${esc(u.name)}</div>
        <div class="small muted">${esc(u.email || u.phone)}</div>
        <div style="margin-top:8px">
          <span class="pill ${u.status === 'ACTIVE' ? '' : 'red'}">${esc(u.status)}</span>
          <span class="pill ${u.verified ? 'gold' : 'grey'}">${u.verified ? 'VERIFIED' : 'BELUM VERIFIED'}</span>
        </div>
      </div>
      <div class="grid c3">
        ${stat(nm(u.points), 'Poin')}
        ${stat(rp(Store.pointsToRupiah(u.points)), 'Saldo')}
        ${stat(orders.length, 'Pesanan')}
      </div>
      <div class="card" style="margin-top:12px">
        ${kv('Nama', esc(u.name))}
        ${kv('Nomor HP', esc(u.phone || '-'))}
        ${kv('Email', esc(u.email || '-'))}
        ${kv('Referral ID', '<b class="mono">' + esc(u.referralId) + '</b>')}
        ${kv('Status akun', esc(u.status))}
        ${kv('Terdaftar', new Date(u.createdAt).toLocaleDateString('id-ID'))}
      </div>
      <div class="section-title">Menu</div>
      <div class="row tap" id="pr-ref"><div class="ic">👥</div><div class="main"><div class="t">Referral Saya</div><div class="s">${refs.length} referral</div></div><span>›</span></div>
      <div class="row tap" id="pr-order"><div class="ic">🧾</div><div class="main"><div class="t">Riwayat Pesanan</div><div class="s">${orders.length} pesanan</div></div><span>›</span></div>
      <div class="row tap" id="pr-ledger"><div class="ic">⭐</div><div class="main"><div class="t">Riwayat Poin</div><div class="s">${Store.ledger(u.userId).length} mutasi</div></div><span>›</span></div>
      <div class="row tap" id="pr-wd"><div class="ic">💸</div><div class="main"><div class="t">Riwayat Withdrawal</div><div class="s">${Store.db.withdrawals.filter(w => w.userId === u.userId).length} pengajuan</div></div><span>›</span></div>
      <div class="row tap" id="pr-link"><div class="ic">🔗</div><div class="main"><div class="t">Salin Link Referral</div><div class="s">${esc(refLink)}</div></div><span>›</span></div>
      <div class="row tap" id="pr-share"><div class="ic">📤</div><div class="main"><div class="t">Bagikan undangan</div><div class="s">WhatsApp / media lain</div></div><span>›</span></div>
      <div class="section-title">Bantuan</div>
      <div class="card small muted">Syarat bonus referral &amp; withdrawal ditampilkan transparan pada halaman Tugas dan Saldo. Untuk kendala akun, hubungi admin melalui WhatsApp toko.</div>
      <button class="btn danger" id="pr-out" type="button">KELUAR</button>
      <div style="height:14px"></div>`;

    $('#pr-ref', view).onclick = () => renderTab('referral');
    $('#pr-order', view).onclick = () => renderTab('order');
    $('#pr-ledger', view).onclick = () => renderTab('tugas');
    $('#pr-wd', view).onclick = () => renderTab('saldo');
    $('#pr-link', view).onclick = () => copy(refLink);
    $('#pr-share', view).onclick = () => shareReferral(u);
    $('#pr-out', view).onclick = () => { Store.logout(); showAuth(); };
  };

  /* ---------------- helpers ---------------- */
  function copy(text) {
    const done = () => toast('Disalin: ' + text);
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done).catch(() => fallbackCopy(text, done));
    else fallbackCopy(text, done);
  }
  function fallbackCopy(text, done) {
    const ta = document.createElement('textarea');
    ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); done(); } catch (e) { toast(text); }
    ta.remove();
  }
  function shareReferral(u) {
    const link = location.origin + location.pathname + '?ref=' + u.referralId;
    const s = Store.db.settings;
    const text = 'Ayo bergabung di HERBALINDO\n\n\n'
      + 'Gunakan Referral ID: ' + u.referralId + '\n'
      + 'Bonus ' + nm(s.referralBonus) + ' poin\n'
      + 'Komisi dan saldo dapat ditarik setiap hari\n\n\n'
      + 'Herbal diet alami, poin harian, dan saldo rupiah.\n\n'
      + 'Unduh di Play Store:\n'
      + 'https://play.google.com/store/apps/details?id=com.altomedia.herbalindo\n\n'
      + link;
    if (navigator.share) navigator.share({ title: 'HERBALINDO', text }).catch(() => { });
    else copy(text);
  }

  /* ---------------- Deep link ?ref= ---------------- */
  function applyDeepLink() {
    const ref = new URLSearchParams(location.search).get('ref');
    if (ref && /^\d{6}$/.test(ref)) {
      const inp = $('#reg-ref');
      if (inp) { inp.value = ref; $('#tab-register').click(); toast('Referral ID terisi otomatis: ' + ref); }
    }
  }

  /* ---------------- Telegram-like back button (Android) ---------------- */
  function initBackButton() {
    window.addEventListener('popstate', () => {
      if ($('#overlay')) { closeSheet(); history.pushState(null, '', location.href); return; }
      if (state.tab !== 'home' && Store.currentUser() && Store.currentUser().role === 'MEMBER') {
        renderTab('home'); history.pushState(null, '', location.href);
      }
    });
    history.pushState(null, '', location.href);
  }

  /* ---------------- Boot ---------------- */
  function boot() {
    Store.load();
    initAuth();
    initTabs();
    initPullToRefresh();
    initBackButton();
    $('#admob-x').onclick = () => AdMob.hide();
    $('#admob').addEventListener('click', (e) => {
      if (e.target.id === 'admob-x') return;
      if (!Store.currentUser()) return;
      if (StorizeRewardMode()) AdMob.rewardUser();
      else renderTab('produk');
    });
    function StorizeRewardMode() { return $('#admob').classList.contains('reward'); }

    const u = Store.currentUser();
    applyDeepLink();
    if (u) enterApp(u); else showAuth();

    // pre-warm QR lib
    if (window.QRCode) { try { const d = document.createElement('div'); new QRCode(d, { text: 'warmup', width: 40, height: 40 }); } catch (e) { } }
  }

  document.addEventListener('DOMContentLoaded', boot);
})();