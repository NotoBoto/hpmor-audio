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
  search: 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  bm: 'M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z',
  bmo: 'M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2zm0 15l-5-2.18L7 18V5h10v13z',
  label: 'M17.63 5.84C17.27 5.33 16.67 5 16 5L5 5.01C3.9 5.01 3 5.9 3 7v10c0 1.1.9 1.99 2 1.99L16 19c.67 0 1.27-.33 1.63-.84L22 12l-4.37-6.16z',
  stats: 'M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z',
  sync: 'M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 0 0 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z',
  moon: 'M12.34 2.02C6.59 1.82 2 6.42 2 12c0 5.52 4.48 10 10 10 3.71 0 6.93-2.02 8.66-5.02-7.51-.25-12.09-8.43-8.32-14.96z',
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 0 0 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  stop: 'M6 6h12v12H6z',
  upload: 'M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z',
  incognito: 'M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46A11.804 11.804 0 0 0 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z',
  dlonly: 'M19.35 10.04A7.49 7.49 0 0 0 12 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 0 0 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM10 17l-3.5-3.5 1.41-1.41L10 14.17l5.18-5.18 1.41 1.41L10 17z',
  group: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  go: 'M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z',
};
const ic = (name, extra = '') => `<svg class="i" viewBox="0 0 24 24" ${extra}><path d="${P[name]}"/></svg>`;

// ---------- persistent state ----------
const settings = Object.assign({ theme: 'dark', fs: 19, speed: 1, wake: true, sortDesc: true, fullTitle: true,
  font: 'sans', lh: 1.6, mx: 18, rbg: 'theme', hl: 0, hlSent: false,            // reading
  perBookSpeed: false, rewindSec: 0, rewindAfter: 5,                              // player
  sleepAuto: false, sleepFrom: '23:00', sleepTo: '06:00', sleepMin: 30,           // sleep timer
  incognito: false, dlOnly: false, libTab: '' }, LS.get('settings', {}));
const saveSettings = () => LS.set('settings', settings);
const bookState = {};
// book:<id> = {pos:{n:sec}, done:{n:true}, ts:{n:ms of last progress change}, dlv:{n:ver}, last:n|null, lastTs:ms,
//              cats:[names], bm:[{n,pos,quote,note,ts}], listenedMs, added:ms, speed:0|rate}
// Old records simply lack ts/lastTs (= 0 for merging), so no migration step is needed.
function bs(id) { return bookState[id] || (bookState[id] = Object.assign({ pos: {}, done: {}, ts: {}, dlv: {}, last: null, lastTs: 0,
  cats: [], bm: [], listenedMs: 0, added: 0, speed: 0 }, LS.get('book:' + id, {}))); }
const saveBs = (id) => LS.set('book:' + id, bs(id));
// every progress write goes through here so the per-chapter ts stays correct (needed for sync merging)
function writeProgress(id, n, pos, done) {
  const s = bs(id), oldPos = s.pos[n] || 0, oldDone = !!s.done[n];
  if (pos != null) { if (pos > 0) s.pos[n] = Math.round(pos * 100) / 100; else delete s.pos[n]; }
  if (done != null) { if (done) s.done[n] = true; else delete s.done[n]; }
  if (Math.abs((s.pos[n] || 0) - oldPos) > 0.5 || !!s.done[n] !== oldDone) s.ts[n] = Date.now();
  saveBs(id);
}
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

const FONTS = { sans: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif', serif: 'Georgia, "PT Serif", "Noto Serif", "Times New Roman", serif',
  mono: 'ui-monospace, "Cascadia Mono", "Roboto Mono", Consolas, monospace' };
