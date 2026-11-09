// App shell is precached; chapter data is cached explicitly by the page ("Скачать").
const SHELL = 'hpmor-shell-v3';
const DATA = 'hpmor-data-v1';
const FILES = ['./', 'index.html', 'style.css', 'app.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(FILES.map((f) => new Request(f, { cache: 'reload' })))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== SHELL && k !== DATA).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  const path = url.pathname.replace(/^.*\/(data\/)/, '$1');
  if (path.startsWith('data/')) {
    // index.json: network first (new chapters appear), then cache; chapter files: cache first
    if (path === 'data/index.json') {
      e.respondWith(fetch(e.request, { cache: 'no-store' }).catch(() => caches.match('data/index.json', { cacheName: DATA })));
    } else {
      e.respondWith(caches.match(path, { cacheName: DATA }).then((r) => r || fetch(e.request)));
    }
    return;
  }
  // shell: stale-while-revalidate
  e.respondWith(caches.open(SHELL).then(async (c) => {
    const hit = await c.match(e.request, { ignoreSearch: true });
    const net = fetch(e.request).then((r) => { if (r.ok) c.put(e.request, r.clone()); return r; }).catch(() => hit);
    return hit || net;
  }));
});
