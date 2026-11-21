const fs = require('fs');
const path = require('path');

const renderDesktop = require('./src/layout-desktop.js');
const renderMobile = require('./src/layout-mobile.js');
const desktopPages = require('./src/desktop-pages.js');
const mobilePages = require('./src/mobile-pages.js');
const buildClearCss = require('./src/theme-clear.js');

const webDir = __dirname;
const desktopDir = path.join(webDir, 'desktop');
const mobileDir = path.join(webDir, 'mobile');
const assetsDir = path.join(webDir, 'assets');

fs.mkdirSync(desktopDir, { recursive: true });
fs.mkdirSync(mobileDir, { recursive: true });
fs.mkdirSync(assetsDir, { recursive: true });

const pageMeta = [
  { id: 'home', title: '首页' },
  { id: 'chat', title: '对话' },
  { id: 'music', title: '音乐' },
  { id: 'settings', title: '设置' }
];

// 1. 生成桌面端 4 个独立页面
pageMeta.forEach(p => {
  const { html, script } = desktopPages[p.id]();
  const full = renderDesktop(p.id, p.title, html, script);
  const outPath = path.join(desktopDir, `${p.id}.html`);
  fs.writeFileSync(outPath, full, 'utf-8');
  console.log('Generated desktop/' + p.id + '.html (' + (full.length / 1024).toFixed(1) + ' KB)');
});

// 2. 生成移动端 4 个独立页面
pageMeta.forEach(p => {
  const { html, script } = mobilePages[p.id]();
  const full = renderMobile(p.id, p.title, html, script);
  const outPath = path.join(mobileDir, `${p.id}.html`);
  fs.writeFileSync(outPath, full, 'utf-8');
  console.log('Generated mobile/' + p.id + '.html (' + (full.length / 1024).toFixed(1) + ' KB)');
});

// 3. 构建 blackgold.css (移除中间 @media 块并统一置于文件最末尾，确保 sm:/md:/lg:/xl: 响应式断点不被基础类覆盖)
const rawBaseTw = fs.readFileSync(path.join(webDir, 'src', 'base-tailwind.css'), 'utf-8');
const baseTw = rawBaseTw.replace(/@media\s*\(min-width:[^)]+\)\s*\{(?:[^{}]|\{[^{}]*\})*\}/g, '');

