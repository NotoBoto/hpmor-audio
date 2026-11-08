'use strict';
const DATA_CACHE = 'hpmor-data-v1';
const $ = (s) => document.querySelector(s);
const audio = $('#audio');
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};
const SPEEDS = [0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2];

let index = [];            // [{n,title,duration,bytes}]
let cur = null;            // loaded chapter data
let words = [];            // [{t, el}] sorted by t
let active = -1;
let audioUrl = null;
let followUntil = 0;       // pause auto-scroll after manual scroll
let raf = 0;

const fmt = (s) => { s = Math.max(0, Math.floor(s)); const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, x = s % 60;
  return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0'); };
const mb = (b) => (b / 1048576).toFixed(b > 104857600 ? 0 : 1) + ' МБ';
const pad = (n) => String(n).padStart(3, '0');
function toast(msg, ms = 2500) { const t = $('#toast'); t.textContent = msg; t.hidden = false; clearTimeout(toast.h); toast.h = setTimeout(() => t.hidden = true, ms); }

// ---------- data / offline ----------
async function cached(url) { try { const c = await caches.open(DATA_CACHE); return await c.match(url); } catch { return null; } }
async function getRes(url) {
  const hit = await cached(url);
  if (hit) return hit;
  const r = await fetch(url);
  if (!r.ok) throw new Error(r.status + ' ' + url);
  return r;
}
async function isDownloaded(n) { return !!(await cached(`data/ch${pad(n)}.ogg`)) && !!(await cached(`data/ch${pad(n)}.json`)); }
async function download(n, btn) {
  const c = await caches.open(DATA_CACHE);
  for (const f of [`data/ch${pad(n)}.json`, `data/ch${pad(n)}.ogg`]) {
    if (await c.match(f)) continue;
    const r = await fetch(f, { cache: 'reload' });
    if (!r.ok) throw new Error('Не удалось скачать ' + f);
    await c.put(f, r);
  }
  if (btn) markDl(btn, true);
}
async function removeDl(n) { const c = await caches.open(DATA_CACHE); await c.delete(`data/ch${pad(n)}.json`); await c.delete(`data/ch${pad(n)}.ogg`); }
function markDl(btn, done) { btn.textContent = done ? '✓ офлайн' : '⇩ ' + btn.dataset.size; btn.classList.toggle('done', done); }
async function showStorage() {
  try { const e = await navigator.storage.estimate(); $('#storage').textContent = 'На телефоне: ' + mb(e.usage || 0); } catch {}
}

// ---------- list ----------
async function loadIndex() {
  try { index = await (await getRes('data/index.json')).json(); }
  catch { index = store.get('index', []); }
  store.set('index', index);
  try { (await caches.open(DATA_CACHE)).put('data/index.json', new Response(JSON.stringify(index))); } catch {}
}
async function renderList() {
  const ol = $('#chapters'); ol.innerHTML = '';
  const last = store.get('last', null);
  for (const ch of index) {
    const li = document.createElement('li');
    if (last === ch.n) li.className = 'cur';
    const pos = store.get('pos' + ch.n, 0);
    const pct = Math.min(100, Math.round(pos / ch.duration * 100));
    li.innerHTML = `<div class="t"><b></b><small>${fmt(ch.duration)}${pct ? ' · прослушано ' + pct + '%' : ''}</small>
      <div class="prog"><i style="width:${pct}%"></i></div></div><button class="dl"></button>`;
    li.querySelector('b').textContent = ch.title;
    li.querySelector('.t').onclick = () => openChapter(ch.n);
    const btn = li.querySelector('.dl'); btn.dataset.size = mb(ch.bytes);
    markDl(btn, await isDownloaded(ch.n));
    btn.onclick = async () => {
      if (btn.classList.contains('done')) {
        if (confirm('Удалить главу ' + ch.n + ' из памяти телефона?')) { await removeDl(ch.n); markDl(btn, false); showStorage(); }
        return;
      }
      btn.textContent = '…'; btn.disabled = true;
      try { await download(ch.n, btn); } catch (e) { toast(e.message); markDl(btn, false); }
      btn.disabled = false; showStorage();
    };
    ol.appendChild(li);
  }
  showStorage();
}
$('#dlAll').onclick = async () => {
  try { await navigator.storage.persist?.(); } catch {}
  const btns = [...document.querySelectorAll('#chapters .dl')];
  for (let i = 0; i < index.length; i++) {
    if (btns[i].classList.contains('done')) continue;
    btns[i].textContent = '…';
    try { await download(index[i].n, btns[i]); } catch (e) { toast(e.message); markDl(btns[i], false); break; }
    showStorage();
  }
};

// ---------- reader ----------
const roleColor = (i) => i === 0 ? '' : `var(--r${((i - 1) % 9) + 1})`;
async function openChapter(n, autoplay = false) {
  const meta = index.find((c) => c.n === n); if (!meta) return;
  saveNow();
  try {
    cur = await (await getRes(`data/ch${pad(n)}.json`)).json();
  } catch { toast('Глава не скачана, а интернета нет'); return; }
  store.set('last', n);
  const art = $('#text'); art.innerHTML = '';
  words = []; active = -1;
  const legend = document.createElement('div'); legend.className = 'legend';
  cur.roles.forEach((r, i) => { if (i) { const s = document.createElement('span'); s.textContent = '● ' + r; s.style.color = roleColor(i); legend.appendChild(s); } });
  cur.paras.forEach((toks, pi) => {
    const el = document.createElement(pi === 0 ? 'h2' : 'p');
    const frag = document.createDocumentFragment();
    toks.forEach(([w, t, r], k) => {
      const s = document.createElement('span'); s.textContent = w;
      if (r) s.style.color = roleColor(r);
      if (t != null) { s.dataset.t = t; words.push({ t, el: s }); }
      frag.appendChild(s); if (k < toks.length - 1) frag.appendChild(document.createTextNode(' '));
    });
    el.appendChild(frag); art.appendChild(el);
    if (pi === 0) art.appendChild(legend);
  });
  words.sort((a, b) => a.t - b.t);
  $('#list').hidden = true; $('#reader').hidden = false; $('#player').hidden = false;
  $('#back').hidden = false; $('#fontUp').hidden = false; $('#fontDown').hidden = false;
  $('#title').textContent = cur.title;
  $('#prevCh').disabled = !index.find((c) => c.n === n - 1) && index[0].n === n;
  history.replaceState(null, '', '#' + n);

  if (audioUrl) URL.revokeObjectURL(audioUrl);
  try {
    const blob = await (await getRes(`data/ch${pad(n)}.ogg`)).blob();
    audioUrl = URL.createObjectURL(blob);
  } catch { toast('Аудио не скачано, а интернета нет'); return; }
  audio.src = audioUrl;
  audio.playbackRate = store.get('speed', 1);
  const pos = store.get('pos' + n, 0);
  audio.addEventListener('loadedmetadata', () => {
    audio.currentTime = pos > cur.duration - 3 ? 0 : pos;
    tick(true); scrollToActive(true);
    if (autoplay) audio.play();
  }, { once: true });
  setMediaSession();
  window.scrollTo(0, 0);
}

function findWord(t) {
  let lo = 0, hi = words.length - 1, ans = -1;
  while (lo <= hi) { const m = (lo + hi) >> 1; if (words[m].t <= t + 0.05) { ans = m; lo = m + 1; } else hi = m - 1; }
  return ans;
}
function tick(force) {
  const t = audio.currentTime;
  const i = findWord(t);
  if (i !== active || force) {
    if (active >= 0 && words[active]) words[active].el.classList.remove('on');
    active = i;
    if (i >= 0) { words[i].el.classList.add('on'); scrollToActive(false); }
  }
  $('#time').textContent = fmt(t) + ' / ' + fmt(cur ? cur.duration : 0);
  if (!seeking) $('#seek').value = cur ? Math.round(t / cur.duration * 1000) : 0;
}
function scrollToActive(force) {
  if (active < 0) return;
  const el = words[active].el;
  if (!force && Date.now() < followUntil) { $('#follow').hidden = false; return; }
  $('#follow').hidden = true;
  const r = el.getBoundingClientRect();
  const top = 70, bottom = window.innerHeight - 170;
  if (force || r.top < top || r.bottom > bottom) {
    programmaticScroll = Date.now();
    window.scrollBy({ top: r.top - window.innerHeight * 0.33, behavior: force ? 'auto' : 'smooth' });
  }
}
let programmaticScroll = 0;
const userScrolled = () => { if (Date.now() - programmaticScroll > 900 && !audio.paused) followUntil = Date.now() + 8000; };
window.addEventListener('touchmove', userScrolled, { passive: true });
window.addEventListener('wheel', userScrolled, { passive: true });
$('#follow').onclick = () => { followUntil = 0; scrollToActive(true); };

function loop() { tick(false); raf = requestAnimationFrame(loop); }
audio.addEventListener('play', () => { $('#play').textContent = '❚❚'; cancelAnimationFrame(raf); loop(); });
audio.addEventListener('pause', () => { $('#play').textContent = '▶'; cancelAnimationFrame(raf); saveNow(); });
audio.addEventListener('timeupdate', () => { if (document.hidden) tick(false); if (Math.floor(audio.currentTime) % 5 === 0) saveNow(); });
audio.addEventListener('seeked', () => { if (cur) { tick(true); if (audio.paused) scrollToActive(true); } });
audio.addEventListener('ended', () => {
  store.set('pos' + cur.n, 0);
  const next = index.find((c) => c.n > cur.n);
  if (next) openChapter(next.n, true);
});
function saveNow() { if (cur && audio.src && !isNaN(audio.currentTime)) store.set('pos' + cur.n, audio.currentTime); }
document.addEventListener('visibilitychange', () => { if (document.hidden) saveNow(); else if (cur) tick(true); });

$('#text').addEventListener('click', (e) => {
  const t = e.target.dataset && e.target.dataset.t;
  if (t == null) return;
  audio.currentTime = +t; followUntil = 0; tick(true);
  if (audio.paused) audio.play();
});
$('#play').onclick = () => audio.paused ? audio.play() : audio.pause();
$('#rew').onclick = () => { audio.currentTime = Math.max(0, audio.currentTime - 15); followUntil = 0; tick(true); };
$('#fwd').onclick = () => { audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 15); followUntil = 0; tick(true); };
function setSpeedLabel() { $('#speed').textContent = audio.playbackRate.toString().replace('.', ',') + '×'; }
$('#speed').onclick = () => {
  const i = (SPEEDS.indexOf(audio.playbackRate) + 1) % SPEEDS.length;
  audio.playbackRate = SPEEDS[i < 0 ? 2 : i]; store.set('speed', audio.playbackRate); setSpeedLabel();
};
audio.addEventListener('ratechange', setSpeedLabel);
let seeking = false;
$('#seek').addEventListener('input', () => { seeking = true; $('#time').textContent = fmt($('#seek').value / 1000 * cur.duration) + ' / ' + fmt(cur.duration); });
$('#seek').addEventListener('change', () => { audio.currentTime = $('#seek').value / 1000 * cur.duration; seeking = false; followUntil = 0; tick(true); });
$('#prevCh').onclick = () => { const p = [...index].reverse().find((c) => c.n < cur.n); if (p) openChapter(p.n); };
$('#nextCh').onclick = () => { const p = index.find((c) => c.n > cur.n); if (p) openChapter(p.n); };
$('#back').onclick = showList;
function setFont(d) { const v = Math.min(30, Math.max(14, store.get('fs', 19) + d)); store.set('fs', v); document.documentElement.style.setProperty('--fs', v + 'px'); }
$('#fontUp').onclick = () => setFont(1); $('#fontDown').onclick = () => setFont(-1);