const HLC = ['#ffd54a', '#9be38f', '#8fc7ff', '#ffa3c6', '#ffb46b'];   // 0 = theme default
const RBG = { sepia: '#f4ecd8', black: '#000000', white: '#ffffff' };
function applyTheme() {
  const t = settings.theme === 'system' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : settings.theme;
  const de = document.documentElement, st = de.style;
  de.dataset.theme = t;
  st.setProperty('--fs', settings.fs + 'px');
  st.setProperty('--rff', FONTS[settings.font] || FONTS.sans); st.setProperty('--lh', settings.lh); st.setProperty('--mx', settings.mx + 'px');
  if (settings.hl > 0) { st.setProperty('--hl', HLC[settings.hl]); st.setProperty('--hlfg', '#15131b'); } else { st.removeProperty('--hl'); st.removeProperty('--hlfg'); }
  const rb = route.v === 'read' && RBG[settings.rbg] ? settings.rbg : '';
  if (rb) de.dataset.rbg = rb; else delete de.dataset.rbg;
  $('meta[name=theme-color]').content = rb ? RBG[rb] : t === 'light' ? '#fdf7fa' : '#1a1520';
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
  ns.forEach((n) => (val ? writeProgress(b, n, null, true) : writeProgress(b, n, 0, false)));
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
function closeOverlays() { ['#menu', '#sheet', '#dialog', '#scrim'].forEach((s) => ($(s).hidden = true)); $('#scrim').className = ''; $$('.picked').forEach((e) => e.classList.remove('picked')); }
function promptDlg(title, value, okLabel, fn, { multiline = false, placeholder = '' } = {}) {
  const d = $('#dialog');
  d.innerHTML = `<h3>${esc(title)}</h3>${multiline ? `<textarea id="dlgIn" rows="4" placeholder="${esc(placeholder)}">${esc(value)}</textarea>`
    : `<input id="dlgIn" value="${esc(value)}" placeholder="${esc(placeholder)}" autocomplete="off">`}<div class="acts"><button data-x>Отмена</button><button data-ok>${esc(okLabel)}</button></div>`;
  d.hidden = false; $('#menu').hidden = true; $('#sheet').hidden = true; $('#scrim').hidden = false; $('#scrim').className = '';
  const inp = $('#dlgIn'); inp.focus(); if (!multiline) inp.select();
  const ok = () => { const v = inp.value.trim(); closeOverlays(); fn(v); };
  $('[data-x]', d).onclick = closeOverlays; $('[data-ok]', d).onclick = ok;
  if (!multiline) inp.onkeydown = (e) => { if (e.key === 'Enter') ok(); };
}
async function copyText(t) {
  try { await navigator.clipboard.writeText(t); return true; } catch {}
  const ta = document.createElement('textarea'); ta.value = t; ta.style.cssText = 'position:fixed;opacity:0;top:0'; document.body.appendChild(ta); ta.select();
  let ok = false; try { ok = document.execCommand('copy'); } catch {} ta.remove(); return ok;
}
const norm = (s) => String(s).toLowerCase().replace(/ё/g, 'е');
const dayKey = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const dur = (ms) => { const m = Math.round(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)} ч ${m % 60} мин` : `${m} мин`; };
const getCats = () => LS.get('categories', []);
const saveCats = (c) => LS.set('categories', c);
$('#scrim').onclick = closeOverlays;
function menu(anchor, items, { below = false } = {}) {
  const m = $('#menu'); m.innerHTML = '';
  items.forEach(([label, fn, disabled]) => {
    const btn = document.createElement('button'); btn.textContent = label; btn.disabled = !!disabled;
    btn.onclick = () => { closeOverlays(); fn(); }; m.appendChild(btn);
  });
  $('#scrim').hidden = false; $('#scrim').className = 'light'; m.hidden = false;
  const r = anchor.getBoundingClientRect(), w = m.offsetWidth, hgt = m.offsetHeight;
  m.style.left = Math.max(8, Math.min(innerWidth - w - 8, r.right - w)) + 'px';
  const top = !below ? r.top + 4 : r.bottom + 8 + hgt <= innerHeight - 8 ? r.bottom + 8 : r.top - hgt - 8;
  m.style.top = Math.max(8, Math.min(innerHeight - hgt - 8, top)) + 'px';
  if (below) m.style.left = Math.max(8, Math.min(innerWidth - w - 8, r.left + r.width / 2 - w / 2)) + 'px';
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
  if (['bookmarks', 'chars', 'search'].includes(p[0]) && p[1]) return { v: 'sub', kind: p[0], b: p[1] };
  return { v: TABS.includes(p[0]) ? p[0] : 'library' };
}
function render() {
  const prev = route; route = parseRoute();
  closeOverlays(); stopSample();
  if (prev.v === 'read' && route.v !== 'read') leaveReader();
  if (route.v !== 'book') exitSelection(true);
  applyTheme();
  $$('.view').forEach((v) => (v.hidden = true));
  document.body.classList.toggle('reading', route.v === 'read');
  $('#player').hidden = route.v !== 'read';
  $('#fab').hidden = route.v !== 'book';
  const tab = route.v === 'page' ? 'more' : route.v === 'book' || route.v === 'sub' ? 'library' : route.v;
  $$('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
  const v = { library: renderLibrary, updates: renderUpdates, history: renderHistory, more: renderMore, page: renderPage, sub: renderSub, book: renderBook, read: renderReader }[route.v];
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
let libQuery = '', libSearch = false;
function renderLibrary() {
  const v = $('#v-library');
  books.forEach((b) => { const s = bs(b.id); if (!s.added) { s.added = Date.now(); saveBs(b.id); } });
  v.innerHTML = bar('Библиотека', { actions: `<button class="ib" id="libSearch" aria-label="Поиск">${ic('search')}</button><button class="ib" id="libMenu" aria-label="Меню">${ic('vert')}</button>` }) +
    `<div class="sbox" ${libSearch ? '' : 'hidden'}>${ic('search')}<input id="libQ" type="search" placeholder="Название, автор, жанр" value="${esc(libQuery)}" autocomplete="off">
      <button class="ib" id="libQx" aria-label="Закрыть поиск">${ic('close')}</button></div><div id="libBody"></div>`;
  v.hidden = false;
  $('#libSearch').onclick = () => { libSearch = !libSearch; if (!libSearch) libQuery = ''; renderLibrary(); if (libSearch) $('#libQ').focus(); };
  $('#libQ').oninput = (e) => { libQuery = e.target.value; paintLibrary(); };
  $('#libQx').onclick = () => { libQuery = ''; libSearch = false; renderLibrary(); };
  $('#libMenu').onclick = (e) => menu(e.currentTarget, [['Обновить скачанные', updateOutdated], ['Категории', () => go('#/more/categories')], ['Настройки', () => go('#/more/settings')]]);
  paintLibrary();
}
function paintLibrary() {
  const body = $('#libBody'); if (!body) return;
  if (!books.length) { body.innerHTML = `<div class="empty"><span class="big">📚</span>Библиотека пуста.<br>Нужен интернет, чтобы загрузить список книг.</div>`; return; }
  const cats = getCats(), q = norm(libQuery.trim());
  const all = settings.dlOnly ? books.filter((b) => b.chapters.some((c) => downloaded.has(key(b.id, c.n)))) : books;
  const match = (b) => !q || norm([b.title, b.short, b.author, b.translator, ...(b.tags || [])].join(' ')).includes(q);
  const inCat = (b, c) => bs(b.id).cats.includes(c);
  const tabs = [], unc = all.filter((b) => !cats.some((c) => inCat(b, c)));
  if (!cats.length || unc.length) tabs.push({ id: '', label: 'По умолчанию', list: unc });
  cats.forEach((c) => tabs.push({ id: c, label: c, list: all.filter((b) => inCat(b, c)) }));
  const t = tabs.find((x) => x.id === settings.libTab) || tabs[0], list = t.list.filter(match);
  body.innerHTML = (tabs.length > 1 ? `<div class="tabs" role="tablist">${tabs.map((x) => `<button role="tab" class="${x === t ? 'on' : ''}" data-ct="${esc(x.id)}">${esc(x.label)}<b>${x.list.filter(match).length}</b></button>`).join('')}</div>` : '') +
    (list.length ? `<div class="grid">${list.map((b) => `
      <button class="tile" data-book="${esc(b.id)}"><img src="${esc(b.coverSm || b.cover)}" alt="" loading="lazy">
        <div class="badges" data-badges="${esc(b.id)}"></div><div class="cap"><span>${esc(b.title)}</span></div></button>`).join('')}</div>`
      : `<div class="empty">${q ? 'Ничего не найдено.' : settings.dlOnly && !all.length ? 'Нет скачанных книг.<br>Отключите «Только скачанное» в разделе «Ещё».' : 'В этой категории пока нет книг.<br>Добавить книгу: ⋮ на странице книги → «Категории».'}</div>`);
  $$('[data-ct]', body).forEach((x) => (x.onclick = () => { settings.libTab = x.dataset.ct; saveSettings(); paintLibrary(); x.scrollIntoView?.({ inline: 'center', block: 'nearest' }); }));
  $$('.tile', body).forEach((x) => (x.onclick = () => go('#/book/' + x.dataset.book)));
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
function bookChapters(b) {
  const list = settings.dlOnly ? b.chapters.filter((c) => downloaded.has(key(b.id, c.n))) : [...b.chapters];
  return settings.sortDesc ? list.reverse() : list;
}
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
    ['Категории…', () => catSheet(b)],
    [`Закладки (${bs(b.id).bm.length})`, () => go('#/bookmarks/' + b.id)],
    ['Действующие лица', () => go('#/chars/' + b.id)],
    ['Поиск по книге', () => go('#/search/' + b.id)],
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
  books.forEach((b) => b.chapters.forEach((c) => c.date && (!settings.dlOnly || downloaded.has(key(b.id, c.n))) && all.push({ b, c })));
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
  const nDl = downloaded.size, nCat = getCats().length;
  const row = (p, icon, t, sub) => `<button class="row" data-p="${p}"><span class="ico">${ic(icon)}</span><span class="txt">${t}<small>${sub}</small></span></button>`;
  const link = (href, icon, t, sub) => `<a class="row" href="${href}" target="_blank" rel="noopener" style="color:inherit;text-decoration:none"><span class="ico">${ic(icon)}</span><span class="txt">${t}<small>${sub}</small></span></a>`;
  v.innerHTML = `<div class="brand"><img class="logo" src="icon-192.png" alt=""><b>Unison</b><span>Книги в несколько голосов</span></div>
    <button class="row" data-tg="dlOnly"><span class="ico">${ic('dlonly')}</span><span class="txt">Только скачанное<small>Показывать только скачанные книги и главы</small></span><span class="switch ${settings.dlOnly ? 'on' : ''}"></span></button>
    <button class="row" data-tg="incognito"><span class="ico">${ic('incognito')}</span><span class="txt">Режим инкогнито<small>Не записывать историю и статистику</small></span><span class="switch ${settings.incognito ? 'on' : ''}"></span></button>
    <div class="sep"></div>
    ${row('downloads', 'download', 'Загрузки', `${nDl} ${plural(nDl, 'глава', 'главы', 'глав')} на телефоне`)}
    ${row('categories', 'label', 'Категории', nCat ? `${nCat} ${plural(nCat, 'категория', 'категории', 'категорий')}` : 'Полки для книг в библиотеке')}
    ${row('stats', 'stats', 'Статистика', 'Время прослушивания и прогресс')}
    ${row('sync', 'sync', 'Синхронизация', 'Перенос прогресса между сайтом и приложением')}
    ${row('settings', 'settings', 'Настройки', 'Тема, чтение, плеер, таймер сна')}
    ${row('about', 'info', 'О проекте', 'Авторы, лицензии, голоса')}
    ${link('https://github.com/NotoBoto/unison/releases/latest', 'download', 'Приложение для Android', 'Фоновое воспроизведение, таймер сна, загрузки')}
    ${link('https://github.com/NotoBoto/unison', 'code', 'GitHub', 'Исходный код')}`;
  v.hidden = false;
  $$('[data-p]', v).forEach((b) => (b.onclick = () => go('#/more/' + b.dataset.p)));
  $$('[data-tg]', v).forEach((b) => (b.onclick = () => { settings[b.dataset.tg] = !settings[b.dataset.tg]; saveSettings(); paintModes(); renderMore(); }));
}
function paintModes() {
  const m = $('#modes');
  m.innerHTML = (settings.dlOnly ? '<span class="m-dl">Только скачанное</span>' : '') + (settings.incognito ? '<span class="m-inc">Режим инкогнито</span>' : '');
  m.hidden = !m.innerHTML; document.body.classList.toggle('modes', !m.hidden);
}
function renderPage() {
  const v = $('#v-page'), p = route.page;
  const titles = { settings: 'Настройки', downloads: 'Загрузки', about: 'О проекте', categories: 'Категории', stats: 'Статистика', sync: 'Синхронизация' };
  v.innerHTML = bar(titles[p] || '', { back: true }) + '<div id="pageBody"></div>'; v.hidden = false;
  bindBack(v, '#/more');
  ({ settings: pageSettings, downloads: pageDownloads, about: pageAbout, categories: pageCategories, stats: pageStats, sync: pageSync }[p] || (() => go('#/more', true)))($('#pageBody'));
}
function renderSub() {
  const b = bookById(route.b), v = $('#v-page');
  if (!b) { go('#/library', true); return; }
  const t = { bookmarks: 'Закладки', chars: 'Действующие лица', search: 'Поиск по книге' }[route.kind];
  v.innerHTML = bar(t, { back: true }) + `<div class="bar-sub">${esc(b.title)}</div><div id="pageBody"></div>`; v.hidden = false;
  bindBack(v, '#/book/' + b.id);
  ({ bookmarks: subBookmarks, chars: subChars, search: subSearch })[route.kind]($('#pageBody'), b);
}

// settings controls shared by the settings page and the reader's «Aa» sheet:
// [data-set] = segmented choice, [data-st] = stepper, [data-tg] = switch
const LIM = { fs: [14, 30, 1], lh: [1.2, 2.2, 0.1], mx: [4, 48, 4], sleepMin: [5, 120, 5] };
const segHtml = (k, opts) => `<div class="seg" data-set="${k}">${opts.map(([v, l]) => `<button data-v="${v}" class="${String(settings[k]) === String(v) ? 'on' : ''}">${l}</button>`).join('')}</div>`;
const stepHtml = (k, shown) => `<div class="stepper"><button data-st="${k}" data-d="-1" aria-label="Меньше">−</button><b>${shown}</b><button data-st="${k}" data-d="1" aria-label="Больше">+</button></div>`;
const swHtml = (k, title, sub, icon) => `<button class="${icon ? 'row' : 'set'}" data-tg="${k}">${icon ? `<span class="ico">${ic(icon)}</span>` : ''}<span class="txt">${title}${sub ? `<small>${sub}</small>` : ''}</span><span class="switch ${settings[k] ? 'on' : ''}"></span></button>`;
function readingHtml() {
  return `<div class="set"><span class="txt">Шрифт</span>${segHtml('font', [['sans', 'Обычный'], ['serif', 'С засечками'], ['mono', 'Моно']])}</div>
    <div class="set"><span class="txt">Размер текста</span>${stepHtml('fs', settings.fs)}</div>
    <div class="set"><span class="txt">Межстрочный интервал</span>${stepHtml('lh', settings.lh.toFixed(1).replace('.', ','))}</div>
    <div class="set"><span class="txt">Поля по бокам</span>${stepHtml('mx', settings.mx)}</div>
    <div class="set"><span class="txt">Фон</span><div class="sw" data-set="rbg">${[['theme', 'Тема'], ['sepia', 'Сепия'], ['black', 'Чёрный'], ['white', 'Белый']].map(([v, l]) =>
      `<button data-v="${v}" class="${settings.rbg === v ? 'on' : ''}" style="--c:${RBG[v] || 'var(--surface2)'}"><i></i>${l}</button>`).join('')}</div></div>
    <div class="set"><span class="txt">Цвет подсветки</span><div class="sw" data-set="hl">${HLC.map((c, i) =>
      `<button data-v="${i}" class="${settings.hl === i ? 'on' : ''}" style="--c:${c}" aria-label="Цвет ${i + 1}"><i></i></button>`).join('')}</div></div>
    ${swHtml('hlSent', 'Подсвечивать предложение', 'вместо отдельного слова')}`;
}
function bindSettings(root, rerender) {
  $$('[data-set]', root).forEach((g) => $$('button', g).forEach((b) => (b.onclick = () => {
    const v = b.dataset.v; settings[g.dataset.set] = v !== '' && !isNaN(+v) ? +v : v; saveSettings(); applyTheme(); rerender(); })));
  $$('[data-st]', root).forEach((b) => (b.onclick = () => {
    const k = b.dataset.st, [lo, hi, step] = LIM[k];
    settings[k] = Math.round(Math.min(hi, Math.max(lo, settings[k] + step * +b.dataset.d)) * 10) / 10; saveSettings(); applyTheme(); rerender(); }));
  $$('[data-tg]', root).forEach((b) => (b.onclick = () => { const k = b.dataset.tg; settings[k] = !settings[k]; saveSettings();
    if (k === 'hlSent') paintHl(); if (k === 'wake') updateWake(); rerender(); }));
}
function pageSettings(el) {
  el.innerHTML = `<div class="grp">Оформление</div>
    <div class="set"><span class="txt">Тема</span>${segHtml('theme', [['dark', 'Тёмная'], ['light', 'Светлая'], ['system', 'Система']])}</div>
    ${swHtml('fullTitle', 'Полные названия глав', 'Иначе — «Глава N»')}
    <div class="grp">Чтение</div>${readingHtml()}
    <div class="grp">Плеер</div>
    <div class="set"><span class="txt">Скорость по умолчанию</span><div class="stepper"><button id="spD">−</button><b id="spV">${String(settings.speed).replace('.', ',')}×</b><button id="spU">+</button></div></div>
    ${swHtml('perBookSpeed', 'Своя скорость для каждой книги', 'Запоминать скорость отдельно для книги')}
    <div class="set"><span class="txt">Отмотка назад после паузы<small>Чтобы вспомнить, на чём остановились</small></span>${segHtml('rewindSec', [[0, 'Выкл'], [5, '5 с'], [10, '10 с'], [15, '15 с']])}</div>
    ${settings.rewindSec ? `<div class="set"><span class="txt">Если пауза дольше</span>${segHtml('rewindAfter', [[1, '1'], [5, '5'], [15, '15'], [30, '30 мин']])}</div>` : ''}
    <div class="grp">Таймер сна</div>
    ${swHtml('sleepAuto', 'Включать автоматически', 'Если воспроизведение началось в это время')}
    ${settings.sleepAuto ? `<div class="set"><span class="txt">Время</span><span class="times">с <input type="time" id="slFrom" value="${esc(settings.sleepFrom)}"> до <input type="time" id="slTo" value="${esc(settings.sleepTo)}"></span></div>` : ''}
    <div class="set"><span class="txt">Длительность таймера<small>Последние 10 с звук затихает</small></span>${stepHtml('sleepMin', settings.sleepMin + ' мин')}</div>
    <div class="grp">Экран</div>
    ${swHtml('wake', 'Не гасить экран', 'Пока идёт воспроизведение')}`;
  bindSettings(el, () => pageSettings(el));
  const sp = (d) => { const i = Math.min(SPEEDS.length - 1, Math.max(0, SPEEDS.indexOf(settings.speed) + d)); settings.speed = SPEEDS[i < 0 ? 2 : i]; saveSettings(); $('#spV').textContent = String(settings.speed).replace('.', ',') + '×'; };
  $('#spD').onclick = () => sp(-1); $('#spU').onclick = () => sp(1);
  const f = $('#slFrom'), t = $('#slTo');
  if (f) f.onchange = () => { if (f.value) { settings.sleepFrom = f.value; saveSettings(); } };
  if (t) t.onchange = () => { if (t.value) { settings.sleepTo = t.value; saveSettings(); } };
}

// ---------- Categories ----------
function addCategory(after) {
  promptDlg('Новая категория', '', 'Создать', (name) => {
    const cats = getCats();
    if (!name) return; if (cats.includes(name)) { toast('Такая категория уже есть'); return; }
    cats.push(name); saveCats(cats); after?.(name);
  }, { placeholder: 'Например, «Слушаю»' });
}
function pageCategories(el) {
  const cats = getCats(), count = (c) => books.filter((b) => bs(b.id).cats.includes(c)).length;
  el.innerHTML = (cats.length ? `<ul class="cats">${cats.map((c, i) => `<li data-i="${i}"><span class="ico">${ic('label')}</span>
      <span class="txt">${esc(c)}<small>${count(c)} ${plural(count(c), 'книга', 'книги', 'книг')}</small></span>
      <button class="ib" data-a="up" ${i ? '' : 'disabled'} aria-label="Выше">${ic('collapse')}</button><button class="ib" data-a="down" ${i < cats.length - 1 ? '' : 'disabled'} aria-label="Ниже">${ic('expand')}</button>
      <button class="ib" data-a="ren" aria-label="Переименовать">${ic('edit')}</button><button class="ib" data-a="del" aria-label="Удалить">${ic('del')}</button></li>`).join('')}</ul>`
    : `<div class="empty"><span class="big">🏷️</span>Категорий пока нет.<br>Разложите книги по полкам: «Слушаю», «Прослушано», «Отложено»…</div>`) +
    `<div class="pad"><button class="btn" id="catAdd">${ic('add')}Новая категория</button></div>`;
  $('#catAdd').onclick = () => addCategory(() => pageCategories(el));
  $$('.cats li', el).forEach((li) => $$('[data-a]', li).forEach((b) => (b.onclick = () => {
    const i = +li.dataset.i, c = cats[i], a = b.dataset.a;
    if (a === 'up' || a === 'down') { const j = a === 'up' ? i - 1 : i + 1; [cats[i], cats[j]] = [cats[j], cats[i]]; saveCats(cats); pageCategories(el); }
    if (a === 'ren') promptDlg('Переименовать', c, 'Сохранить', (name) => {
      if (!name || name === c) return; if (cats.includes(name)) { toast('Такая категория уже есть'); return; }
      cats[i] = name; saveCats(cats);
      books.forEach((bk) => { const s = bs(bk.id); if (s.cats.includes(c)) { s.cats = s.cats.map((x) => (x === c ? name : x)); saveBs(bk.id); } });
      if (settings.libTab === c) { settings.libTab = name; saveSettings(); }
      pageCategories(el);
    });
    if (a === 'del') confirmDlg('Удалить категорию?', `Категория «${c}» будет удалена. Книги останутся в библиотеке.`, 'Удалить', () => {
      cats.splice(i, 1); saveCats(cats);
      books.forEach((bk) => { const s = bs(bk.id); if (s.cats.includes(c)) { s.cats = s.cats.filter((x) => x !== c); saveBs(bk.id); } });
      pageCategories(el);
    });
  })));
}
function catSheet(b) {
  const paint = (s) => {
    const cats = getCats(), mine = bs(b.id).cats;
    s.innerHTML = `<div class="grab"></div><h3>Категории книги</h3>` + (cats.length ? cats.map((c) => `<button class="opt" data-c="${esc(c)}"><span class="ico">${mine.includes(c) ? ic('check') : ''}</span>${esc(c)}</button>`).join('')
      : '<p class="hint">Категорий пока нет — создайте первую.</p>') +
      `<button class="opt" data-new><span class="ico">${ic('add')}</span>Новая категория</button><button class="opt" data-manage><span class="ico">${ic('edit')}</span>Управление категориями</button>`;
    $$('[data-c]', s).forEach((x) => (x.onclick = () => {
      const st = bs(b.id), c = x.dataset.c;
      st.cats = st.cats.includes(c) ? st.cats.filter((y) => y !== c) : getCats().filter((y) => y === c || st.cats.includes(y));
      saveBs(b.id); paint(s);
    }));
    $('[data-new]', s).onclick = () => addCategory((name) => { const st = bs(b.id); st.cats.push(name); saveBs(b.id); toast(`Книга добавлена в «${name}»`); });
    $('[data-manage]', s).onclick = () => go('#/more/categories');
  };
  sheet('', paint);
}

// ---------- Statistics ----------
let statAcc = 0, statLast = 0, statBook = null;
function statTick() {
  const now = Date.now();
  if (statLast) statAcc += Math.min(now - statLast, 3000);
  statLast = audio.paused ? 0 : now; if (curBook) statBook = curBook.id;
  if (statAcc >= 15000) statFlush();
}
function statFlush() {
  if (statAcc && statBook && !settings.incognito) {
    const days = LS.get('statsDays', {}), k = dayKey(); days[k] = (days[k] || 0) + statAcc; LS.set('statsDays', days);
    const s = bs(statBook); s.listenedMs = (s.listenedMs || 0) + statAcc; saveBs(statBook);
  }
  statAcc = 0;
}
function pageStats(el) {
  statFlush();
  const days = LS.get('statsDays', {}), total = Object.values(days).reduce((a, x) => a + x, 0);
  const today = new Date(); today.setHours(12, 0, 0, 0);
  const last14 = []; for (let i = 13; i >= 0; i--) { const d = new Date(today); d.setDate(d.getDate() - i); last14.push({ d, ms: days[dayKey(d)] || 0 }); }
  const week = last14.slice(7).reduce((a, x) => a + x.ms, 0), max = Math.max(...last14.map((x) => x.ms), 60000);
  let chDone = 0, started = 0, finished = 0;
  const per = books.map((b) => {
    const s = bs(b.id), done = b.chapters.filter((c) => s.done[c.n]).length;
    chDone += done;
    if (done || Object.values(s.pos).some((p) => p > 0) || s.last != null) started++;
    if (b.chapters.length && done === b.chapters.length && !(b.total > b.chapters.length)) finished++;   // all voiced and nothing left to voice
    return { b, s, done };
  });
  const tiles = [['Всего', dur(total)], ['Сегодня', dur(days[dayKey()] || 0)], ['За 7 дней', dur(week)],
    ['Глав прослушано', chDone], ['Книг начато', started], ['Книг закончено', finished]];
  el.innerHTML = `${settings.incognito ? '<div class="note">Включён режим инкогнито — время прослушивания сейчас не учитывается.</div>' : ''}
    <div class="tiles">${tiles.map(([k, v]) => `<div><b>${v}</b><span>${k}</span></div>`).join('')}</div>
    <div class="grp">Последние 14 дней, минут в день</div>
    <div class="chart" role="img" aria-label="Минуты прослушивания по дням">${last14.map((x, i) => `<div class="col ${i === 13 ? 'today' : ''}" title="${x.d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' })}: ${dur(x.ms)}">
      <span class="v">${x.ms >= 60000 ? Math.round(x.ms / 60000) : ''}</span><i style="height:${Math.max(x.ms ? 2 : 0, Math.round((x.ms / max) * 100))}%"></i><span class="d">${x.d.getDate()}</span></div>`).join('')}</div>
    <div class="grp">По книгам</div>
    <ul class="chs" id="stBooks">${per.map(({ b, s, done }) => `<li class="ch" data-book="${esc(b.id)}"><img class="thumb" src="${esc(b.coverSm || b.cover)}" alt="">
      <div class="main"><div class="t1"><span>${esc(b.title)}</span></div>
      <div class="pbar" style="margin:7px 0 0"><i style="width:${b.chapters.length ? Math.round((done / b.chapters.length) * 100) : 0}%"></i></div>
      <div class="t2">${done} из ${b.chapters.length} ${plural(b.chapters.length, 'главы', 'глав', 'глав')} · слушали ${dur(s.listenedMs || 0)}</div></div></li>`).join('')}</ul>`;
  $$('#stBooks .ch', el).forEach((li) => (li.onclick = () => go('#/book/' + li.dataset.book)));
}

// ---------- Sync (format: SYNC_FORMAT.md, shared with the Android app) ----------
const SITE = () => new URL('./', location.href).href;
const bkey = (id) => SITE() + '|' + id;
const splitKey = (k) => { const i = String(k).lastIndexOf('|'); return i < 0 ? null : [k.slice(0, i), k.slice(i + 1)]; };
const unionNames = (a, b) => { const out = [...(a || [])]; (b || []).forEach((x) => { if (typeof x === 'string' && x.trim() && !out.includes(x)) out.push(x); }); return out; };
function knownBookIds() {
  const ids = new Set(books.map((b) => b.id));
  for (let i = 0; i < localStorage.length; i++) { const k = localStorage.key(i); if (k && k.startsWith('book:')) ids.add(k.slice(5)); }
  return [...ids];
}
function toEntry(id) {
  const s = bs(id), progress = {};
  new Set([...Object.keys(s.pos), ...Object.keys(s.done), ...Object.keys(s.ts)]).forEach((n) => {
    progress[n] = { pos: Math.round((s.pos[n] || 0) * 10) / 10, done: !!s.done[n], ts: s.ts[n] || 0 }; });
  const e = { inLibrary: true, added: s.added || 0, categories: [...s.cats] };
  if (s.last != null) e.last = { n: s.last, ts: s.lastTs || 0 };
  return Object.assign(e, { progress, bookmarks: s.bm.map((m) => ({ ...m })), listenedMs: Math.round(s.listenedMs || 0) });
}
function fromEntry(id, e) {
  const s = bs(id); s.pos = {}; s.done = {}; s.ts = {};
  Object.entries(e.progress || {}).forEach(([n, p]) => { if (p.pos > 0) s.pos[n] = +p.pos; if (p.done) s.done[n] = true; if (p.ts) s.ts[n] = +p.ts; });
  s.cats = [...(e.categories || [])]; s.added = e.added || s.added || 0;
  if (e.last) { s.last = +e.last.n; s.lastTs = +e.last.ts || 0; }
  s.bm = (e.bookmarks || []).map((m) => ({ n: +m.n, pos: +m.pos || 0, quote: String(m.quote || ''), note: String(m.note || ''), ts: +m.ts || 0 }));
  s.listenedMs = e.listenedMs || 0; saveBs(id);
}
function mergeEntry(a, b, st) {   // a = local, b = file; both in sync-file shape
  a = a || {}; b = b || {};
  const out = { inLibrary: !!(a.inLibrary || b.inLibrary) };
  const ad = [a.added, b.added].map(Number).filter((x) => x > 0); out.added = ad.length ? Math.min(...ad) : 0;
  out.categories = unionNames(a.categories, b.categories);
  const la = a.last, lb = b.last, last = !la ? lb : !lb ? la : (+lb.ts || 0) > (+la.ts || 0) ? lb : la;
  if (last) out.last = { n: +last.n, ts: +last.ts || 0 };
  out.progress = { ...(a.progress || {}) };
  for (const [n, p] of Object.entries(b.progress || {})) {
    if (!p) continue;
    const q = out.progress[n], tp = +p.ts || 0, tq = q ? +q.ts || 0 : -1;
    if (!q || tp > tq || (tp === tq && p.done && !q.done)) { out.progress[n] = { pos: +p.pos || 0, done: !!p.done, ts: tp }; st.progress++; }
  }
  const bmKey = (x) => `${x.n}|${Math.round(+x.pos || 0)}|${x.quote || ''}`;
  const better = (x, y) => { const nx = !!String(x.note || '').trim(), ny = !!String(y.note || '').trim(); return nx !== ny ? nx : (+x.ts || 0) > (+y.ts || 0); };
  const map = new Map(); (a.bookmarks || []).forEach((x) => map.set(bmKey(x), x));
  (b.bookmarks || []).forEach((x) => { if (!x) return; const k = bmKey(x), y = map.get(k); if (!y) { map.set(k, x); st.bookmarks++; } else if (better(x, y)) map.set(k, x); });
  out.bookmarks = [...map.values()];
  out.listenedMs = Math.max(+a.listenedMs || 0, +b.listenedMs || 0);
  return out;
}
function buildSync() {
  statFlush();
  const foreign = LS.get('syncForeign', { books: {}, history: [] });
  const out = { unison: 1, exported: Date.now(), device: 'Web', categories: getCats(), books: { ...foreign.books }, history: [], statsDays: LS.get('statsDays', {}) };
  knownBookIds().forEach((id) => (out.books[bkey(id)] = toEntry(id)));
  out.history = [...hist.map((h) => ({ book: bkey(h.b), n: h.n, ts: h.ts })), ...foreign.history].sort((x, y) => y.ts - x.ts).slice(0, 300);
  return out;
}
function importSync(f) {
  if (!f || f.unison !== 1 || typeof f.books !== 'object') throw new Error('format');
  const st = { books: 0, progress: 0, bookmarks: 0, history: 0, categories: 0 }, site = SITE();
  const foreign = LS.get('syncForeign', { books: {}, history: [] });
  const cats0 = getCats(); let cats = unionNames(cats0, f.categories);
  for (const [k, e] of Object.entries(f.books || {})) {
    const p = splitKey(k); if (!p || !e) continue;
    st.books++;
    if (p[0] === site) { fromEntry(p[1], mergeEntry(toEntry(p[1]), e, st)); cats = unionNames(cats, bs(p[1]).cats); }
    else foreign.books[k] = mergeEntry(foreign.books[k], e, st);
  }
  st.categories = cats.length - cats0.length; saveCats(cats);
  const all = new Map(), put = (h) => { const k = h.book + '\n' + h.n, o = all.get(k); if (!o || h.ts > o.ts) all.set(k, h); };
  [...hist.map((h) => ({ book: bkey(h.b), n: h.n, ts: h.ts })), ...foreign.history].forEach(put);
  const before = all.size;
  (f.history || []).forEach((h) => { if (h && h.book && h.n != null) put({ book: String(h.book), n: +h.n, ts: +h.ts || 0 }); });
  st.history = all.size - before;
  hist = []; foreign.history = [];
  [...all.values()].sort((x, y) => y.ts - x.ts).slice(0, 300).forEach((h) => {
    const p = splitKey(h.book); if (p && p[0] === site) hist.push({ b: p[1], n: h.n, ts: h.ts }); else foreign.history.push(h); });
  saveHist();
  const days = LS.get('statsDays', {});
  for (const [d, ms] of Object.entries(f.statsDays || {})) if ((+ms || 0) > (days[d] || 0)) days[d] = +ms;
  LS.set('statsDays', days); LS.set('syncForeign', foreign);
  cur = null;   // force the reader to reopen the chapter with the merged position
  return st;
}
function pageSync(el) {
  const last = LS.get('lastExport', 0);
  el.innerHTML = `<div class="prose"><p>Файл прогресса переносит позиции глав, отметки «прослушано», закладки, категории, историю и статистику между сайтом и приложением для Android.
    При загрузке данные объединяются, ничего не теряется: для каждой главы берётся более свежая позиция.</p></div>
    <button class="row" id="syExp"><span class="ico">${ic('download')}</span><span class="txt">Сохранить файл прогресса<small>${last ? 'Последний раз: ' + new Date(last).toLocaleString('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }) : 'JSON-файл, который можно открыть в приложении'}</small></span></button>
    <label class="row" for="syFile"><span class="ico">${ic('upload')}</span><span class="txt">Загрузить файл<small>Объединить с прогрессом на этом устройстве</small></span></label>
    <input type="file" id="syFile" accept=".json,application/json" hidden><div id="syRes"></div>`;
  $('#syExp').onclick = () => {
    const blob = new Blob([JSON.stringify(buildSync(), null, 1)], { type: 'application/json' });
    const d = new Date(), p2 = (x) => String(x).padStart(2, '0');
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
    a.download = `unison-progress_${dayKey(d)}_${p2(d.getHours())}-${p2(d.getMinutes())}.json`;
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    LS.set('lastExport', Date.now()); toast('Файл прогресса сохранён');
  };
  $('#syFile').onchange = async (e) => {
    const file = e.target.files[0]; if (!file) return;
    try {
      const st = importSync(JSON.parse(await file.text()));
      const msg = `Объединено: глав обновлено — ${st.progress}, новых закладок — ${st.bookmarks}, записей истории — ${st.history}, новых категорий — ${st.categories}`;
      $('#syRes').innerHTML = `<div class="note">${esc(msg)}<br><small>Книг в файле: ${st.books}</small></div>`; toast(msg, 5000);
    } catch { toast('Это не файл прогресса Unison'); }
    e.target.value = '';
  };
}

// ---------- Bookmarks page ----------
let pendingJump = null;   // {b, n, pos} or {b, n, pi, ti, te}: applied when the reader opens that chapter
function subBookmarks(el, b) {
  const s = bs(b.id), list = s.bm.map((m, i) => ({ m, i })).sort((x, y) => x.m.n - y.m.n || x.m.pos - y.m.pos);
  if (!list.length) { el.innerHTML = `<div class="empty"><span class="big">🔖</span>Закладок пока нет.<br>В режиме чтения нажмите на значок закладки вверху или удерживайте слово.</div>`; return; }
  let html = '', lastN = null;
  list.forEach(({ m, i }) => {
    const c = b.chapters.find((x) => x.n === m.n);
    if (m.n !== lastN) { html += `<div class="grp">${esc(c ? chName(c) : 'Глава ' + m.n)}</div>`; lastN = m.n; }
    html += `<div class="bmk" data-i="${i}"><div class="main"><div class="q">«${esc(m.quote)}»</div>${m.note ? `<div class="nt">${esc(m.note)}</div>` : ''}
      <div class="t2">${fmt(m.pos)} · ${new Date(m.ts).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })}</div></div>
      <button class="ib" data-a="note" aria-label="Заметка">${ic('edit')}</button><button class="ib" data-a="del" aria-label="Удалить">${ic('del')}</button></div>`;
  });
  el.innerHTML = html;
  $$('.bmk', el).forEach((row) => (row.onclick = (e) => {
    const i = +row.dataset.i, m = s.bm[i], a = e.target.closest('[data-a]')?.dataset.a;
    if (a === 'del') { s.bm.splice(i, 1); saveBs(b.id); subBookmarks(el, b); toast('Закладка удалена'); return; }
    if (a === 'note') { promptDlg('Заметка', m.note || '', 'Сохранить', (v) => { m.note = v; m.ts = Date.now(); saveBs(b.id); subBookmarks(el, b); }, { multiline: true, placeholder: 'Ваша заметка к закладке' }); return; }
    pendingJump = { b: b.id, n: m.n, pos: m.pos }; go(`#/read/${b.id}/${m.n}`);
  }));
}

