/**
 * 运行时外观主题切换（黑金 / 清透轻拟态）。
 *
 * 两套主题共用同一份 DOM：通过切换 html[data-theme] 与 <link id="r1ThemeCss">
 * 以及 <meta name="color-scheme"> 实现无闪烁切换，选择持久化在 localStorage，
 * 支持 ?theme=clear / ?theme=blackgold 指定，并支持跨标签页同步。
 */
(function () {
  'use strict';

  var KEY = 'r1_theme';
  var RAIL_KEY = 'r1_rail_collapsed';
  var GOLD = 'blackgold';
  var CLEAR = 'clear';

  function normalize(v) { return v === CLEAR ? CLEAR : GOLD; }

  function fromUrl() {
    try {
      var m = /[?&]theme=([^&]*)/.exec(location.search);
      return m ? decodeURIComponent(m[1]) : null;
    } catch (e) { return null; }
  }

  function read() {
    var url = fromUrl();
    if (url) return normalize(url);
    try { return normalize(localStorage.getItem(KEY)); } catch (e) { return GOLD; }
  }

  function persist(t) {
    try { localStorage.setItem(KEY, t); } catch (e) { /* 隐私模式下降级为不记忆 */ }
  }

  function stripUrlParam() {
    try {
      if (!/[?&]theme=/.test(location.search)) return;
      var search = location.search
        .replace(/([?&])theme=[^&]*&?/, '$1')
        .replace(/[?&]$/, '');
      history.replaceState(null, '', location.pathname + search + location.hash);
    } catch (e) { /* ignore */ }
  }

  var ICON_TO_CLEAR = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>';
  var ICON_TO_GOLD = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>';

  var current = GOLD;

  function apply(t, remember) {
    current = normalize(t);
    document.documentElement.setAttribute('data-theme', current);
    document.documentElement.classList.toggle('dark', current !== CLEAR);

    var href = '../assets/' + (current === CLEAR ? 'clear.css' : 'blackgold.css');
    var link = document.getElementById('r1ThemeCss');
    if (link && link.getAttribute('href') !== href) link.setAttribute('href', href);

    var metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) metaTheme.setAttribute('content', current === CLEAR ? '#e8edf4' : '#0c0e12');

    var metaScheme = document.querySelector('meta[name="color-scheme"]');
    if (metaScheme) metaScheme.setAttribute('content', current === CLEAR ? 'light' : 'dark');

    if (remember) {
      persist(current);
      stripUrlParam();
    }

    var btn = document.getElementById('r1ThemeBtn');
    if (btn) {
      var label = current === CLEAR
        ? '当前：清透新拟态 · 点击切换至黑金暗色'
        : '当前：黑金暗色 · 点击切换至清透新拟态';
      btn.setAttribute('title', label);
      btn.setAttribute('aria-label', label);
      btn.innerHTML = current === CLEAR ? ICON_TO_GOLD : ICON_TO_CLEAR;
    }
    var txt = document.getElementById('r1ThemeLabel');
    if (txt) txt.textContent = current === CLEAR ? '清透' : '黑金';
    return current;
  }

  // 同步恢复桌面端左侧栏折叠状态（避免首屏闪烁）
  try {
    if (localStorage.getItem(RAIL_KEY) === '1') {
      document.documentElement.classList.add('r1-rail-collapsed');
    }
  } catch (e) {}

  apply(read(), false);

  window.addEventListener('storage', function (e) {
    if (e.key === KEY && e.newValue) apply(e.newValue, false);
  });

  window.R1Theme = {
    current: function () { return current; },
    set: function (t) { return apply(t, true); },
    toggle: function () { return apply(current === CLEAR ? GOLD : CLEAR, true); },
    toggleRail: function () {
      var collapsed = document.documentElement.classList.toggle('r1-rail-collapsed');
      try { localStorage.setItem(RAIL_KEY, collapsed ? '1' : '0'); } catch (e) {}
      return collapsed;
    }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { apply(current, false); });
  } else {
    apply(current, false);
  }
})();
