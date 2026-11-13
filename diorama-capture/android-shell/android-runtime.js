/* Android-only integration. Authored gameplay and timeline are not rewritten. */
(() => {
  'use strict';
  const contexts = new Set(), media = new Set(), resumeContexts = new Set();
  let background = false;
  for (const name of ['AudioContext', 'webkitAudioContext']) {
    const Original = window[name];
    if (!Original) continue;
    window[name] = new Proxy(Original, {
      construct(Target, args) {
        const context = Reflect.construct(Target, args);
        contexts.add(context);
        const originalResume = context.resume.bind(context);
        context.resume = () => {
          if (background || document.hidden) return Promise.reject(new DOMException('App is backgrounded', 'NotAllowedError'));
          return originalResume();
        };
        if (background || document.hidden) context.suspend().catch(() => {});
        return context;
      }
    });
  }
  const originalPlay = HTMLMediaElement.prototype.play;
  HTMLMediaElement.prototype.play = function (...args) {
    media.add(this);
    if (background || document.hidden) return Promise.reject(new DOMException('App is backgrounded', 'NotAllowedError'));
    return originalPlay.apply(this, args);
  };
  function pause() {
    background = true;
    // Use the cinematic's own transport so its clock and sound stay synchronized.
    window.__DIORAMA_ENDING_PREVIEW__?.pause();
    for (const element of media) element.pause();
    for (const context of contexts) {
      if (context.state === 'running') {
        resumeContexts.add(context);
        context.suspend().catch(() => {});
      }
    }
    for (const frame of document.querySelectorAll('iframe')) {
      try { frame.contentWindow.__dioramaAndroid?.pause(); } catch (_) { /* Only same-origin frames. */ }
    }
  }
  function foreground() {
    background = false;
    for (const frame of document.querySelectorAll('iframe')) {
      try { frame.contentWindow.__dioramaAndroid?.foreground(); } catch (_) {}
    }
    // AudioContexts resume at the next deliberate touch; movie stays on Play.
  }
  document.addEventListener('visibilitychange', () => document.hidden ? pause() : foreground(), true);
  window.addEventListener('pagehide', pause);
  document.addEventListener('pointerdown', () => {
    if (background || document.hidden) return;
    for (const context of resumeContexts) context.resume().catch(() => {});
    resumeContexts.clear();
  }, true);
  function external(url) {
    const target = new URL(url, location.href);
    if (target.origin === location.origin) return false;
    if (!['https:', 'mailto:'].includes(target.protocol)) return true;
    // Capacitor's Android WebViewClient opens this navigation via ACTION_VIEW.
    window.top.location.href = target.href;
    return true;
  }
  document.addEventListener('click', event => {
    const anchor = event.target.closest?.('a[href]');
    if (anchor && external(anchor.href)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);
  const originalOpen = window.open;
  window.open = function (url, ...args) {
    if (url && external(url)) return null;
    return originalOpen.call(window, url, ...args);
  };
  window.__dioramaAndroid = {
    pause, foreground,
    closeOverlay() {
      const board = document.getElementById('cheatBoard');
      const legend = document.getElementById('mechanicLegendPanel');
      if (board?.getAttribute('aria-hidden') === 'false') { document.getElementById('cheatClose').click(); return true; }
      if (legend?.getAttribute('aria-hidden') === 'false') { document.getElementById('legendClose').click(); return true; }
      return false;
    }
  };
})();
