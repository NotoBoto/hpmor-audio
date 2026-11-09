'use strict';
/* Unison — library / book / reader PWA. No build step, no dependencies. */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const LS = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};
const DATA_CACHE = 'unison-data-v1';
const SPEEDS = [0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2];
const audio = $('#audio');
const pad = (n) => String(n).padStart(3, '0');
const fmt = (s) => { s = Math.max(0, Math.floor(s)); const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, x = s % 60;
  return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0'); };
const mb = (b) => (b / 1048576).toFixed(b > 104857600 ? 0 : 1) + ' МБ';
const hours = (s) => s >= 3600 ? Math.round(s / 3600) + ' ч' : Math.round(s / 60) + ' мин';
const plural = (n, a, b, c) => { const m = n % 100, k = n % 10; return m > 10 && m < 20 ? c : k === 1 ? a : k > 1 && k < 5 ? b : c; };

// ---------- icons (Material Symbols paths) ----------
const P = {
  library: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12zM10 9h8v2h-8zm0 3h4v2h-4zm0-6h8v2h-8z',
  updates: 'M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
  history: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
  more: 'M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  vert: 'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  download: 'M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z',
  filter: 'M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z',
  play: 'M8 5v14l11-7z', pause: 'M6 19h4V5H6v14zm8-14v14h4V5h-4z',
  close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  selall: 'M3 5h2V3c-1.1 0-2 .9-2 2zm0 8h2v-2H3v2zm4 8h2v-2H7v2zM3 9h2V7H3v2zm10-6h-2v2h2V3zm6 0v2h2c0-1.1-.9-2-2-2zM5 21v-2H3c0 1.1.9 2 2 2zm-2-4h2v-2H3v2zM9 3H7v2h2V3zm2 18h2v-2h-2v2zm8-8h2v-2h-2v2zm0 8c1.1 0 2-.9 2-2h-2v2zm0-12h2V7h-2v2zm0 8h2v-2h-2v2zm-4 4h2v-2h-2v2zm0-16h2V3h-2v2zM7 17h10V7H7v10zm2-8h6v6H9V9z',
  invert: 'M16 17.01V10h-2v7.01h-3L15 21l4-3.99h-3zM9 3L5 6.99h3V14h2V6.99h3L9 3z',
  read: 'M18 7l-1.41-1.41-6.34 6.34 1.41 1.41L18 7zm4.24-1.41L11.66 16.17 7.48 12l-1.41 1.41L11.66 19l12-12-1.42-1.41zM.41 13.41L6 19l1.41-1.41L1.83 12 .41 13.41z',
  unread: 'M1.79 12l5.58 5.59L5.96 19 .37 13.41 1.79 12zm.45-7.78L12.9 14.89l-1.28 1.28L7.44 12l-1.41 1.41L11.62 19l2.69-2.69 4.89 4.89 1.41-1.41L3.65 2.81 2.24 4.22zm14.9 9.27L23.62 7 22.2 5.59l-6.48 6.48 1.42 1.42zM17.96 7l-1.41-1.41-3.65 3.66 1.41 1.41L17.96 7z',
  prev: 'M14 10H2v2h12v-2zm0-4H2v2h12V6zM2 16h8v-2H2v2zm19.5-4.5L23 13l-6.99 7-4.51-4.5L13 14l3.01 3 5.49-5.5z',
  del: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  info: 'M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z',
  refresh: 'M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  code: 'M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z',
  check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  arrow: 'M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z',
  expand: 'M16.59 8.59L12 13.17 7.41 8.59 6 10l6 6 6-6z', collapse: 'M12 8l-6 6 1.41 1.41L12 10.83l4.59 4.58L18 14z',
  sun: 'M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1z',
  full: 'M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z',
  person: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  translate: 'M12.87 15.07l-2.54-2.51.03-.03A17.52 17.52 0 0 0 14.07 6H17V4h-7V2H8v2H1v2h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z',
  time: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
  sort: 'M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z', title: 'M5 4v3h5.5v12h3V7H19V4z',
  palette: 'M12 3a9 9 0 0 0 0 18c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z',
  speed: 'M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z',
};
const ic = (name, extra = '') => `<svg class="i" viewBox="0 0 24 24" ${extra}><path d="${P[name]}"/></svg>`;

// ---------- persistent state ----------
const settings = Object.assign({ theme: 'dark', fs: 19, speed: 1, wake: true, sortDesc: true, fullTitle: true }, LS.get('settings', {}));
const saveSettings = () => LS.set('settings', settings);
const bookState = {};   // id -> {pos:{}, done:{}, dlv:{}, last:null}
function bs(id) { return bookState[id] || (bookState[id] = Object.assign({ pos: {}, done: {}, dlv: {}, last: null }, LS.get('book:' + id, {}))); }
const saveBs = (id) => LS.set('book:' + id, bs(id));
let hist = LS.get('history', []);          // [{b, n, ts}] newest first, one per chapter
const saveHist = () => LS.set('history', hist.slice(0, 300));

(function migrateOld() {  // keys from the single-book version of the site
  if (LS.get('migrated', false)) return;
  try {
    const s = bs('hpmor');
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i), m = /^pos(\d+)$/.exec(k);
      if (m) s.pos[m[1]] = LS.get(k, 0);
    }
    if (LS.get('last', null)) s.last = LS.get('last', null);
    ['speed', 'fs', 'wake'].forEach((k) => { const v = LS.get(k, null); if (v != null) settings[k] = v; });
    saveBs('hpmor'); saveSettings();
  } catch {}
  LS.set('migrated', true);
})();

function applyTheme() {
  const t = settings.theme === 'system' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : settings.theme;
  document.documentElement.dataset.theme = t;
  $('meta[name=theme-color]').content = t === 'light' ? '#fdf7fa' : '#1a1520';
  document.documentElement.style.setProperty('--fs', settings.fs + 'px');
}
matchMedia('(prefers-color-scheme: light)').addEventListener?.('change', applyTheme);

// ---------- data ----------
let books = [];               // books.json + .chapters
const bookById = (id) => books.find((b) => b.id === id);
const chUrl = (b, n, ext) => `${b.data}ch${pad(n)}.${ext}`;

async function fetchJsonFresh(url) {
  try {
    const r = await fetch(url, { cache: 'no-store' });
    if (!r.ok) throw 0;
    const j = await r.json();
    try { (await caches.open(DATA_CACHE)).put(url, new Response(JSON.stringify(j))); } catch {}
    return j;
  } catch {
    try { return await (await (await caches.open(DATA_CACHE)).match(url)).json(); } catch { return null; }
  }
}
async function loadData() {
  books = (await fetchJsonFresh('books.json')) || LS.get('booksCache', []);
  LS.set('booksCache', books);
  await Promise.all(books.map(async (b) => {
    b.chapters = (await fetchJsonFresh(b.data + 'index.json')) || [];
    b.chapters.sort((x, y) => x.n - y.n);
  }));
}