// ---------- Characters page ----------
let sample = null;
function stopSample() { if (!sample) return; sample.a.pause(); if (sample.btn.isConnected) sample.btn.innerHTML = ic('play'); sample = null; }
function chRanges(a) {
  if (!a?.length) return '';
  const s = [...a].sort((x, y) => x - y), out = []; let st = s[0], pr = s[0];
  for (const n of [...s.slice(1), Infinity]) { if (n === pr + 1) { pr = n; continue; } out.push(st === pr ? `${st}` : `${st}–${pr}`); st = pr = n; }
  return (s.length === 1 ? 'глава ' : 'главы ') + out.join(', ');
}
async function subChars(el, b) {
  el.innerHTML = '<div class="empty">Загрузка…</div>';
  const list = await fetchJsonFresh(b.data + 'characters.json');
  if (route.v !== 'sub' || route.kind !== 'chars') return;
  if (!list) { el.innerHTML = '<div class="empty">Список персонажей недоступен без интернета.</div>'; return; }
  el.innerHTML = `<div class="bar-sub">${list.length} ${plural(list.length, 'персонаж', 'персонажа', 'персонажей')} · нажмите ▶, чтобы послушать</div>
    <ul class="chs" id="chars">${list.map((c, i) => `<li class="ch" data-i="${i}"><div class="av" style="--c:var(--r${(i % 9) + 1})">${esc((c.name || '?')[0])}</div>
      <div class="main"><div class="t1"><span>${esc(c.name)}</span>${c.narrator ? '<em class="tag">рассказчик</em>' : ''}</div>
      <div class="t2">${esc(c.voice || '')}</div>
      <div class="t2 wrap">${c.lines} ${plural(c.lines, 'реплика', 'реплики', 'реплик')} · ${esc(chRanges(c.chapters))}</div></div>
      ${c.sample ? `<button class="dl" data-play aria-label="Прослушать образец" title="${esc(c.sampleText || '')}">${ic('play')}</button>` : ''}</li>`).join('')}</ul>`;
  $$('[data-play]', el).forEach((btn) => (btn.onclick = () => {
    if (sample && sample.btn === btn) { stopSample(); return; }
    stopSample(); if (!audio.paused) audio.pause();
    const c = list[+btn.closest('.ch').dataset.i], a = new Audio(b.data + c.sample);
    sample = { a, btn }; btn.innerHTML = ic('stop'); a.onended = stopSample;
    a.play().catch(() => { toast('Не удалось воспроизвести образец'); stopSample(); });
  }));
}

