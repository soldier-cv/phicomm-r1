/**
 * 清透主题样式生成器（构建期使用，不进 APK）。
 *
 * 输入是黑金样式表的构建期字符串，输出同结构的清透样式表：
 * 逐字保留全部布局 / 排版 / 尺寸 / 响应式规则，只对颜色表达式做基色替换，
 * 再由 CLEAR_OVERRIDES 补上「新拟态」材质层。
 * 两套主题因此共用同一份 DOM 与同一套类名，布局 100% 一致，差异只在样式层。
 *
 * 设计取向（2026-11-05 定稿）：
 * - 新拟态只当控件语言，不当全站皮肤。结构层轻浮雕，控件层真凸凹，
 *   信息层（文字 / 表格 / 表单）不加光影，保住对比度。
 * - 底色用中性灰白（几乎无色），立体感全部由光影完成；彩色只表达状态。
 * - 光源统一左上：左上白色外阴影、右下冷灰外阴影，凹槽用双向内阴影。
 *
 * 色板与材质来源：D:\dev\agent-repos\web\清透web.html（只读未改），
 * 核心样式已归档到 web/design-refs/clear-web-styles.css。
 */
'use strict';

// 黑金 → 清透 的基色映射。键为 "r,g,b"，值为 [r,g,b]；alpha 一律原样保留。
const PALETTE = {
  // 主色一族：金 → 深青蓝
  '253,211,133': [47, 93, 124],
  '223,183,108': [220, 230, 238],
  '234,193,117': [201, 218, 232],
  '247,222,171': [236, 241, 246],
  '143,109,44':  [160, 178, 196],
  // 背景层级：深黑 → 中性灰白
  '12,14,17':    [253, 254, 255],
  '26,28,31':    [247, 248, 251],
  '30,32,35':    [222, 227, 234],
  '40,42,45':    [214, 220, 229],
  '40,42,46':    [214, 220, 229],
  '51,53,56':    [206, 213, 223],
  '17,19,22':    [242, 244, 248],
  '10,12,14':    [240, 243, 247],
  '20,22,25':    [249, 250, 252],
  '63,71,82':    [226, 230, 236],
  // 文字层级：浅色字 → 深灰蓝
  '226,226,230': [38, 43, 51],
  '209,197,180': [90, 98, 110],
  '191,199,212': [90, 98, 110],
  '154,143,127': [138, 146, 158],
  // on-primary-container 一族：深棕 → 深青蓝
  '58,42,8':     [31, 63, 85],
  '39,25,0':     [31, 63, 85],
  '65,45,0':     [31, 63, 85],
  '99,71,3':     [31, 63, 85],
  // 语义色：亮色 → 白底可读的压暗版本
  '99,241,180':  [46, 139, 87],
  '52,211,153':  [46, 139, 87],
  '16,185,129':  [46, 139, 87],
  '252,211,77':  [183, 121, 31],
  '245,158,11':  [183, 121, 31],
  '255,180,171': [192, 69, 58]
};

// 阴影统一用的冷灰（比纯黑透明更干净，且和底色同色相）
const SHADOW_RGB = '80, 90, 110';

function mapTriple(r, g, b) {
  return PALETTE[[Number(r), Number(g), Number(b)].join(',')] || null;
}

function toHex(n) {
  return n.map(v => v.toString(16).padStart(2, '0')).join('');
}

/**
 * 新拟态材质层。黑金的 DOM 与原型不同，这里按「类名组合」把材质映射过去：
 * 凸起 = 左上白光 + 右下冷灰柔光 + 顶部 1px 边缘光；
 * 凹进 = 双向 inset。
 * 不改 DOM、不改布局。
 */