// ---------- downloads ----------
const downloaded = new Set();      // "book:n"
const dlJobs = new Map();          // "book:n" -> {state:'queued'|'run', p, ctrl}
const key = (b, n) => b + ':' + n;
async function scanDownloads() {
  try {
    const c = await caches.open(DATA_CACHE);
    const names = new Set((await c.keys()).map((r) => new URL(r.url).pathname));
    for (const b of books) for (const ch of b.chapters) {
      const base = new URL(chUrl(b, ch.n, ''), location.href).pathname;
      if (names.has(base + 'ogg') && names.has(base + 'json')) downloaded.add(key(b.id, ch.n));
    }
  } catch {}
}
async function migrateOldCache() {
  try {
    if (!(await caches.has('hpmor-data-v1'))) return;
    const old = await caches.open('hpmor-data-v1'), neu = await caches.open(DATA_CACHE);
    for (const req of await old.keys()) { const r = await old.match(req); if (r) await neu.put(req, r); }
    await caches.delete('hpmor-data-v1');
  } catch {}
}
function dlState(b, n) {
  const k = key(b, n), j = dlJobs.get(k);
  if (j) return j.state === 'run' ? { s: 'run', p: j.p } : { s: 'queued' };
  if (downloaded.has(k)) {
    const ch = bookById(b)?.chapters.find((c) => c.n === n), v = bs(b).dlv[n];
    return { s: ch && ch.v && v && v !== ch.v ? 'old' : 'done' };
  }
  return { s: 'none' };
}
function dlIcon(st) {
  const C = 2 * Math.PI * 10;
  if (st.s === 'none') return `<svg viewBox="0 0 24 24"><circle class="ring" cx="12" cy="12" r="10"/><path fill="currentColor" d="M11 7h2v6.2l2.3-2.3 1.4 1.4L12 17l-4.7-4.7 1.4-1.4 2.3 2.3z"/></svg>`;
  if (st.s === 'queued') return `<svg viewBox="0 0 24 24"><circle class="ring" cx="12" cy="12" r="10" stroke-dasharray="4 3"/></svg>`;
  if (st.s === 'run') return `<svg viewBox="0 0 24 24"><circle class="ring" cx="12" cy="12" r="10"/><circle class="prog" cx="12" cy="12" r="10" stroke-dasharray="${C}" stroke-dashoffset="${C * (1 - (st.p || 0))}"/><rect x="9" y="9" width="6" height="6" rx="1" fill="currentColor"/></svg>`;
  if (st.s === 'old') return `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill="var(--accent)"/><path fill="#fff" transform="translate(4.8 4.8) scale(.6)" d="${P.refresh}"/></svg>`;
  return `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill="var(--dl-done-bg)"/><path fill="var(--dl-done-fg)" transform="translate(4.8 4.8) scale(.6)" d="${P.check}"/></svg>`;
}
function paintDl(b, n) {
  const st = dlState(b, n);
  $$(`[data-dl="${key(b, n)}"]`).forEach((el) => { el.className = 'dl ' + st.s; el.innerHTML = dlIcon(st); });
}
function enqueue(b, ns) {
  let added = 0;
  for (const n of ns) {
    const k = key(b, n), st = dlState(b, n).s;
    if (dlJobs.has(k) || st === 'done') continue;
    dlJobs.set(k, { state: 'queued', b, n, p: 0 }); added++; paintDl(b, n);
  }
  try { navigator.storage?.persist?.(); } catch {}
  pump();
  return added;
}
function pump() {
  const running = [...dlJobs.values()].filter((j) => j.state === 'run').length;
  const next = [...dlJobs.values()].filter((j) => j.state === 'queued').slice(0, Math.max(0, 2 - running));
  next.forEach(runJob);
  refreshDownloadsPage();
}
async function runJob(j) {
  j.state = 'run'; j.ctrl = new AbortController(); paintDl(j.b, j.n);
  const b = bookById(j.b), meta = b.chapters.find((c) => c.n === j.n);
  try {
    const c = await caches.open(DATA_CACHE);
    const rj = await fetch(chUrl(b, j.n, 'json'), { cache: 'reload', signal: j.ctrl.signal });
    if (!rj.ok) throw new Error('HTTP ' + rj.status);
    const jsonBlob = await rj.blob();
    const r = await fetch(chUrl(b, j.n, 'ogg'), { cache: 'reload', signal: j.ctrl.signal });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const total = +r.headers.get('content-length') || (meta.bytes - jsonBlob.size) || 1;
    const rd = r.body.getReader(), parts = []; let got = 0, lastPaint = 0;
    for (;;) {
      const { done, value } = await rd.read(); if (done) break;
      parts.push(value); got += value.length; j.p = Math.min(1, got / total);
      if (Date.now() - lastPaint > 150) { lastPaint = Date.now(); paintDl(j.b, j.n); refreshDownloadsPage(); }
    }
    await c.put(chUrl(b, j.n, 'json'), new Response(jsonBlob, { headers: { 'content-type': 'application/json' } }));
    await c.put(chUrl(b, j.n, 'ogg'), new Response(new Blob(parts, { type: 'audio/ogg' }), { headers: { 'content-type': 'audio/ogg' } }));
    downloaded.add(key(j.b, j.n)); bs(j.b).dlv[j.n] = meta.v || '1'; saveBs(j.b);
  } catch (e) {
    if (e.name !== 'AbortError') toast(`Глава ${j.n}: не удалось скачать`);
  }
  dlJobs.delete(key(j.b, j.n)); paintDl(j.b, j.n); refreshLibraryBadges(); pump();
}
function cancelDl(b, n) { const j = dlJobs.get(key(b, n)); if (!j) return; j.ctrl?.abort(); dlJobs.delete(key(b, n)); paintDl(b, n); pump(); }
async function removeDl(b, ns) {
  const c = await caches.open(DATA_CACHE), book = bookById(b);
  for (const n of ns) {
    cancelDl(b, n);
    await c.delete(chUrl(book, n, 'json')); await c.delete(chUrl(book, n, 'ogg'));
    downloaded.delete(key(b, n)); delete bs(b).dlv[n]; paintDl(b, n);
  }
  saveBs(b); refreshLibraryBadges(); refreshDownloadsPage();
}
function outdated() {
  const out = [];
  for (const b of books) for (const ch of b.chapters) if (dlState(b.id, ch.n).s === 'old') out.push([b.id, ch.n]);
  return out;
}
async function updateOutdated() {
  const list = outdated();
  if (!list.length) { toast('Все скачанные главы актуальны'); return; }
  for (const [b, n] of list) { downloaded.delete(key(b, n)); }
  const by = {}; list.forEach(([b, n]) => (by[b] = by[b] || []).push(n));
  Object.entries(by).forEach(([b, ns]) => enqueue(b, ns));
  toast(`Обновляется ${list.length} ${plural(list.length, 'глава', 'главы', 'глав')}`);
}

// ---------- progress helpers ----------
function progress(b, ch) {
  const s = bs(b);
  if (s.done[ch.n]) return 1;
  return Math.min(1, (s.pos[ch.n] || 0) / ch.duration);
}
function setDone(b, ns, val) {
  const s = bs(b);
  ns.forEach((n) => { if (val) s.done[n] = true; else { delete s.done[n]; delete s.pos[n]; } });
  saveBs(b);
}
const chName = (ch) => settings.fullTitle ? ch.title : `Глава ${ch.n}`;
function subline(b, ch) {
  const p = progress(b, ch), parts = [fmt(ch.duration)];
  if (p >= 1) parts.push('прослушано');
  else if (p > 0.005) parts.push(`прослушано ${Math.round(p * 100)}% · осталось ${hours(ch.duration * (1 - p))}`);
  return parts.join(' • ');
}

