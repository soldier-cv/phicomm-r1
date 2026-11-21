/**
 * 运行时外观主题切换（黑金 / 清透）。
 *
 * 两套主题共用同一份 DOM：只把 <link id="r1ThemeCss"> 的 href 在
 * blackgold.css 与 clear.css 之间切换。选择记在 localStorage，
 * 也支持 ?theme=clear 直接指定（用于分享链接 / 现场验证）。
 *
 * 本脚本放在 <head> 里同步执行，首次渲染前就定好样式表，避免先闪一下黑金。
 */
(function () {
  'use strict';

  var KEY = 'r1_theme';
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

  // 手动切换后去掉 URL 上的 theme 参数，否则刷新会被参数重新覆盖
  function stripUrlParam() {
    try {
      if (!/[?&]theme=/.test(location.search)) return;
      var search = location.search
        .replace(/([?&])theme=[^&]*&?/, '$1')
        .replace(/[?&]$/, '');
      history.replaceState(null, '', location.pathname + search + location.hash);
    } catch (e) { /* 不支持 history 时忽略 */ }
  }

  var current = GOLD;

  function apply(t, remember) {
    current = normalize(t);
    document.documentElement.setAttribute('data-theme', current);
    var href = '../assets/' + (current === CLEAR ? 'clear.css' : 'blackgold.css');
    var link = document.getElementById('r1ThemeCss');
    if (link && link.getAttribute('href') !== href) link.setAttribute('href', href);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', current === CLEAR ? '#f4f8ff' : '#111316');

    if (remember) {
      persist(current);
      stripUrlParam();
    }
    var btn = document.getElementById('r1ThemeBtn');
    if (btn) {
      var label = current === CLEAR
        ? '当前：清透 · 点击切回黑金'
        : '当前：黑金 · 点击切换到清透';
      btn.setAttribute('title', label);
      btn.setAttribute('aria-label', label);
    }
    return current;
  }

  // head 阶段先应用一次（此时按钮还没解析出来，只切样式表）
  apply(read(), false);

  window.R1Theme = {
    current: function () { return current; },
    set: function (t) { return apply(t, true); },
    toggle: function () { return apply(current === CLEAR ? GOLD : CLEAR, true); }
  };

  // 按钮解析完成后补一次 title / aria-label
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () { apply(current, false); });
  } else {
    apply(current, false);
  }
})();