// ---------- Search ----------
function searchParas(paras, q, limit = 500) {
  const nq = norm(q.trim()), out = [];
  if (nq.length < 2) return out;
  paras.forEach((toks, pi) => {
    let text = ''; const starts = [];
    toks.forEach((tk, k) => { if (k) text += ' '; starts.push(text.length); text += typeof tk === 'string' ? tk : tk[0]; });
    const nt = norm(text), at = (i) => { let lo = 0, hi = starts.length - 1; while (lo < hi) { const m = (lo + hi + 1) >> 1; if (starts[m] <= i) lo = m; else hi = m - 1; } return lo; };
    for (let i = nt.indexOf(nq); i >= 0 && out.length < limit; i = nt.indexOf(nq, i + nq.length)) {
      const a = Math.max(0, i - 45), z = Math.min(text.length, i + nq.length + 70);
      out.push({ pi, ti: at(i), te: at(i + nq.length - 1),
        snip: (a ? '…' : '') + esc(text.slice(a, i)) + '<mark>' + esc(text.slice(i, i + nq.length)) + '</mark>' + esc(text.slice(i + nq.length, z)) + (z < text.length ? '…' : '') });
    }
  });
  return out;
}
const chText = new Map();   // "book:n" -> [[word...]...] (session cache for book-wide search)
async function loadChText(b, n) {
  const k = key(b.id, n); if (chText.has(k)) return chText.get(k);
  if (cur && curBook === b && cur.n === n) { const t = cur.paras.map((p) => p.map((x) => x[0])); chText.set(k, t); return t; }
  const url = chUrl(b, n, 'json'); let j = null;
  try { const hit = await (await caches.open(DATA_CACHE)).match(url); if (hit) j = await hit.json(); } catch {}
  if (!j) try { const r = await fetch(url); if (r.ok) j = await r.json(); } catch {}
  const t = j ? j.paras.map((p) => p.map((x) => x[0])) : null; if (t) chText.set(k, t); return t;
}
const bsearch = { b: null, q: '', gen: 0 };
function subSearch(el, b) {
  el.innerHTML = `<form class="sbox" id="bsForm">${ic('search')}<input id="bsQ" type="search" placeholder="Слово или фраза" enterkeyhint="search" autocomplete="off"
      value="${esc(bsearch.b === b.id ? bsearch.q : '')}"><button class="ib" type="submit" aria-label="Искать">${ic('go')}</button></form>
    <div class="bar-sub" id="bsStat">Поиск по тексту всех глав. Скачанные главы ищутся и без интернета.</div><div class="pbar" id="bsBar" hidden><i></i></div><div id="bsRes"></div>`;
  $('#bsForm').onsubmit = (e) => { e.preventDefault(); $('#bsQ').blur(); runBookSearch(b, $('#bsQ').value); };
  if (bsearch.b === b.id && bsearch.q) runBookSearch(b, bsearch.q); else $('#bsQ').focus();
}
async function runBookSearch(b, q) {
  q = q.trim(); const gen = ++bsearch.gen; bsearch.b = b.id; bsearch.q = q;
  const res = $('#bsRes'), stat = $('#bsStat'), pb = $('#bsBar');
  if (norm(q).length < 2) { stat.textContent = 'Введите хотя бы две буквы'; res.innerHTML = ''; return; }
  res.innerHTML = ''; pb.hidden = false;
  const chs = [...b.chapters], found = []; let done = 0, miss = 0, hits = 0, paintT = 0;
  const paint = () => {
    if (gen !== bsearch.gen || !res.isConnected) return;
    stat.textContent = (done < chs.length ? `Просмотрено ${done} из ${chs.length}` : `Готово: ${chs.length} ${plural(chs.length, 'глава', 'главы', 'глав')}`) +
      ` · найдено ${hits}` + (miss ? ` · недоступно без интернета: ${miss}` : '');
    $('i', pb).style.width = Math.round((done / chs.length) * 100) + '%'; pb.hidden = done >= chs.length;
    found.sort((x, y) => x.c.n - y.c.n);
    res.innerHTML = found.map(({ c, r }) => `<div class="grp">${esc(chName(c))} · ${r.length}</div>` + r.slice(0, 30).map((x, i) =>
      `<button class="hit" data-n="${c.n}" data-i="${i}">${x.snip}</button>`).join('') + (r.length > 30 ? `<div class="bar-sub">и ещё ${r.length - 30}…</div>` : '')).join('') ||
      (done >= chs.length ? '<div class="empty">Ничего не найдено.</div>' : '');
  };
  const worker = async () => {
    while (gen === bsearch.gen) {
      const c = queue.shift(); if (!c) return;
      const paras = await loadChText(b, c.n); if (gen !== bsearch.gen) return;
      done++;
      if (!paras) miss++; else { const r = searchParas(paras, q, 200); if (r.length) { hits += r.length; found.push({ c, r }); } }
      if (Date.now() - paintT > 120 || done === chs.length) { paintT = Date.now(); paint(); }
    }
  };
  const queue = [...chs];
  res.onclick = (e) => {
    const h = e.target.closest('.hit'); if (!h) return;
    const f = found.find((x) => x.c.n === +h.dataset.n), r = f.r[+h.dataset.i];
    pendingJump = { b: b.id, n: f.c.n, pi: r.pi, ti: r.ti, te: r.te }; go(`#/read/${b.id}/${f.c.n}`);
  };
  paint();
  await Promise.all([worker(), worker(), worker(), worker()]);
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
let tokEls = [], sents = [], hlEls = [], roleFocus = -1, readOnly = false, noScrollUntil = 0;
const roleColor = (i) => (i === 0 ? '' : `var(--r${((i - 1) % 9) + 1})`);
$('#rBack').innerHTML = ic('back'); $('#rSearch').innerHTML = ic('search'); $('#rMenu').innerHTML = ic('vert'); $('#play').innerHTML = ic('play');
$('#rBack').onclick = () => (appNav > 0 ? history.back() : go('#/book/' + (curBook?.id || 'hpmor'), true));

async function getRes(url) {
  try { const hit = await (await caches.open(DATA_CACHE)).match(url); if (hit) return hit; } catch {}
  const r = await fetch(url); if (!r.ok) throw new Error(r.status + ' ' + url); return r;
}
const bookSpeed = () => (settings.perBookSpeed && curBook && bs(curBook.id).speed) || settings.speed;
function takeJump(b, n) { const j = pendingJump; if (j && j.b === b && j.n === n) { pendingJump = null; return j; } return null; }
async function renderReader() {
  $('#v-reader').hidden = false;
  const b = bookById(route.b), meta = b?.chapters.find((c) => c.n === route.n);
  if (!meta) { toast('Глава не найдена'); go('#/library', true); return; }
  if (cur && curBook === b && cur.n === route.n && audio.src) { const j = takeJump(b.id, cur.n); if (j) applyJump(j); paintBm(); return; }  // already open
  if (readOnly && cur) syncToFirstVisible();
  saveNow(); statFlush();
  let data;
  try { data = await (await getRes(chUrl(b, route.n, 'json'))).json(); }
  catch { toast('Глава не скачана, а интернета нет'); return; }
  cur = data; curBook = b; roleFocus = -1;
  if (!settings.incognito) {
    const s = bs(b.id); s.last = cur.n; s.lastTs = Date.now(); saveBs(b.id);
    hist = [{ b: b.id, n: cur.n, ts: Date.now() }, ...hist.filter((h) => !(h.b === b.id && h.n === cur.n))]; saveHist();
  }
  $('#rTitle').textContent = cur.title;
  buildText();
  const nav = navChapters(), i = nav.findIndex((c) => c.n === cur.n);
  $('#prevCh').disabled = i <= 0; $('#nextCh').disabled = i >= nav.length - 1;
  if (audioUrl) URL.revokeObjectURL(audioUrl);
  try { audioUrl = URL.createObjectURL(await (await getRes(chUrl(b, cur.n, 'ogg'))).blob()); }
  catch { toast('Аудио не скачано, а интернета нет'); return; }
  audio.src = audioUrl; audio.defaultPlaybackRate = audio.playbackRate = bookSpeed(); setSpeedLabel();
  const s = bs(b.id), pos = s.done[cur.n] ? 0 : s.pos[cur.n] || 0, auto = autoplayNext; autoplayNext = false;
  audio.addEventListener('loadedmetadata', () => {
    const j = takeJump(b.id, cur.n);
    audio.currentTime = j && j.pos != null ? j.pos : pos > cur.duration - 3 ? 0 : pos; tick(true); scrollToActive(true);
    if (j && j.pi != null) applyJump(j);
    if (auto) audio.play();
  }, { once: true });
  setMediaSession(); paintBm(); window.scrollTo(0, 0);
}
function buildText() {
  const art = $('#text'); art.innerHTML = ''; words = []; active = -1; tokEls = []; sents = []; hlEls = [];
  const legend = document.createElement('div'); legend.className = 'legend';
  cur.roles.forEach((r, i) => { const x = document.createElement('button'); x.type = 'button'; x.textContent = '● ' + r; x.dataset.role = i;
    if (i) x.style.color = roleColor(i); legend.appendChild(x); });
  const endRe = /[.!?…]["»”)\]]*$/;
  cur.paras.forEach((toks, pi) => {
    const el = document.createElement(pi === 0 ? 'h2' : 'p'), frag = document.createDocumentFragment(), row = [];
    let si = -1, fresh = true;
    toks.forEach(([w, t, r], k) => {
      if (fresh) { sents.push({ els: [] }); si = sents.length - 1; fresh = false; }
      const sp = document.createElement('span'); sp.textContent = w; sp.dataset.r = r || 0; sp._s = si; sp._p = pi; sp._k = k;
      if (r) sp.style.color = roleColor(r);
      if (t != null) { sp.dataset.t = t; words.push({ t, el: sp }); }
      sents[si].els.push(sp); row.push(sp);
      frag.appendChild(sp); if (k < toks.length - 1) frag.appendChild(document.createTextNode(' '));
      if (pi > 0 && endRe.test(w)) fresh = true;
    });
    tokEls.push(row); el.appendChild(frag); art.appendChild(el); if (pi === 0) art.appendChild(legend);
  });
  words.sort((a, c) => a.t - c.t);
}
let autoplayNext = false;
function leaveReader() { if (readOnly) syncToFirstVisible(); setReadOnly(false, true); saveNow(); audio.pause(); statFlush(); setZen(false); }
function findWord(t) { let lo = 0, hi = words.length - 1, ans = -1; while (lo <= hi) { const m = (lo + hi) >> 1; if (words[m].t <= t + 0.05) { ans = m; lo = m + 1; } else hi = m - 1; } return ans; }
function paintHl() {
  hlEls.forEach((e) => e.classList.remove('on', 'son')); hlEls = [];
  if (active < 0 || !words[active]) return;
  const el = words[active].el;
  if (settings.hlSent && sents[el._s]) { hlEls = [...sents[el._s].els]; hlEls.forEach((e) => e.classList.add('son')); }
  else { hlEls = [el]; el.classList.add('on'); }
}
function tick(force) {
  if (!cur) return;
  const t = audio.currentTime, i = findWord(t);
  if (i !== active || force) { active = i; paintHl(); if (i >= 0) scrollToActive(false); }
  $('#time').textContent = fmt(t) + ' / ' + fmt(cur.duration);
  if (!seeking) $('#seek').value = Math.round((t / cur.duration) * 1000);
}
function scrollToActive(force) {
  if (active < 0 || route.v !== 'read' || readOnly || Date.now() < noScrollUntil) return;
  const el = words[active].el;
  if (!force && Date.now() < followUntil) { $('#follow').hidden = false; return; }
  $('#follow').hidden = true;
  const r = el.getBoundingClientRect(), top = 70, bottom = innerHeight - 170;
  if (force || r.top < top || r.bottom > bottom) { programmaticScroll = Date.now(); scrollBy({ top: r.top - innerHeight * 0.33, behavior: force ? 'auto' : 'smooth' }); }
}
const userScrolled = () => { if (Date.now() - programmaticScroll > 900 && !audio.paused) followUntil = Date.now() + 8000; };
addEventListener('touchmove', userScrolled, { passive: true });
addEventListener('wheel', userScrolled, { passive: true });
$('#follow').onclick = () => { followUntil = 0; noScrollUntil = 0; scrollToActive(true); };
function loop() { tick(false); raf = requestAnimationFrame(loop); }
audio.addEventListener('play', () => {
  $('#play').innerHTML = ic('pause'); cancelAnimationFrame(raf); loop(); updateWake();
  if (readOnly) setReadOnly(false);
  const lp = LS.get('pauseTs', 0);
  if (settings.rewindSec && lp && Date.now() - lp > settings.rewindAfter * 60000 && audio.currentTime > 1) {
    audio.currentTime = Math.max(0, audio.currentTime - settings.rewindSec); toast(`Отмотано на ${settings.rewindSec} с назад`);
  }
  LS.set('pauseTs', 0);
  statLast = Date.now(); if (curBook) statBook = curBook.id;
  if (settings.sleepAuto && !sleep.on && inSleepWindow()) startSleep(settings.sleepMin, true);
});
audio.addEventListener('pause', () => {
  $('#play').innerHTML = ic('play'); cancelAnimationFrame(raf); saveNow(); updateWake();
  if (cur) LS.set('pauseTs', Date.now());
  if (statLast) statAcc += Math.min(Date.now() - statLast, 3000); statLast = 0; statFlush();
});
audio.addEventListener('timeupdate', () => { if (document.hidden) tick(false); if (Math.floor(audio.currentTime) % 5 === 0) saveNow(); if (!audio.paused) statTick(); });
audio.addEventListener('seeked', () => { if (cur) { tick(true); if (audio.paused) scrollToActive(true); } });
audio.addEventListener('ended', () => {
  if (!cur || !curBook) return;
  writeProgress(curBook.id, cur.n, 0, true);
  if (sleep.on && sleep.eoc) { stopSleep(); toast('Таймер сна: глава закончилась'); return; }
  const nav = navChapters(), next = nav[nav.findIndex((c) => c.n === cur.n) + 1];
  if (next && route.v === 'read') { autoplayNext = true; go(`#/read/${curBook.id}/${next.n}`, true); }
});
function saveNow() {
  if (!cur || !curBook || !audio.src || isNaN(audio.currentTime)) return;
  writeProgress(curBook.id, cur.n, audio.currentTime, audio.currentTime > cur.duration * 0.97 ? true : null);
}
document.addEventListener('visibilitychange', () => { if (document.hidden) { saveNow(); statFlush(); } else if (cur && route.v === 'read') tick(true); updateWake(); });

// taps: legend = focus role, read-only = page turn, zen = show UI, word = seek
let wordLpAt = 0;
$('#v-reader').addEventListener('click', (e) => {
  if (Date.now() - wordLpAt < 700) return;
  const lg = e.target.closest('.legend [data-role]'); if (lg) { toggleRole(+lg.dataset.role); return; }
  if (readOnly) {
    if (e.target.closest('#rbar, .chnav, button')) return;
    const x = e.clientX / innerWidth; if (x < 0.25) pageTurn(-1); else if (x > 0.75) pageTurn(1);
    return;
  }
  if (!e.target.closest('#text')) return;
  if (document.body.classList.contains('zen')) { setZen(false); return; }
  const t = e.target.dataset && e.target.dataset.t; if (t == null) return;
  audio.currentTime = +t; followUntil = 0; noScrollUntil = 0; tick(true); if (audio.paused) audio.play();
});
function pageTurn(d) { const h = innerHeight - ($('#rbar').offsetHeight || 60) - 40; scrollBy({ top: d * h, behavior: 'smooth' }); }
function toggleRole(i) {
  roleFocus = roleFocus === i ? -1 : i;
  $$('#text p span, #text h2 span').forEach((sp) => sp.classList.toggle('dim', roleFocus >= 0 && +sp.dataset.r !== roleFocus));
  $$('#text .legend [data-role]').forEach((x) => x.classList.toggle('on', +x.dataset.role === roleFocus));
  if (roleFocus >= 0) toast(`Выделены реплики: ${cur.roles[roleFocus]}. Нажмите ещё раз, чтобы снять`);
}

// long press / right click on a word
(() => {
  const text = $('#text'); let t = 0, x0 = 0, y0 = 0;
  const open = (sp) => { wordLpAt = Date.now(); wordMenu(sp); };
  text.addEventListener('pointerdown', (e) => {
    const sp = e.target.closest('#text p span, #text h2 span'); if (!sp || e.pointerType === 'mouse') return;
    x0 = e.clientX; y0 = e.clientY; clearTimeout(t); t = setTimeout(() => { navigator.vibrate?.(15); open(sp); }, 520);
  });
  text.addEventListener('pointermove', (e) => { if (Math.hypot(e.clientX - x0, e.clientY - y0) > 10) clearTimeout(t); });
  ['pointerup', 'pointercancel'].forEach((ev) => text.addEventListener(ev, () => clearTimeout(t)));
  text.addEventListener('contextmenu', (e) => {
    const sp = e.target.closest('#text p span, #text h2 span'); if (!sp) return;
    e.preventDefault(); clearTimeout(t); if (Date.now() - wordLpAt < 1500) return; open(sp);
  });
})();
const sentText = (si) => (sents[si] ? sents[si].els.map((e) => e.textContent).join(' ') : '');
function wordMenu(sp) {
  const q = sentText(sp._s), w = sp.textContent.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '');
  menu(sp, [
    ['Копировать предложение', () => copyText(q).then((ok) => toast(ok ? 'Предложение скопировано' : 'Не удалось скопировать'))],
    ['Поделиться цитатой', () => shareQuote(q)],
    ['Поделиться картинкой', () => shareImage(q)],
    [`Словарь: «${w}»`, () => window.open('https://ru.wiktionary.org/wiki/' + encodeURIComponent(w.toLowerCase()), '_blank', 'noopener'), !w],
    ['Закладка на это предложение', () => addBookmark(sp._s)],
    ['Слушать отсюда', () => { const tt = firstTime(sp._p, sp._k); if (tt != null) { if (readOnly) setReadOnly(false, true); audio.currentTime = tt; followUntil = 0; noScrollUntil = 0; tick(true); audio.play(); } }],
  ], { below: true });
  sents[sp._s]?.els.forEach((e) => e.classList.add('picked'));
}
const quoteCredit = () => `${curBook.author}, «${curBook.title}»`;
async function shareQuote(q) {
  const text = `«${q}»\n\n— ${quoteCredit()}`;
  if (navigator.share) { try { await navigator.share({ text }); return; } catch (e) { if (e.name === 'AbortError') return; } }
  toast((await copyText(text)) ? 'Цитата скопирована' : 'Не удалось поделиться');
}
const loadImg = (src) => new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; });
function drawCover(x, img, dx, dy, dw, dh) {
  const s = Math.max(dw / img.width, dh / img.height), sw = dw / s, sh = dh / s;
  x.drawImage(img, (img.width - sw) / 2, (img.height - sh) / 2, sw, sh, dx, dy, dw, dh);
}
function wrapText(x, text, maxW) {
  const out = []; let line = '';
  text.split(/\s+/).forEach((w) => { const t = line ? line + ' ' + w : w; if (x.measureText(t).width > maxW && line) { out.push(line); line = w; } else line = t; });
  if (line) out.push(line); return out;
}
async function shareImage(q) {
  const b = curBook, W = 1080, H = 1350, c = document.createElement('canvas'); c.width = W; c.height = H;
  const x = c.getContext('2d'), img = await loadImg(b.cover).catch(() => null);
  x.fillStyle = '#1a1520'; x.fillRect(0, 0, W, H);
  if (img) { x.save(); x.filter = 'blur(36px) brightness(.4)'; drawCover(x, img, -80, -80, W + 160, H + 160); x.restore(); }
  const g = x.createLinearGradient(0, 0, 0, H); g.addColorStop(0, 'rgba(26,21,32,.35)'); g.addColorStop(1, 'rgba(26,21,32,.95)'); x.fillStyle = g; x.fillRect(0, 0, W, H);
  x.fillStyle = '#e8417a'; x.font = 'bold 200px Georgia, serif'; x.fillText('«', 70, 250);
  let fs = 64, lines = [];
  for (; fs >= 30; fs -= 2) { x.font = `${fs}px Georgia, "PT Serif", serif`; lines = wrapText(x, q, W - 160); if (lines.length * fs * 1.38 <= 700) break; }
  const maxL = Math.floor(700 / (fs * 1.38));
  if (lines.length > maxL) { lines = lines.slice(0, maxL); lines[maxL - 1] += '…'; }
  x.fillStyle = '#fff'; lines.forEach((l, i) => x.fillText(l, 80, 300 + fs + i * fs * 1.38));
  const fy = H - 330;
  x.fillStyle = 'rgba(255,255,255,.18)'; x.fillRect(80, fy - 36, W - 160, 2);
  if (img) drawCover(x, img, 80, fy, 154, 230);
  x.fillStyle = '#fff'; x.font = '600 40px system-ui, sans-serif';
  const tl = wrapText(x, b.title, W - 160 - 190).slice(0, 3); tl.forEach((l, i) => x.fillText(l, 270, fy + 44 + i * 50));
  x.fillStyle = '#c9bccd'; x.font = '32px system-ui, sans-serif'; x.fillText(b.author || '', 270, fy + 44 + tl.length * 50 + 10);
  x.fillStyle = '#e8417a'; x.font = '600 30px system-ui, sans-serif'; x.fillText('Unison · книги в несколько голосов', 80, H - 50);
  const blob = await new Promise((r) => c.toBlob(r, 'image/png'));
  const file = new File([blob], 'unison-quote.png', { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], text: `«${q}» — ${quoteCredit()}` }); return; } catch (e) { if (e.name === 'AbortError') return; }
  }
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'unison-quote.png';
  document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 5000); toast('Картинка сохранена');
}