// ---------- UI primitives ----------
function toast(msg, ms = 2600) { const t = $('#toast'); t.textContent = msg; t.hidden = false; clearTimeout(toast.h); toast.h = setTimeout(() => (t.hidden = true), ms); }
function closeOverlays() { ['#menu', '#sheet', '#dialog', '#scrim'].forEach((s) => ($(s).hidden = true)); $('#scrim').className = ''; }
$('#scrim').onclick = closeOverlays;
function menu(anchor, items) {
  const m = $('#menu'); m.innerHTML = '';
  items.forEach(([label, fn, disabled]) => {
    const btn = document.createElement('button'); btn.textContent = label; btn.disabled = !!disabled;
    btn.onclick = () => { closeOverlays(); fn(); }; m.appendChild(btn);
  });
  $('#scrim').hidden = false; $('#scrim').className = 'light'; m.hidden = false;
  const r = anchor.getBoundingClientRect(), w = m.offsetWidth, hgt = m.offsetHeight;
  m.style.left = Math.max(8, Math.min(innerWidth - w - 8, r.right - w)) + 'px';
  m.style.top = Math.max(8, Math.min(innerHeight - hgt - 8, r.top + 4)) + 'px';
}
function sheet(html, bind) {
  const s = $('#sheet'); s.innerHTML = '<div class="grab"></div>' + html; s.hidden = false; $('#scrim').hidden = false; $('#scrim').className = '';
  bind?.(s);
}
function confirmDlg(title, text, okLabel, fn) {
  const d = $('#dialog');
  d.innerHTML = `<h3>${esc(title)}</h3><p>${esc(text)}</p><div class="acts"><button data-x>Отмена</button><button data-ok>${esc(okLabel)}</button></div>`;
  d.hidden = false; $('#scrim').hidden = false; $('#scrim').className = '';
  $('[data-x]', d).onclick = closeOverlays; $('[data-ok]', d).onclick = () => { closeOverlays(); fn(); };
}

