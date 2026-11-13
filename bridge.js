// CodeGuard browser bridge. Only wraps browser-only APIs (screen capture, MediaRecorder, fullscreen,
// visibility, clipboard) and forwards everything to the Java backend. No business logic lives here.
(() => {
  'use strict';
  const f = document.getElementById('regForm');
  if (f) { // registration page: disclose-and-send basic device info
    f.screenRes.value = screen.width + 'x' + screen.height;
    f.language.value = navigator.language;
    f.timezone.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return;
  }
  const sid = document.body.dataset.sid;
  if (!sid) return;
  const $ = s => document.querySelector(s), $$ = s => [...document.querySelectorAll(s)];
  const api = p => '/api/session/' + sid + p;
  const post = (p, d) => fetch(api(p), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(d || {}) });
  const fmt = s => { s = Math.max(s, 0); return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0'); };
  let stream = null, rec = null, chain = Promise.resolve(), live = false, finishing = false, remaining = 0, timer = null;
  const dirty = {};
  const report = (type, description) => { if (live) post('/event', { type, description }).catch(() => {}); };
  const stopStream = () => { if (stream) stream.getTracks().forEach(t => t.stop()); };

  async function begin() {
    const msg = $('#gateMsg'); msg.textContent = '';
    let s;
    try { s = await navigator.mediaDevices.getDisplayMedia({ video: { displaySurface: 'monitor' }, audio: false }); }
    catch (e) { msg.textContent = 'Screen sharing is required for this exam. Please allow it and try again.'; return; }
    const track = s.getVideoTracks()[0], surface = track.getSettings().displaySurface;
    if (surface && surface !== 'monitor') {
      s.getTracks().forEach(t => t.stop());
      msg.textContent = 'Please share your ENTIRE screen (not a window or a tab).';
      if (document.body.dataset.status === 'IN_PROGRESS') { live = true; report('NOT_ENTIRE_SCREEN', 'Shared a ' + surface + ' instead of the entire screen'); live = false; }
      return;
    }
    const r = await post('/start');
    if (!r.ok) { s.getTracks().forEach(t => t.stop()); msg.textContent = 'Could not start the exam (it may already be submitted).'; return; }
    stream = s; remaining = (await r.json()).remainingSeconds; live = true; document.body.dataset.status = 'IN_PROGRESS';
    track.addEventListener('ended', () => { if (live) { report('SCREEN_SHARE_STOPPED', 'Screen sharing ended'); lockOut(); } });
    startRecording();
    $('#gate').classList.add('hidden'); $('#app').classList.remove('hidden');
    try { await document.documentElement.requestFullscreen(); } catch (e) { $('#fsbar').classList.remove('hidden'); }
    startTimer();
  }

  function startRecording() {
    const part = Date.now();
    const mime = ['video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'].find(m => MediaRecorder.isTypeSupported(m));
    rec = new MediaRecorder(stream, mime ? { mimeType: mime, videoBitsPerSecond: 800000 } : {});
    rec.ondataavailable = e => {
      if (!e.data.size) return;
      chain = chain.then(() => fetch(api('/chunk?part=' + part), { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: e.data }))
        .then(r => { if (!r.ok) throw 0; }).catch(() => report('RECORDING_ERROR', 'Chunk upload failed'));
    };
    rec.onerror = () => report('RECORDING_ERROR', 'MediaRecorder error');
    rec.start(5000);
  }
  const stopRecording = () => new Promise(res => {
    if (!rec || rec.state === 'inactive') return res();
    rec.onstop = () => chain.then(res); rec.stop();
  });

  function lockOut() {
    live = false; clearInterval(timer); stopRecording(); stopStream();
    $('#app').classList.add('hidden'); $('#gate').classList.remove('hidden');
    $('#gateMsg').textContent = 'Screen sharing stopped. This was reported to the examiner. Share your entire screen again to continue; your timer keeps running.';
    $('#start').textContent = 'Share screen & resume';
    if (document.fullscreenElement) document.exitFullscreen();
  }

  function startTimer() { clearInterval(timer); tick(); timer = setInterval(() => { remaining--; tick(); }, 1000); }
  function tick() { $('#timer').textContent = fmt(remaining); if (remaining <= 0) { clearInterval(timer); finish(); } }

  async function saveAll(force) {
    if (!live && !force) return;
    for (const t of $$('.code')) {
      const q = t.dataset.q; if (!force && !dirty[q]) continue;
      dirty[q] = false;
      try { await post('/save', { questionId: +q, code: t.value }); } catch (e) { dirty[q] = true; }
    }
  }

  async function finish() {
    if (finishing) return; finishing = true; clearInterval(timer);
    await stopRecording(); await saveAll(true);
    let ok = false;
    for (let i = 0; i < 3 && !ok; i++) {
      try { ok = (await post('/submit')).ok; } catch (e) {}
      if (!ok) await new Promise(r => setTimeout(r, 1500));
    }
    if (!ok) { finishing = false; alert('Submission failed. Check your connection and press Submit again.'); return; }
    live = false; stopStream();
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    location.href = '/session/' + sid + '/done';
  }

  // ---- UI wiring ----
  $('#start').addEventListener('click', begin);
  $('#submit').addEventListener('click', () => { if (confirm('Submit the exam? You cannot return to it.')) finish(); });
  $('#fsbtn').addEventListener('click', () => document.documentElement.requestFullscreen().catch(() => {}));
  $$('.qtab').forEach(b => b.addEventListener('click', () => {
    $$('.qpanel').forEach(p => p.classList.add('hidden')); $('#q-' + b.dataset.q).classList.remove('hidden');
    $$('.qtab').forEach(x => x.classList.remove('active')); b.classList.add('active');
  }));
  $$('.code').forEach(t => t.addEventListener('input', () => { dirty[t.dataset.q] = true; }));
  $$('.run').forEach(b => b.addEventListener('click', async () => {
    const q = b.dataset.q, out = $('#out-' + q); out.textContent = 'Running…'; b.disabled = true;
    try {
      const r = await post('/run', { questionId: +q, code: $('.code[data-q="' + q + '"]').value });
      const d = r.ok ? await r.json() : null;
      out.textContent = d ? d.output + (d.sampleMatch ? '\n\n✔ Matches the sample output' : '\n\n✘ Does not match the sample output') : 'Error ' + r.status;
      dirty[q] = false;
    } catch (e) { out.textContent = 'Request failed'; }
    b.disabled = false;
  }));

  // ---- monitoring (events are reported to Java only while the exam is live) ----
  document.addEventListener('visibilitychange', () => { if (document.hidden) report('TAB_HIDDEN', 'Page hidden / tab switched'); });
  window.addEventListener('blur', () => setTimeout(() => { if (!document.hidden) report('WINDOW_BLUR', 'Window lost focus'); }, 300));
  document.addEventListener('fullscreenchange', () => {
    if (document.fullscreenElement) $('#fsbar').classList.add('hidden');
    else if (live) { report('FULLSCREEN_EXIT', 'Left fullscreen'); $('#fsbar').classList.remove('hidden'); }
  });
  ['copy', 'cut', 'paste'].forEach(t => document.addEventListener(t, () => report(t.toUpperCase(), 'Clipboard ' + t + ' event')));
  window.addEventListener('beforeunload', e => { if (live) { e.preventDefault(); e.returnValue = ''; } });
  setInterval(() => { if (live) post('/event', { type: 'HEARTBEAT' }).catch(() => {}); }, 10000);
  setInterval(() => saveAll(false), 8000);
})();
