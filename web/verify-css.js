const fs = require('fs');
const path = require('path');

const css = fs.readFileSync(path.join(__dirname, 'assets/blackgold.css'), 'utf-8');

const files = [
  'desktop/home.html', 'desktop/chat.html', 'desktop/music.html', 'desktop/settings.html',
  'mobile/home.html', 'mobile/chat.html', 'mobile/music.html', 'mobile/settings.html'
];

const ignoreCustomClasses = new Set([
  'dark', 'group', 'src-tab', 'dsp-item', 'set-tab', 'set-panel', 'm-src', 'spec-bar', 'm-spec'
]);

const validIcons = new Set([
  '&#xE32D;', '&#xE429;', '&#xE0CA;', '&#xE019;', '&#xE8C0;', '&#xE88E;',
  '&#xE01D;', '&#xE518;', '&#xE51C;', '&#xE7FD;', '&#xE312;', '&#xE5D8;',
  '&#xE042;', '&#xE03D;', '&#xE043;', '&#xE045;', '&#xE037;', '&#xE044;',
  '&#xE040;', '&#xE0BF;', '&#xE425;', '&#xE049;',
  // 2026-11-05 补：此前字体子集缺这几个字形，页面渲染为空白
  '&#xE405;', '&#xE8B6;', '&#xE2C7;', '&#xE0F0;', '&#xE0F1;', '&#xE3F0;'
]);

const allClasses = new Set();
const invalidIcons = [];

files.forEach(f => {
  const s = fs.readFileSync(path.join(__dirname, f), 'utf-8');
  const matches = [...s.matchAll(/class="([^"]+)"/g), ...s.matchAll(/className\s*=\s*[`'"]([^`'"]+)[`'"]/g)];
  matches.forEach(m => {
    const cleaned = m[1].replace(/\$\{[^}]+\}/g, ' ');
    cleaned.split(/\s+/).forEach(c => {
      if (c && /^[a-zA-Z0-9_\-\/\[\]\.:%]+$/.test(c) && !ignoreCustomClasses.has(c)) {
        allClasses.add(c);
      }
    });
  });

  const iconMatches = [...s.matchAll(/&#x[0-9A-Fa-f]+;/g)];
  iconMatches.forEach(im => {
    const code = im[0].toUpperCase().replace('&#X', '&#x');
    if (!validIcons.has(code)) {
      invalidIcons.push({ file: f, icon: im[0] });
    }
  });
});

function escapeSelector(cls) {
  return '.' + cls.replace(/([!"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~])/g, '\\$1');
}

const missing = [];
allClasses.forEach(c => {
  const sel = escapeSelector(c);
  if (!css.includes(sel)) {
    missing.push(c);
  }
});

console.log('Total checked classes:', allClasses.size);
console.log('Missing classes count:', missing.length);
if (missing.length > 0) {
  console.log('Missing classes:', missing);
}
console.log('Invalid subset font icons count:', invalidIcons.length);
if (invalidIcons.length > 0) {
  console.log('Invalid icons:', invalidIcons);
}