// ---------- routing ----------
const TABS = ['library', 'updates', 'history', 'more'];
let route = {};
function go(hash, replace) { if (replace) history.replaceState(null, '', hash); else history.pushState(null, '', hash); render(); }
function parseRoute() {
  const h = location.hash.replace(/^#\/?/, '');
  const old = /^(\d+)$/.exec(h);                       // links from the old site: #65
  if (old) return { v: 'read', b: 'hpmor', n: +old[1] };
  const p = h.split('/').filter(Boolean);
  if (p[0] === 'book' && p[1]) return { v: 'book', b: p[1] };
  if (p[0] === 'read' && p[1] && p[2]) return { v: 'read', b: p[1], n: +p[2] };
  if (p[0] === 'more' && p[1]) return { v: 'page', page: p[1] };
  return { v: TABS.includes(p[0]) ? p[0] : 'library' };
}
function render() {
  const prev = route; route = parseRoute();
  closeOverlays();
  if (prev.v === 'read' && route.v !== 'read') leaveReader();
  if (route.v !== 'book') exitSelection(true);
  $$('.view').forEach((v) => (v.hidden = true));
  document.body.classList.toggle('reading', route.v === 'read');
  $('#player').hidden = route.v !== 'read';
  $('#fab').hidden = route.v !== 'book';
  const tab = route.v === 'page' ? 'more' : route.v === 'book' ? 'library' : route.v;
  $$('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
  const v = { library: renderLibrary, updates: renderUpdates, history: renderHistory, more: renderMore, page: renderPage, book: renderBook, read: renderReader }[route.v];
  v();
  if (route.v !== 'read') window.scrollTo(0, scrollMemo[location.hash] || 0);
}
const scrollMemo = {};
addEventListener('scroll', () => { if (route.v && route.v !== 'read') scrollMemo[location.hash] = scrollY; onScrollBar(); }, { passive: true });
addEventListener('popstate', render);
$$('#nav button').forEach((b) => (b.onclick = () => { const h = '#/' + b.dataset.tab; if (location.hash !== h) go(h); else window.scrollTo({ top: 0, behavior: 'smooth' }); }));
$$('#nav i').forEach((i, k) => (i.innerHTML = ic(TABS[k])));

function bar(title, { back = false, clear = false, actions = '' } = {}) {
  return `<header class="bar ${clear ? 'clear' : ''}">${back ? `<button class="ib" data-back aria-label="Назад">${ic('back')}</button>` : ''}
    <h1 class="${back ? 'bar-title' : 'big-title'}">${esc(title)}</h1>${actions}</header>`;
}
function bindBack(root, fallback) { const b = $('[data-back]', root); if (b) b.onclick = () => (history.length > 1 && appNav > 0 ? history.back() : go(fallback, true)); }
let appNav = 0; addEventListener('popstate', () => appNav--); const _push = history.pushState.bind(history); history.pushState = (...a) => { appNav++; _push(...a); };
function onScrollBar() { $$('.bar.clear').forEach((b) => b.classList.toggle('scrolled', scrollY > 120)); }

// ---------- Library ----------
function renderLibrary() {
  const v = $('#v-library');
  v.innerHTML = bar('Библиотека', { actions: `<button class="ib" id="libMenu" aria-label="Меню">${ic('vert')}</button>` }) +
    (books.length ? `<div class="grid">${books.map((b) => `
      <button class="tile" data-book="${esc(b.id)}"><img src="${esc(b.coverSm || b.cover)}" alt="" loading="lazy">
        <div class="badges" data-badges="${esc(b.id)}"></div><div class="cap"><span>${esc(b.title)}</span></div></button>`).join('')}</div>`
      : `<div class="empty"><span class="big">📚</span>Библиотека пуста.<br>Нужен интернет, чтобы загрузить список книг.</div>`);
  v.hidden = false;
  $$('.tile', v).forEach((t) => (t.onclick = () => go('#/book/' + t.dataset.book)));
  $('#libMenu').onclick = (e) => menu(e.currentTarget, [['Обновить скачанные', updateOutdated], ['Настройки', () => go('#/more/settings')]]);
  refreshLibraryBadges();
}
function refreshLibraryBadges() {
  $$('[data-badges]').forEach((el) => {
    const b = bookById(el.dataset.badges); if (!b) return;
    const unread = b.chapters.filter((c) => !bs(b.id).done[c.n]).length;
    const dl = b.chapters.filter((c) => downloaded.has(key(b.id, c.n))).length;
    el.innerHTML = (unread ? `<b class="unread">${unread}</b>` : '') + (dl ? `<b class="dled">${dl}</b>` : '');
  });
}

// ---------- Book page ----------
let sel = new Set(), selAnchor = null, lpFired = false;
function bookChapters(b) { const list = [...b.chapters]; return settings.sortDesc ? list.reverse() : list; }
function continueTarget(b) {
  const s = bs(b.id);
  let ch = b.chapters.find((c) => c.n === s.last);
  if (ch && progress(b.id, ch) >= 1) ch = b.chapters.find((c) => c.n > ch.n) || ch;
  if (!ch) ch = b.chapters.find((c) => !s.done[c.n]) || b.chapters[0];
  return ch;
}
function renderBook() {
  const b = bookById(route.b), v = $('#v-book');
  if (!b) { go('#/library', true); return; }
  const total = b.chapters.reduce((a, c) => a + c.duration, 0);
  v.innerHTML = `<header class="bar clear" id="bookBar">
      <button class="ib" data-back aria-label="Назад">${ic('back')}</button><h1 class="bar-title">${esc(b.title)}</h1>
      <button class="ib" id="bDl" aria-label="Скачать">${ic('download')}</button>
      <button class="ib" id="bFilter" aria-label="Сортировка и отображение">${ic('filter')}</button>
      <button class="ib" id="bMenu" aria-label="Меню">${ic('vert')}</button></header>
    <div class="hero" style="--cover:url('${esc(b.cover)}')"><img src="${esc(b.cover)}" alt="Обложка">
      <div><h1>${esc(b.title)}</h1>
        <div class="meta">${ic('person', 'style="width:16px;height:16px"')}${esc(b.author)}</div>
        <div class="meta">${ic('translate', 'style="width:16px;height:16px"')}${esc(b.translator || '')}</div>
        <div class="meta">${ic('time', 'style="width:16px;height:16px"')}${hours(total)} · озвучено ${b.chapters.length}${b.total ? ' из ' + b.total : ''}</div></div></div>
    <div class="desc clamp" id="desc"><p>${esc(b.description || '')}</p><button class="more" aria-label="Развернуть">${ic('expand')}</button></div>
    <div class="chips">${(b.tags || []).map((t) => `<span>${esc(t)}</span>`).join('')}</div>
    <div class="count">${b.chapters.length} ${plural(b.chapters.length, 'глава', 'главы', 'глав')}<small id="dlSum"></small></div>
    <ul class="chs" id="chs"></ul>`;
  v.hidden = false;
  bindBack(v, '#/library');
  const desc = $('#desc');
  $('.more', desc).onclick = () => { desc.classList.toggle('clamp'); $('.more', desc).innerHTML = ic(desc.classList.contains('clamp') ? 'expand' : 'collapse'); };
  $('#bFilter').onclick = () => filterSheet(b);
  $('#bDl').onclick = (e) => {
    const unread = b.chapters.filter((c) => !bs(b.id).done[c.n]);
    const from = continueTarget(b), after = b.chapters.filter((c) => c.n >= from.n && !downloaded.has(key(b.id, c.n)));
    const q = (list) => { const k = enqueue(b.id, list.map((c) => c.n)); toast(k ? `В очереди: ${k} ${plural(k, 'глава', 'главы', 'глав')}` : 'Уже скачано'); };
    menu(e.currentTarget, [['Следующую главу', () => q(after.slice(0, 1))], ['Следующие 5', () => q(after.slice(0, 5))], ['Следующие 10', () => q(after.slice(0, 10))],
      ['Непрослушанные', () => q(unread)], ['Все', () => q(b.chapters)]]);
  };
  $('#bMenu').onclick = (e) => menu(e.currentTarget, [
    ['Удалить все скачанные', () => confirmDlg('Удалить скачанные?', `Все скачанные главы книги «${b.short || b.title}» будут удалены с телефона. Прогресс прослушивания сохранится.`, 'Удалить',
      () => removeDl(b.id, b.chapters.map((c) => c.n)).then(() => { toast('Удалено'); renderChapterList(b); }))],
    [`Обновить скачанные (${outdated().filter(([x]) => x === b.id).length})`, updateOutdated],
    ['Настройки', () => go('#/more/settings')], ['О проекте', () => go('#/more/about')]]);
  renderChapterList(b);
  paintFab(b);
  onScrollBar();
}
function renderChapterList(b) {
  const ul = $('#chs'); if (!ul) return;
  ul.innerHTML = bookChapters(b).map((c) => chRow(b, c)).join('');
  const dlc = b.chapters.filter((c) => downloaded.has(key(b.id, c.n)));
  $('#dlSum').textContent = dlc.length ? `скачано ${dlc.length} · ${mb(dlc.reduce((a, c) => a + c.bytes, 0))}` : '';
  bindChapterRows(ul, b);
}
function chRow(b, c, { thumb = false, sub = null } = {}) {
  const read = progress(b.id, c) >= 1, k = key(b.id, c.n), st = dlState(b.id, c.n);
  return `<li class="ch ${read ? 'read' : ''} ${sel.has(c.n) && route.v === 'book' ? 'sel' : ''}" data-b="${esc(b.id)}" data-n="${c.n}">
    ${thumb ? `<img class="thumb" src="${esc(b.coverSm || b.cover)}" alt="">` : ''}
    <div class="main"><div class="t1">${read ? '' : '<i class="dot"></i>'}<span>${esc(chName(c))}</span></div>
    <div class="t2">${esc(sub ?? subline(b.id, c))}</div></div>
    <button class="dl ${st.s}" data-dl="${k}" aria-label="Загрузка">${dlIcon(st)}</button></li>`;
}
function bindChapterRows(ul, b0, selectable = true) {
  ul.onclick = (e) => {
    const li = e.target.closest('.ch'); if (!li) return;
    const b = li.dataset.b, n = +li.dataset.n;
    if (lpFired) { lpFired = false; return; }
    if (e.target.closest('.dl')) { dlButton(e.target.closest('.dl'), b, n); return; }
    if (selectable && sel.size) { toggleSel(n); return; }
    go(`#/read/${b}/${n}`);
  };
  if (!selectable) return;
  let timer = 0, sx = 0, sy = 0;
  const start = (e) => { const li = e.target.closest('.ch'); if (!li || e.target.closest('.dl')) return;
    sx = e.clientX; sy = e.clientY; clearTimeout(timer);
    timer = setTimeout(() => { lpFired = true; longPress(+li.dataset.n); navigator.vibrate?.(20); }, 450); };
  ul.onpointerdown = start;
  ul.onpointermove = (e) => { if (Math.hypot(e.clientX - sx, e.clientY - sy) > 10) clearTimeout(timer); };
  ul.onpointerup = ul.onpointercancel = () => clearTimeout(timer);
  ul.oncontextmenu = (e) => { e.preventDefault(); };
}
function dlButton(btn, b, n) {
  const st = dlState(b, n).s;
  if (st === 'none') enqueue(b, [n]);
  else if (st === 'queued' || st === 'run') cancelDl(b, n);
  else menu(btn, [...(st === 'old' ? [['Скачать новую версию', () => { downloaded.delete(key(b, n)); enqueue(b, [n]); }]] : []),
    ['Удалить с телефона', () => removeDl(b, [n]).then(() => { if (route.v === 'book') renderChapterList(bookById(b)); })]]);
}
function longPress(n) {
  const b = bookById(route.b);
  if (sel.size && selAnchor != null && !sel.has(n)) {         // range: from the last selected to this one
    const order = bookChapters(b).map((c) => c.n), i = order.indexOf(selAnchor), j = order.indexOf(n);
    order.slice(Math.min(i, j), Math.max(i, j) + 1).forEach((x) => sel.add(x));
    selAnchor = n;
  } else toggleSel(n);
  paintSelection();
}
function toggleSel(n) { if (sel.has(n)) sel.delete(n); else { sel.add(n); selAnchor = n; } paintSelection(); }
function paintSelection() {
  const b = bookById(route.b); if (!b) return;
  $$('#chs .ch').forEach((li) => li.classList.toggle('sel', sel.has(+li.dataset.n)));
  const on = sel.size > 0;
  document.body.classList.toggle('selecting', on);
  const barEl = $('#bookBar');
  if (on) {
    barEl.className = 'bar solid'; barEl.innerHTML = `<button class="ib" id="sx" aria-label="Отменить выбор">${ic('close')}</button>
      <h1 class="bar-title">${sel.size}</h1><button class="ib" id="sAll" aria-label="Выбрать все">${ic('selall')}</button>
      <button class="ib" id="sInv" aria-label="Инвертировать выбор">${ic('invert')}</button>`;
    $('#sx').onclick = () => exitSelection();
    $('#sAll').onclick = () => { b.chapters.forEach((c) => sel.add(c.n)); paintSelection(); };
    $('#sInv').onclick = () => { b.chapters.forEach((c) => (sel.has(c.n) ? sel.delete(c.n) : sel.add(c.n))); paintSelection(); };
    const s = $('#selbar'); s.hidden = false;
    s.innerHTML = [['read', 'Прослушано'], ['unread', 'Не прослушано'], ['prev', 'Предыдущие'], ['download', 'Скачать'], ['del', 'Удалить']]
      .map(([i, t]) => `<button data-a="${i}">${ic(i)}<span>${t}</span></button>`).join('');
    $$('button', s).forEach((btn) => (btn.onclick = () => selAction(b, btn.dataset.a)));
  } else if (barEl && barEl.querySelector('#sx')) { renderBook(); }
  if (!on) $('#selbar').hidden = true;
}
function exitSelection(silent) {
  const had = sel.size; sel = new Set(); selAnchor = null; $('#selbar').hidden = true; document.body.classList.remove('selecting');
  if (had && !silent && route.v === 'book') renderBook();
}
function selAction(b, a) {
  const ns = [...sel];
  if (a === 'read') setDone(b.id, ns, true);
  if (a === 'unread') setDone(b.id, ns, false);
  if (a === 'prev') { const mx = Math.max(...ns); setDone(b.id, b.chapters.filter((c) => c.n < mx).map((c) => c.n), true); toast(`Главы до ${mx} отмечены прослушанными`); }
  if (a === 'download') { const k = enqueue(b.id, ns); toast(k ? `В очереди: ${k}` : 'Уже скачано'); }
  if (a === 'del') { removeDl(b.id, ns); }
  exitSelection();
}
function filterSheet(b) {
  sheet(`<h3>Сортировка</h3>
    <button class="opt" data-sort="desc"><span class="ico">${settings.sortDesc ? ic('check') : ''}</span>Сначала последние</button>
    <button class="opt" data-sort="asc"><span class="ico">${!settings.sortDesc ? ic('check') : ''}</span>По порядку, с первой главы</button>
    <h3>Отображение</h3>
    <button class="opt" data-t="full"><span class="ico">${settings.fullTitle ? ic('check') : ''}</span>Название главы</button>
    <button class="opt" data-t="num"><span class="ico">${!settings.fullTitle ? ic('check') : ''}</span>«Глава N»</button>`, (s) => {
    $$('[data-sort]', s).forEach((x) => (x.onclick = () => { settings.sortDesc = x.dataset.sort === 'desc'; saveSettings(); closeOverlays(); renderChapterList(b); }));
    $$('[data-t]', s).forEach((x) => (x.onclick = () => { settings.fullTitle = x.dataset.t === 'full'; saveSettings(); closeOverlays(); renderChapterList(b); }));
  });
}
function paintFab(b) {
  const f = $('#fab'), ch = continueTarget(b); if (!ch) { f.hidden = true; return; }
  const started = Object.keys(bs(b.id).pos).length || Object.keys(bs(b.id).done).length;
  f.querySelector('.fab-i').innerHTML = ic('play');
  f.querySelector('.fab-t').textContent = started ? 'Продолжить' : 'Начать';
  f.onclick = () => go(`#/read/${b.id}/${ch.n}`);
}
let lastY = 0;
addEventListener('scroll', () => { $('#fab').classList.toggle('mini', scrollY > lastY && scrollY > 200); lastY = scrollY; }, { passive: true });

// ---------- Updates ----------
function dayLabel(d) {
  const t = new Date(); t.setHours(0, 0, 0, 0);
  const x = new Date(d); x.setHours(0, 0, 0, 0);
  const diff = Math.round((t - x) / 864e5);
  if (diff === 0) return 'Сегодня'; if (diff === 1) return 'Вчера';
  return x.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: x.getFullYear() === t.getFullYear() ? undefined : 'numeric' });
}
function renderUpdates() {
  const v = $('#v-updates');
  const all = [];
  books.forEach((b) => b.chapters.forEach((c) => c.date && all.push({ b, c })));
  all.sort((x, y) => (y.c.date > x.c.date ? 1 : y.c.date < x.c.date ? -1 : y.c.n - x.c.n));
  let html = bar('Обновления', { actions: `<button class="ib" id="upRefresh" aria-label="Обновить">${ic('refresh')}</button>` });
  if (!all.length) html += `<div class="empty"><span class="big">🔔</span>Пока нет обновлений.</div>`;
  let lastDay = '';
  html += '<ul class="chs" id="upList">';
  all.slice(0, 400).forEach(({ b, c }) => {
    const d = dayLabel(c.date);
    if (d !== lastDay) { html += `<li class="grp">${d}</li>`; lastDay = d; }
    html += chRow(b, c, { thumb: true, sub: `${b.short || b.title} • ${new Date(c.date).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}` });
  });
  v.innerHTML = html + '</ul>'; v.hidden = false;
  bindChapterRows($('#upList'), null, false);
  $('#upRefresh').onclick = async () => { toast('Проверяю…'); await loadData(); await scanDownloads(); renderUpdates(); paintBadge(); toast('Готово'); };
  LS.set('seenUpdates', all[0]?.c.date || ''); paintBadge();
}
function paintBadge() {
  const seen = LS.get('seenUpdates', '');
  let n = 0; books.forEach((b) => b.chapters.forEach((c) => { if (c.date && c.date > seen) n++; }));
  const el = $('#nav [data-tab=updates] .badge'); el.hidden = !n || !seen; el.textContent = n > 99 ? '99+' : n;
}

