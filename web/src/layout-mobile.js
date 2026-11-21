/**
 * 移动端黑金 App 外壳模板 (layout-mobile.js)
 * 生产部署版：
 *  - 固定顶部 Header + 中间紧凑单列 Main (pt-16 pb-24) + 固定底部 4 宫格导航栏 Nav (常驻可见)
 *  - 严禁提供任何网页端语音拾音按钮，时间统一显示中国标准时间 (北京时间 UTC+8)
 */
module.exports = function renderMobileLayout(activePage, pageTitle, mainContentHtml, pageScriptHtml) {
  const navItems = [
    { id: 'home', href: 'home.html', icon: '&#xE429;', label: '首页' },
    { id: 'chat', href: 'chat.html', icon: '&#xE0CA;', label: '对话' },
    { id: 'music', href: 'music.html', icon: '&#xE019;', label: '音乐' },
    { id: 'settings', href: 'settings.html', icon: '&#xE8C0;', label: '设置' }
  ];

  const bottomNavHtml = navItems.map(item => {
    const isActive = item.id === activePage;
    if (isActive) {
      return `
        <a aria-current="page" class="flex flex-col items-center justify-center min-w-[64px] min-h-[44px] px-3.5 py-1 rounded-xl bg-surface-container-high/60 border border-primary/30 shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.2)] transition-all text-primary font-medium active:scale-95 no-underline" href="${item.href}">
          <span class="material-symbols-outlined text-[20px] shadow-[0_0_8px_rgba(253,211,133,0.5)]">${item.icon}</span>
          <span class="font-label-xs text-label-xs mt-0.5">${item.label}</span>
        </a>`;
    } else {
      return `
        <a class="flex flex-col items-center justify-center min-w-[64px] min-h-[44px] px-3.5 py-1 rounded-xl text-secondary hover:text-primary transition-all active:scale-95 no-underline" href="${item.href}">
          <span class="material-symbols-outlined text-[20px]">${item.icon}</span>
          <span class="font-label-xs text-label-xs mt-0.5">${item.label}</span>
        </a>`;
    }
  }).join('\n');

  return `<!-- 斐讯 R1 · ${pageTitle} -->
<!DOCTYPE html>
<html class="dark" lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover" name="viewport">
  <meta content="mobile_tab" name="shell-type">
  <meta content="#111316" name="theme-color">
  <link rel="icon" type="image/x-icon" href="../assets/favicon.ico">
  <link rel="apple-touch-icon" href="../assets/apple-touch-icon.png">
  <title>R1 · ${pageTitle}</title>
  <link rel="stylesheet" href="../assets/fonts.css">
  <link rel="stylesheet" id="r1ThemeCss" href="../assets/blackgold.css">
  <script src="../assets/r1-theme.js"></script>
</head>
<body class="bg-surface font-body-md text-body-md text-on-surface flex flex-col min-h-screen" id="mobileBodyRoot">

  <!-- ================= 移动端顶部固定状态栏 ================= -->
  <header class="fixed top-0 left-0 right-0 w-full z-50 pt-safe bg-surface/85 backdrop-blur-xl shadow-[0_1px_8px_rgba(0,0,0,0.5)] border-b border-white/5" id="mobileHeaderBar">
    <div class="h-16 px-gutter flex items-center justify-between max-w-[540px] mx-auto w-full">
      <div class="flex items-center gap-space-sm">
        <div class="flex flex-col">
          <div class="flex items-center gap-2">
            <a href="home.html" class="font-headline-sm text-headline-sm text-on-surface tracking-tight font-semibold no-underline">斐讯 R1</a>
            <div onclick="R1.promptSpeakerHost()" class="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-surface-container-high/80 border border-primary/25 cursor-pointer" title="点击配置音箱连接地址">
              <span class="w-1.5 h-1.5 rounded-full bg-outline" id="mHdrDot"></span>
              <span class="font-label-xs text-label-xs text-primary font-medium tracking-wide" id="mHdrModeText">离线</span>
            </div>
          </div>
          <span class="font-label-xs text-label-xs text-secondary/70 tracking-wider font-mono tabular-nums" id="mHdrSubText">北京时间 --:--:--</span>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <!-- 快捷静音/音量胶囊按钮 (黑金浮雕样式) -->
        <button onclick="R1.toggleMute()" aria-label="静音切换" class="px-2.5 h-8 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.2)] flex items-center justify-center gap-1 text-primary active:scale-95 transition-all" title="点击静音或恢复音量">
          <span class="material-symbols-outlined text-[16px]">&#xE049;</span>
          <span class="font-label-xs text-label-xs font-mono" id="mHdrVolText">40%</span>
        </button>
        <!-- 系统设置中心 -->
        <a href="settings.html" aria-label="系统设置" class="w-8 h-8 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.2)] flex items-center justify-center text-primary active:scale-95 transition-transform no-underline" title="系统设置">
          <span class="material-symbols-outlined text-[18px]">&#xE7FD;</span>
        </a>
        <!-- 右上角外观主题切换：黑金 / 清透，同一份 DOM 只换样式表 -->
        <button type="button" id="r1ThemeBtn" class="r1-theme-btn" onclick="R1Theme.toggle()" title="切换外观主题" aria-label="切换外观主题">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor" stroke="none"/></svg>
        </button>
      </div>
    </div>
  </header>

  <!-- ================= 移动端主内容区域 ================= -->
  <main class="flex flex-col relative w-full pt-16 pb-24 bg-surface min-h-screen max-w-[540px] mx-auto">
    <div class="flex flex-col w-full px-gutter pb-6 gap-space-md select-none pt-2">
${mainContentHtml}
    </div>
  </main>

  <!-- ================= 移动端底部固定 4 宫格导航栏 (常驻可见) ================= -->
  <nav class="fixed bottom-0 left-0 right-0 w-full z-50 pb-safe bg-surface-container-lowest/95 backdrop-blur-xl shadow-[0_-1px_12px_rgba(0,0,0,0.6)] border-t border-white/5">
    <div class="h-16 px-gutter flex items-center justify-around max-w-[540px] mx-auto w-full">
${bottomNavHtml}
    </div>
  </nav>

  <script src="../assets/r1-engine.js"></script>
  <script>
    R1.onStateChange(function(st) {
      const modeEl = document.getElementById('mHdrModeText');
      if (modeEl) modeEl.textContent = st.online ? '在线' : '离线';
      const dotEl = document.getElementById('mHdrDot');
      if (dotEl) dotEl.className = 'w-1.5 h-1.5 rounded-full ' + (st.online ? 'bg-tertiary animate-pulse' : 'bg-outline');
      const subEl = document.getElementById('mHdrSubText');
      if (subEl) subEl.textContent = '北京时间 ' + R1.nowTimeStr() + (st.online && st.ip !== '--' ? (' · ' + st.ip) : '');
      const volEl = document.getElementById('mHdrVolText');
      if (volEl) volEl.textContent = st.muted ? '静音' : (st.volume + '%');
    });
  </script>
${pageScriptHtml || ''}
</body>
</html>`;
};
