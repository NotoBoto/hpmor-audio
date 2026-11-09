// Unison service worker: app shell precached; chapter files live in DATA (filled by the page's "Скачать").
const SHELL = 'unison-shell-v3';
const DATA = 'unison-data-v1';
const KEEP = [SHELL, DATA, 'hpmor-data-v1'];  // old data cache is migrated by the page, then deleted
const FILES = ['./', 'index.html', 'style.css', 'app.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'books.json'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(FILES.map((f) => new Request(f, { cache: 'reload' })))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => !KEEP.includes(k)).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

const isChapterFile = (p) => /\/ch\d+\.(json|ogg(\.\d+)?)$/.test(p);
const isList = (p) => /\/(index|books)\.json$/.test(p);
const isCover = (p) => /\/cover(-sm)?\.(jpg|jpeg|png|webp)$/.test(p);
const isPicture = (p) => /\/img\/[^/]+\.(jpg|jpeg|png|webp)$/.test(p);   // illustrations inside chapters

// stale-while-revalidate; always fetched with CORS (the host sends Access-Control-Allow-Origin: *),
// so one cached copy serves both <img> and the canvas of the quote picture
async function coverFromCache(url) {
  const c = await caches.open(SHELL), key = url.origin + url.pathname;
  const hit = await c.match(key);
  const net = fetch(key, { mode: 'cors' }).then((r) => { if (r.ok) c.put(key, r.clone()); return r; }).catch(() => hit);
  return hit || net;
}

self.addEventListener('fetch', (e) => {
  const req = e.request, url = new URL(req.url);
  if (req.method !== 'GET') return;
  // illustrations (any host): the copy saved with a downloaded chapter first (offline), else the network
  if (isPicture(url.pathname)) { e.respondWith(caches.match(url.origin + url.pathname, { cacheName: DATA }).then((r) => r || fetch(req))); return; }
  if (url.origin !== location.origin) {    // covers on the audio host (Cloudflare): keep a copy for offline
    if (isCover(url.pathname)) e.respondWith(coverFromCache(url));
    return;
  }
  const p = url.pathname;
  if (isList(p)) {                         // lists: network first, so new chapters appear
    e.respondWith(fetch(req, { cache: 'no-store' }).catch(() => caches.match(req, { ignoreSearch: true })));
    return;
  }
  if (isChapterFile(p)) {                  // chapters: downloaded copy first; explicit reload bypasses it
    if (req.cache === 'reload' || req.cache === 'no-store') return;
    e.respondWith(caches.match(req, { cacheName: DATA, ignoreSearch: true }).then((r) => r || fetch(req)));
    return;
  }
  // shell & covers: stale-while-revalidate
  e.respondWith(caches.open(SHELL).then(async (c) => {
    const hit = await c.match(req, { ignoreSearch: true });
    const net = fetch(req).then((r) => { if (r.ok) c.put(req, r.clone()); return r; }).catch(() => hit);
    return hit || net;
  }));
});