// time of the first timed token at/after (pi, k), else the closest before it
function firstTime(pi, k) {
  const row = tokEls[pi] || [];
  for (let i = k; i < row.length; i++) if (row[i].dataset.t != null) return +row[i].dataset.t;
  for (let i = k - 1; i >= 0; i--) if (row[i].dataset.t != null) return +row[i].dataset.t;
  for (let p = pi + 1; p < tokEls.length; p++) for (const e of tokEls[p]) if (e.dataset.t != null) return +e.dataset.t;
  return null;
}
function applyJump(j) {
  if (j.pos != null) { audio.currentTime = j.pos; followUntil = 0; noScrollUntil = 0; tick(true); scrollToActive(true); return; }
  const row = tokEls[j.pi]; if (!row || !row[j.ti]) return;
  const els = row.slice(j.ti, (j.te ?? j.ti) + 1), t = firstTime(j.pi, j.ti);
  if (t != null) { noScrollUntil = Date.now() + 1200; audio.currentTime = t; tick(true); }
  programmaticScroll = Date.now(); els[0].scrollIntoView({ block: 'center' });
  els.forEach((e) => e.classList.add('found')); setTimeout(() => els.forEach((e) => e.classList.remove('found')), 2600);
}
function firstVisibleWord() {
  const top = $('#rbar').getBoundingClientRect().bottom + 2;
  let lo = 0, hi = words.length - 1, ans = -1;
  while (lo <= hi) { const m = (lo + hi) >> 1; if (words[m].el.getBoundingClientRect().top >= top) { ans = m; hi = m - 1; } else lo = m + 1; }
  return ans >= 0 ? words[ans] : null;
}
function syncToFirstVisible() {
  const w = firstVisibleWord(); if (!w || !cur) return;
  noScrollUntil = Date.now() + 1200; audio.currentTime = w.t; tick(true); saveNow();
}
function setReadOnly(on, silent) {
  if (readOnly === on) return;
  if (!on && !silent) syncToFirstVisible();
  readOnly = on; document.body.classList.toggle('ro', on); $('#roChip').hidden = !on;
  if (on) { audio.pause(); $('#follow').hidden = true; if (!silent) toast('Только чтение: касание у левого или правого края листает страницу'); }
}
$('#roListen').onclick = () => { setReadOnly(false); audio.play(); };
$('#roExit').onclick = () => setReadOnly(false);
const navChapters = () => (settings.dlOnly ? curBook.chapters.filter((c) => c.n === cur.n || downloaded.has(key(curBook.id, c.n))) : curBook.chapters);