// ---------- History ----------
function renderHistory() {
  const v = $('#v-history');
  const items = hist.map((h) => ({ ...h, b: bookById(h.b), c: bookById(h.b)?.chapters.find((c) => c.n === h.n) })).filter((x) => x.b && x.c);
  let html = bar('История', { actions: items.length ? `<button class="ib" id="hClear" aria-label="Очистить историю">${ic('del')}</button>` : '' });
  if (!items.length) html += `<div class="empty"><span class="big">🕮</span>Здесь появятся главы, которые вы слушали.</div>`;
  let lastDay = '';
  html += '<ul class="chs" id="hList">';
  items.forEach(({ b, c, ts }) => {
    const d = dayLabel(ts); if (d !== lastDay) { html += `<li class="grp">${d}</li>`; lastDay = d; }
    const p = Math.round(progress(b.id, c) * 100);
    html += `<li class="ch" data-b="${esc(b.id)}" data-n="${c.n}"><img class="thumb" src="${esc(b.coverSm || b.cover)}" alt="">
      <div class="main"><div class="t1"><span>${esc(chName(c))}</span></div>
      <div class="t2">${new Date(ts).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })} • ${p >= 100 ? 'прослушано' : p + '%'} • ${esc(b.short || b.title)}</div></div>
      <button class="dl" data-rm aria-label="Удалить из истории">${ic('del')}</button></li>`;
  });
  v.innerHTML = html + '</ul>'; v.hidden = false;
  $('#hList').onclick = (e) => {
    const li = e.target.closest('.ch'); if (!li) return;
    if (e.target.closest('[data-rm]')) { hist = hist.filter((h) => !(h.b === li.dataset.b && h.n === +li.dataset.n)); saveHist(); renderHistory(); return; }
    go(`#/read/${li.dataset.b}/${li.dataset.n}`);
  };
  const c = $('#hClear'); if (c) c.onclick = () => confirmDlg('Очистить историю?', 'Список прослушанного будет очищен. Прогресс глав сохранится.', 'Очистить', () => { hist = []; saveHist(); renderHistory(); });
}

