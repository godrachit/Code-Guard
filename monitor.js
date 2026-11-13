// Admin live alerts: Java pushes events over /ws/admin; this only shows a banner and refreshes the page.
(() => {
  const banner = document.getElementById('alert'), last = sessionStorage.getItem('cgLast');
  if (banner && last) { banner.textContent = last; banner.classList.remove('hidden'); sessionStorage.removeItem('cgLast'); }
  const ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/admin');
  let t = null;
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    sessionStorage.setItem('cgLast', 'Latest: ' + m.candidate + ' – ' + m.type + ' (' + m.severity + ')');
    if (!t) t = setTimeout(() => location.reload(), 1500);
  };
})();