// bookmarks in the reader
function curSentence() { if (active >= 0 && words[active]) return words[active].el._s; const w = firstVisibleWord(); return w ? w.el._s : 0; }
function addBookmark(si = curSentence()) {
  if (!cur || !sents[si]) return;
  let quote = sentText(si); if (quote.length > 400) quote = quote.slice(0, 400) + '…';
  const e0 = sents[si].els.find((e) => e.dataset.t != null), pos = Math.round((e0 ? +e0.dataset.t : audio.currentTime) * 100) / 100;
  const s = bs(curBook.id);
  if (s.bm.some((m) => m.n === cur.n && Math.round(m.pos) === Math.round(pos) && m.quote === quote)) { toast('Такая закладка уже есть'); return; }
  s.bm.push({ n: cur.n, pos, quote, note: '', ts: Date.now() }); saveBs(curBook.id); paintBm(); toast('Закладка добавлена');
}
function paintBm() { if (!cur || !curBook) return; const has = bs(curBook.id).bm.some((m) => m.n === cur.n); $('#rBm').innerHTML = ic(has ? 'bm' : 'bmo'); $('#rBm').classList.toggle('acc', has); }
$('#rBm').onclick = (e) => {
  if (!cur) return;
  const s = bs(curBook.id), mine = s.bm.filter((m) => m.n === cur.n).sort((a, c) => a.pos - c.pos);
  menu(e.currentTarget, [['Добавить закладку здесь', () => addBookmark()],
    ...mine.map((m) => [`${fmt(m.pos)} · ${m.quote.length > 42 ? m.quote.slice(0, 42) + '…' : m.quote}`, () => applyJump({ pos: m.pos })]),
    [`Все закладки книги (${s.bm.length})`, () => go('#/bookmarks/' + curBook.id)]]);
};
// in-chapter search
$('#rSearch').onclick = () => {
  if (!cur) return;
  sheet(`<form class="sbox" id="csForm">${ic('search')}<input id="csQ" type="search" placeholder="Найти в главе" autocomplete="off" enterkeyhint="search"></form>
    <div class="bar-sub" id="csStat"></div><div id="csRes" class="hits"></div>
    <button class="opt" id="csBook"><span class="ico">${ic('search')}</span>Искать во всей книге</button>`, (s) => {
    let r = [], h = 0;
    const run = () => {
      r = searchParas(cur.paras, $('#csQ').value);
      $('#csStat').textContent = norm($('#csQ').value.trim()).length < 2 ? '' : r.length ? `Найдено: ${r.length}` : 'Ничего не найдено';
      $('#csRes').innerHTML = r.slice(0, 100).map((x, i) => `<button class="hit" data-i="${i}">${x.snip}</button>`).join('');
    };
    $('#csQ').oninput = () => { clearTimeout(h); h = setTimeout(run, 200); };
    $('#csForm').onsubmit = (e) => { e.preventDefault(); run(); $('#csQ').blur(); };
    $('#csRes').onclick = (e) => { const b = e.target.closest('.hit'); if (!b) return; closeOverlays(); applyJump(r[+b.dataset.i]); };
    $('#csBook').onclick = () => { bsearch.b = curBook.id; bsearch.q = $('#csQ').value.trim(); go('#/search/' + curBook.id); };
    setTimeout(() => $('#csQ').focus(), 50);
  });
};
// reading appearance quick sheet
$('#rAa').onclick = () => {
  const paint = (s) => { s.innerHTML = '<div class="grab"></div><h3>Оформление текста</h3>' + readingHtml(); bindSettings(s, () => paint(s)); };
  sheet('', paint); $('#scrim').className = 'light';
};
$('#rMenu').onclick = (e) => {
  if (!curBook) return;
  menu(e.currentTarget, [
    [readOnly ? 'Выйти из режима «Только читать»' : 'Только читать', () => setReadOnly(!readOnly)],
    [sleep.on ? `Таймер сна · ${sleepLabel()}` : 'Таймер сна…', sleepSheet],
    ['Поиск по книге', () => go('#/search/' + curBook.id)],
    [`Закладки книги (${bs(curBook.id).bm.length})`, () => go('#/bookmarks/' + curBook.id)],
    ['Действующие лица', () => go('#/chars/' + curBook.id)],
    [(settings.wake ? '✓ ' : '') + 'Не гасить экран', () => { settings.wake = !settings.wake; saveSettings(); updateWake();
      toast(settings.wake ? 'Экран не будет гаснуть во время прослушивания' : 'Экран гаснет как обычно'); }],
    ['Скрыть интерфейс', () => { setZen(true); toast('Нажмите на текст, чтобы вернуть интерфейс'); }],
  ]);
};