const extraCss = `
/* 基础重置与安全区 */
@layer base {
  html, body { margin: 0; padding: 0; background-color: #0c0e11; color: #e2e2e6; -webkit-font-smoothing: antialiased; }
  body { overscroll-behavior: none; }
  main > :first-child { margin-top: 0 !important; }
  main > :last-child { margin-bottom: 0 !important; }
}
::-webkit-scrollbar { width: 5px; height: 5px; }
::-webkit-scrollbar-track { background: rgba(12, 14, 17, 0.4); }
::-webkit-scrollbar-thumb { background: rgba(253, 211, 133, 0.25); border-radius: 999px; }
::-webkit-scrollbar-thumb:hover { background: rgba(253, 211, 133, 0.5); }

.pb-safe { padding-bottom: env(safe-area-inset-bottom, 0px); }
.pt-safe { padding-top: env(safe-area-inset-top, 0px); }
.no-underline { text-decoration: none !important; }

/* 黑胶转盘旋转动效 */
@keyframes vinylSpin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.vinyl-spinning { animation: vinylSpin 16s linear infinite; }
.vinyl-paused { animation-play-state: paused !important; }

/* 声学频谱平滑过渡 */
.spec-bar, .m-spec { transition: height 0.16s ease; }

/* LRC 歌词视窗遮罩与高亮微光 */
.lyrics-box {
  mask-image: linear-gradient(to bottom, transparent 0%, black 15%, black 85%, transparent 100%);
  -webkit-mask-image: linear-gradient(to bottom, transparent 0%, black 15%, black 85%, transparent 100%);
}
.lyric-item {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  opacity: 0.45;
  transform: scale(0.97);
}
.lyric-item.active {
  opacity: 1;
  color: #fdd385 !important;
  font-weight: 700;
  transform: scale(1.04);
  text-shadow: 0 0 14px rgba(253, 211, 133, 0.55);
}

/* 实时日志终端 */
.log-terminal {
  background: rgba(10, 12, 14, 0.94);
  font-family: 'JetBrains Mono', monospace;
}

/* 滑块样式 */
.range-gold {
  -webkit-appearance: none;
  appearance: none;
  height: 6px;
  border-radius: 999px;
  background: #0c0e11;
  border: 1px solid rgba(253, 211, 133, 0.25);
}
.range-gold::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fdd385;
  box-shadow: 0 0 8px rgba(253, 211, 133, 0.8);
  cursor: pointer;
}

/* 配置页操作按钮：测试 / 保存 / 确认共用一套尺寸与字号，避免同一行里高低不齐 */
.btn-gold {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(253, 211, 133, 0.45);
  background: linear-gradient(to bottom, #282a2e, #1e2023);
  color: #fdd385;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6), inset 0 1px 1px rgba(255, 255, 255, 0.15);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.1s ease;
}
.btn-gold:hover { border-color: rgba(253, 211, 133, 0.7); }
.btn-gold:active { transform: scale(0.96); }
.btn-gold-block { display: flex; width: 100%; }

/* 未保存确认条：只有表单相对已保存状态有差异时才出现 */
.cfg-bar {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 60;
  display: none;
  align-items: center;
  gap: 14px;
  max-width: calc(100vw - 32px);
  padding: 10px 12px 10px 18px;
  border-radius: 999px;
  border: 1px solid rgba(253, 211, 133, 0.4);
  background: rgba(20, 22, 25, 0.96);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.7), inset 0 1px 1px rgba(253, 211, 133, 0.2);
  backdrop-filter: blur(12px);
}
.cfg-bar.show { display: flex; }
/* 移动端底部有 4 宫格导航，确认条要抬到它上面 */
#mobileBodyRoot .cfg-bar { bottom: calc(76px + env(safe-area-inset-bottom, 0px)); }
.cfg-bar-text { color: #fdd385; font-family: 'JetBrains Mono', monospace; font-size: 13px; white-space: nowrap; }
.cfg-bar-ghost {
  height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  background: transparent;
  color: #bfc7d4;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  cursor: pointer;
}

/* 移动端配置模式切换：面板 / 配置文件 二选一 */
.m-cfg-mode { transition: border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease; }
.m-cfg-mode.active { box-shadow: 0 0 12px rgba(253, 211, 133, 0.22); }

/* 未保存更改的悬浮确认按钮：只有出现差异才浮出，红点提示，点开看 diff 再确认 */
.cfg-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 70;
  display: none;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 999px;
  border: 1px solid rgba(253, 211, 133, 0.7);
  background: linear-gradient(to bottom, #282a2e, #1e2023);
  color: #fdd385;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.7), 0 0 18px rgba(253, 211, 133, 0.28), inset 0 1px 1px rgba(255, 255, 255, 0.18);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.cfg-fab.show { display: flex; animation: cfgFabIn 0.25s ease-out; }
.cfg-fab:hover { transform: translateY(-2px); box-shadow: 0 14px 34px rgba(0, 0, 0, 0.75), 0 0 24px rgba(253, 211, 133, 0.42), inset 0 1px 1px rgba(255, 255, 255, 0.2); }
.cfg-fab:active { transform: scale(0.94); }
@keyframes cfgFabIn { from { opacity: 0; transform: translateY(10px) scale(0.85); } to { opacity: 1; transform: translateY(0) scale(1); } }
.cfg-fab-dot {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 13px;
  height: 13px;
  border-radius: 999px;
  background: #ff5c5c;
  border: 2px solid #1a1c1f;
  box-shadow: 0 0 10px rgba(255, 92, 92, 0.85);
  animation: cfgDotPulse 1.6s ease-in-out infinite;
}
@keyframes cfgDotPulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.22); } }
#mobileBodyRoot .cfg-fab { bottom: calc(84px + env(safe-area-inset-bottom, 0px)); right: 16px; width: 52px; height: 52px; }

/* 配置差异确认弹窗：像 git diff 一样列出待写入的改动 */
.cfg-review-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.72);
  backdrop-filter: blur(4px);
}
.cfg-review-card {
  width: min(760px, 100%);
  max-height: min(82vh, 720px);
  display: flex;
  flex-direction: column;
  border-radius: 26px;
  border: 1px solid rgba(253, 211, 133, 0.45);
  background: #1a1c1f;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.8), inset 0 1px 1px rgba(253, 211, 133, 0.18);
  overflow: hidden;
}
.cfg-review-head { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px 10px; gap: 12px; }
.cfg-review-x {
  width: 30px; height: 30px; flex-shrink: 0;
  border-radius: 999px; border: 1px solid rgba(255, 255, 255, 0.18);
  background: transparent; color: #bfc7d4;
  font-size: 18px; line-height: 1; cursor: pointer;
}
.cfg-review-sub { padding: 0 20px 12px; color: #bfc7d4; font-family: 'JetBrains Mono', monospace; font-size: 12px; line-height: 1.6; }
.cfg-diff {
  margin: 0 20px;
  padding: 12px 14px;
  overflow: auto;
  border-radius: 16px;
  border: 1px solid rgba(253, 211, 133, 0.18);
  background: rgba(10, 12, 14, 0.94);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre;
}
.cfg-diff-line { display: block; }
.cfg-diff-file { color: #fdd385; }
.cfg-diff-hunk { color: #8f9bb0; background: rgba(143, 109, 44, 0.12); }
.cfg-diff-ctx { color: #bfc7d4; }
.cfg-diff-add { color: #63f1b4; background: rgba(99, 241, 180, 0.10); }
.cfg-diff-del { color: #ffb4ab; background: rgba(255, 180, 171, 0.10); }
.cfg-diff-meta { color: #8f9bb0; }
.cfg-review-foot {
  display: flex; align-items: center; justify-content: flex-end; gap: 10px;
  padding: 14px 20px 18px; border-top: 1px solid rgba(255, 255, 255, 0.06); flex-wrap: wrap;
}
#mobileBodyRoot .cfg-review-card { max-height: 74vh; border-radius: 22px; }
#mobileBodyRoot .cfg-diff { font-size: 11px; margin: 0 14px; }

/* Toast 提示条：颜色抽成 :root 变量，运行时由 r1-engine.js 引用，
   清透版随 theme-clear.js 的色板一起派生，避免动态 DOM 残留黑金配色 */
:root {
  --r1-toast-bg: rgba(26, 28, 31, 0.96);
  --r1-toast-border: rgba(253, 211, 133, 0.45);
  --r1-toast-fg: #e2e2e6;
  --r1-toast-shadow: 0 8px 24px rgba(0, 0, 0, 0.75), 0 0 14px rgba(253, 211, 133, 0.18);
  --r1-toast-dot-ok: #63f1b4;
  --r1-toast-dot-err: #ffb4ab;
  --r1-toast-dot-info: #fdd385;
}

/* 右上角外观主题切换按钮：颜色值走同一套色板，清透版由 theme-clear.js 自动派生 */
.r1-theme-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 999px;
  border: 1px solid rgba(253, 211, 133, 0.35);
  background: linear-gradient(to bottom, #282a2e, #1e2023);
  color: #fdd385;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.5), inset 0 1px 1px rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.1s ease;
}
.r1-theme-btn:hover { border-color: rgba(253, 211, 133, 0.7); }
.r1-theme-btn:active { transform: scale(0.94); }
.r1-theme-btn svg { width: 16px; height: 16px; display: block; }

/* 补充工具类 */
.mr-1 { margin-right: 0.25rem; }
.ml-auto { margin-left: auto; }
.min-h-\\[1em\\] { min-height: 1em; }
.gap-x-3 { column-gap: 0.75rem; }
.gap-y-2 { row-gap: 0.5rem; }
.max-h-56 { max-height: 14rem; }
.hover\\:border-primary\\/55:hover { border-color: rgba(253, 211, 133, 0.55); }
.font-headline-md { font-family: 'Space Grotesk', sans-serif; }
.text-headline-md { font-size: 20px; line-height: 28px; letter-spacing: -0.01em; font-weight: 500; }
.text-\\[8px\\] { font-size: 8px; }
.text-\\[10px\\] { font-size: 10px; }
.text-\\[13px\\] { font-size: 13px; }
.tracking-\\[0\\.12em\\] { letter-spacing: 0.12em; }
.max-w-\\[1440px\\] { max-width: 1440px; }
.max-w-\\[920px\\] { max-width: 920px; }
.max-w-\\[540px\\] { max-width: 540px; }
.max-w-\\[46px\\] { max-width: 46px; }
.max-w-\\[78\\%\\] { max-width: 78%; }
.max-w-\\[84\\%\\] { max-width: 84%; }
.max-w-\\[86\\%\\] { max-width: 86%; }
.min-w-\\[42px\\] { min-width: 42px; }
.min-w-\\[64px\\] { min-width: 64px; }
.min-h-\\[380px\\] { min-height: 380px; }
.min-h-\\[340px\\] { min-height: 340px; }
.min-h-\\[300px\\] { min-height: 300px; }
.max-h-\\[500px\\] { max-height: 500px; }
.max-h-\\[460px\\] { max-height: 460px; }
.max-h-\\[340px\\] { max-height: 340px; }
.max-h-48 { max-height: 12rem; }
.h-\\[280px\\] { height: 280px; }
.h-56 { height: 14rem; }
.h-52 { height: 13rem; }
.h-40 { height: 10rem; }
.h-36 { height: 9rem; }
.h-28 { height: 7rem; }
.h-20 { height: 5rem; }
.w-20 { width: 5rem; }
.w-11 { width: 2.75rem; }
.h-11 { height: 2.75rem; }
.w-5 { width: 1.25rem; }
.w-6 { width: 1.5rem; }
.h-6 { height: 1.5rem; }
.grid-cols-4 { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.items-stretch { align-items: stretch; }
.flex-wrap { flex-wrap: wrap; }
.overflow-y-auto { overflow-y: auto; }
.cursor-pointer { cursor: pointer; }
.mx-auto { margin-left: auto; margin-right: auto; }
.mr-2 { margin-right: 0.5rem; }
.mb-0\\.5 { margin-bottom: 0.125rem; }
.mt-1 { margin-top: 0.25rem; }
.mt-2 { margin-top: 0.5rem; }
.pt-3 { padding-top: 0.75rem; }
.pt-1\\.5 { padding-top: 0.375rem; }
.px-1\\.5 { padding-left: 0.375rem; padding-right: 0.375rem; }
.py-0\\.2 { padding-top: 2px; padding-bottom: 2px; }
.py-2\\.5 { padding-top: 0.625rem; padding-bottom: 0.625rem; }
.p-6 { padding: 1.5rem; }
.p-5 { padding: 1.25rem; }
.p-4 { padding: 1rem; }
.p-3\\.5 { padding: 0.875rem; }
.p-3 { padding: 0.75rem; }
.p-2\\.5 { padding: 0.625rem; }
.px-5 { padding-left: 1.25rem; padding-right: 1.25rem; }
.px-4 { padding-left: 1rem; padding-right: 1rem; }
.pl-4 { padding-left: 1rem; }
.py-4 { padding-top: 1rem; padding-bottom: 1rem; }
.py-8 { padding-top: 2rem; padding-bottom: 2rem; }
.py-12 { padding-top: 3rem; padding-bottom: 3rem; }
.px-6 { padding-left: 1.5rem; padding-right: 1.5rem; }
.pr-3\\.5 { padding-right: 0.875rem; }
.py-2 { padding-top: 0.5rem; padding-bottom: 0.5rem; }
.border-primary { border-color: #fdd385; }
.bg-outline { background-color: rgb(154, 143, 127); }
.gap-6 { gap: 1.5rem; }
.gap-4 { gap: 1rem; }
.gap-3\\.5 { gap: 0.875rem; }
.gap-3 { gap: 0.75rem; }
.gap-2\\.5 { gap: 0.625rem; }
.gap-2 { gap: 0.5rem; }
.gap-1\\.5 { gap: 0.375rem; }
.gap-1 { gap: 0.25rem; }
.shadow-lg { box-shadow: 0 10px 15px -3px rgba(0,0,0,0.6), 0 4px 6px -4px rgba(0,0,0,0.6); }
.border-primary\\/30 { border-color: rgba(253, 211, 133, 0.3); }
.border-primary\\/35 { border-color: rgba(253, 211, 133, 0.35); }
.border-primary\\/45 { border-color: rgba(253, 211, 133, 0.45); }
.border-primary\\/50 { border-color: rgba(253, 211, 133, 0.5); }
.border-error\\/40 { border-color: rgba(255, 180, 171, 0.4); }
.border-white\\/10 { border-color: rgba(255, 255, 255, 0.1); }
.bg-primary\\/20 { background-color: rgba(253, 211, 133, 0.2); }
.bg-tertiary\\/10 { background-color: rgba(99, 241, 180, 0.1); }
.bg-surface-container-lowest\\/60 { background-color: rgba(12, 14, 17, 0.6); }
.bg-surface-container-lowest\\/70 { background-color: rgba(12, 14, 17, 0.7); }
.bg-surface-container-lowest\\/85 { background-color: rgba(12, 14, 17, 0.85); }
.bg-surface-container-lowest\\/95 { background-color: rgba(12, 14, 17, 0.95); }
.bg-surface-container-low\\/90 { background-color: rgba(26, 28, 31, 0.9); }
.bg-surface-container-low\\/95 { background-color: rgba(26, 28, 31, 0.95); }
.bg-surface-container-high\\/90 { background-color: rgba(40, 42, 46, 0.9); }
.bg-surface-container-lowest\\/80 { background-color: rgba(12, 14, 17, 0.8); }
.bg-surface-container-lowest\\/90 { background-color: rgba(12, 14, 17, 0.9); }
.border-dashed { border-style: dashed; }
.border-primary\\/15 { border-color: rgba(253, 211, 133, 0.15); }
.border-primary\\/20 { border-color: rgba(253, 211, 133, 0.2); }
.border-primary\\/25 { border-color: rgba(253, 211, 133, 0.25); }
.border-primary\\/40 { border-color: rgba(253, 211, 133, 0.4); }
.border-primary\\/60 { border-color: rgba(253, 211, 133, 0.6); }
.bg-emerald-500\\/20 { background-color: rgba(16, 185, 129, 0.2); }
.text-emerald-400 { color: #34d399; }
.bg-amber-500\\/20 { background-color: rgba(245, 158, 11, 0.2); }
.text-amber-300 { color: #fcd34d; }
.hover\\:border-error\\/70:hover { border-color: rgba(255, 180, 171, 0.7); }
.hover\\:border-primary\\/35:hover { border-color: rgba(253, 211, 133, 0.35); }
.max-h-80 { max-height: 20rem; }
.max-h-64 { max-height: 16rem; }
.gap-5 { gap: 1.25rem; }
.text-\\[28px\\] { font-size: 28px; }
.text-\\[11px\\] { font-size: 11px; }
.py-6 { padding-top: 1.5rem; padding-bottom: 1.5rem; }
.pr-2 { padding-right: 0.5rem; }
.text-primary\\/90 { color: rgba(253, 211, 133, 0.9); }
.text-secondary\\/90 { color: rgba(191, 199, 212, 0.9); }
.hover\\:border-primary\\/30:hover { border-color: rgba(253, 211, 133, 0.3); }
.hover\\:border-primary\\/45:hover { border-color: rgba(253, 211, 133, 0.45); }
.hover\\:border-primary\\/50:hover { border-color: rgba(253, 211, 133, 0.5); }
.hover\\:border-error\\/50:hover { border-color: rgba(255, 180, 171, 0.5); }
.hover\\:text-error:hover { color: #ffb4ab; }

/* 响应式断点规则（统一置于样式表最底部，确保优先级高于基础原子类） */
@media (min-width: 640px) {
  .sm\\:col-span-1 { grid-column: span 1 / span 1; }
  .sm\\:col-span-2 { grid-column: span 2 / span 2; }
  .sm\\:inline { display: inline; }
  .sm\\:flex { display: flex; }
  .sm\\:flex-row { flex-direction: row; }
  .sm\\:grid-cols-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .sm\\:grid-cols-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (min-width: 768px) {
  .md\\:grid-cols-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .md\\:grid-cols-5 { grid-template-columns: repeat(5, minmax(0, 1fr)); }
}
@media (min-width: 1024px) {
  .lg\\:col-span-5 { grid-column: span 5 / span 5; }
  .lg\\:col-span-6 { grid-column: span 6 / span 6; }
  .lg\\:col-span-7 { grid-column: span 7 / span 7; }
  .lg\\:col-span-12 { grid-column: span 12 / span 12; }
  .lg\\:grid-cols-12 { grid-template-columns: repeat(12, minmax(0, 1fr)); }
}
@media (min-width: 1280px) {
  .xl\\:flex { display: flex; }
}
`;

