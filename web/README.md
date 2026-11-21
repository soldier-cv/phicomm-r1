# web —— 控制台前端（黑金 / 清透双主题）

这是音箱 8686 端口网页控制台的**唯一源码目录**。APK 打包时会把这里的产物复制进
`l2-app/assets/`（该目录是构建产物，已在 `.gitignore` 中忽略）。

## 目录

```
web/
├── src/                    源码（改这里）
│   ├── layout-desktop.js   桌面端外壳：左侧导航 + 顶部状态栏
│   ├── layout-mobile.js    移动端外壳：顶部栏 + 底部 4 标签
│   ├── desktop-pages.js    桌面端 4 页内容（home/chat/music/settings）
│   ├── mobile-pages.js     移动端 4 页内容
│   ├── base-tailwind.css   样式源（编译成 assets/blackgold.css）
│   └── theme-clear.js      清透样式生成器（构建期，由 blackgold.css 派生 clear.css）
├── assets/                 运行资源
│   ├── fonts.css           内嵌字体（零外链）
│   ├── r1-engine.js        前端引擎：API、状态、播放器、日志、滚动
│   ├── r1-theme.js         运行时主题切换（黑金 / 清透）
│   ├── blackgold.css       黑金样式（勿手改）
│   └── clear.css           清透样式（勿手改）
├── design-refs/            设计参考归档（只读，不参与构建）
│   └── clear-web-styles.css  清透原型样式，源自 D:\dev\agent-repos\web\清透web.html
├── desktop/                生成产物：桌面端 4 页
├── mobile/                 生成产物：移动端 4 页
├── build-pages.js          构建 + 同步到 l2-app/assets
└── verify-*.js             自检脚本
```

## 构建 / 自检

```powershell
node web\build-pages.js     # 生成 desktop/ mobile/ 两套 css 并同步到 l2-app/assets
node web\verify-css.js      # 检查页面用到的 class / 图标是否都有样式
node web\verify-pages.js    # 检查 id 引用、重复 id、本地资源、外链
```

或者：

```powershell
cd web
npm run build
npm run check
```

## 双主题约定

- 两套主题共用**同一份 DOM 与同一套类名**：`clear.css` 由 `blackgold.css` 在构建期
  通过 `src/theme-clear.js` 派生，只替换颜色与材质（含阴影/渐变），布局、排版、
  尺寸、响应式规则逐字相同。因此不得在页面模板里为某个主题写分支。
- 默认黑金。右上角按钮切换，选择记在 `localStorage['r1_theme']`；
  `?theme=clear` / `?theme=blackgold` 可强制指定（优先级高于本地记忆）。
- 新增颜色必须写进 `src/theme-clear.js` 的 `PALETTE`，否则清透版会残留黑金配色。
  HTML 内联装饰渐变无法被色板替换，需在 `CLEAR_OVERRIDES` 里收尾覆盖。
- 主题样式表在 `<head>` 由 `r1-theme.js` 同步切换 href，避免首次渲染先闪一下黑金。
- 运行时动态创建的 DOM 不要内联硬编码颜色：统一用 `:root` 的 `--r1-*` 变量，
  这样 `clear.css` 派生时能一并换色（见 `r1-engine.js` 的 toast 提示条）。

## 约定

- **零 CDN**：字体、图标、样式全部本地内联/本地文件；不要引入 `fonts.googleapis.com`、
  `cdn.tailwindcss.com` 等外链。
- **真实数据优先**：连接音箱时所有状态来自 `/api/v1/*`；未连接时才显示 `--` 或离线提示，
  不预置虚拟曲目/虚拟硬件参数。
- **北京时间**：所有时间显示固定 UTC+8。
- **滚动**：聊天、日志、曲库只在数据真正变化时重建 DOM；用户向上翻看时不会被每秒轮询拉回底部。
- **鉴权**：页面本身公开。未设令牌时 `/api/**` 也开放（首次使用免配置）；在设置页点「设置访问令牌」后，`/api/**` 才强制校验。用 `http://<音箱IP>:8686/?token=<你设置的令牌>` 打开一次，服务端下发 `r1_token` Cookie，后续页面自动带鉴权。
- 浏览器要求：现代 Chrome / Edge / Safari（需要 `fetch`、CSS Grid）。不承诺 Android 5.1
  自带老 WebView 的排版效果。