$('#play').onclick = () => (audio.paused ? audio.play() : audio.pause());
$('#rew').onclick = () => { audio.currentTime = Math.max(0, audio.currentTime - 15); followUntil = 0; tick(true); };
$('#fwd').onclick = () => { audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 15); followUntil = 0; tick(true); };
function setSpeedLabel() { $('#speed').textContent = audio.playbackRate.toString().replace('.', ',') + '×'; }
$('#speed').onclick = () => {
  const i = (SPEEDS.indexOf(audio.playbackRate) + 1) % SPEEDS.length, r = SPEEDS[i < 0 ? 2 : i];
  audio.playbackRate = audio.defaultPlaybackRate = r;
  if (settings.perBookSpeed && curBook) { bs(curBook.id).speed = r; saveBs(curBook.id); } else { settings.speed = r; saveSettings(); }
};
audio.addEventListener('ratechange', setSpeedLabel);
$('#seek').addEventListener('input', () => { seeking = true; $('#time').textContent = fmt(($('#seek').value / 1000) * cur.duration) + ' / ' + fmt(cur.duration); });
$('#seek').addEventListener('change', () => { audio.currentTime = ($('#seek').value / 1000) * cur.duration; seeking = false; followUntil = 0; tick(true); });
const neighbour = (d) => { const l = navChapters(), i = l.findIndex((c) => c.n === cur.n); return l[i + d]; };
$('#prevCh').onclick = () => { const p = neighbour(-1); if (p) go(`#/read/${curBook.id}/${p.n}`, true); };
$('#nextCh').onclick = () => { const p = neighbour(1); if (p) go(`#/read/${curBook.id}/${p.n}`, true); };