const CLEAR_OVERRIDES = `
/* ============ 清透主题：新拟态材质（光源统一左上） ============ */

/* 页面底色：中性灰白，不带色相，让光影成为唯一的立体来源 */
html[data-theme="clear"] body { background: #EBEEF3 !important; }
html[data-theme="clear"] main,
html[data-theme="clear"] #mobileBodyRoot main { background-color: transparent !important; }

/* 凸起面板：左上白光 + 右下冷灰柔光 + 顶部边缘光 */
html[data-theme="clear"] [class*="bg-surface-container-lowest"][class*="rounded-2xl"],
html[data-theme="clear"] [class*="bg-surface-container-lowest"][class*="rounded-[2"],
html[data-theme="clear"] [class*="bg-surface-container-low"][class*="rounded-[2"] {
  background: #FDFEFF !important;
  border: 1px solid rgba(255, 255, 255, 0.95) !important;
  box-shadow:
    -4px -4px 8px #FFFFFF,
    0 8px 20px rgba(${SHADOW_RGB}, 0.10),
    0 2px 5px rgba(${SHADOW_RGB}, 0.05),
    inset 0 1px 0 #FFFFFF !important;
}

/* 凹槽：黑金用 shadow-inner 表示内嵌，清透改成双向内阴影 */
html[data-theme="clear"] .shadow-inner {
  background-color: #E4E8EE !important;
  box-shadow: inset 2px 2px 5px rgba(${SHADOW_RGB}, 0.12), inset -2px -2px 5px #FFFFFF !important;
}

/* 表单控件：容器语义恒为凹槽，优先级压过上面的卡片规则 */
html[data-theme="clear"] input[class*="bg-surface-container-lowest"][class*="rounded-"],
html[data-theme="clear"] select[class*="bg-surface-container-lowest"][class*="rounded-"],
html[data-theme="clear"] textarea[class*="bg-surface-container-lowest"][class*="rounded-"] {
  background: #E9EDF3 !important;
  border-color: rgba(255, 255, 255, 0.85) !important;
  box-shadow: inset 2px 2px 5px rgba(${SHADOW_RGB}, 0.12), inset -2px -2px 5px #FFFFFF !important;
  -webkit-backdrop-filter: none;
  backdrop-filter: none;
}

/* 普通按钮：默认凸起，按下瞬间凹进 */
html[data-theme="clear"] [class~="from-surface-container-high"][class~="to-surface-container"] {
  background: linear-gradient(180deg, #FBFCFD 0%, #F2F5F8 100%) !important;
  border-color: rgba(255, 255, 255, 0.9) !important;
  box-shadow: -3px -3px 6px #FFFFFF, 0 4px 10px rgba(${SHADOW_RGB}, 0.08), inset 0 1px 0 #FFFFFF !important;
}
html[data-theme="clear"] [class~="from-surface-container-high"][class~="to-surface-container"]:hover {
  background: linear-gradient(180deg, #FFFFFF 0%, #F0F3F7 100%) !important;
}
html[data-theme="clear"] [class~="from-surface-container-high"][class~="to-surface-container"]:active {
  background: #F2F5F8 !important;
  transform: translateY(1px);
  box-shadow: inset 2px 2px 5px rgba(${SHADOW_RGB}, 0.12), inset -2px -2px 5px #FFFFFF !important;
}

/* 主操作：深青蓝实心，彩色只出现在这类按钮上 */
html[data-theme="clear"] button.bg-primary,
html[data-theme="clear"] #playBtn,
html[data-theme="clear"] button[class~="w-12"][class~="h-12"][class~="from-surface-container-high"],
html[data-theme="clear"] button[class~="w-11"][class~="h-11"][class~="from-surface-container-high"] {
  background: linear-gradient(180deg, #3F7396 0%, #2F5D7C 100%) !important;
  border-color: rgba(47, 93, 124, 0.4) !important;
  color: #FFFFFF !important;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.35), 0 4px 10px rgba(47, 93, 124, 0.28) !important;
}
html[data-theme="clear"] button.bg-primary:active,
html[data-theme="clear"] #playBtn:active,
html[data-theme="clear"] button[class~="w-12"][class~="h-12"][class~="from-surface-container-high"]:active,
html[data-theme="clear"] button[class~="w-11"][class~="h-11"][class~="from-surface-container-high"]:active {
  transform: scale(0.95);
  box-shadow: inset 0 2px 4px rgba(12, 30, 45, 0.35) !important;
}
html[data-theme="clear"] #playIcon,
html[data-theme="clear"] button[class~="w-12"][class~="h-12"][class~="from-surface-container-high"] .material-symbols-outlined,
html[data-theme="clear"] button[class~="w-11"][class~="h-11"][class~="from-surface-container-high"] .material-symbols-outlined {
  color: #FFFFFF !important;
}

/* 设置页的 .btn-gold 也走同一套按钮材质 */
html[data-theme="clear"] .btn-gold {
  background: linear-gradient(180deg, #FBFCFD 0%, #F2F5F8 100%) !important;
  border-color: rgba(255, 255, 255, 0.9) !important;
  color: #2F5D7C !important;
  box-shadow: -3px -3px 6px #FFFFFF, 0 4px 10px rgba(${SHADOW_RGB}, 0.08) !important;
}
html[data-theme="clear"] .btn-gold:hover { border-color: rgba(47, 93, 124, 0.4) !important; }

/* 导航选中：凸起 + 主色（凹进留给开关和按压，导航凹进去会找不到） */
html[data-theme="clear"] a[aria-current="page"] {
  background: #F9FAFC !important;
  border-color: rgba(255, 255, 255, 0.9) !important;
  box-shadow: -3px -3px 6px #FFFFFF, 0 4px 10px rgba(${SHADOW_RGB}, 0.08), inset 0 1px 0 #FFFFFF !important;
}

/* 开关：底座内凹，滑块凸起 */
html[data-theme="clear"] button[class~="w-10"][class~="h-5"][class~="rounded-full"] {
  background: #E4E8EE !important;
  box-shadow: inset 2px 2px 4px rgba(${SHADOW_RGB}, 0.15), inset -2px -2px 4px #FFFFFF !important;
}

/* 侧栏 / 顶栏 */
html[data-theme="clear"] aside {
  background-color: #F4F6F9 !important;
  box-shadow: 4px 0 20px rgba(${SHADOW_RGB}, 0.05) !important;
}
html[data-theme="clear"] header { background-color: rgba(239, 241, 244, 0.85) !important; }

/* 唱片：浅色浮雕盘 + 主色扫光 + 白色中心 */
html[data-theme="clear"] .w-40.h-40.rounded-full {
  background: #F7F9FC !important;
  border-color: rgba(255, 255, 255, 0.9) !important;
  box-shadow: -6px -6px 14px #FFFFFF, 0 10px 24px rgba(${SHADOW_RGB}, 0.10) !important;
}
html[data-theme="clear"] [style*="conic-gradient(from 200deg"] {
  background:
    conic-gradient(from 0deg, transparent 0deg, rgba(47, 93, 124, 0.30) 45deg, transparent 90deg, rgba(47, 93, 124, 0.18) 180deg, transparent 220deg, rgba(47, 93, 124, 0.30) 315deg, transparent 360deg),
    radial-gradient(circle at 50% 50%, #E4E8EE 0 100%) !important;
  box-shadow: inset 3px 3px 6px rgba(${SHADOW_RGB}, 0.12), inset -3px -3px 6px #FFFFFF !important;
}
html[data-theme="clear"] [style*="radial-gradient(circle at 35% 30%"] {
  background: linear-gradient(180deg, #FFFFFF 0%, #F2F5F8 100%) !important;
  box-shadow: -2px -2px 4px #FFFFFF, 2px 2px 5px rgba(${SHADOW_RGB}, 0.12) !important;
}
html[data-theme="clear"] .bg-\\[\\#0a0c0e\\] {
  background-color: #2F5D7C !important;
  box-shadow: 0 0 5px rgba(47, 93, 124, 0.4) !important;
}
html[data-theme="clear"] [style*="inset 0 0 14px"] {
  box-shadow: inset 0 0 14px rgba(${SHADOW_RGB}, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.95) !important;
}

/* 滑块与日志窗口：凹进 */
html[data-theme="clear"] .range-gold {
  background: #E4E8EE !important;
  box-shadow: inset 1px 1px 3px rgba(${SHADOW_RGB}, 0.15), inset -1px -1px 2px #FFFFFF !important;
}
html[data-theme="clear"] .log-terminal {
  background: #F0F3F7 !important;
  box-shadow: inset 2px 2px 6px rgba(${SHADOW_RGB}, 0.10), inset -2px -2px 6px #FFFFFF !important;
}

/* 白底上看不见的白色细线换成冷灰 */
html[data-theme="clear"] .border-white\\/5,
html[data-theme="clear"] .border-white\\/10 { border-color: rgba(${SHADOW_RGB}, 0.08) !important; }
`;