function showList() {
  saveNow(); audio.pause();
  $('#list').hidden = false; $('#reader').hidden = true; $('#player').hidden = true;
  $('#back').hidden = true; $('#fontUp').hidden = true; $('#fontDown').hidden = true;
  $('#title').textContent = 'Методы рационального мышления';
  history.replaceState(null, '', '#');
  renderList();
}

function setMediaSession() {
  if (!('mediaSession' in navigator) || !cur) return;
  navigator.mediaSession.metadata = new MediaMetadata({ title: cur.title, artist: 'ГПиМРМ', album: 'Аудиокнига',
    artwork: [{ src: 'icon-512.png', sizes: '512x512', type: 'image/png' }] });
  const ms = navigator.mediaSession;
  ms.setActionHandler('play', () => audio.play());
  ms.setActionHandler('pause', () => audio.pause());
  ms.setActionHandler('seekbackward', () => $('#rew').onclick());
  ms.setActionHandler('seekforward', () => $('#fwd').onclick());
  ms.setActionHandler('previoustrack', () => $('#prevCh').onclick());
  ms.setActionHandler('nexttrack', () => $('#nextCh').onclick());
  try { ms.setActionHandler('seekto', (e) => { audio.currentTime = e.seekTime; }); } catch {}
}

// ---------- start ----------
(async function start() {
  setFont(0); setSpeedLabel();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  await loadIndex();
  const h = parseInt(location.hash.slice(1), 10);
  if (h && index.find((c) => c.n === h)) openChapter(h); else renderList();
})();