// ---------- sleep timer ----------
const sleep = { on: false, end: 0, eoc: false, iv: 0 };
function inSleepWindow(d = new Date()) {
  const m = (s) => { const [h, mm] = String(s).split(':').map(Number); return h * 60 + (mm || 0); };
  const a = m(settings.sleepFrom), b = m(settings.sleepTo), x = d.getHours() * 60 + d.getMinutes();
  return a <= b ? x >= a && x < b : x >= a || x < b;
}
function startSleep(min, auto) {
  sleep.on = true; sleep.eoc = min === 'eoc'; sleep.end = sleep.eoc ? 0 : Date.now() + min * 60000;
  if (!sleep.eoc && !auto) { settings.sleepMin = min; saveSettings(); }
  clearInterval(sleep.iv); sleep.iv = setInterval(sleepTick, 250); sleepTick();
  toast(auto ? `Таймер сна включён автоматически: ${min} мин` : sleep.eoc ? 'Остановлю в конце главы' : `Таймер сна: ${min} мин`);
}
function stopSleep() { sleep.on = false; clearInterval(sleep.iv); audio.volume = 1; paintSleep(); }
function sleepLeft() { return sleep.eoc ? (cur ? Math.max(0, ((audio.duration || cur.duration) - audio.currentTime) / (audio.playbackRate || 1)) : 0) : (sleep.end - Date.now()) / 1000; }
const sleepLabel = () => (sleep.eoc ? 'до конца главы' : fmt(Math.max(0, sleepLeft())));
function sleepTick() {
  if (!sleep.on) return;
  const left = sleepLeft();
  if (!sleep.eoc && left <= 0) { audio.pause(); stopSleep(); toast('Таймер сна: воспроизведение остановлено'); return; }
  audio.volume = !audio.paused && left < 10 ? Math.max(0, Math.min(1, left / 10)) : 1;
  paintSleep();
}
function paintSleep() { const el = $('#sleepInd'); el.hidden = !sleep.on; if (sleep.on) el.innerHTML = ic('moon') + `<span>${sleepLabel()}</span>`; }
$('#sleepInd').onclick = () => sleepSheet();
function sleepSheet() {
  sheet(`<h3>Таймер сна${sleep.on ? ' · ' + sleepLabel() : ''}</h3>
    <div class="chipsel">${[5, 10, 15, 20, 30, 45, 60, 90].map((m) => `<button data-m="${m}">${m} мин</button>`).join('')}<button data-m="eoc">До конца главы</button></div>
    ${sleep.on ? `<button class="opt" id="slOff"><span class="ico">${ic('close')}</span>Выключить таймер</button>` : ''}
    ${swHtml('sleepAuto', 'Включать автоматически', `с ${settings.sleepFrom} до ${settings.sleepTo}, на ${settings.sleepMin} мин`, 'moon')}
    <p class="hint">Последние 10 секунд звук плавно затихает. Время расписания — в настройках.</p>`, (s) => {
    $$('[data-m]', s).forEach((b) => (b.onclick = () => { closeOverlays(); startSleep(b.dataset.m === 'eoc' ? 'eoc' : +b.dataset.m); }));
    const off = $('#slOff', s); if (off) off.onclick = () => { closeOverlays(); stopSleep(); toast('Таймер сна выключен'); };
    bindSettings(s, () => { closeOverlays(); sleepSheet(); });
  });
}

// keep screen on while playing
let wakeLock = null;
async function updateWake() {
  const want = settings.wake && !audio.paused && !document.hidden && route.v === 'read';
  try {
    if (want && !wakeLock && 'wakeLock' in navigator) { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener('release', () => (wakeLock = null)); }
    else if (!want && wakeLock) { await wakeLock.release(); wakeLock = null; }
  } catch {}
}

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
  paintBadge(); paintModes();
})();
