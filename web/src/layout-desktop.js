/**
 * 桌面端统一外壳模板 (layout-desktop.js)
 * Material Design 3 Adaptive Desktop Shell：
 *  - 左侧可折叠自适应导航栏 (240px Drawer <-> 76px Navigation Rail)
 *  - 顶部极简应用栏 (当前页标题 + 连接地址/在线状态 + 主音量静音 + 黑金/清透主题切换)
 *  - 消除信息重复（单一真源），内置 Skip Link、全套语义 Landmark 与全局键盘快捷键
 *  - 严禁提供任何网页端语音拾音输入按钮，时间统一采用北京时间 (UTC+8)
 */
module.exports = function renderDesktopLayout(activePage, pageTitle, mainContentHtml, pageScriptHtml) {
  const navItems = [
    { id: 'home', href: 'home.html', icon: '&#xE429;', label: '首页' },
    { id: 'chat', href: 'chat.html', icon: '&#xE0CA;', label: '对话' },
    { id: 'music', href: 'music.html', icon: '&#xE019;', label: '音乐' },
    { id: 'settings', href: 'settings.html', icon: '&#xE8C0;', label: '设置' }
  ];

  const navLinksHtml = navItems.map(item => {
    const isActive = item.id === activePage;
    return `
          <a href="${item.href}"${isActive ? ' aria-current="page"' : ''} class="r1-nav-item${isActive ? ' is-active' : ''}" title="${item.label}">
            <span class="material-symbols-outlined" aria-hidden="true">${item.icon}</span>
            <span class="r1-nav-label">${item.label}</span>
          </a>`;
  }).join('\n');

  const isChatPage = activePage === 'chat';

  return `<!-- 斐讯 R1 · ${pageTitle} -->
<!DOCTYPE html>
<html class="dark" lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta content="width=device-width, initial-scale=1.0" name="viewport">
  <meta content="web_dashboard" name="shell-type">
  <meta content="dark" name="color-scheme">
  <meta content="#0c0e12" name="theme-color">
  <link rel="icon" type="image/x-icon" href="../assets/favicon.ico">
  <link rel="apple-touch-icon" href="../assets/apple-touch-icon.png">
  <title>${pageTitle} · 斐讯 R1 控制台</title>
  <link rel="stylesheet" href="../assets/fonts.css">
  <link rel="stylesheet" id="r1ThemeCss" href="../assets/blackgold.css">
  <script src="../assets/r1-theme.js"></script>
</head>
<body class="r1-desktop-body">
  <a href="#mainContent" class="r1-skip-link">跳至主内容</a>

  <!-- ================= 左侧自适应导航栏 (Drawer <-> Rail) ================= -->
  <aside class="r1-aside" aria-label="侧栏导航">
    <div class="r1-aside-top">
      <div class="r1-brand-bar">
        <a href="home.html" class="r1-brand-link" title="斐讯 R1 控制台首页">
          <div class="r1-brand-mark" aria-hidden="true">
            <span class="material-symbols-outlined">&#xE32D;</span>
          </div>
          <div class="r1-brand-text">
            <span class="r1-brand-name">斐讯 R1</span>
            <span class="r1-brand-sub">小讯智脑</span>
          </div>
        </a>
        <button type="button" onclick="R1Theme.toggleRail()" class="r1-btn r1-btn-ghost r1-btn-icon-sm r1-rail-toggle-aside" title="收起侧栏" aria-label="收起侧栏">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M9 4v16"/></svg>
        </button>
      </div>

      <div class="r1-nav-wrap">
        <div class="r1-nav-section-label">控制台</div>
        <nav class="r1-nav-list" aria-label="主导航">
${navLinksHtml}
        </nav>
      </div>
    </div>

    <div class="r1-aside-foot">
      <div class="r1-device-card">
        <div class="r1-device-row">
          <span class="r1-subtle">连接状态</span>
          <span class="r1-row" style="gap:6px;">
            <span class="r1-dot" id="asideOnlineDot"></span>
            <span id="asideOnlineText" style="font-weight:600;">离线</span>
          </span>
        </div>
        <div class="r1-device-row">
          <span class="r1-subtle">硬件</span>
          <span class="r1-mono r1-muted r1-truncate" id="asideSoc" style="max-width:130px;">--</span>
        </div>
        <div class="r1-device-row">
          <span class="r1-subtle">版本</span>
          <span class="r1-mono r1-muted r1-truncate" id="asideVer" style="max-width:130px;">--</span>
        </div>
      </div>
    </div>
  </aside>

  <!-- ================= 右侧主工作区 ================= -->
  <div class="r1-shell">
    <header class="r1-topbar">
      <div class="r1-topbar-left">
        <button type="button" onclick="R1Theme.toggleRail()" class="r1-btn r1-btn-ghost r1-btn-icon-sm" title="展开/收起侧栏" aria-label="展开或收起侧栏">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="4" y1="7" x2="20" y2="7"/><line x1="4" y1="12" x2="20" y2="12"/><line x1="4" y1="17" x2="20" y2="17"/></svg>
        </button>
        <h1 class="r1-page-title" id="mainContent" tabindex="-1">${pageTitle}</h1>
      </div>

      <div class="r1-topbar-right">
        <!-- 音箱连接状态与地址配置入口 -->
        <button type="button" onclick="R1.promptSpeakerHost()" class="r1-btn r1-btn-outlined r1-btn-sm" title="点击配置音箱局域网地址或访问令牌">
          <span class="r1-dot" id="hdrHostDot"></span>
          <span class="r1-mono" id="hdrHostPill">未连接</span>
        </button>

        <!-- 主音量状态与一键静音 -->
        <button type="button" onclick="R1.toggleMute()" class="r1-btn r1-btn-tonal r1-btn-sm" title="点击静音或恢复主音量" aria-label="静音或恢复主音量">
          <span class="material-symbols-outlined" aria-hidden="true">&#xE049;</span>
          <span class="r1-mono" id="hdrVolText">40%</span>
        </button>

        <!-- 外观主题切换：黑金暗色 / 清透新拟态 -->
        <button type="button" id="r1ThemeBtn" class="r1-theme-btn" onclick="R1Theme.toggle()" title="切换外观主题" aria-label="切换外观主题">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true"><circle cx="12" cy="12" r="4"/></svg>
        </button>
      </div>
    </header>

    <main class="r1-main">
      ${isChatPage ? mainContentHtml : `<div class="r1-container">\n${mainContentHtml}\n      </div>`}
    </main>
  </div>

  <script src="../assets/r1-engine.js"></script>
  <script>
    R1.onStateChange(function(st) {
      var hdrHost = document.getElementById('hdrHostPill');
      if (hdrHost) {
        hdrHost.textContent = st.online
          ? ((st.ip && st.ip !== '--') ? (st.ip + ':8686') : '已连接 :8686')
          : (st.hostIp ? ('离线 · ' + st.hostIp) : '连接音箱');
      }
      var hdrDot = document.getElementById('hdrHostDot');
      if (hdrDot) hdrDot.className = 'r1-dot' + (st.online ? ' is-online' : '');

      var hdrVol = document.getElementById('hdrVolText');
      if (hdrVol) hdrVol.textContent = st.muted ? '静音' : (st.volume + '%');

      var asideDot = document.getElementById('asideOnlineDot');
      if (asideDot) asideDot.className = 'r1-dot' + (st.online ? ' is-online' : '');

      var asideText = document.getElementById('asideOnlineText');
      if (asideText) {
        asideText.textContent = st.online ? '在线' : '离线';
        asideText.className = st.online ? 'r1-success-text' : 'r1-subtle';
      }

      var soc = document.getElementById('asideSoc');
      if (soc) soc.textContent = st.online ? (st.hardware + (st.memTotalMb ? (' · ' + st.memTotalMb + 'M') : '')) : '--';

      var ver = document.getElementById('asideVer');
      if (ver) ver.textContent = st.online ? ('v' + st.version + ' · A' + st.androidRelease) : ':8686';
    });

    // 全局桌面快捷体验：非输入态按 "/" 快速聚焦主输入框
    document.addEventListener('keydown', function(e) {
      var tag = (e.target && e.target.tagName) ? e.target.tagName.toLowerCase() : '';
      var isEditable = tag === 'input' || tag === 'textarea' || tag === 'select' || (e.target && e.target.isContentEditable);
      if (e.key === '/' && !isEditable && !e.ctrlKey && !e.metaKey && !e.altKey) {
        var targetInput = document.querySelector('#cmdInput, #chatPageInput, #cloudSearchInput, #musicSearchInput');
        if (targetInput) {
          e.preventDefault();
          targetInput.focus();
        }
      }
    });
  </script>
${pageScriptHtml || ''}
</body>
</html>`;
};
