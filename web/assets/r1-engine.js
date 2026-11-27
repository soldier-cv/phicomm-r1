/**
 * 斐讯 R1 · 黑金声学控制台核心引擎 (r1-engine.js)
 *
 * 设计准则：
 *   1. 真实数据源：连接音箱 (:8686) 时，所有硬件状态、音量、蓝牙、曲库、播放器、对话历史、闹钟与配置均实时读写自音箱 HttpServer。
 *   2. 零演示数据：不预置任何虚拟曲目、虚拟对话或虚假硬件参数；未连接音箱时硬件遥测统一显示为 "--"。
 *   3. 北京标准时间：所有时间展示、对话时间戳、日志时间与闹钟触发均强制采用中国标准时间 (Asia/Shanghai, UTC+8)。
 *   4. 离线可直接预览：直接通过浏览器打开 HTML 文件时，允许交互体验各页面控件与配置表单，连接音箱后自动以音箱真实数据为准。
 */
(function (window) {
  'use strict';

  // ---- 老浏览器兼容垫片（Chrome 45+ / Android 5 WebView 级别） ----
  if (!String.prototype.padStart) {
    String.prototype.padStart = function (len, pad) {
      let s = String(this);
      pad = pad === undefined ? ' ' : String(pad);
      while (s.length < len) s = pad + s;
      return s;
    };
  }
  if (!String.prototype.matchAll) {
    String.prototype.matchAll = function (re) {
      let flags = '';
      if (re.global) flags += 'g';
      if (re.ignoreCase) flags += 'i';
      if (re.multiline) flags += 'm';
      if (flags.indexOf('g') < 0) flags += 'g';
      const rx = new RegExp(re.source, flags);
      const out = [];
      let m;
      while ((m = rx.exec(this)) !== null) {
        out.push(m);
        if (m[0] === '') rx.lastIndex++;
      }
      return out;
    };
  }
  if (!Array.prototype.find) {
    Array.prototype.find = function (fn, thisArg) {
      for (let i = 0; i < this.length; i++) if (fn.call(thisArg, this[i], i, this)) return this[i];
      return undefined;
    };
  }
  if (!Array.prototype.findIndex) {
    Array.prototype.findIndex = function (fn, thisArg) {
      for (let i = 0; i < this.length; i++) if (fn.call(thisArg, this[i], i, this)) return i;
      return -1;
    };
  }
  if (!Object.assign) {
    Object.assign = function (target) {
      for (let i = 1; i < arguments.length; i++) {
        const src = arguments[i];
        if (!src) continue;
        Object.keys(src).forEach(function (k) { target[k] = src[k]; });
      }
      return target;
    };
  }

  const STORAGE_KEY = 'r1_console_v1';

  // 清理旧版本遗留缓存
  try {
    ['r1_blackgold_state_v1', 'r1_blackgold_state_v2', 'r1_blackgold_state_v3', 'r1_blackgold_prod_v1']
      .forEach(function (k) { localStorage.removeItem(k); });
  } catch (e) {}

  const isServedFromSpeaker = (typeof location !== 'undefined') && location.port === '8686';

  const DEFAULT_STATE = {
    hostIp: (isServedFromSpeaker && location.host) ? location.host : '',
    token: '',
    online: false,
    authRequired: false,

    // 设备真实遥测状态 (来自 /api/v1/status)
    uptimeSec: 0,
    memUsedMb: 0,
    memTotalMb: 0,
    socTemp: '--',
    version: '--',
    hardware: '--',
    androidRelease: '--',
    ip: '--',
    serverTime: '--',
    btSupported: false,
    btEnabled: false,
    btDiscoverable: false,
    btName: '',
    volume: 40,
    lastVolume: 40,
    muted: false,

    // 播放器真实状态 (来自 /api/v1/player)
    player: {
      hasTrack: false,
      source: '',
      title: '',
      playing: false,
      positionMs: 0,
      durationMs: 0,
      loop: 'list',
      sleepRemainSec: 0,
      error: ''
    },
    isPlaying: false,
    currentTime: 0,
    loopMode: 'list',
    sleepRemainSec: 0,
    trackIdx: -1,

    // 曲库：音箱本地存储扫描结果 + 用户自定义 HTTP 音频流
    speakerTracks: [],
    streams: [],
    musicSource: 'all',
    lyricsKey: '',
    lyrics: [],

    chatHistory: [],
    alarms: [],
    config: {
      llmProvider: 'zhipu',
      llmBaseUrl: 'https://open.bigmodel.cn/api/paas/v4/chat/completions',
      hasKey: false,
      llmModel: 'GLM-4.5-Flash',
      llmReasoning: 'none',
      llmSystemPrompt: '你是斐讯 R1 智能音箱的语音助手“小讯”。回答要口语化、简短自然，控制在两三句话以内，不要用 markdown。',
      ttsEngine: 'openai',
      ttsUrl: '',
      hasTtsKey: false,
      ttsModel: 'grok-tts',
      ttsVoice: 'zh-CN-XiaoxiaoNeural',
      ttsSpeed: 1.0,
      autoSpeak: true,
      haUrl: '',
      hasHaToken: false,
      hasWeatherKey: false,
      weatherKey: '',
      weatherCity: '',
      weatherAuth: 'jwt',
      weatherJwtIss: '',
      weatherJwtProjectId: '',
      weatherJwtKeyId: '',
      hasWeatherJwtKey: false,
      weatherJwtKey: '',
      weatherJwtPub: '',
      asrEngine: 'native',
      asrUrl: ''
    }
  };

  function clone(o) {
    return JSON.parse(JSON.stringify(o));
  }

  function loadState() {
    const st = clone(DEFAULT_STATE);
    try {
      const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
      if (Array.isArray(raw.streams)) st.streams = raw.streams;
      if (raw.hostIp && !isServedFromSpeaker) st.hostIp = raw.hostIp;
      if (raw.token) st.token = raw.token;
      if (raw.musicSource) st.musicSource = raw.musicSource;
      if (Array.isArray(raw.alarms)) st.alarms = raw.alarms;
      if (raw.config && typeof raw.config === 'object') {
        Object.assign(st.config, raw.config);
      }
    } catch (e) {}
    return st;
  }

  let state = loadState();

  try {
    const qTok = new URLSearchParams(window.location.search).get('token');
    if (qTok) state.token = qTok;
  } catch (e) {}

  const listeners = [];

  function persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        streams: state.streams,
        hostIp: state.hostIp,
        token: state.token,
        musicSource: state.musicSource,
        alarms: state.alarms,
        config: state.config
      }));
    } catch (e) {}
  }

  function notify() {
    listeners.forEach(function (fn) {
      try { fn(state); } catch (e) { console.error(e); }
    });
  }

  function save() {
    persist();
    notify();
  }

  // ==========================================
  // 北京时间 (UTC+8 / Asia/Shanghai) 与格式化工具
  // ==========================================
  function cnDateParts() {
    // 强制转换为东八区北京时间 (UTC + 8小时)
    const now = new Date();
    const utcMs = now.getTime() + (now.getTimezoneOffset() * 60000);
    const cn = new Date(utcMs + 8 * 3600000);
    const pad = function (n) { return (n < 10 ? '0' : '') + n; };
    return {
      year: cn.getFullYear(),
      month: pad(cn.getMonth() + 1),
      day: pad(cn.getDate()),
      hour: pad(cn.getHours()),
      minute: pad(cn.getMinutes()),
      second: pad(cn.getSeconds()),
      ms: String(cn.getMilliseconds()).padStart(3, '0')
    };
  }

  function nowTimeStr() {
    const p = cnDateParts();
    return p.hour + ':' + p.minute + ':' + p.second;
  }

  function escapeHtml(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatTime(sec) {
    sec = Math.max(0, Math.floor(sec || 0));
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
  }

  function formatUptime(sec) {
    sec = Math.max(0, Math.floor(sec || 0));
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
  }

  function playPauseGlyph(isPlaying, sizePx) {
    const sz = sizePx || 20;
    if (isPlaying) {
      return '<svg width="' + sz + '" height="' + sz + '" viewBox="0 0 24 24" fill="currentColor" style="display:block"><rect x="6" y="5" width="4" height="14" rx="1.2"></rect><rect x="14" y="5" width="4" height="14" rx="1.2"></rect></svg>';
    }
    return '&#xE037;';
  }

  function toast(msg, type) {
    type = type || 'gold';
    let box = document.getElementById('r1ToastContainer');
    if (!box) {
      box = document.createElement('div');
      box.id = 'r1ToastContainer';
      box.setAttribute('role', 'status');
      box.setAttribute('aria-live', 'polite');
      box.style.cssText = 'position:fixed;bottom:84px;right:20px;z-index:9999;display:flex;flex-direction:column;gap:8px;pointer-events:none;max-width:340px;';
      document.body.appendChild(box);
    }
    const item = document.createElement('div');
    const dot = type === 'ok' ? 'var(--r1-toast-dot-ok)' : (type === 'err' ? 'var(--r1-toast-dot-err)' : 'var(--r1-toast-dot-info)');
    item.style.cssText = 'background:var(--r1-toast-bg);border:1px solid var(--r1-toast-border);color:var(--r1-toast-fg);padding:10px 16px;border-radius:999px;font-size:13px;font-family:var(--r1-font-sans,Manrope,sans-serif);box-shadow:var(--r1-toast-shadow);display:flex;align-items:center;gap:10px;backdrop-filter:blur(12px);transform:translateY(-6px);opacity:0;transition:all 0.22s ease;pointer-events:auto;';
    item.innerHTML = '<span style="width:8px;height:8px;border-radius:50%;background:' + dot + ';box-shadow:0 0 8px ' + dot + ';flex-shrink:0;"></span><span style="line-height:1.4;">' + escapeHtml(msg) + '</span>';
    box.appendChild(item);
    requestAnimationFrame(function () {
      item.style.transform = 'translateY(0)';
      item.style.opacity = '1';
    });
    setTimeout(function () {
      item.style.transform = 'translateY(-6px)';
      item.style.opacity = '0';
      setTimeout(function () { item.remove(); }, 240);
    }, 3000);
  }

  // ==========================================
  // 原生 <dialog> 模态交互组件（替代原生 prompt / confirm）
  // ==========================================
  function bindLightDismiss(dlg) {
    if (!dlg || dlg.dataset.ldBound) return;
    dlg.dataset.ldBound = '1';
    if (typeof HTMLDialogElement !== 'undefined' && ('closedBy' in HTMLDialogElement.prototype)) return;
    var outside = function (e) {
      var r = dlg.getBoundingClientRect();
      return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
    };
    var pressedOutside = false;
    dlg.addEventListener('pointerdown', function (e) { pressedOutside = outside(e); });
    dlg.addEventListener('pointerup', function (e) {
      if (pressedOutside && outside(e)) dlg.close('');
    });
  }

  function openModal(opts) {
    opts = opts || {};
    var fields = Array.isArray(opts.fields) ? opts.fields : [];
    var isDanger = !!opts.danger;
    var okText = opts.confirmText || '确认';
    var cancelText = opts.cancelText || '取消';
    var titleText = opts.title || '操作确认';
    var descText = opts.desc || '';

    // 极老浏览器无 <dialog> 时优雅降级
    if (typeof HTMLDialogElement === 'undefined' || !document.createElement('dialog').showModal) {
      if (!fields.length) return Promise.resolve(confirm(titleText + (descText ? ('\n' + descText) : '')) ? {} : null);
      var out = {};
      for (var i = 0; i < fields.length; i++) {
        var f = fields[i];
        var v = prompt(f.label || titleText, f.value || '');
        if (v === null) return Promise.resolve(null);
        out[f.name] = v;
      }
      return Promise.resolve(out);
    }

    return new Promise(function (resolve) {
      var dlg = document.createElement('dialog');
      dlg.className = 'r1-modal';
      dlg.setAttribute('closedby', 'any');
      var titleId = 'r1DlgTitle_' + Date.now();
      dlg.setAttribute('aria-labelledby', titleId);

      var fieldsHtml = fields.map(function (f, idx) {
        var fid = 'r1DlgField_' + idx + '_' + Date.now();
        var auto = idx === 0 ? ' autofocus' : '';
        var cls = f.mono ? ' r1-mono' : '';
        var hintHtml = f.hint ? '<span class="r1-hint">' + escapeHtml(f.hint) + '</span>' : '';
        var ctrl = f.type === 'textarea'
          ? '<textarea id="' + fid + '" name="' + escapeHtml(f.name) + '" rows="' + (f.rows || 4) + '" placeholder="' + escapeHtml(f.placeholder || '') + '" class="r1-textarea' + cls + '"' + auto + '>' + escapeHtml(f.value || '') + '</textarea>'
          : '<input id="' + fid + '" name="' + escapeHtml(f.name) + '" type="' + escapeHtml(f.type || 'text') + '" value="' + escapeHtml(f.value || '') + '" placeholder="' + escapeHtml(f.placeholder || '') + '" class="r1-input' + cls + '"' + auto + '>';
        return '<div class="r1-field"><label class="r1-label" for="' + fid + '">' + escapeHtml(f.label || '') + '</label>' + hintHtml + ctrl + '</div>';
      }).join('');

      var cancelAuto = (!fields.length && isDanger) ? ' autofocus' : '';
      var okAuto = (!fields.length && !isDanger) ? ' autofocus' : '';
      var okBtnCls = isDanger ? 'r1-btn r1-btn-danger' : 'r1-btn r1-btn-primary';

      dlg.innerHTML = ''
        + '<form method="dialog" style="display:flex;flex-direction:column;max-height:inherit;">'
        + '  <div class="r1-modal-head">'
        + '    <h1 id="' + titleId + '" class="r1-modal-title">' + escapeHtml(titleText) + '</h1>'
        + '    <button type="submit" value="cancel" formnovalidate class="r1-btn r1-btn-ghost r1-btn-icon-sm" aria-label="关闭">×</button>'
        + '  </div>'
        + '  <div class="r1-modal-body">'
        + (descText ? '    <p style="margin:0;line-height:1.6;">' + escapeHtml(descText) + '</p>' : '')
        + fieldsHtml
        + '  </div>'
        + '  <div class="r1-modal-foot">'
        + '    <button type="submit" value="cancel" formnovalidate class="r1-btn r1-btn-outlined"' + cancelAuto + '>' + escapeHtml(cancelText) + '</button>'
        + '    <button type="submit" value="ok" class="' + okBtnCls + '"' + okAuto + '>' + escapeHtml(okText) + '</button>'
        + '  </div>'
        + '</form>';

      document.body.appendChild(dlg);
      bindLightDismiss(dlg);

      dlg.addEventListener('close', function () {
        var rv = dlg.returnValue;
        dlg.returnValue = '';
        if (rv !== 'ok') {
          dlg.remove();
          resolve(null);
          return;
        }
        var data = {};
        fields.forEach(function (f) {
          var el = dlg.querySelector('[name="' + f.name + '"]');
          data[f.name] = el ? el.value : '';
        });
        dlg.remove();
        resolve(data);
      });

      dlg.showModal();
    });
  }

  function confirmModal(opts) {
    return openModal(Object.assign({ confirmText: '确认', cancelText: '取消' }, opts || {}, { fields: [] }))
      .then(function (res) { return res !== null; });
  }

  // 运行日志 (北京时间时间戳)
  const logEntries = [];
  const logCallbacks = [];
  function addLog(level, msg, tag) {
    const p = cnDateParts();
    const entry = {
      time: p.hour + ':' + p.minute + ':' + p.second + '.' + p.ms,
      level: level || 'INFO',
      tag: tag || 'CONSOLE',
      msg: msg,
      source: 'console'
    };
    logEntries.push(entry);
    if (logEntries.length > 300) logEntries.shift();
    logCallbacks.forEach(function (cb) {
      try { cb(entry, logEntries); } catch (e) {}
    });
  }

  // ==========================================
  // 音箱 REST API 通信
  // ==========================================
  function apiBase() {
    if (isServedFromSpeaker) return '';
    const host = (state.hostIp || '').replace(/^https?:\/\//, '');
    return host ? 'http://' + host : '';
  }

  function api(method, path, body, timeoutMs) {
    if (typeof fetch !== 'function') {
      return Promise.reject(new Error('当前浏览器不支持 fetch，请使用较新的 Chrome / Edge / Safari'));
    }
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) headers['X-Token'] = state.token;
    const base = apiBase();
    if (!isServedFromSpeaker && !base) {
      return Promise.reject(new Error('尚未设置音箱连接地址'));
    }
    if (state.authRequired && !state.token && path !== '/api/v1/token') {
      return Promise.reject(new Error('需要访问令牌'));
    }
    const opts = {
      method: method,
      headers: headers,
      body: body ? JSON.stringify(body) : undefined
    };
    let timer = null;
    if (typeof AbortController !== 'undefined') {
      const ctrl = new AbortController();
      opts.signal = ctrl.signal;
      timer = setTimeout(function () { try { ctrl.abort(); } catch (e) {} }, timeoutMs || 8000);
    }
    return fetch(base + path, opts).then(function (r) {
      return r.text().then(function (txt) {
        let j;
        try { j = txt ? JSON.parse(txt) : {}; } catch (e) { j = { ok: false, error: 'HTTP ' + r.status }; }
        if (r.status === 401) {
          state.authRequired = true;
          state.online = false;
          const err = new Error(j.error || '需要访问令牌');
          err.auth = true;
          throw err;
        }
        if (!r.ok || j.ok === false) throw new Error(j.error || ('HTTP ' + r.status));
        return j;
      });
    }).catch(function (e) {
      if (e && e.name === 'AbortError') throw new Error('请求超时');
      throw e;
    }).then(function (v) {
      if (timer) clearTimeout(timer);
      return v;
    }, function (e) {
      if (timer) clearTimeout(timer);
      throw e;
    });
  }

  function applyStatus(st) {
    state.online = true;
    if (typeof st.uptimeMs === 'number') state.uptimeSec = Math.floor(st.uptimeMs / 1000);
    if (st.memTotalKb > 0) state.memTotalMb = Math.round(st.memTotalKb / 1024);
    if (st.memFreeKb >= 0 && state.memTotalMb > 0) {
      state.memUsedMb = Math.max(1, state.memTotalMb - Math.round(st.memFreeKb / 1024));
    }
    state.socTemp = (typeof st.socTempC === 'number' && st.socTempC > 0) ? st.socTempC.toFixed(1) : '--';
    if (st.version) state.version = st.version;
    if (st.hardware) state.hardware = st.hardware;
    if (st.androidRelease) state.androidRelease = st.androidRelease;
    if (st.ip) state.ip = st.ip;
    if (st.serverTime) state.serverTime = st.serverTime;
    state.btSupported = !!st.btSupported;
    state.btEnabled = !!st.btEnabled;
    state.btDiscoverable = !!st.btDiscoverable;
    state.btName = st.btName || '';
    if (typeof st.volumePct === 'number') {
      state.volume = st.volumePct;
      state.muted = st.volumePct === 0;
      if (st.volumePct > 0) state.lastVolume = st.volumePct;
    }
    if (st.model) state.config.llmModel = st.model;
    if (typeof st.autoSpeak === 'boolean') state.config.autoSpeak = st.autoSpeak;
  }

  let lastErr = '';
  function applyPlayer(p) {
    state.player = p;
    state.isPlaying = !!p.playing;
    state.currentTime = Math.floor((p.positionMs || 0) / 1000);
    state.loopMode = p.loop || 'list';
    state.sleepRemainSec = p.sleepRemainSec || 0;
    state.trackIdx = p.hasTrack ? findTrackIdx(p.source) : -1;
    if (p.error && p.error !== lastErr) {
      lastErr = p.error;
      addLog('WARN', p.error, 'MediaPlayer');
    } else if (!p.error) {
      lastErr = '';
    }
    if (p.hasTrack && p.source.charAt(0) === '/' && state.lyricsKey !== p.source) {
      loadLyrics(p.source);
    }
    if (!p.hasTrack) {
      state.lyricsKey = '';
      state.lyrics = [];
    }
  }

  function parseLrc(text) {
    const out = [];
    String(text || '').split(/\r?\n/).forEach(function (line) {
      const stamps = [...line.matchAll(/\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g)];
      const txt = line.replace(/\[[^\]]*\]/g, '').trim();
      if (!stamps.length || !txt) return;
      stamps.forEach(function (m) {
        out.push({ t: parseInt(m[1], 10) * 60 + parseInt(m[2], 10), txt: txt });
      });
    });
    return out.sort(function (a, b) { return a.t - b.t; });
  }

  function loadLyrics(path) {
    state.lyricsKey = path;
    state.lyrics = [];
    api('GET', '/api/v1/music/lyrics?path=' + encodeURIComponent(path))
      .then(function (r) {
        if (state.lyricsKey === path) {
          state.lyrics = parseLrc(r.lrc);
          notify();
        }
      })
      .catch(function () {});
  }

  function syncPlayer() {
    return api('GET', '/api/v1/player')
      .then(function (p) { applyPlayer(p); notify(); })
      .catch(function () {});
  }

  let syncInFlight = false;
  let authPrompted = false;

  function notifyAuthProblem(err) {
    if (authPrompted || !err || !err.auth) return;
    authPrompted = true;
    addLog('WARN', '音箱 API 需要访问令牌 (HTTP 401)', 'HttpClient');
    const tip = '需要访问令牌。请点「设置访问令牌」填写，或用 http://音箱IP:8686/?token=<令牌> 打开。';
    toast(tip, 'err');
    setTimeout(function () { R1.promptToken(); }, 300);
  }

  function syncFromSpeaker(silent) {
    if (syncInFlight) return Promise.resolve(state.online);
    syncInFlight = true;
    return api('GET', '/api/v1/status')
      .then(function (st) {
        const wasOffline = !state.online;
        state.authRequired = !!st.authRequired;
        applyStatus(st);
        // 不再调用 maybeHintOpenApi()：898e74a3 只加了调用、从未定义该函数，
        // 抛出的 ReferenceError 被下面 .catch 吞掉，会把刚置 true 的 online 打回 false。
        notify();
        api('GET', '/api/v1/config').then(function (cfg) {
          const c = state.config;
          c.llmProvider = cfg.llmProvider || c.llmProvider;
          c.llmBaseUrl = cfg.baseUrl || c.llmBaseUrl;
          c.llmModel = cfg.model || c.llmModel;
          c.llmReasoning = cfg.reasoningEffort || 'none';
          c.llmSystemPrompt = cfg.systemPrompt || c.llmSystemPrompt;
          c.hasKey = !!cfg.hasKey;
          c.ttsEngine = cfg.ttsEngine || c.ttsEngine;
          c.ttsUrl = cfg.ttsUrl || '';
          c.ttsModel = cfg.ttsModel || c.ttsModel;
          c.ttsVoice = cfg.ttsVoice || c.ttsVoice;
          if (typeof cfg.ttsSpeed === 'number') c.ttsSpeed = cfg.ttsSpeed;
          if (typeof cfg.autoSpeak === 'boolean') c.autoSpeak = cfg.autoSpeak;
          if (typeof cfg.continuousChat === 'boolean') c.continuousChat = cfg.continuousChat;
          if (typeof cfg.earlyEndpoint === 'boolean') c.earlyEndpoint = cfg.earlyEndpoint;
          c.hasTtsKey = !!cfg.hasTtsKey;
          c.haUrl = cfg.haUrl || '';
          c.hasHaToken = !!cfg.hasHaToken;
          c.hasWeatherKey = !!cfg.hasWeatherKey;
          c.weatherCity = cfg.weatherCity || '';
          c.weatherHost = cfg.weatherHost || c.weatherHost;
          // 新增字段必须在这里显式映射，否则页面永远读不到（保存了也显示为空）
          c.weatherAuth = cfg.weatherAuth || 'jwt';
          c.weatherJwtIss = cfg.weatherJwtIss || '';
          c.weatherJwtProjectId = cfg.weatherJwtProjectId || '';
          c.weatherJwtKeyId = cfg.weatherJwtKeyId || '';
          c.hasWeatherJwtKey = !!cfg.hasWeatherJwtKey;
          c.weatherJwtKey = cfg.weatherJwtKey || '';
          c.weatherJwtPub = cfg.weatherJwtPub || '';
          c.asrEngine = cfg.asrEngine || 'native';
          c.asrUrl = cfg.asrUrl || '';
          try {
            R1.cloudFlags = { netease: !!cfg.hasNeteaseCookie, qq: !!cfg.hasQQCookie };
            R1.applyCloudSourceUi();
          } catch (e) { }
          if (Array.isArray(cfg.alarms)) state.alarms = cfg.alarms;
          persist();
          notify();
        }).catch(function (e) { notifyAuthProblem(e); });
        api('GET', '/api/v1/history').then(function (h) {
          if (Array.isArray(h.history)) {
            state.chatHistory = h.history;
            notify();
          }
        }).catch(function (e) { notifyAuthProblem(e); });
        api('GET', '/api/v1/music/list').then(function (m) {
          state.speakerTracks = (m.tracks || []).map(function (t) {
            return Object.assign({ duration: 0, lyrics: [] }, t);
          });
          if (state.player.hasTrack) state.trackIdx = findTrackIdx(state.player.source);
          notify();
        }).catch(function (e) { notifyAuthProblem(e); });
        syncPlayer();
        if (!silent || wasOffline) addLog('INFO', '已连接音箱服务 ' + (apiBase() || location.host), 'HttpClient');
        if (!silent) toast('已连接 R1 音箱，状态已同步', 'ok');
        return true;
      })
      .catch(function (err) {
        if (state.online) addLog('WARN', '与音箱失去连接: ' + err.message, 'HttpClient');
        state.online = false;
        if (err && err.auth) state.authRequired = true;
        notify();
        if (!silent) addLog('WARN', '音箱暂未响应: ' + err.message, 'HttpClient');
        notifyAuthProblem(err);
        return false;
      })
      .then(function (v) {
        syncInFlight = false;
        return v;
      }, function (e) {
        syncInFlight = false;
        return false;
      });
  }

  function fetchSpeakerLogs() {
    if (!state.online) return;
    api('GET', '/api/v1/logs', null, 10000).then(function (d) {
      if (!Array.isArray(d.logs) || !d.logs.length) return;
      // 只替换设备侧日志，保留本页面产生的控制台日志，避免每次刷新把本地记录冲掉
      const kept = logEntries.filter(function (e) { return e.source !== 'device'; });
      const dev = d.logs.map(function (i) {
        return {
          time: i.time || '',
          level: i.level || 'INFO',
          tag: i.tag || 'SYSTEM',
          msg: i.msg || '',
          source: 'device'
        };
      });
      logEntries.length = 0;
      kept.concat(dev).slice(-300).forEach(function (e) { logEntries.push(e); });
      logCallbacks.forEach(function (cb) {
        try { cb(logEntries[logEntries.length - 1], logEntries); } catch (e) {}
      });
    }).catch(function (e) { notifyAuthProblem(e); });
  }

  // ==========================================
  // 曲库辅助
  // ==========================================
  const IDLE_TRACK = {
    id: 'idle',
    title: '未在播放',
    subTitle: '未在播放',
    artist: '等待选择曲目',
    album: '音箱本地存储 / HTTP 音频流',
    format: 'STANDBY',
    spec: '--',
    source: 'all',
    duration: 0,
    lyrics: []
  };

  function trackKey(t) {
    return t.path || t.url || '';
  }

  function allTracks() {
    return state.streams.concat(state.speakerTracks);
  }

  function findTrackIdx(key) {
    if (!key) return -1;
    return allTracks().findIndex(function (t) { return trackKey(t) === key; });
  }

  // ==========================================
  // 对外公开 API (window.R1)
  // ==========================================
  const R1 = {
    get TRACKS() { return allTracks(); },
    getState: function () { return state; },
    getCurrentTrack: function () {
      const p = state.player;
      if (!p.hasTrack) return IDLE_TRACK;
      const idx = findTrackIdx(p.source);
      const base = idx >= 0 ? allTracks()[idx] : {
        id: 'cur',
        title: p.title || p.source,
        subTitle: p.title || p.source,
        artist: 'HTTP 音频流',
        album: p.source,
        format: 'STREAM',
        spec: 'HTTP Stream',
        source: 'stream'
      };
      return Object.assign({}, base, {
        duration: Math.floor((p.durationMs || 0) / 1000),
        lyrics: state.lyrics
      });
    },
    getLogs: function () { return logEntries; },
    onStateChange: function (fn) { listeners.push(fn); fn(state); },
    onLog: function (fn) { logCallbacks.push(fn); },
    toast: toast,
    openModal: openModal,
    confirmModal: confirmModal,
    bindLightDismiss: bindLightDismiss,
    formatTime: formatTime,
    formatUptime: formatUptime,
    nowTimeStr: nowTimeStr,
    escapeHtml: escapeHtml,
    playPauseGlyph: playPauseGlyph,
    addLog: addLog,
    syncFromSpeaker: syncFromSpeaker,
    fetchSpeakerLogs: fetchSpeakerLogs,
    setToken: function (t) {
      state.token = (t || '').trim();
      state.authRequired = !state.token;
      persist();
      notify();
      if (state.token) {
        authPrompted = false;
        syncFromSpeaker(false);
      }
    },
    promptToken: function () {
      const desc = state.authRequired
        ? '修改音箱访问令牌（至少 4 位）；留空保存将关闭鉴权，局域网内任何设备可直接访问控制台。'
        : '为音箱控制台设置访问令牌（至少 4 位）；留空保持开放访问。';
      openModal({
        title: '访问令牌设置',
        desc: desc,
        confirmText: '保存令牌',
        fields: [
          { name: 'token', label: '访问令牌', type: 'password', mono: true, placeholder: '至少 4 位字符，留空表示关闭鉴权', value: '' }
        ]
      }).then(function (res) {
        if (!res) return;
        const val = (res.token || '').trim();
        if (val && val.length < 4) {
          toast('令牌至少需要 4 位字符', 'err');
          return;
        }
        api('POST', '/api/v1/token', { token: val }).then(function (j) {
          state.token = val;
          state.authRequired = !!j.authRequired;
          persist();
          notify();
          toast(val ? '访问令牌已保存到音箱' : '已关闭访问令牌（控制台开放）', 'ok');
          if (val) {
            location.href = '/?token=' + encodeURIComponent(val);
          }
        }).catch(function (e) {
          toast('设置失败：' + e.message, 'err');
        });
      });
    },
    loopName: function (m) {
      return ({ list: '列表循环', single: '单曲循环', shuffle: '随机播放' })[m || state.loopMode] || '列表循环';
    },

    promptSpeakerHost: function () {
      if (isServedFromSpeaker) {
        if (state.authRequired || !state.token) {
          R1.promptToken();
          return;
        }
        toast('当前页面已由音箱 (' + location.host + ') 直接提供服务');
        return;
      }
      openModal({
        title: '连接斐讯 R1 音箱',
        desc: '输入音箱局域网地址与端口（默认 8686），若已开启鉴权可一并填写访问令牌。',
        confirmText: '连接音箱',
        fields: [
          { name: 'hostIp', label: '音箱地址 : 端口', type: 'text', mono: true, placeholder: '192.168.1.88:8686', value: state.hostIp || '192.168.1.88:8686' },
          { name: 'token', label: '访问令牌（可选）', type: 'password', mono: true, placeholder: '未开启鉴权可留空', value: state.token || '' }
        ]
      }).then(function (res) {
        if (!res) return;
        state.hostIp = (res.hostIp || '').trim().replace(/^https?:\/\//, '');
        if ((res.token || '').trim()) state.token = res.token.trim();
        persist();
        if (!state.hostIp) {
          state.online = false;
          notify();
          toast('已清除音箱连接地址');
          return;
        }
        toast('正在连接音箱 ' + state.hostIp + ' ...');
        syncFromSpeaker(false).then(function (ok) {
          if (!ok) toast('未能连接 ' + state.hostIp + '，请检查音箱网络、端口或访问令牌', 'err');
        });
      });
    },

    // ---- 云音乐（网易云） ----
    // ---- 云音乐（网易云 / QQ 音乐） ----
    cloudResults: [],
    cloudSource: 'netease',
    cloudFlags: { netease: false, qq: false, songloft: false },

    cloudStatus: function () {
      api('GET', '/api/v1/config').then(function (c) {
        R1.cloudFlags = { netease: !!c.hasNeteaseCookie, qq: !!c.hasQQCookie, songloft: !!(c.songloftUrl && c.hasSongloftToken) };
        R1.songloftUrl = c.songloftUrl || '';
        R1.applyCloudSourceUi();
      }).catch(function () { });
    },

    /** 点歌音源开关：哪些音源参与「按歌名点歌」，以及顺序（本地 → 网易云 → QQ）。 */
    musicProviderFlags: { songloft: true, netease: true, qq: true },

    loadMusicProviders: function () {
      api('GET', '/api/v1/config').then(function (c) {
        const csv = (c && c.musicProviders) || 'songloft,netease,qq';
        const f = { songloft: false, netease: false, qq: false };
        csv.split(',').forEach(function (x) {
          const id = (x || '').trim();
          if (f[id] !== undefined) f[id] = true;
        });
        R1.musicProviderFlags = f;
        R1.applyMusicProviderUi();
      }).catch(function () { });
    },

    toggleMusicProvider: function (id) {
      const f = R1.musicProviderFlags;
      f[id] = !f[id];
      const csv = ['songloft', 'netease', 'qq'].filter(function (x) { return f[x]; }).join(',');
      if (!csv) {
        f[id] = true;
        R1.applyMusicProviderUi();
        toast('至少保留一个音源', 'err');
        return;
      }
      api('POST', '/api/v1/config', { musicProviders: csv })
        .then(function () { toast('点歌音源已保存', 'ok'); R1.applyMusicProviderUi(); })
        .catch(function (e) {
          f[id] = !f[id];
          R1.applyMusicProviderUi();
          toast('保存失败：' + e.message, 'err');
        });
    },

    applyMusicProviderUi: function () {
      const f = R1.musicProviderFlags;
      [['mpSongloft', 'songloft'], ['mpNetease', 'netease'], ['mpQq', 'qq'],
       ['mMpSongloft', 'songloft'], ['mMpNetease', 'netease'], ['mMpQq', 'qq']].forEach(function (p) {
        const el = document.getElementById(p[0]);
        if (el) el.checked = !!f[p[1]];
      });
    },

    /** 切换音乐源：只改请求参数与界面状态，不影响已播放的曲目。 */
    setCloudSource: function (src) {
      R1.cloudSource = src;
      R1.cloudResults = [];
      R1.renderCloudResults();
      R1.applyCloudSourceUi();
      const input = document.getElementById('cloudSearchInput') || document.getElementById('mCloudSearchInput');
      if (input) {
        input.placeholder = src === 'songloft' ? '本地曲库：歌名 / 歌手'
          : (src === 'qq' ? 'QQ 音乐：歌名 / 歌手' : '网易云：歌名 / 歌手（可搜歌词）');
      }
    },

    /** 同步音源选中态与登录徽标（桌面端走 r1-seg-btn/r1-badge，移动端保持原类）。 */
    applyCloudSourceUi: function () {
      const src = R1.cloudSource;
      const on = !!R1.cloudFlags[src];
      const isDesktopUi = document.body && document.body.classList.contains('r1-desktop-body');
      [['cloudSrcNetease', 'netease'], ['cloudSrcQQ', 'qq'], ['cloudSrcSongloft', 'songloft']].forEach(function (p) {
        ['', 'm-'].forEach(function (prefix) {
          const el = document.getElementById(prefix + p[0]);
          if (!el) return;
          const active = p[1] === src;
          if (!prefix && isDesktopUi) {
            el.className = 'r1-seg-btn' + (active ? ' is-active' : '');
            el.setAttribute('aria-pressed', active ? 'true' : 'false');
          } else {
            el.className = 'px-2.5 py-0.5 rounded-full font-label-xs text-label-xs font-mono shrink-0 border transition-all '
              + (active
                ? 'bg-primary/20 border-primary/60 text-primary font-bold'
                : 'bg-surface-container-lowest border-primary/20 text-secondary hover:text-primary');
          }
        });
      });
      ['cloudLoginState', 'mCloudLoginState'].forEach(function (id) {
        const el = document.getElementById(id);
        if (!el) return;
        el.textContent = on ? (src === 'songloft' ? '已配置' : '已登录') : (src === 'songloft' ? '未配置' : '未登录');
        if (id === 'cloudLoginState' && isDesktopUi) {
          el.className = on ? 'r1-badge r1-badge-success' : 'r1-badge';
        } else {
          el.className = on
            ? 'px-2.5 py-0.5 rounded-full bg-primary/15 border border-primary/60 text-primary font-label-xs text-label-xs font-mono shrink-0'
            : 'px-2.5 py-0.5 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary font-label-xs text-label-xs font-mono shrink-0';
        }
      });
    },

    cloudSetCookie: function () {
      if (R1.cloudSource === 'songloft') { R1.cloudConfigSongloft(); return; }
      const isQQ = R1.cloudSource === 'qq';
      openModal({
        title: isQQ ? 'QQ 音乐登录凭据' : '网易云音乐登录凭据',
        desc: isQQ
          ? '粘贴 y.qq.com 的完整请求头 Cookie（需包含 uin 与 qm_keyst）。'
          : '粘贴 music.163.com 的完整请求头 Cookie（F12 → Network → 任意请求 → Cookie）。',
        confirmText: '保存到音箱',
        fields: [
          { name: 'cookie', label: 'Cookie 内容', type: 'textarea', rows: 4, mono: true, placeholder: '粘贴整段 Cookie 字符串...' }
        ]
      }).then(function (res) {
        if (!res) return;
        api('POST', '/api/v1/music/cookie', { source: R1.cloudSource, cookie: (res.cookie || '').trim() })
          .then(function () { toast('Cookie 已保存到音箱', 'ok'); R1.cloudStatus(); })
          .catch(function (e) { toast('保存失败：' + e.message, 'err'); });
      });
    },

    /** 本地曲库没有 Cookie：地址 + 令牌一起提交，保存后立刻做一次连通性测试。 */
    cloudConfigSongloft: function () {
      openModal({
        title: '配置本地曲库 (Songloft)',
        desc: '填写自建 Songloft 服务地址与个人访问令牌，保存后自动测试连通性。',
        confirmText: '保存并测试',
        fields: [
          { name: 'url', label: '服务地址', type: 'text', mono: true, placeholder: 'http://192.168.1.100:58091', value: R1.songloftUrl || '' },
          { name: 'token', label: '个人访问令牌', type: 'password', mono: true, placeholder: '在 Songloft 网页中生成' }
        ]
      }).then(function (res) {
        if (!res) return;
        api('POST', '/api/v1/config', { songloftUrl: (res.url || '').trim(), songloftToken: (res.token || '').trim() })
          .then(function () {
            toast('本地曲库已保存', 'ok');
            return api('POST', '/api/v1/music/songloft/test', {});
          })
          .then(function (r) {
            if (r && r.ok) toast('连接正常，曲库 ' + r.total + ' 首', 'ok');
            else toast('连接失败：' + ((r && r.error) || '未知原因'), 'err');
            R1.cloudStatus();
          })
          .catch(function (e) { toast('保存失败：' + e.message, 'err'); });
      });
    },

    cloudSearch: function () {
      const input = document.getElementById('cloudSearchInput') || document.getElementById('mCloudSearchInput');
      const kw = input ? input.value.trim() : '';
      if (!kw) { toast('请输入歌名或歌手'); return; }
      toast('搜索中…');
      api('GET', '/api/v1/music/search?source=' + R1.cloudSource + '&q=' + encodeURIComponent(kw), null, 20000)
        .then(function (r) { R1.cloudResults = r.list || []; R1.renderCloudResults(); })
        .catch(function (e) { toast('搜索失败：' + e.message, 'err'); });
    },

    renderCloudResults: function () {
      const list = R1.cloudResults || [];
      const isDesktopUi = document.body && document.body.classList.contains('r1-desktop-body');
      const desktopBody = list.length
        ? list.map(function (s, i) {
            return '<button type="button" onclick="R1.cloudPlay(' + i + ')" class="r1-track-row">'
              + '<div class="r1-row r1-flex-1" style="min-width:0;">'
              + '<span class="r1-mono r1-subtle" style="width:24px;font-size:12px;flex-shrink:0;">' + String(i + 1).padStart(2, '0') + '</span>'
              + '<span class="r1-truncate" style="font-size:14px;font-weight:500;">' + escapeHtml(s.name || '') + '</span>'
              + '</div>'
              + '<span class="r1-subtle r1-truncate" style="font-size:12px;max-width:40%;flex-shrink:0;">' + escapeHtml(s.artist || '') + '</span>'
              + '</button>';
          }).join('')
        : '<div class="r1-empty"><span style="font-size:13px;">输入歌名或歌手后点击搜索</span></div>';

      const mobileBody = list.length
        ? list.map(function (s, i) {
            return '<button onclick="R1.cloudPlay(' + i + ')" class="text-left px-3.5 py-2 rounded-2xl bg-surface-container-lowest/70 border border-primary/15 hover:border-primary/45 transition-all flex items-center justify-between gap-3">'
              + '<span class="font-body-sm text-body-sm text-on-surface truncate">' + escapeHtml(s.name || '') + '</span>'
              + '<span class="font-label-xs text-label-xs text-secondary/70 font-mono truncate shrink-0 max-w-[45%]">' + escapeHtml(s.artist || '') + '</span>'
              + '</button>';
          }).join('')
        : '<p class="font-body-sm text-body-sm text-secondary/70 py-2">没有结果</p>';

      const deskEl = document.getElementById('cloudResults');
      if (deskEl) deskEl.innerHTML = isDesktopUi ? desktopBody : mobileBody;
      const mobEl = document.getElementById('mCloudResults');
      if (mobEl) mobEl.innerHTML = mobileBody;
    },

    cloudPlay: function (i) {
      const s = (R1.cloudResults || [])[i];
      if (!s) return;
      toast('正在获取播放地址…');
      api('POST', '/api/v1/music/play', {
        source: R1.cloudSource, id: s.id, title: s.name + ' - ' + s.artist
      }, 20000).then(function () {
        toast('开始播放：' + s.name, 'ok');
        syncFromSpeaker(false);
      }).catch(function (e) {
        toast('播放失败：' + e.message, 'err');
      });
    },

    // ---- 音箱播放器控制 ----
    togglePlay: function () {
      if (!state.online) {
        toast('未连接音箱：请先点击右上角设置音箱 IP:8686', 'err');
        return;
      }
      if (!state.player.hasTrack && allTracks().length === 0) {
        toast('曲库为空：请将音频放入音箱 /sdcard/Music 或添加音频流 URL');
        return;
      }
      if (!state.player.hasTrack) {
        R1.selectTrack(0);
        return;
      }
      api('POST', '/api/v1/player/toggle')
        .then(function (p) { applyPlayer(p); notify(); })
        .catch(function (e) { toast(e.message, 'err'); });
    },

    selectTrack: function (idx) {
      const all = allTracks();
      if (!all.length) {
        toast('当前曲库为空');
        return;
      }
      const t = all[(idx + all.length) % all.length];
      if (!state.online) {
        toast('未连接音箱，无法在音箱端播放《' + t.title + '》', 'err');
        return;
      }
      api('POST', '/api/v1/player/play', { source: trackKey(t), title: t.title })
        .then(function (p) {
          applyPlayer(p);
          notify();
          addLog('INFO', '开始播放《' + t.title + '》', 'MediaPlayer');
        })
        .catch(function (e) { toast(e.message, 'err'); });
    },

    prevTrack: function () { stepTrack(-1); },
    nextTrack: function () { stepTrack(1); },

    seekTo: function (sec) {
      if (!state.online || !state.player.hasTrack) return;
      api('POST', '/api/v1/player/seek', { ms: Math.max(0, Math.floor(sec)) * 1000 })
        .then(function (p) { applyPlayer(p); notify(); })
        .catch(function () {});
    },

    seekByRatio: function (ratio) {
      const dur = (state.player.durationMs || 0) / 1000;
      if (!state.player.hasTrack || !dur) {
        toast('未在播放曲目');
        return;
      }
      R1.seekTo(Math.round(Math.max(0, Math.min(1, ratio)) * dur));
    },

    toggleLoopMode: function () {
      const modes = ['list', 'single', 'shuffle'];
      const prev = state.loopMode;
      const next = modes[(modes.indexOf(state.loopMode) + 1) % modes.length];
      state.loopMode = next;
      notify();
      if (!state.online) {
        toast('播放模式：' + R1.loopName(next));
        return;
      }
      api('POST', '/api/v1/player/mode', { loop: next })
        .then(function (p) {
          applyPlayer(p);
          notify();
          toast('播放模式：' + R1.loopName(next));
        })
        .catch(function (e) {
          state.loopMode = prev;
          notify();
          toast('切换播放模式失败：' + e.message, 'err');
        });
    },

    setSleepTimer: function (minutes) {
      state.sleepRemainSec = minutes > 0 ? minutes * 60 : 0;
      notify();
      if (!state.online) {
        toast(minutes > 0 ? ('已设定 ' + minutes + ' 分钟睡眠定时') : '已关闭睡眠定时', 'ok');
        return;
      }
      api('POST', '/api/v1/player/sleep', { minutes: minutes })
        .then(function (p) {
          applyPlayer(p);
          notify();
          toast(minutes > 0 ? (minutes + ' 分钟后自动停止播放') : '已关闭睡眠定时', 'ok');
        })
        .catch(function (e) { toast(e.message, 'err'); });
    },

    cycleSleepTimer: function () {
      const steps = [15, 30, 60, 0];
      const cur = Math.ceil(state.sleepRemainSec / 60);
      const next = cur <= 0 ? 15 : (steps.find(function (s) { return s > cur; }) || 0);
      R1.setSleepTimer(next);
    },

    addStream: function (url, title) {
      if (!url) return;
      const t = (title || '网络音频流').trim();
      state.streams = state.streams.filter(function (s) { return s.url !== url; });
      state.streams.unshift({
        id: 'stream_' + Date.now(),
        title: t,
        subTitle: t,
        artist: 'HTTP 音频流',
        album: url,
        format: 'STREAM',
        spec: 'HTTP Stream',
        source: 'stream',
        url: url,
        duration: 0,
        lyrics: []
      });
      save();
      toast('已添加音频流《' + t + '》', 'ok');
      if (state.online) R1.selectTrack(0);
    },

    removeStream: function (url) {
      state.streams = state.streams.filter(function (s) { return s.url !== url; });
      save();
      toast('已移除音频流');
    },

    setMusicSource: function (src) {
      state.musicSource = src;
      save();
    },

    // ---- 硬件主音量与蓝牙控制 ----
    setVolume: function (val) {
      const pct = Math.max(0, Math.min(100, Math.round(val)));
      state.volume = pct;
      state.muted = pct === 0;
      if (pct > 0) state.lastVolume = pct;
      notify();
      if (!state.online) return;
      api('POST', '/api/v1/volume', { pct: pct })
        .then(function (d) {
          if (typeof d.pct === 'number') {
            state.volume = d.pct;
            notify();
          }
        })
        .catch(function (e) { toast(e.message, 'err'); });
    },

    stepVolume: function (delta) {
      R1.setVolume(state.volume + delta);
    },

    toggleMute: function () {
      R1.setVolume(state.muted ? (state.lastVolume || 40) : 0);
    },

    /** 把统一开关接口的状态画到徽章上，并把错误写进 #castHint。 */
    paintCast: function (r) {
      const d = document.getElementById('castDlnaBadge');
      const a = document.getElementById('castAirplayBadge');
      const hint = document.getElementById('castHint');
      const isDesktopUi = document.body && document.body.classList.contains('r1-desktop-body');
      const dlnaOn = r && r.dlna && (r.dlna.on === true || r.dlna === true);
      const airOn = r && r.airplay && (r.airplay.listening === true || r.airplay.on === true || r.airplay === true);
      if (isDesktopUi) {
        if (d) {
          d.textContent = dlnaOn ? 'DLNA · 开' : 'DLNA';
          d.className = 'r1-chip' + (dlnaOn ? ' is-active' : '');
          d.setAttribute('aria-pressed', dlnaOn ? 'true' : 'false');
        }
        if (a) {
          a.textContent = airOn ? 'AirPlay · 开' : 'AirPlay';
          a.className = 'r1-chip' + (airOn ? ' is-active' : '');
          a.setAttribute('aria-pressed', airOn ? 'true' : 'false');
        }
      } else {
        const onCls = 'px-2.5 py-1 rounded-full bg-primary/15 border border-primary/60 text-primary font-label-xs text-label-xs font-mono cursor-pointer';
        const offCls = 'px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary font-label-xs text-label-xs font-mono cursor-pointer';
        if (d) { d.textContent = dlnaOn ? 'DLNA 开' : 'DLNA'; d.className = dlnaOn ? onCls : offCls; }
        if (a) { a.textContent = airOn ? 'AirPlay 开' : 'AirPlay'; a.className = airOn ? onCls : offCls; }
      }
      if (hint) {
        const parts = [];
        if (r && r.dlna && r.dlna.playError) parts.push('DLNA 播放: ' + r.dlna.playError);
        if (r && r.dlna && r.dlna.error) parts.push('DLNA: ' + r.dlna.error);
        if (r && r.airplay && r.airplay.error) parts.push('AirPlay: ' + r.airplay.error);
        if (r && r.airplay && r.airplay.wanted && !airOn) parts.push('隔空播放想开但端口未监听');
        if (r && r.bluetooth && r.bluetooth.error) parts.push('蓝牙: ' + r.bluetooth.error);
        hint.textContent = parts.join(' · ');
        hint.title = parts.join('\n');
      }
    },

    refreshCast: function () {
      api('GET', '/api/v1/receivers', null, 8000).then(function (r) {
        R1.paintCast(r);
      }).catch(function () {
        // 旧固件没有 /receivers 时回落到端口探测，避免首页空白
        api('GET', '/api/v1/cast/status', null, 8000).then(function (r) {
          R1.paintCast(r);
        }).catch(function () { });
      });
    },

    /** 点击徽章：切换对应接收端开关（不是推流）。 */
    toggleCast: function (target) {
      if (!state.online) {
        toast('音箱不在线', 'err');
        return;
      }
      const names = { dlna: 'DLNA', airplay: '隔空播放' };
      api('GET', '/api/v1/receivers', null, 8000).then(function (cur) {
        const node = cur && cur[target];
        const isOn = node && (node.on === true || node.listening === true);
        const body = {};
        body[target] = !isOn;
        toast((isOn ? '正在关闭' : '正在打开') + (names[target] || target));
        return api('POST', '/api/v1/receivers', body, 20000).then(function (r) {
          R1.paintCast(r);
          const now = r && r[target];
          const opened = now && (now.on === true || now.listening === true);
          const err = now && now.error;
          if (!isOn && opened) toast((names[target] || target) + '已打开', 'ok');
          else if (isOn && !(now && (now.on === true || now.listening === true))) toast((names[target] || target) + '已关闭', 'ok');
          else toast(err || ((names[target] || target) + '未达到目标状态'), 'err');
        });
      }).catch(function (e) { toast(e.message || '开关没有响应', 'err'); });
    },

    /** 兼容旧 onclick：视为打开/唤醒 DLNA。 */
    wakeCast: function () {
      R1.toggleCast('dlna');
    },

    pairBluetooth: function () {
      if (!state.online) {
        toast('未连接音箱', 'err');
        return;
      }
      toast('正在进入蓝牙配对');
      api('POST', '/api/v1/bluetooth', { enabled: true, discoverable: true }, 15000).then(function (r) {
        state.btEnabled = !!r.btEnabled;
        state.btDiscoverable = !!r.discoverable;
        notify();
        if (r.ok && r.discoverable) toast('已进入配对，两分钟内可被发现', 'ok');
        else toast(r.error || '没能进入可发现', 'err');
        setTimeout(function () { syncFromSpeaker(true); }, 1500);
        R1.refreshCast();
      }).catch(function (e) { toast(e.message, 'err'); });
    },
    /** 灯环：经 L1 的原厂 IPC 控制，只有预置效果。 */
    setLight: function (mode) {
      const names = { off: '关闭', loading: '呼吸灯', wakeup: '唤醒色', dormant: '休眠微光', scan: '流光' };
      api('GET', '/api/v1/l1/light?mode=' + encodeURIComponent(mode), null, 8000)
        .then(function (r) {
          if (!r.ok) { toast('L1 不在线，灯环指令没发出去', 'err'); return; }
          toast('灯环：' + (names[mode] || mode), 'ok');
        })
        .catch(function (e) { toast('灯环指令失败：' + e.message, 'err'); });
    },

    toggleBluetooth: function () {      if (!state.online) {
        toast('未连接音箱，无法切换音箱硬件蓝牙', 'err');
        return;
      }
      if (!state.btSupported) {
        toast('该设备不支持蓝牙', 'err');
        return;
      }
      const want = !state.btEnabled;
      api('POST', '/api/v1/bluetooth', { enabled: want })
        .then(function () {
          state.btEnabled = want;
          notify();
          toast(want ? '正在开启音箱蓝牙' : '已关闭音箱蓝牙', 'ok');
          setTimeout(function () { syncFromSpeaker(true); R1.refreshCast(); }, 2000);
        })
        .catch(function (e) { toast(e.message, 'err'); });
    },

    // ---- 智能对话与指令下发 ----
    sendCommand: function (rawText, sourceLabel) {
      const text = (rawText || '').trim();
      if (!text) return;
      if (text.length > 500) {
        toast('单条指令最多 500 字，当前 ' + text.length + ' 字', 'err');
        return;
      }
      const userMsg = {
        id: Date.now(),
        role: 'user',
        source: sourceLabel || 'Web 控制台',
        time: nowTimeStr(),
        text: text
      };
      state.chatHistory.push(userMsg);
      if (state.chatHistory.length > 50) state.chatHistory = state.chatHistory.slice(-50);
      notify();
      addLog('INFO', '发送指令 -> ' + text, 'Brain');

      api('POST', '/api/v1/chat', { text: text, source: userMsg.source }, 30000)
        .then(function (res) {
          state.chatHistory.push({
            id: Date.now() + 1,
            role: 'assistant',
            model: res.model || state.config.llmModel,
            costMs: res.costMs || 0,
            status: '已执行',
            time: res.time || nowTimeStr(),
            text: res.reply
          });
          if (state.chatHistory.length > 50) state.chatHistory = state.chatHistory.slice(-50);
          notify();
          syncFromSpeaker(true);
        })
        .catch(function (err) {
          const why = (err && err.message) || '未知错误';
          const hint = (err && err.auth)
            ? '需要访问令牌，请点击顶部状态栏或设置页填入令牌。'
            : (!state.online
              ? '音箱暂未连接，请点击顶部状态栏设置局域网 IP:8686。'
              : '音箱已收到请求但执行失败，请检查设置页的 API Key / 网络。');
          state.chatHistory.push({
            id: Date.now() + 1,
            role: 'assistant',
            model: '系统提示',
            costMs: 0,
            status: '失败',
            time: nowTimeStr(),
            text: '请求失败：' + why + '。' + hint
          });
          if (state.chatHistory.length > 50) state.chatHistory = state.chatHistory.slice(-50);
          notify();
          addLog('ERR', '指令失败: ' + why, 'Brain');
          notifyAuthProblem(err);
        });
    },

    clearChatHistory: function () {
      state.chatHistory = [];
      notify();
      if (state.online) {
        api('DELETE', '/api/v1/history').catch(function () {});
      }
      toast('已清空对话历史');
    },

    speak: function (text) {
      if (!text) return;
      if (!state.online) {
        toast('未连接音箱，无法触发音箱喇叭播报', 'err');
        return;
      }
      api('POST', '/api/v1/tts', { text: text }, 25000)
        .then(function (res) {
          const ms = (typeof res.firstAudioMs === 'number' && res.firstAudioMs >= 0) ? ('首帧 ' + res.firstAudioMs + 'ms') : '已开始';
          toast('音箱正在播报 (' + ms + ')', 'ok');
        })
        .catch(function (e) { toast('播报失败：' + e.message, 'err'); });
    },

    stopSpeak: function () {
      if (!state.online) return;
      api('POST', '/api/v1/tts/stop')
        .then(function () { toast('已停止语音播报'); })
        .catch(function () {});
    },

    // ---- 配置与北京时区定时闹钟 ----
    updateConfig: function (patch) {
      const c = state.config;
      if (patch.llmProvider !== undefined) c.llmProvider = patch.llmProvider;
      if (patch.llmModel !== undefined) c.llmModel = patch.llmModel;
      if (patch.reasoningEffort !== undefined) c.llmReasoning = patch.reasoningEffort;
      if (patch.llmBaseUrl !== undefined) c.llmBaseUrl = patch.llmBaseUrl;
      if (patch.llmSystemPrompt !== undefined) c.llmSystemPrompt = patch.llmSystemPrompt;
      if (patch.llmApiKey) c.hasKey = true;
      if (patch.ttsEngine !== undefined) c.ttsEngine = patch.ttsEngine;
      if (patch.ttsVoice !== undefined) c.ttsVoice = patch.ttsVoice;
      if (patch.ttsModel !== undefined) c.ttsModel = patch.ttsModel;
      if (patch.ttsUrl !== undefined) c.ttsUrl = patch.ttsUrl;
      if (patch.ttsSpeed !== undefined) c.ttsSpeed = patch.ttsSpeed;
      if (patch.autoSpeak !== undefined) c.autoSpeak = patch.autoSpeak;
      if (patch.continuousChat !== undefined) c.continuousChat = patch.continuousChat;
      if (patch.earlyEndpoint !== undefined) c.earlyEndpoint = patch.earlyEndpoint;
      if (patch.haUrl !== undefined) c.haUrl = patch.haUrl;
      if (patch.haToken) c.hasHaToken = true;
      if (patch.weatherKey) c.hasWeatherKey = true;
      if (patch.weatherCity !== undefined) c.weatherCity = patch.weatherCity;
      if (patch.weatherHost !== undefined) c.weatherHost = patch.weatherHost;
      if (patch.weatherAuth !== undefined) c.weatherAuth = patch.weatherAuth;
      if (patch.weatherJwtIss !== undefined) c.weatherJwtIss = patch.weatherJwtIss;
      if (patch.weatherJwtProjectId !== undefined) c.weatherJwtProjectId = patch.weatherJwtProjectId;
      if (patch.weatherJwtKeyId !== undefined) c.weatherJwtKeyId = patch.weatherJwtKeyId;
      if (patch.weatherJwtKey) { c.hasWeatherJwtKey = true; c.weatherJwtKey = patch.weatherJwtKey; }
      if (patch.weatherJwtPub !== undefined) c.weatherJwtPub = patch.weatherJwtPub;
      if (patch.asrEngine !== undefined) c.asrEngine = patch.asrEngine;
      if (patch.asrUrl !== undefined) c.asrUrl = patch.asrUrl;
      save();

      if (!state.online) {
        toast('配置已暂存到浏览器本地（当前未连接音箱，不会下发）', 'ok');
        return Promise.resolve();
      }
      // 返回 Promise：设置页的确认条要等写入完成再收起，不能发完就当成功
      return api('POST', '/api/v1/config', Object.assign({}, patch, { alarms: state.alarms }), 15000)
        .then(function () {
          toast('配置已持久化保存到音箱', 'ok');
          syncFromSpeaker(true);
        })
        .catch(function (e) {
          toast('保存到音箱失败：' + e.message, 'err');
          notifyAuthProblem(e);
          throw e;
        });
    },

    addAlarm: function (item) {
      state.alarms.unshift({
        id: Date.now(),
        time: item.time || '08:00',
        repeat: item.repeat || '每天',
        label: item.label || '定时提醒',
        // action 决定触发时做什么：speak = 只念备注，weather = 先取天气再播报。
        // 漏了这个字段，服务端就当 speak 处理，7:55 的闹钟只会念出「天气播报」四个字。
        action: item.action || 'speak',
        enabled: true
      });
      pushAlarms('已新增北京时间 ' + (item.time || '08:00') + ' 闹钟');
    },

    toggleAlarm: function (id) {
      const a = state.alarms.find(function (x) { return String(x.id) === String(id); });
      if (!a) return;
      a.enabled = !a.enabled;
      pushAlarms('闹钟 ' + a.time + (a.enabled ? ' 已启用' : ' 已停用'));
    },

    deleteAlarm: function (id) {
      state.alarms = state.alarms.filter(function (x) { return String(x.id) !== String(id); });
      pushAlarms('闹钟已删除');
    },

    // ---- 运维中枢、应用管理与 OTA 升级 ----
    fetchApps: function () {
      if (!state.online) return Promise.reject(new Error('未连接音箱'));
      return api('GET', '/api/v1/apps');
    },

    uploadApk: function (file, onProgress) {
      if (!state.online) return Promise.reject(new Error('未连接音箱'));
      if (!file || !file.name || !file.name.toLowerCase().endsWith('.apk')) {
        return Promise.reject(new Error('请选择有效的 Android 安装包 (.apk 文件)'));
      }
      return new Promise(function (resolve, reject) {
        const xhr = new XMLHttpRequest();
        const base = apiBase();
        xhr.open('POST', base + '/api/v1/apps/install');
        xhr.setRequestHeader('Content-Type', 'application/octet-stream');
        if (state.token) xhr.setRequestHeader('X-Token', state.token);

        if (xhr.upload && typeof onProgress === 'function') {
          xhr.upload.onprogress = function (e) {
            if (e.lengthComputable) {
              const pct = Math.round((e.loaded / e.total) * 100);
              onProgress({ loaded: e.loaded, total: e.total, percent: pct });
            }
          };
        }

        xhr.onload = function () {
          let res;
          try {
            res = JSON.parse(xhr.responseText || '{}');
          } catch (e) {
            res = { ok: false, error: '解析响应失败 (HTTP ' + xhr.status + ')' };
          }
          if (xhr.status >= 200 && xhr.status < 300 && res.success !== false) {
            resolve(res);
          } else {
            reject(new Error(res.error || res.output || ('安装失败 (HTTP ' + xhr.status + ')')));
          }
        };

        xhr.onerror = function () {
          reject(new Error('网络传输失败，请检查音箱连接'));
        };

        xhr.send(file);
      });
    },

    uninstallApp: function (pkg) {
      if (!state.online) return Promise.reject(new Error('未连接音箱'));
      return api('POST', '/api/v1/apps/uninstall', { package: pkg });
    },

    stopApp: function (pkg) {
      if (!state.online) return Promise.reject(new Error('未连接音箱'));
      return api('POST', '/api/v1/apps/stop', { package: pkg });
    },

    rebootSystem: function () {
      if (!state.online) return Promise.reject(new Error('未连接音箱'));
      return api('POST', '/api/v1/system/reboot');
    },

    cleanSystem: function () {
      if (!state.online) return Promise.reject(new Error('未连接音箱'));
      return api('POST', '/api/v1/system/clean');
    },

    queryWeather: function (city) {
      const q = city ? ('?city=' + encodeURIComponent(city)) : '';
      return api('GET', '/api/v1/weather' + q);
    },

    clearLocalCache: function () {
      try { localStorage.removeItem(STORAGE_KEY); } catch (e) {}
      state.streams = [];
      state.chatHistory = [];
      notify();
      toast('已清空本地缓存', 'ok');
    },

    resetFactory: function () {
      R1.clearLocalCache();
    }
  };

  function pushAlarms(okMsg) {
    save();
    notify();
    if (!state.online) {
      toast(okMsg, 'ok');
      return;
    }
    api('POST', '/api/v1/config', { alarms: state.alarms })
      .then(function () { toast(okMsg, 'ok'); })
      .catch(function (e) { toast(e.message, 'err'); });
  }

  function stepTrack(delta) {
    if (!state.online) {
      toast('未连接音箱，无法切歌', 'err');
      return;
    }
    const local = state.speakerTracks.length > 0 && state.player.source.charAt(0) === '/';
    if (local) {
      api('POST', '/api/v1/player/' + (delta > 0 ? 'next' : 'prev'))
        .then(function (p) { applyPlayer(p); notify(); })
        .catch(function (e) { toast(e.message, 'err'); });
      return;
    }
    const all = allTracks();
    if (!all.length) {
      toast('当前曲库为空');
      return;
    }
    const cur = findTrackIdx(state.player.source);
    R1.selectTrack(cur < 0 ? 0 : cur + delta);
  }

  // 初始化记录一条北京时区启动日志并尝试连接音箱
  addLog('INFO', '黑金声学控制台已就绪 (北京标准时间 UTC+8)', 'System');
  syncFromSpeaker(true);

  let tick = 0;
  // 页面不可见时停止轮询：这台音箱的 8686 服务只有 8 个线程，
  // 手机浏览器常驻后台还每秒打一遍状态没有意义，切回前台立刻补一次。
  let pageHidden = false;
  document.addEventListener('visibilitychange', function () {
    pageHidden = !!document.hidden;
    if (!pageHidden && state.online) syncFromSpeaker(true);
  });

  setInterval(function () {
    if (pageHidden) return;
    tick++;
    if (state.sleepRemainSec > 0) state.sleepRemainSec--;
    if (state.online) {
      state.uptimeSec++;
      if (state.isPlaying) state.currentTime++;
      if (tick % 3 === 0) syncPlayer();
    }
    if (tick % 10 === 0) syncFromSpeaker(true);
    notify();
  }, 1000);

  // 把底层请求函数暴露出去：页面里的「获取模型/测试连接」等按钮要用
  R1.request = api;
  window.R1 = R1;
})(window);