// ---------- More / settings / downloads / about ----------
function renderMore() {
  const v = $('#v-more');
  const nDl = downloaded.size;
  v.innerHTML = `<div class="brand"><img class="logo" src="icon-192.png" alt=""><b>Unison</b><span>Книги в несколько голосов</span></div>
    <button class="row" data-p="downloads"><span class="ico">${ic('download')}</span><span class="txt">Загрузки<small>${nDl} ${plural(nDl, 'глава', 'главы', 'глав')} на телефоне</small></span></button>
    <button class="row" data-p="settings"><span class="ico">${ic('settings')}</span><span class="txt">Настройки<small>Тема, шрифт, скорость, экран</small></span></button>
    <button class="row" data-p="about"><span class="ico">${ic('info')}</span><span class="txt">О проекте<small>Авторы, лицензии, голоса</small></span></button>
    <a class="row" href="https://github.com/NotoBoto/unison" target="_blank" rel="noopener" style="color:inherit;text-decoration:none"><span class="ico">${ic('code')}</span><span class="txt">GitHub<small>Исходный код</small></span></a>`;
  v.hidden = false;
  $$('[data-p]', v).forEach((b) => (b.onclick = () => go('#/more/' + b.dataset.p)));
}
function renderPage() {
  const v = $('#v-page'), p = route.page;
  const titles = { settings: 'Настройки', downloads: 'Загрузки', about: 'О проекте' };
  v.innerHTML = bar(titles[p] || '', { back: true }) + '<div id="pageBody"></div>'; v.hidden = false;
  bindBack(v, '#/more');
  ({ settings: pageSettings, downloads: pageDownloads, about: pageAbout }[p] || (() => go('#/more', true)))($('#pageBody'));
}
function pageSettings(el) {
  const th = settings.theme;
  el.innerHTML = `
    <div class="row"><span class="ico">${ic('palette')}</span><span class="txt">Тема</span>
      <div class="seg" id="sTheme"><button data-v="dark" class="${th === 'dark' ? 'on' : ''}">Тёмная</button><button data-v="light" class="${th === 'light' ? 'on' : ''}">Светлая</button><button data-v="system" class="${th === 'system' ? 'on' : ''}">Система</button></div></div>
    <div class="row"><span class="ico">${ic('title')}</span><span class="txt">Размер текста<small>В режиме чтения</small></span>
      <div class="stepper"><button id="fsD">−</button><b id="fsV">${settings.fs}</b><button id="fsU">+</button></div></div>
    <div class="row"><span class="ico">${ic('speed')}</span><span class="txt">Скорость по умолчанию</span>
      <div class="stepper"><button id="spD">−</button><b id="spV">${String(settings.speed).replace('.', ',')}×</b><button id="spU">+</button></div></div>
    <button class="row" id="sWake"><span class="ico">${ic('sun')}</span><span class="txt">Не гасить экран<small>Пока идёт воспроизведение</small></span><span class="switch ${settings.wake ? 'on' : ''}"></span></button>
    <button class="row" id="sTitle"><span class="ico">${ic('sort')}</span><span class="txt">Полные названия глав<small>Иначе — «Глава N»</small></span><span class="switch ${settings.fullTitle ? 'on' : ''}"></span></button>`;
  $$('#sTheme button').forEach((b) => (b.onclick = () => { settings.theme = b.dataset.v; saveSettings(); applyTheme(); pageSettings(el); }));
  const fs = (d) => { settings.fs = Math.min(30, Math.max(14, settings.fs + d)); saveSettings(); applyTheme(); $('#fsV').textContent = settings.fs; };
  $('#fsD').onclick = () => fs(-1); $('#fsU').onclick = () => fs(1);
  const sp = (d) => { const i = Math.min(SPEEDS.length - 1, Math.max(0, SPEEDS.indexOf(settings.speed) + d)); settings.speed = SPEEDS[i < 0 ? 2 : i]; saveSettings(); $('#spV').textContent = String(settings.speed).replace('.', ',') + '×'; };
  $('#spD').onclick = () => sp(-1); $('#spU').onclick = () => sp(1);
  $('#sWake').onclick = () => { settings.wake = !settings.wake; saveSettings(); pageSettings(el); };
  $('#sTitle').onclick = () => { settings.fullTitle = !settings.fullTitle; saveSettings(); pageSettings(el); };
}
function pageDownloads(el) {
  const jobs = [...dlJobs.values()];
  const old = outdated();
  let used = 0; books.forEach((b) => b.chapters.forEach((c) => { if (downloaded.has(key(b.id, c.n))) used += c.bytes; }));
  el.innerHTML = `<div class="bar-sub" id="storageLine">Скачано: ${downloaded.size} ${plural(downloaded.size, 'глава', 'главы', 'глав')}, ${mb(used)}</div>
    <button class="row" id="dUpd"><span class="ico">${ic('refresh')}</span><span class="txt">Обновить скачанные<small>${old.length ? `Есть новая версия у ${old.length} ${plural(old.length, 'главы', 'глав', 'глав')}` : 'Все скачанные главы актуальны'}</small></span></button>
    <button class="row" id="dDel"><span class="ico">${ic('del')}</span><span class="txt">Удалить все скачанные<small>Прогресс прослушивания сохранится</small></span></button>
    <div class="grp">${jobs.length ? 'Очередь загрузки' : 'Очередь пуста'}</div>
    <ul class="chs">${jobs.map((j) => { const b = bookById(j.b), c = b.chapters.find((x) => x.n === j.n);
      return `<li class="ch" data-b="${esc(j.b)}" data-n="${j.n}"><div class="main"><div class="t1"><span>${esc(chName(c))}</span></div>
        <div class="pbar" style="margin:6px 0 0"><i style="width:${Math.round((j.p || 0) * 100)}%"></i></div></div>
        <button class="dl" data-cancel aria-label="Отменить">${ic('close')}</button></li>`; }).join('')}</ul>`;
  $('#dUpd').onclick = updateOutdated;
  $('#dDel').onclick = () => confirmDlg('Удалить всё скачанное?', 'Все главы будут удалены с телефона. Прогресс прослушивания сохранится.', 'Удалить',
    async () => { for (const b of books) await removeDl(b.id, b.chapters.map((c) => c.n)); toast('Удалено'); pageDownloads(el); });
  $$('[data-cancel]', el).forEach((b) => (b.onclick = () => { const li = b.closest('.ch'); cancelDl(li.dataset.b, +li.dataset.n); }));
  navigator.storage?.estimate?.().then((e) => { const l = $('#storageLine'); if (l) l.textContent += ` · всего занято ${mb(e.usage || 0)}`; }).catch(() => {});
}
let dlPageT = 0;
function refreshDownloadsPage() { if (route.v === 'page' && route.page === 'downloads') { clearTimeout(dlPageT); dlPageT = setTimeout(() => pageDownloads($('#pageBody')), 300); } }
function pageAbout(el) {
  el.innerHTML = `<div class="prose">
    <p><b style="color:var(--fg)">Unison</b> — читалка аудиокниг, озвученных по ролям: у каждого персонажа свой голос, а текст подсвечивается синхронно с речью. Работает офлайн: скачайте главы, и интернет больше не нужен.</p>
    ${books.map((b) => `<h4>${esc(b.title)}</h4><p>${esc(b.credits || '')}</p>`).join('')}
    <h4>Озвучка</h4><p>Синтез речи — <a href="https://huggingface.co/coqui/XTTS-v2" target="_blank" rel="noopener">XTTS-v2</a> (Coqui Public Model License, некоммерческое использование). Часть голосов клонирована по фрагментам записей волонтёров <a href="https://librivox.org/" target="_blank" rel="noopener">LibriVox</a> (общественное достояние). Синхронизация слов — faster-whisper.</p>
    <h4>Правообладатели</h4><p>Harry Potter принадлежит J. K. Rowling. Проект некоммерческий и не связан с правообладателями.</p>
    <p><a href="https://github.com/NotoBoto/unison" target="_blank" rel="noopener">Исходный код на GitHub</a></p></div>`;
}