/**
 * 由黑金样式表生成清透样式表。
 * @param {string} blackgoldCss 黑金样式表全文
 * @returns {string} 清透样式表全文
 */
function buildClearCss(blackgoldCss) {
  let css = blackgoldCss;

  // 1) Tailwind 生成的 rgb(r g b/var(--tw-*-opacity,1))，须保留变量后缀
  css = css.replace(
    /rgb\((\d{1,3})\s+(\d{1,3})\s+(\d{1,3})(\/var\([^)]*\))\)/g,
    (m, r, g, b, suffix) => {
      const n = mapTriple(r, g, b);
      return n ? `rgb(${n[0]} ${n[1]} ${n[2]}${suffix})` : m;
    }
  );

  // 2) rgb(r, g, b) 逗号格式
  css = css.replace(
    /rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)/g,
    (m, r, g, b) => {
      const n = mapTriple(r, g, b);
      return n ? `rgb(${n[0]}, ${n[1]}, ${n[2]})` : m;
    }
  );

  // 3) rgba(r, g, b, a)：黑色重阴影换成冷灰柔光，其余只换基色
  css = css.replace(
    /rgba\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*([^)]*)\)/g,
    (m, r, g, b, a) => {
      if (+r === 0 && +g === 0 && +b === 0) {
        const alpha = parseFloat(a);
        const soft = Number.isNaN(alpha) ? 0.05 : Math.min(0.12, alpha * 0.10);
        return `rgba(${SHADOW_RGB}, ${soft.toFixed(3)})`;
      }
      const n = mapTriple(r, g, b);
      return n ? `rgba(${n[0]}, ${n[1]}, ${n[2]}, ${a.trim()})` : m;
    }
  );

  // 4) hsla：金色透明度变体 → 主色；纯白细线 → 冷灰
  css = css.replace(/hsla\(([^)]*)\)/g, (m, inner) => {
    const parts = inner.split(',').map(s => s.trim());
    if (parts.length < 4) return m;
    const alpha = parts.slice(3).join(', ');
    if (parts[0] === '39') return `hsla(209, 45%, 34%, ${alpha})`;
    if (parts[0] === '0' && parts[1] === '0%') return `hsla(212, 20%, 84%, ${alpha})`;
    return m;
  });

  // 5) 六位 hex（#fff / #0000 等简写与语义色不在映射表中，原样保留）
  css = css.replace(/#([0-9a-fA-F]{3,8})\b/g, (m, hex) => {
    if (hex.length !== 6) return m;
    const n = mapTriple(
      parseInt(hex.slice(0, 2), 16),
      parseInt(hex.slice(2, 4), 16),
      parseInt(hex.slice(4, 6), 16)
    );
    return n ? '#' + toHex(n) : m;
  });

  return css + '\n' + CLEAR_OVERRIDES;
}

module.exports = buildClearCss;