const fullBlackgoldCss = baseTw + '\n' + extraCss;
fs.writeFileSync(path.join(assetsDir, 'blackgold.css'), fullBlackgoldCss, 'utf-8');
console.log('Generated assets/blackgold.css (' + (fullBlackgoldCss.length / 1024).toFixed(1) + ' KB)');

// 4. 由黑金样式表派生清透样式表：只换颜色/材质，布局类逐字相同
const fullClearCss = buildClearCss(fullBlackgoldCss);
fs.writeFileSync(path.join(assetsDir, 'clear.css'), fullClearCss, 'utf-8');
console.log('Generated assets/clear.css (' + (fullClearCss.length / 1024).toFixed(1) + ' KB)');

// 5. 仅同步构建产物至 l2-app/assets (APK 正式打包目录)
function copyDirSync(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  fs.readdirSync(src).forEach(item => {
    const s = path.join(src, item);
    const d = path.join(dest, item);
    if (fs.statSync(s).isDirectory()) {
      copyDirSync(s, d);
    } else {
      fs.copyFileSync(s, d);
    }
  });
}

const repoRoot = path.resolve(webDir, '..');
const apkAssetsDir = path.join(repoRoot, 'l2-app', 'assets');
fs.mkdirSync(apkAssetsDir, { recursive: true });
copyDirSync(assetsDir, path.join(apkAssetsDir, 'assets'));
copyDirSync(desktopDir, path.join(apkAssetsDir, 'desktop'));
copyDirSync(mobileDir, path.join(apkAssetsDir, 'mobile'));
if (fs.existsSync(path.join(webDir, 'favicon.ico'))) {
  fs.copyFileSync(path.join(webDir, 'favicon.ico'), path.join(apkAssetsDir, 'favicon.ico'));
}
console.log('Synced production bundle to:', apkAssetsDir);
