/**
 * 静态页面自检（无第三方依赖）：
 *   1. 页面里 getElementById 引用的 id 必须真实存在；
 *   2. 同一页面不允许重复 id；
 *   3. 本地 <script src> / <link href> 必须存在；
 *   4. 静态资源不允许外链（本项目要求零 CDN）；
 *   5. 内联脚本与外链 assets/*.js 都要能解析（语法错会让整页交互全废）。
 *
 * 用法：node web/verify-pages.js
 */
const fs = require('fs');
const path = require('path');

const root = __dirname;
const files = [
  'desktop/home.html', 'desktop/chat.html', 'desktop/music.html', 'desktop/settings.html',
  'mobile/home.html', 'mobile/chat.html', 'mobile/music.html', 'mobile/settings.html'
];

let issues = 0;

for (const rel of files) {
  const full = path.join(root, rel);
  if (!fs.existsSync(full)) {
    console.error('MISSING PAGE ' + rel);
    issues++;
    continue;
  }
  const s = fs.readFileSync(full, 'utf-8');

  const ids = [...s.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
  const seen = new Set();
  const dup = new Set();
  ids.forEach(id => {
    if (seen.has(id)) dup.add(id);
    seen.add(id);
  });
  if (dup.size) {
    console.error(rel + ' 重复 id: ' + [...dup].join(', '));
    issues += dup.size;
  }

  const refs = [...s.matchAll(/getElementById\(\s*['"]([^'"]+)['"]\s*\)/g)].map(m => m[1]);
  const missing = [...new Set(refs.filter(id => !seen.has(id)))];
  if (missing.length) {
    console.error(rel + ' getElementById 找不到: ' + missing.join(', '));
    issues += missing.length;
  }

  const links = [...s.matchAll(/[\s<](?:src|href)="([^"]+)"/g)].map(m => m[1]);
  links.forEach(link => {
    if (/^(https?:)?\/\//i.test(link)) {
      console.error(rel + ' 外链资源: ' + link);
      issues++;
      return;
    }
    if (link.startsWith('#') || link.startsWith('data:') || link.startsWith('javascript:') || link === '') return;
    const clean = link.split('#')[0].split('?')[0];
    if (!clean) return;
    const target = path.resolve(path.dirname(full), clean);
    if (!fs.existsSync(target)) {
      console.error(rel + ' 本地资源不存在: ' + link);
      issues++;
    }
  });

  // 生成期转义漏判：模板字符串里多转义一层会把 \` 原样写进页面，
  // 浏览器解析内联脚本时直接 SyntaxError，整段交互全废（2026-10-31 实测踩到）。
  const escapedBacktick = (s.match(/\\`/g) || []).length;
  if (escapedBacktick > 0) {
    console.error(rel + ' 内联脚本含 ' + escapedBacktick + ' 处 \\`（会导致 SyntaxError，检查 web/src 模板的转义层数）');
    issues += escapedBacktick;
  }

  // 内联脚本语法自检：只解析不执行，能在构建期抓住 SyntaxError，
  // 不必等到浏览器里点按钮才发现「页面能看但点不动」。
  const scripts = [...s.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1]);
  scripts.forEach((code, i) => {
    if (!code.trim()) return;
    try {
      // eslint-disable-next-line no-new-func
      new Function(code);
    } catch (e) {
      console.error(rel + ' 第 ' + (i + 1) + ' 段内联脚本语法错误: ' + e.message);
      issues++;
    }
  });
}

// 外链 JS 语法自检：页面用 <script src="../assets/r1-engine.js"> 引核心逻辑，
// 上面的内联检查覆盖不到它。2026-11-15 实测 parts.join(' 后误插真实换行，
// 整份 R1 未定义 → 页面停在「离线」且按钮全失效，装包后才发现。
const assetsDir = path.join(root, 'assets');
if (fs.existsSync(assetsDir)) {
  for (const name of fs.readdirSync(assetsDir).filter(n => n.endsWith('.js'))) {
    const code = fs.readFileSync(path.join(assetsDir, name), 'utf-8');
    try {
      // eslint-disable-next-line no-new-func
      new Function(code);
    } catch (e) {
      console.error('assets/' + name + ' 语法错误: ' + e.message);
      issues++;
    }
  }
}

if (issues === 0) {
  console.log('verify-pages: OK (' + files.length + ' pages)');
} else {
  console.error('verify-pages: ' + issues + ' issue(s)');
  process.exitCode = 1;
}