// ---------- Reader ----------
let cur = null, curBook = null, words = [], active = -1, audioUrl = null, followUntil = 0, raf = 0, seeking = false, programmaticScroll = 0;
const roleColor = (i) => (i === 0 ? '' : `var(--r${((i - 1) % 9) + 1})`);
$('#rBack').innerHTML = ic('back'); $('#wake').innerHTML = ic('sun'); $('#zen').innerHTML = ic('full'); $('#play').innerHTML = ic('play');
$('#rBack').onclick = () => (appNav > 0 ? history.back() : go('#/book/' + (curBook?.id || 'hpmor'), true));

async function getRes(url) {
  try { const hit = await (await caches.open(DATA_CACHE)).match(url); if (hit) return hit; } catch {}
  const r = await fetch(url); if (!r.ok) throw new Error(r.status + ' ' + url); return r;
}
async function renderReader() {
  $('#v-reader').hidden = false;
  const b = bookById(route.b), meta = b?.chapters.find((c) => c.n === route.n);
  if (!meta) { toast('Глава не найдена'); go('#/library', true); return; }
  if (cur && curBook === b && cur.n === route.n && audio.src) return;  // already open (e.g. back from a menu)
  saveNow();
  let data;
  try { data = await (await getRes(chUrl(b, route.n, 'json'))).json(); }
  catch { toast('Глава не скачана, а интернета нет'); return; }
  cur = data; curBook = b;
  const s = bs(b.id); s.last = cur.n; saveBs(b.id);
  hist = [{ b: b.id, n: cur.n, ts: Date.now() }, ...hist.filter((h) => !(h.b === b.id && h.n === cur.n))]; saveHist();
  $('#rTitle').textContent = cur.title;
  const art = $('#text'); art.innerHTML = ''; words = []; active = -1;
  const legend = document.createElement('div'); legend.className = 'legend';
  cur.roles.forEach((r, i) => { if (i) { const sp = document.createElement('span'); sp.textContent = '● ' + r; sp.style.color = roleColor(i); legend.appendChild(sp); } });
  cur.paras.forEach((toks, pi) => {
    const el = document.createElement(pi === 0 ? 'h2' : 'p'), frag = document.createDocumentFragment();
    toks.forEach(([w, t, r], k) => {
      const sp = document.createElement('span'); sp.textContent = w;
      if (r) sp.style.color = roleColor(r);
      if (t != null) { sp.dataset.t = t; words.push({ t, el: sp }); }
      frag.appendChild(sp); if (k < toks.length - 1) frag.appendChild(document.createTextNode(' '));
    });
    el.appendChild(frag); art.appendChild(el); if (pi === 0) art.appendChild(legend);
  });
  words.sort((a, c) => a.t - c.t);
  const i = b.chapters.findIndex((c) => c.n === cur.n);
  $('#prevCh').disabled = i <= 0; $('#nextCh').disabled = i >= b.chapters.length - 1;
  if (audioUrl) URL.revokeObjectURL(audioUrl);
  try { audioUrl = URL.createObjectURL(await (await getRes(chUrl(b, cur.n, 'ogg'))).blob()); }
  catch { toast('Аудио не скачано, а интернета нет'); return; }
  audio.src = audioUrl; audio.playbackRate = settings.speed; setSpeedLabel();
  const pos = s.done[cur.n] ? 0 : s.pos[cur.n] || 0, auto = autoplayNext; autoplayNext = false;
  audio.addEventListener('loadedmetadata', () => {
    audio.currentTime = pos > cur.duration - 3 ? 0 : pos; tick(true); scrollToActive(true);
    if (auto) audio.play();
  }, { once: true });
  setMediaSession(); markWake(); window.scrollTo(0, 0);
}
let autoplayNext = false;
function leaveReader() { saveNow(); audio.pause(); setZen(false); }
function findWord(t) { let lo = 0, hi = words.length - 1, ans = -1; while (lo <= hi) { const m = (lo + hi) >> 1; if (words[m].t <= t + 0.05) { ans = m; lo = m + 1; } else hi = m - 1; } return ans; }
function tick(force) {
  if (!cur) return;
  const t = audio.currentTime, i = findWord(t);
  if (i !== active || force) {
    if (active >= 0 && words[active]) words[active].el.classList.remove('on');
    active = i; if (i >= 0) { words[i].el.classList.add('on'); scrollToActive(false); }
  }
  $('#time').textContent = fmt(t) + ' / ' + fmt(cur.duration);
  if (!seeking) $('#seek').value = Math.round((t / cur.duration) * 1000);
}
function scrollToActive(force) {
  if (active < 0 || route.v !== 'read') return;
  const el = words[active].el;
  if (!force && Date.now() < followUntil) { $('#follow').hidden = false; return; }
  $('#follow').hidden = true;
  const r = el.getBoundingClientRect(), top = 70, bottom = innerHeight - 170;
  if (force || r.top < top || r.bottom > bottom) { programmaticScroll = Date.now(); scrollBy({ top: r.top - innerHeight * 0.33, behavior: force ? 'auto' : 'smooth' }); }
}
const userScrolled = () => { if (Date.now() - programmaticScroll > 900 && !audio.paused) followUntil = Date.now() + 8000; };
addEventListener('touchmove', userScrolled, { passive: true });
addEventListener('wheel', userScrolled, { passive: true });
$('#follow').onclick = () => { followUntil = 0; scrollToActive(true); };
function loop() { tick(false); raf = requestAnimationFrame(loop); }
audio.addEventListener('play', () => { $('#play').innerHTML = ic('pause'); cancelAnimationFrame(raf); loop(); updateWake(); });
audio.addEventListener('pause', () => { $('#play').innerHTML = ic('play'); cancelAnimationFrame(raf); saveNow(); updateWake(); });
audio.addEventListener('timeupdate', () => { if (document.hidden) tick(false); if (Math.floor(audio.currentTime) % 5 === 0) saveNow(); });
audio.addEventListener('seeked', () => { if (cur) { tick(true); if (audio.paused) scrollToActive(true); } });
audio.addEventListener('ended', () => {
  if (!cur || !curBook) return;
  const s = bs(curBook.id); s.done[cur.n] = true; s.pos[cur.n] = 0; saveBs(curBook.id);
  const next = curBook.chapters.find((c) => c.n > cur.n);
  if (next && route.v === 'read') { autoplayNext = true; go(`#/read/${curBook.id}/${next.n}`, true); }
});
function saveNow() {
  if (!cur || !curBook || !audio.src || isNaN(audio.currentTime)) return;
  const s = bs(curBook.id); s.pos[cur.n] = audio.currentTime;
  if (audio.currentTime > cur.duration * 0.97) s.done[cur.n] = true;
  saveBs(curBook.id);
}
document.addEventListener('visibilitychange', () => { if (document.hidden) saveNow(); else if (cur && route.v === 'read') tick(true); updateWake(); });
$('#text').addEventListener('click', (e) => {
  if (document.body.classList.contains('zen')) { setZen(false); return; }
  const t = e.target.dataset && e.target.dataset.t; if (t == null) return;
  audio.currentTime = +t; followUntil = 0; tick(true); if (audio.paused) audio.play();
});
$('#play').onclick = () => (audio.paused ? audio.play() : audio.pause());
$('#rew').onclick = () => { audio.currentTime = Math.max(0, audio.currentTime - 15); followUntil = 0; tick(true); };
$('#fwd').onclick = () => { audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 15); followUntil = 0; tick(true); };
function setSpeedLabel() { $('#speed').textContent = audio.playbackRate.toString().replace('.', ',') + '×'; }
$('#speed').onclick = () => { const i = (SPEEDS.indexOf(audio.playbackRate) + 1) % SPEEDS.length; audio.playbackRate = SPEEDS[i < 0 ? 2 : i]; settings.speed = audio.playbackRate; saveSettings(); };
audio.addEventListener('ratechange', setSpeedLabel);
$('#seek').addEventListener('input', () => { seeking = true; $('#time').textContent = fmt(($('#seek').value / 1000) * cur.duration) + ' / ' + fmt(cur.duration); });
$('#seek').addEventListener('change', () => { audio.currentTime = ($('#seek').value / 1000) * cur.duration; seeking = false; followUntil = 0; tick(true); });
const neighbour = (d) => { const l = curBook.chapters, i = l.findIndex((c) => c.n === cur.n); return l[i + d]; };
$('#prevCh').onclick = () => { const p = neighbour(-1); if (p) go(`#/read/${curBook.id}/${p.n}`, true); };
$('#nextCh').onclick = () => { const p = neighbour(1); if (p) go(`#/read/${curBook.id}/${p.n}`, true); };
const setFs = (d) => { settings.fs = Math.min(30, Math.max(14, settings.fs + d)); saveSettings(); applyTheme(); };
$('#fontUp').onclick = () => setFs(1); $('#fontDown').onclick = () => setFs(-1);

