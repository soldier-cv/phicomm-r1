/**
 * 桌面端黑金外壳模板 (layout-desktop.js)
 * 生产部署版：
 *  - 固定左侧机架式导航栏 (<aside class="fixed left-0 top-0 h-full w-64 ...">) + 右侧主工作区 (<div class="pl-64">)
 *  - 左侧导航栏仅保留品牌头栏、01-04 四大功能导航及底部精简硬件信息（不含冗余播放器卡片）
 *  - 所有胶囊、徽标与卡片统一采用圆润流线设计 (rounded-full / rounded-2xl)，杜绝 2px 直角方框 (.rounded)
 *  - 严禁提供任何网页端语音拾音输入按钮，时间统一显示中国标准时间 (北京时间 UTC+8)
 */
module.exports = function renderDesktopLayout(activePage, pageTitle, mainContentHtml, pageScriptHtml) {
  const navItems = [
    { id: 'home', href: 'home.html', icon: '&#xE429;', label: '首页', num: '01' },
    { id: 'chat', href: 'chat.html', icon: '&#xE0CA;', label: '对话', num: '02' },
    { id: 'music', href: 'music.html', icon: '&#xE019;', label: '音乐', num: '03' },
    { id: 'settings', href: 'settings.html', icon: '&#xE8C0;', label: '设置', num: '04' }
  ];

  const navLinksHtml = navItems.map(item => {
    const isActive = item.id === activePage;
    if (isActive) {
      return `
        <a aria-current="page" class="flex items-center justify-between px-4 py-3 rounded-2xl transition-all group bg-gradient-to-r from-primary/20 via-primary/10 to-transparent border border-primary/40 text-primary font-medium shadow-[0_0_16px_rgba(253,211,133,0.15)] no-underline" href="${item.href}">
          <div class="flex items-center gap-3">
            <span class="material-symbols-outlined text-[20px] text-primary">${item.icon}</span>
            <span class="font-headline-sm text-headline-sm font-semibold tracking-wide">${item.label}</span>
          </div>
          <span class="font-label-xs text-label-xs text-primary font-mono">${item.num}</span>
        </a>`;
    } else {
      return `
        <a class="flex items-center justify-between px-4 py-3 rounded-2xl text-secondary hover:text-on-surface hover:bg-surface-container-high/60 transition-all group border border-transparent hover:border-outline-variant/30 no-underline" href="${item.href}">
          <div class="flex items-center gap-3">
            <span class="material-symbols-outlined text-[20px] text-secondary group-hover:text-primary transition-colors">${item.icon}</span>
            <span class="font-headline-sm text-headline-sm">${item.label}</span>
          </div>
          <span class="font-label-xs text-label-xs text-outline font-mono">${item.num}</span>
        </a>`;
    }
  }).join('\n');

  return `<!-- 斐讯 R1 · ${pageTitle} -->
<!DOCTYPE html>
<html class="dark" lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta content="width=device-width, initial-scale=1.0" name="viewport">
  <meta content="web_dashboard" name="shell-type">
  <link rel="icon" type="image/x-icon" href="../assets/favicon.ico">
  <link rel="apple-touch-icon" href="../assets/apple-touch-icon.png">
  <title>R1 · ${pageTitle}</title>
  <link rel="stylesheet" href="../assets/fonts.css">
  <link rel="stylesheet" id="r1ThemeCss" href="../assets/blackgold.css">
  <script src="../assets/r1-theme.js"></script>
</head>
<body class="bg-surface-container-lowest text-on-surface font-body-md antialiased min-h-screen selection:bg-primary-container selection:text-on-primary-container" style="background: radial-gradient(circle at 50% 0%, rgba(253, 211, 133, 0.08) 0%, transparent 60%), radial-gradient(circle at 20% 40%, rgba(253, 211, 133, 0.05) 0%, transparent 50%), radial-gradient(circle at 80% 60%, rgba(253, 211, 133, 0.04) 0%, transparent 40%), rgb(12, 14, 17);">

  <!-- ================= 左侧固定机架式导航栏 (常驻可见，不含冗余播控卡) ================= -->
  <aside class="fixed left-0 top-0 h-full w-64 bg-surface-container-low z-50 flex flex-col justify-between border-r border-outline-variant/30 shadow-[0_0_24px_rgba(0,0,0,0.6)]" style="position:fixed;left:0;top:0;bottom:0;width:16rem;z-index:50;display:flex;flex-direction:column;justify-content:space-between;">
    <div class="flex flex-col">
      <!-- 品牌头栏 -->
      <div class="h-16 px-space-md flex items-center justify-between border-b border-outline-variant/20 bg-surface-container-lowest/80 backdrop-blur-sm">
        <a href="home.html" class="flex items-center gap-space-sm no-underline">
          <div class="w-9 h-9 rounded-2xl bg-surface-container-highest flex items-center justify-center border border-primary/35 shadow-inner">
            <span class="material-symbols-outlined text-primary text-[20px]">&#xE32D;</span>
          </div>
          <div>
            <span class="font-headline-sm text-headline-sm font-bold tracking-tight text-on-surface block leading-tight">斐讯 R1</span>
            <span class="font-label-xs text-label-xs uppercase tracking-widest text-outline block">小讯</span>
          </div>
        </a>
        <div class="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-surface-container-high border border-primary/25 cursor-pointer shadow-inner" onclick="R1.promptSpeakerHost()" title="点击设置音箱服务地址 (:8686)">
          <span class="w-1.5 h-1.5 rounded-full bg-outline" id="asideOnlineDot"></span>
          <span class="font-label-xs text-label-xs text-secondary uppercase" id="asideOnlineText">离线</span>
        </div>
      </div>

      <!-- 导航菜单列表 -->
      <div class="p-space-md">
        <div class="px-space-xs py-space-xs mb-space-sm flex items-center justify-between">
          <span class="font-label-xs text-label-xs uppercase tracking-widest text-outline">功能导航</span>
          <span class="font-label-xs text-label-xs font-mono text-primary/70">R1BRAIN</span>
        </div>
        <nav class="flex flex-col gap-1.5">
${navLinksHtml}
        </nav>
      </div>
    </div>

    <!-- 侧栏底部设备与北京标准时间摘要 (圆角卡片 rounded-2xl) -->
    <div class="p-space-md border-t border-outline-variant/20 bg-surface-container-lowest/60">
      <div class="p-3.5 rounded-2xl bg-surface-container-high/90 border border-primary/20 shadow-inner flex flex-col gap-2">
        <div class="flex items-center justify-between">
          <span class="font-label-xs text-label-xs text-outline">北京时间</span>
          <span class="font-label-xs text-label-xs text-primary font-mono tabular-nums" id="asideCnTime">--:--:--</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="font-label-xs text-label-xs text-outline">硬件平台</span>
          <span class="font-label-xs text-label-xs text-tertiary font-mono" id="asideSoc">--</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="font-label-xs text-label-xs text-outline">服务版本</span>
          <span class="font-label-xs text-label-xs text-on-surface-variant font-mono" id="asideVer">--</span>
        </div>
      </div>
    </div>
  </aside>

  <!-- ================= 右侧主工作区 (左留白 pl-64) ================= -->
  <div class="pl-64" style="padding-left:16rem;">
    <!-- 顶部固定全局状态栏 (全部采用圆润胶囊 rounded-full，无直角方框) -->
    <header class="fixed top-0 left-64 right-0 h-16 bg-surface-container-lowest/90 backdrop-blur-md z-40 border-b border-outline-variant/30 flex items-center justify-between px-space-lg" style="left:16rem;right:0;top:0;">
      <div class="flex items-center gap-space-md">
        <div class="flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-surface-container-high/90 border border-primary/25 shadow-inner cursor-pointer hover:border-primary/50 transition-all" onclick="R1.promptSpeakerHost()" title="点击配置音箱局域网地址 (:8686)">
          <span class="material-symbols-outlined text-primary text-[16px]">&#xE88E;</span>
          <span class="font-label-sm text-label-sm text-on-surface-variant font-mono tracking-tight" id="hdrStatusText">未连接音箱 · 点击设置地址</span>
        </div>
        <div class="hidden xl:flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-low/90 border border-primary/20 shadow-inner">
          <span class="material-symbols-outlined text-[14px] text-primary">&#xE425;</span>
          <span class="font-label-xs text-label-xs text-secondary font-mono tabular-nums" id="hdrHwText">北京时间 --:--:--</span>
        </div>
      </div>

      <div class="flex items-center gap-space-md">
        <!-- 主音量状态与快捷静音胶囊 (纯正黑金浮雕按钮) -->
        <button onclick="R1.toggleMute()" class="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 text-primary shadow-[0_2px_6px_rgba(0,0,0,0.5),inset_0_1px_1px_rgba(255,255,255,0.1)] transition-all active:scale-95" title="点击静音或恢复主音量">
          <span class="material-symbols-outlined text-[15px] text-primary">&#xE049;</span>
          <span class="font-label-xs text-label-xs font-mono" id="hdrVolText">音量 40%</span>
        </button>

        <!-- 服务端点状态胶囊 -->
        <div onclick="R1.promptSpeakerHost()" class="hidden sm:flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-surface-container-high/80 border border-primary/25 shadow-inner backdrop-blur-sm cursor-pointer hover:border-primary/50 transition-all" title="点击配置音箱连接地址">
          <span class="w-1.5 h-1.5 rounded-full bg-outline" id="hdrHostDot"></span>
          <span class="font-label-xs text-label-xs text-secondary/80 font-mono tracking-wider" id="hdrHostPill">:8686</span>
        </div>

        <!-- 系统节点与设置入口胶囊 (纯正黑金浮雕样式) -->
        <a href="settings.html" class="group relative flex items-center gap-2 px-2.5 py-1 pr-3.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 hover:border-primary/60 shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)] hover:shadow-[0_0_12px_rgba(253,211,133,0.25)] transition-all active:scale-95 no-underline" title="前往智脑与系统维护设置">
          <div class="w-7 h-7 rounded-full bg-surface-container-lowest border border-primary/50 text-primary flex items-center justify-center shadow-[0_0_8px_rgba(253,211,133,0.25)]">
            <span class="material-symbols-outlined text-[16px] text-primary">&#xE7FD;</span>
          </div>
          <div class="flex flex-col text-left">
            <span class="font-label-xs text-label-xs text-primary font-medium leading-none">R1 节点</span>
            <span class="font-label-xs text-[9px] text-secondary/70 leading-none mt-0.5" id="hdrNodeSub">系统设置</span>
          </div>
        </a>
        <!-- 右上角外观主题切换：黑金 / 清透，同一份 DOM 只换样式表 -->
        <button type="button" id="r1ThemeBtn" class="r1-theme-btn" onclick="R1Theme.toggle()" title="切换外观主题" aria-label="切换外观主题">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor" stroke="none"/></svg>
        </button>
      </div>
    </header>

    <!-- 页面核心内容挂载区 -->
    <main class="relative pt-16 bg-surface-container-lowest min-h-screen">
      <div class="w-full p-space-lg">
        <div class="flex flex-col w-full gap-space-lg">
${mainContentHtml}
        </div>
      </div>
    </main>
  </div>

  <script src="../assets/r1-engine.js"></script>
  <script>
    R1.onStateChange(function(st) {
      const cnNow = R1.nowTimeStr();
      const hdrStatus = document.getElementById('hdrStatusText');
      if (hdrStatus) {
        if (st.online) {
          const mem = st.memTotalMb > 0 ? (st.memUsedMb + 'M / ' + st.memTotalMb + 'M') : '--';
          hdrStatus.textContent = '已运行 ' + R1.formatUptime(st.uptimeSec) + ' · 内存 ' + mem;
        } else {
          hdrStatus.textContent = st.hostIp ? ('未连接 (' + st.hostIp + ') · 点击重设') : '未连接音箱 · 点击设置地址';
        }
      }
      const hdrHost = document.getElementById('hdrHostPill');
      if (hdrHost) hdrHost.textContent = st.online ? (st.ip !== '--' ? st.ip + ':8686' : ':8686') : (st.hostIp || '设置IP');
      const hdrDot = document.getElementById('hdrHostDot');
      if (hdrDot) hdrDot.className = 'w-1.5 h-1.5 rounded-full ' + (st.online ? 'bg-tertiary animate-pulse' : 'bg-outline');

      const hdrVol = document.getElementById('hdrVolText');
      if (hdrVol) hdrVol.textContent = st.muted ? '已静音' : ('音量 ' + st.volume + '%');

      const hw = document.getElementById('hdrHwText');
      if (hw) hw.textContent = '北京时间 ' + cnNow + (st.online ? (' · ' + st.hardware) : '');

      const cnEl = document.getElementById('asideCnTime');
      if (cnEl) cnEl.textContent = cnNow;
      const soc = document.getElementById('asideSoc');
      if (soc) soc.textContent = st.online ? (st.hardware + (st.memTotalMb ? ' · ' + st.memTotalMb + 'M' : '')) : '--';
      const ver = document.getElementById('asideVer');
      if (ver) ver.textContent = st.online ? ('v' + st.version + ' · Android ' + st.androidRelease) : 'TCP :8686';

      const asideText = document.getElementById('asideOnlineText');
      if (asideText) {
        asideText.textContent = st.online ? '在线' : '离线';
        asideText.className = 'font-label-xs text-label-xs uppercase ' + (st.online ? 'text-tertiary' : 'text-secondary');
      }
      const asideDot = document.getElementById('asideOnlineDot');
      if (asideDot) asideDot.className = 'w-1.5 h-1.5 rounded-full ' + (st.online ? 'bg-tertiary animate-pulse' : 'bg-outline');
    });
  </script>
${pageScriptHtml || ''}
</body>
</html>`;
};