// keep screen on while playing
let wakeLock = null;
async function updateWake() {
  const want = settings.wake && !audio.paused && !document.hidden && route.v === 'read';
  try {
    if (want && !wakeLock && 'wakeLock' in navigator) { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener('release', () => (wakeLock = null)); }
    else if (!want && wakeLock) { await wakeLock.release(); wakeLock = null; }
  } catch {}
}
function markWake() { $('#wake').style.opacity = settings.wake ? 1 : 0.35; $('#wake').setAttribute('aria-pressed', settings.wake); }
$('#wake').onclick = () => { settings.wake = !settings.wake; saveSettings(); markWake(); updateWake();
  toast(settings.wake ? 'Экран не будет гаснуть во время прослушивания' : 'Экран гаснет как обычно'); };

// hide interface
function setZen(on) {
  if (document.body.classList.contains('zen') === on) return;
  document.body.classList.toggle('zen', on);
  try {
    if (on && document.documentElement.requestFullscreen && !document.fullscreenElement) document.documentElement.requestFullscreen({ navigationUI: 'hide' }).catch(() => {});
    if (!on && document.fullscreenElement) document.exitFullscreen().catch(() => {});
  } catch {}
  if (on) { followUntil = 0; setTimeout(() => scrollToActive(true), 300); }
}
$('#zen').onclick = () => { setZen(true); toast('Нажмите на текст, чтобы вернуть интерфейс'); };
document.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement) setZen(false); });

function setMediaSession() {
  if (!('mediaSession' in navigator) || !cur) return;
  navigator.mediaSession.metadata = new MediaMetadata({ title: cur.title, artist: curBook.author, album: curBook.title,
    artwork: [{ src: curBook.cover, sizes: '480x691', type: 'image/jpeg' }, { src: 'icon-512.png', sizes: '512x512', type: 'image/png' }] });
  const ms = navigator.mediaSession;
  ms.setActionHandler('play', () => audio.play()); ms.setActionHandler('pause', () => audio.pause());
  ms.setActionHandler('seekbackward', () => $('#rew').onclick()); ms.setActionHandler('seekforward', () => $('#fwd').onclick());
  ms.setActionHandler('previoustrack', () => $('#prevCh').onclick()); ms.setActionHandler('nexttrack', () => $('#nextCh').onclick());
  try { ms.setActionHandler('seekto', (e) => { audio.currentTime = e.seekTime; }); } catch {}
}

// ---------- start ----------
(async function start() {
  applyTheme();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  await migrateOldCache();
  await loadData();
  await scanDownloads();
  if (!location.hash || location.hash === '#') history.replaceState(null, '', books.length === 1 ? '#/library' : '#/library');
  render();
  paintBadge();
})();
