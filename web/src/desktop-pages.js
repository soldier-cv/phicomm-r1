/**
 * 桌面端 4 个独立页面的主体内容模板 (desktop-pages.js)
 * Material Design 3 桌面工作台重构版：
 *  - 统一使用 design-system.css 语义组件类（.r1-btn-* / .r1-card / .r1-chip / .r1-seg / .r1-switch / .r1-slider）
 *  - 全面适配黑金暗色 & 清透轻拟态双主题，零漏样式按钮
 *  - 全部交互确认与输入弹窗走原生 <dialog> (R1.openModal / R1.confirmModal)
 *  - 严禁网页端语音拾音输入按钮；零虚拟数据；北京时间 UTC+8
 */

// =========================================================
// 页面 01: 首页工作台 (desktop/home.html)
// =========================================================
function getDesktopHome() {
  const html = `
        <!-- 1. 顶部指令交互坞 -->
        <section class="r1-card">
          <div class="r1-search-bar">
            <span class="material-symbols-outlined r1-primary-text" style="font-size:20px;" aria-hidden="true">&#xE312;</span>
            <input id="cmdInput" type="text" maxlength="500" placeholder="输入指令或对话内容，按 Enter 发送，Esc 清空（按 / 快速聚焦）" aria-label="输入指令或对话内容">
            <button type="button" id="btnClearCmd" class="r1-btn r1-btn-ghost r1-btn-sm" title="清空输入框 (Esc)">清空</button>
            <button type="button" id="sendBtn" class="r1-btn r1-btn-primary" aria-label="发送指令">
              <span>发送</span>
              <span class="material-symbols-outlined" aria-hidden="true">&#xE5D8;</span>
            </button>
          </div>

          <div class="r1-grid-2">
            <!-- 最近一条用户指令 -->
            <div class="r1-subcard r1-col" style="justify-content:space-between;gap:8px;">
              <div class="r1-row-between">
                <span class="r1-subtle" style="font-size:12px;font-weight:600;" id="homeLastUserSrc">你的指令</span>
                <span class="r1-mono r1-subtle" style="font-size:12px;" id="homeLastUserTime">待命</span>
              </div>
              <p id="homeLastUserText" class="r1-muted" style="margin:0;font-size:14px;line-height:1.55;">还没有对话记录</p>
            </div>

            <!-- 最近一条小讯回复 -->
            <div class="r1-subcard r1-col" style="justify-content:space-between;gap:10px;">
              <div class="r1-col" style="gap:6px;">
                <div class="r1-row-between">
                  <span class="r1-row" style="gap:8px;">
                    <span class="r1-dot is-active" aria-hidden="true"></span>
                    <span class="r1-primary-text" style="font-size:12px;font-weight:600;" id="homeLastAiMeta">小讯</span>
                  </span>
                  <span class="r1-badge" id="homeLastAiStatus">就绪</span>
                </div>
                <p id="homeLastAiText" style="margin:0;font-size:14px;line-height:1.55;">在。</p>
              </div>
              <div class="r1-row-between" style="padding-top:8px;border-top:1px solid var(--r1-border-subtle);">
                <a href="chat.html" class="r1-btn r1-btn-ghost r1-btn-sm">完整对话 &rarr;</a>
                <button type="button" id="btnReplayHome" class="r1-btn r1-btn-tonal r1-btn-sm">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE042;</span>
                  <span>播报回复</span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- 2. 主工作台：左 7 列黑胶播控 + 右 5 列硬件与快捷控制 -->
        <div class="r1-grid-home">
          <!-- 左列：黑胶唱片播放台 -->
          <section class="r1-card" style="justify-content:space-between;">
            <div class="r1-card-head">
              <div class="r1-row-wrap">
                <h2 class="r1-card-title">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE019;</span>
                  <span>正在播放</span>
                </h2>
                <span class="r1-badge r1-badge-primary" id="homeSourceBadge">音箱本机</span>
                <span class="r1-badge r1-mono" id="homeDspActiveTag">空闲</span>
              </div>
              <div class="r1-spectrum" id="homeSpectrumBox" aria-hidden="true">
                <div class="spec-bar" style="height:6px;"></div>
                <div class="spec-bar" style="height:8px;"></div>
                <div class="spec-bar" style="height:6px;"></div>
                <div class="spec-bar" style="height:10px;"></div>
                <div class="spec-bar" style="height:8px;"></div>
                <div class="spec-bar" style="height:6px;"></div>
                <div class="spec-bar" style="height:8px;"></div>
                <div class="spec-bar" style="height:6px;"></div>
              </div>
            </div>

            <div class="r1-row" style="gap:24px;flex-wrap:wrap;padding:6px 0;">
              <button type="button" onclick="R1.togglePlay()" class="r1-vinyl-btn" title="点击播放或暂停" aria-label="播放或暂停">
                <div id="homeVinylDisc" class="r1-vinyl-disc vinyl-spinning vinyl-paused">
                  <div class="r1-vinyl-center">
                    <span class="r1-vinyl-label-txt" id="homeDiscCenterLabel">R1 HI-FI</span>
                    <span class="r1-vinyl-hole"></span>
                  </div>
                </div>
              </button>

              <div class="r1-col r1-flex-1" style="gap:12px;">
                <div>
                  <div class="r1-row" style="gap:8px;">
                    <span id="homeTrackTitle" class="r1-truncate" style="font-size:20px;font-weight:700;line-height:1.3;">未在播放</span>
                    <span class="r1-badge r1-badge-primary r1-mono" id="homeTrackFormat">STANDBY</span>
                  </div>
                  <p id="homeTrackAlbum" class="r1-muted r1-truncate" style="margin:4px 0 0;font-size:14px;">等待选择曲目</p>
                </div>

                <div class="r1-lyric-strip">
                  <span class="material-symbols-outlined" style="font-size:16px;flex-shrink:0;" aria-hidden="true">&#xE405;</span>
                  <span id="homeLyricLine" class="r1-truncate">暂无歌词</span>
                </div>

                <div class="r1-col" style="gap:4px;">
                  <input id="homeSeekRange" type="range" min="0" max="1000" value="0" class="r1-slider" aria-label="播放进度" style="--val:0%;">
                  <div class="r1-row-between r1-mono r1-subtle" style="font-size:12px;">
                    <span class="r1-primary-text" id="homeCurTime">00:00</span>
                    <span id="homeCenterSpec">--</span>
                    <span id="homeTotTime">00:00</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="r1-row-between" style="padding-top:14px;border-top:1px solid var(--r1-border-subtle);flex-wrap:wrap;">
              <a href="music.html" class="r1-btn r1-btn-outlined r1-btn-sm">
                <span class="material-symbols-outlined" aria-hidden="true">&#xE03D;</span>
                <span>打开曲库</span>
              </a>

              <div class="r1-row" style="gap:12px;">
                <button type="button" onclick="R1.prevTrack()" class="r1-btn r1-btn-outlined r1-btn-icon" aria-label="上一曲" title="上一曲">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE045;</span>
                </button>
                <button type="button" onclick="R1.togglePlay()" id="playBtn" class="r1-btn r1-btn-primary r1-btn-icon-lg" aria-label="播放或暂停" title="播放 / 暂停">
                  <span class="material-symbols-outlined" style="font-size:26px;" id="playIcon" aria-hidden="true">&#xE037;</span>
                </button>
                <button type="button" onclick="R1.nextTrack()" class="r1-btn r1-btn-outlined r1-btn-icon" aria-label="下一曲" title="下一曲">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE044;</span>
                </button>
              </div>

              <button type="button" onclick="R1.toggleLoopMode()" class="r1-btn r1-btn-outlined r1-btn-sm" title="切换播放循环模式">
                <span class="material-symbols-outlined" aria-hidden="true">&#xE040;</span>
                <span id="loopModeText">列表循环</span>
              </button>
            </div>
          </section>

          <!-- 右列：2x2 硬件控制磁贴 + 快捷/灯环控制 -->
          <div class="r1-col" style="gap:16px;justify-content:space-between;">
            <div class="r1-grid-2">
              <!-- 磁贴 1：主音量控制 -->
              <div class="r1-tile">
                <div class="r1-tile-head">
                  <button type="button" onclick="R1.toggleMute()" class="r1-btn r1-btn-ghost r1-btn-sm" style="padding-left:0;font-size:14px;color:var(--r1-fg);" title="点击静音或恢复">
                    <span class="material-symbols-outlined r1-primary-text" aria-hidden="true">&#xE049;</span>
                    <span>主音量</span>
                  </button>
                  <span class="r1-badge r1-badge-primary r1-mono" id="volVal">40%</span>
                </div>
                <div class="r1-row" style="gap:8px;">
                  <button type="button" onclick="R1.stepVolume(-5)" id="volDec" class="r1-btn r1-btn-outlined r1-btn-icon-sm r1-mono" aria-label="减小音量" title="音量 -5%">-</button>
                  <input id="volRange" type="range" min="0" max="100" value="40" class="r1-slider r1-flex-1" aria-label="主音量滑块" style="--val:40%;">
                  <button type="button" onclick="R1.stepVolume(5)" id="volInc" class="r1-btn r1-btn-outlined r1-btn-icon-sm r1-mono" aria-label="增大音量" title="音量 +5%">+</button>
                </div>
              </div>

              <!-- 磁贴 2：蓝牙音频 -->
              <div class="r1-tile">
                <div class="r1-tile-head">
                  <span class="r1-tile-title">蓝牙音频</span>
                  <span class="r1-row" style="gap:6px;">
                    <span class="r1-dot" id="btDot"></span>
                    <span class="r1-subtle" style="font-size:12px;" id="btStateLabel">待命</span>
                  </span>
                </div>
                <div class="r1-muted r1-truncate" style="font-size:13px;" id="btDevName">未连接</div>
                <div class="r1-row" style="gap:8px;">
                  <button type="button" onclick="R1.toggleBluetooth()" id="btToggleBtn" class="r1-btn r1-btn-tonal r1-btn-sm r1-flex-1">切换开关</button>
                  <button type="button" onclick="R1.pairBluetooth()" id="btPairBtn" class="r1-btn r1-btn-outlined r1-btn-sm">配对</button>
                </div>
              </div>

              <!-- 磁贴 3：睡眠定时 -->
              <div class="r1-tile">
                <div class="r1-tile-head">
                  <span class="r1-tile-title">睡眠定时</span>
                  <span class="r1-badge r1-badge-primary r1-mono" id="sleepVal">关闭</span>
                </div>
                <div class="r1-subtle" style="font-size:12px;" id="sleepDesc">到点自动停止播放</div>
                <button type="button" onclick="R1.cycleSleepTimer()" class="r1-btn r1-btn-tonal r1-btn-sm" style="width:100%;">循环切换 15 / 30 / 60 分</button>
              </div>

              <!-- 磁贴 4：投屏接收 (DLNA / AirPlay) -->
              <div class="r1-tile">
                <div class="r1-tile-head">
                  <span class="r1-tile-title">无线投屏</span>
                  <span class="r1-subtle" style="font-size:12px;">音频接收</span>
                </div>
                <div class="r1-row-wrap" id="castRow">
                  <button type="button" id="castDlnaBadge" onclick="R1.toggleCast('dlna')" class="r1-chip" aria-pressed="false">DLNA</button>
                  <button type="button" id="castAirplayBadge" onclick="R1.toggleCast('airplay')" class="r1-chip" aria-pressed="false">AirPlay</button>
                </div>
                <div class="r1-subtle r1-truncate" style="font-size:12px;min-height:18px;" id="castHint">点击按钮开关接收服务</div>
              </div>
            </div>

            <!-- 快捷场景与灯环 -->
            <section class="r1-card r1-card-compact r1-flex-1" style="justify-content:space-between;">
              <div class="r1-col" style="gap:8px;">
                <span class="r1-subtle" style="font-size:12px;font-weight:600;">快捷指令</span>
                <div class="r1-row-wrap">
                  <button type="button" onclick="R1.sendCommand('系统体检', '快捷指令')" class="r1-chip">系统体检</button>
                  <button type="button" onclick="R1.sendCommand('现在几点了', '快捷指令')" class="r1-chip">播报时间</button>
                  <button type="button" onclick="R1.sendCommand('30分钟后停止播放', '快捷指令')" class="r1-chip">30 分钟睡眠</button>
                  <button type="button" onclick="R1.sendCommand('今天天气怎么样', '快捷指令')" class="r1-chip">播报天气</button>
                </div>
              </div>

              <div class="r1-col" style="gap:8px;padding-top:10px;border-top:1px solid var(--r1-border-subtle);">
                <span class="r1-subtle" style="font-size:12px;font-weight:600;">音箱灯环</span>
                <div class="r1-row-wrap">
                  <button type="button" onclick="R1.setLight('wakeup')" class="r1-chip"><span class="material-symbols-outlined" aria-hidden="true">&#xE0F0;</span>唤醒色</button>
                  <button type="button" onclick="R1.setLight('loading')" class="r1-chip">呼吸灯</button>
                  <button type="button" onclick="R1.setLight('dormant')" class="r1-chip">休眠微光</button>
                  <button type="button" onclick="R1.setLight('scan')" class="r1-chip"><span class="material-symbols-outlined" aria-hidden="true">&#xE3F0;</span>流光</button>
                  <button type="button" onclick="R1.setLight('off')" class="r1-chip"><span class="material-symbols-outlined" aria-hidden="true">&#xE0F1;</span>关闭</button>
                </div>
              </div>
            </section>
          </div>
        </div>

        <!-- 3. 底部单行紧凑遥测状态带 -->
        <footer class="r1-telemetry-strip" aria-label="设备运行摘要">
          <div class="r1-telemetry-group">
            <span class="r1-telemetry-item">
              <span class="r1-dot" id="footOnlineDot"></span>
              <span id="footDevice" class="r1-mono">设备未连接</span>
            </span>
            <span class="r1-telemetry-item">
              <span class="r1-subtle">运行</span>
              <span id="footUptime" class="r1-mono">00:00:00</span>
            </span>
            <span class="r1-telemetry-item">
              <span class="r1-subtle">内存</span>
              <span id="footRam" class="r1-mono r1-success-text">--</span>
              <span id="footRamTotal" class="r1-mono r1-subtle">/ --</span>
              <span class="r1-mem-meter" aria-hidden="true"><span id="footRamBar" style="width:0%;"></span></span>
            </span>
            <span class="r1-telemetry-item">
              <span class="r1-subtle">温度</span>
              <span id="footTemp" class="r1-mono">-- °C</span>
            </span>
          </div>
          <span class="r1-telemetry-item">
            <span class="material-symbols-outlined r1-primary-text" style="font-size:15px;" aria-hidden="true">&#xE425;</span>
            <span class="r1-subtle">北京时间</span>
            <span id="footCnTime" class="r1-mono r1-primary-text">--:--:--</span>
          </span>
        </footer>`;

  const script = `
  <script>
    (function() {
      R1.refreshCast();
      var cmdInput = document.getElementById('cmdInput');
      var sendBtn = document.getElementById('sendBtn');
      var btnClear = document.getElementById('btnClearCmd');
      var seekRange = document.getElementById('homeSeekRange');
      var volRange = document.getElementById('volRange');
      var seeking = false;
      var draggingVol = false;

      function submitCmd() {
        var v = cmdInput.value.trim();
        if (!v) return;
        cmdInput.value = '';
        R1.sendCommand(v, 'Web 控制台');
      }
      sendBtn.addEventListener('click', submitCmd);
      btnClear.addEventListener('click', function() { cmdInput.value = ''; cmdInput.focus(); });
      cmdInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') submitCmd();
        else if (e.key === 'Escape') { cmdInput.value = ''; }
      });

      // 可拖拽进度条
      seekRange.addEventListener('pointerdown', function() { seeking = true; });
      seekRange.addEventListener('input', function() {
        var pct = (Number(this.value) / 10).toFixed(1) + '%';
        this.style.setProperty('--val', pct);
      });
      seekRange.addEventListener('change', function() {
        seeking = false;
        R1.seekByRatio(Number(this.value) / 1000);
      });

      // 可拖拽主音量条
      volRange.addEventListener('pointerdown', function() { draggingVol = true; });
      volRange.addEventListener('input', function() {
        this.style.setProperty('--val', this.value + '%');
        document.getElementById('volVal').textContent = this.value + '%';
      });
      volRange.addEventListener('change', function() {
        draggingVol = false;
        R1.setVolume(Number(this.value));
      });

      document.getElementById('btnReplayHome').addEventListener('click', function() {
        var st = R1.getState();
        var lastAi = st.chatHistory.slice().reverse().find(function(x) { return x.role === 'assistant'; });
        R1.speak(lastAi ? lastAi.text : '在。');
      });

      R1.onStateChange(function(st) {
        var tr = R1.getCurrentTrack();
        var lastUser = st.chatHistory.slice().reverse().find(function(x) { return x.role === 'user'; });
        var lastAi = st.chatHistory.slice().reverse().find(function(x) { return x.role === 'assistant'; });

        if (lastUser) {
          document.getElementById('homeLastUserSrc').textContent = lastUser.source || '控制台指令';
          document.getElementById('homeLastUserTime').textContent = lastUser.time || '';
          document.getElementById('homeLastUserText').textContent = '“' + lastUser.text + '”';
          document.getElementById('homeLastUserText').className = '';
        } else {
          document.getElementById('homeLastUserSrc').textContent = '你的指令';
          document.getElementById('homeLastUserTime').textContent = '待命';
          document.getElementById('homeLastUserText').textContent = '还没有对话记录';
          document.getElementById('homeLastUserText').className = 'r1-muted';
        }

        if (lastAi) {
          document.getElementById('homeLastAiMeta').textContent = lastAi.model || '小讯';
          var aiStatus = document.getElementById('homeLastAiStatus');
          aiStatus.textContent = lastAi.status || '已回复';
          aiStatus.className = lastAi.status === '失败' ? 'r1-badge r1-badge-danger' : 'r1-badge r1-badge-success';
          document.getElementById('homeLastAiText').textContent = lastAi.text;
        } else {
          document.getElementById('homeLastAiMeta').textContent = '小讯';
          var aiIdle = document.getElementById('homeLastAiStatus');
          aiIdle.textContent = st.online ? '在线' : '离线';
          aiIdle.className = st.online ? 'r1-badge r1-badge-success' : 'r1-badge';
          document.getElementById('homeLastAiText').textContent = '在。';
        }

        document.getElementById('homeTrackTitle').textContent = tr.title;
        document.getElementById('homeDiscCenterLabel').textContent = tr.id === 'idle' ? 'R1 HI-FI' : tr.title;
        document.getElementById('homeTrackFormat').textContent = tr.format;
        document.getElementById('homeTrackAlbum').textContent = tr.artist + (tr.album ? (' · ' + tr.album) : '');
        document.getElementById('homeCurTime').textContent = R1.formatTime(st.currentTime);
        document.getElementById('homeTotTime').textContent = R1.formatTime(tr.duration);
        document.getElementById('homeCenterSpec').textContent = tr.spec;

        if (!seeking) {
          var ratio = tr.duration > 0 ? Math.min(1, st.currentTime / tr.duration) : 0;
          seekRange.value = String(Math.round(ratio * 1000));
          seekRange.style.setProperty('--val', (ratio * 100).toFixed(1) + '%');
        }

        document.getElementById('playIcon').innerHTML = R1.playPauseGlyph(st.isPlaying, 22);
        document.getElementById('homeVinylDisc').classList.toggle('vinyl-paused', !st.isPlaying);
        document.getElementById('loopModeText').textContent = R1.loopName(st.loopMode);

        var dspTag = document.getElementById('homeDspActiveTag');
        if (!st.online) { dspTag.textContent = '离线'; dspTag.className = 'r1-badge'; }
        else if (st.isPlaying) { dspTag.textContent = '播放中'; dspTag.className = 'r1-badge r1-badge-success'; }
        else if (st.player.hasTrack) { dspTag.textContent = '已暂停'; dspTag.className = 'r1-badge r1-badge-warn'; }
        else { dspTag.textContent = '空闲'; dspTag.className = 'r1-badge'; }

        var curLyric = '暂无歌词';
        if (tr.lyrics && tr.lyrics.length) {
          curLyric = tr.lyrics[0].txt;
          for (var i = 0; i < tr.lyrics.length; i++) {
            if (st.currentTime >= tr.lyrics[i].t) curLyric = tr.lyrics[i].txt;
          }
        }
        document.getElementById('homeLyricLine').textContent = curLyric;

        // 音量
        if (!draggingVol) {
          var v = st.muted ? 0 : st.volume;
          document.getElementById('volVal').textContent = st.muted ? '静音' : (st.volume + '%');
          volRange.value = String(v);
          volRange.style.setProperty('--val', v + '%');
        }

        // 蓝牙
        var btName = !st.online ? '音箱未连接' : (!st.btSupported ? '硬件不支持' : (st.btEnabled ? (st.btName || '蓝牙已开启') : '蓝牙已关闭'));
        document.getElementById('btDevName').textContent = btName;
        document.getElementById('btStateLabel').textContent = !st.btEnabled ? '已关闭' : (st.btDiscoverable ? '可被发现' : '已开启');
        document.getElementById('btDot').className = 'r1-dot' + (st.btEnabled ? ' is-online' : '');
        document.getElementById('btToggleBtn').textContent = st.btEnabled ? '关闭蓝牙' : '开启蓝牙';

        // 睡眠定时
        document.getElementById('sleepVal').textContent = st.sleepRemainSec > 0 ? R1.formatTime(st.sleepRemainSec) : '关闭';

        // 底部遥测
        document.getElementById('footOnlineDot').className = 'r1-dot' + (st.online ? ' is-online' : '');
        document.getElementById('footDevice').textContent = st.online ? (st.hardware + ' · Android ' + st.androidRelease) : '音箱未连接';
        document.getElementById('footUptime').textContent = R1.formatUptime(st.uptimeSec);
        document.getElementById('footRam').textContent = st.memUsedMb > 0 ? (st.memUsedMb + 'M') : '--';
        document.getElementById('footRamTotal').textContent = '/ ' + (st.memTotalMb || '--') + 'M';
        var memPct = (st.memTotalMb > 0 && st.memUsedMb > 0) ? Math.min(100, Math.round((st.memUsedMb / st.memTotalMb) * 100)) : 0;
        document.getElementById('footRamBar').style.width = memPct + '%';
        document.getElementById('footTemp').textContent = st.socTemp !== '--' ? (st.socTemp + ' °C') : '-- °C';
        document.getElementById('footCnTime').textContent = R1.nowTimeStr();
      });

      setInterval(function() {
        var st = R1.getState();
        var bars = document.querySelectorAll('#homeSpectrumBox .spec-bar');
        bars.forEach(function(b, i) {
          if (st.isPlaying) {
            b.style.height = (Math.floor(Math.random() * 18) + 6) + 'px';
          } else {
            b.style.height = [5, 8, 6, 10, 8, 6, 8, 5][i] + 'px';
          }
        });
      }, 180);
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 页面 02: 全视口自适应智能对话终端 (desktop/chat.html)
// =========================================================
function getDesktopChat() {
  const html = `
      <div class="r1-chat-shell">
        <!-- 顶部工具条 + 快捷指令栏 -->
        <section class="r1-card r1-card-compact" style="flex-shrink:0;">
          <div class="r1-row-between" style="flex-wrap:wrap;">
            <div class="r1-row" style="gap:10px;">
              <span class="r1-badge r1-badge-primary r1-mono" id="chatModelBadge">小讯</span>
              <span class="r1-subtle" style="font-size:12px;">发送指令后由音箱自动执行与播报</span>
            </div>
            <div class="r1-row" style="gap:12px;">
              <label class="r1-row" style="gap:8px;cursor:pointer;font-size:13px;" for="chatAutoSpeakSwitch">
                <span class="r1-muted">自动播报</span>
                <input id="chatAutoSpeakSwitch" type="checkbox" role="switch" class="r1-switch" checked>
              </label>
              <button type="button" id="btnClearChat" class="r1-btn r1-btn-danger r1-btn-sm">清空对话</button>
            </div>
          </div>

          <div class="r1-row-wrap" style="padding-top:10px;border-top:1px solid var(--r1-border-subtle);">
            <span class="r1-subtle" style="font-size:12px;">快捷：</span>
            <button type="button" onclick="R1.sendCommand('现在几点了')" class="r1-chip">现在几点了</button>
            <button type="button" onclick="R1.sendCommand('今天天气怎么样')" class="r1-chip">今天天气</button>
            <button type="button" onclick="R1.sendCommand('把主音量调到 20')" class="r1-chip">音量 20%</button>
            <button type="button" onclick="R1.sendCommand('调高音量')" class="r1-chip">调高音量</button>
            <button type="button" onclick="R1.sendCommand('调低音量')" class="r1-chip">调低音量</button>
            <button type="button" onclick="R1.sendCommand('暂停播放')" class="r1-chip">暂停播放</button>
            <button type="button" onclick="R1.sendCommand('下一首')" class="r1-chip">下一首</button>
            <button type="button" onclick="R1.sendCommand('30分钟后定时停止播放')" class="r1-chip">30 分钟停止</button>
            <button type="button" onclick="R1.sendCommand('系统体检')" class="r1-chip">系统体检</button>
          </div>
        </section>

        <!-- 自适应全高消息流 -->
        <section id="chatStreamBox" class="r1-chat-stream" aria-live="polite" aria-label="对话记录"></section>

        <!-- 底部常驻纯文本输入坞 -->
        <section class="r1-card r1-card-compact" style="flex-shrink:0;">
          <div class="r1-search-bar">
            <span class="material-symbols-outlined r1-primary-text" style="font-size:20px;" aria-hidden="true">&#xE312;</span>
            <input id="chatPageInput" type="text" maxlength="500" placeholder="给小讯发送指令或提问（Enter 发送，Esc 清空）" aria-label="对话输入框">
            <button type="button" id="chatClearInputBtn" class="r1-btn r1-btn-ghost r1-btn-sm">清空</button>
            <button type="button" id="chatPageSendBtn" class="r1-btn r1-btn-primary">
              <span>发送</span>
              <span class="material-symbols-outlined" aria-hidden="true">&#xE5D8;</span>
            </button>
          </div>
        </section>
      </div>`;

  const script = `
  <script>
    (function() {
      var streamBox = document.getElementById('chatStreamBox');
      var input = document.getElementById('chatPageInput');
      var sendBtn = document.getElementById('chatPageSendBtn');
      var clearInputBtn = document.getElementById('chatClearInputBtn');
      var autoSpeakSwitch = document.getElementById('chatAutoSpeakSwitch');
      var btnClearChat = document.getElementById('btnClearChat');

      function send() {
        var v = input.value.trim();
        if (!v) return;
        input.value = '';
        stickBottom = true;
        R1.sendCommand(v, 'Web 对话终端');
      }
      sendBtn.addEventListener('click', send);
      clearInputBtn.addEventListener('click', function() { input.value = ''; input.focus(); });
      input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') send();
        else if (e.key === 'Escape') { input.value = ''; }
      });

      autoSpeakSwitch.addEventListener('change', function() {
        R1.updateConfig({ autoSpeak: !!this.checked });
      });

      btnClearChat.addEventListener('click', function() {
        R1.confirmModal({
          title: '清空对话历史',
          desc: '将同时清空控制台与音箱端的近期对话记录，此操作不可恢复。',
          confirmText: '确认清空',
          danger: true
        }).then(function(ok) {
          if (ok) R1.clearChatHistory();
        });
      });

      function copyText(txt) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(txt).catch(function() { R1.toast('复制失败，请手动选择文本', 'err'); });
        } else {
          var ta = document.createElement('textarea');
          ta.value = txt;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.select();
          try { document.execCommand('copy'); } catch (e) {}
          document.body.removeChild(ta);
        }
        R1.toast('已复制到剪贴板', 'ok');
      }

      var lastChatSig = '__init__';
      var stickBottom = true;
      streamBox.addEventListener('scroll', function() {
        stickBottom = (streamBox.scrollHeight - streamBox.scrollTop - streamBox.clientHeight) < 60;
      });
      streamBox.addEventListener('click', function(e) {
        var btn = e.target.closest ? e.target.closest('[data-chat-action]') : null;
        if (!btn) return;
        var idx = parseInt(btn.getAttribute('data-idx'), 10);
        var item = R1.getState().chatHistory[idx];
        if (!item || typeof item.text !== 'string' || !item.text) return;
        if (btn.getAttribute('data-chat-action') === 'copy') copyText(item.text);
        else R1.speak(item.text);
      });

      R1.onStateChange(function(st) {
        var modeBadge = st.config.hasKey
          ? (st.config.llmModel || 'GLM-4.5-Flash')
          : ((st.config.llmModel || 'GLM-4.5-Flash') + ' · 待配密钥');
        document.getElementById('chatModelBadge').textContent = modeBadge;
        autoSpeakSwitch.checked = st.config.autoSpeak !== false;

        var sig = (st.chatHistory || []).map(function(item) {
          return item.id + '|' + (item.status || '') + '|' + (item.text || '').length;
        }).join(',');
        if (sig === lastChatSig) return;
        lastChatSig = sig;

        if (!st.chatHistory || st.chatHistory.length === 0) {
          streamBox.innerHTML = ''
            + '<div class="r1-empty" style="margin:auto;width:100%;max-width:460px;">'
            + '  <span class="material-symbols-outlined r1-primary-text" style="font-size:32px;" aria-hidden="true">&#xE0CA;</span>'
            + '  <span style="font-size:15px;font-weight:600;color:var(--r1-fg);">还没有对话记录</span>'
            + '  <span class="r1-subtle" style="font-size:13px;">点击上方快捷指令，或在下方输入内容直接与小讯对话</span>'
            + '</div>';
          return;
        }

        streamBox.innerHTML = st.chatHistory.map(function(item, idx) {
          if (item.role === 'user') {
            return ''
              + '<div class="r1-bubble-user">'
              + '  <div class="r1-bubble-meta">'
              + '    <span class="r1-primary-text" style="font-weight:600;">' + R1.escapeHtml(item.source || '用户指令') + '</span>'
              + '    <span class="r1-mono">' + R1.escapeHtml(item.time) + '</span>'
              + '  </div>'
              + '  <p style="margin:0;font-size:14px;line-height:1.6;">' + R1.escapeHtml(item.text) + '</p>'
              + '</div>';
          }
          var statusCls = item.status === '失败' ? 'r1-danger-text' : 'r1-success-text';
          return ''
            + '<div class="r1-bubble-ai">'
            + '  <div class="r1-bubble-meta">'
            + '    <span class="r1-row" style="gap:8px;"><span class="r1-dot is-active"></span><span class="r1-primary-text" style="font-weight:600;">' + R1.escapeHtml(item.model || '小讯') + '</span></span>'
            + '    <span class="r1-mono ' + statusCls + '">' + R1.escapeHtml(item.status || '已执行') + ' · ' + R1.escapeHtml(item.time) + '</span>'
            + '  </div>'
            + '  <p style="margin:0;font-size:14px;line-height:1.65;">' + R1.escapeHtml(item.text) + '</p>'
            + '  <div class="r1-bubble-foot">'
            + '    <button type="button" data-chat-action="copy" data-idx="' + idx + '" class="r1-btn r1-btn-outlined r1-btn-sm">复制文本</button>'
            + '    <button type="button" data-chat-action="speak" data-idx="' + idx + '" class="r1-btn r1-btn-tonal r1-btn-sm">'
            + '      <span class="material-symbols-outlined" aria-hidden="true">&#xE042;</span><span>重播</span>'
            + '    </button>'
            + '  </div>'
            + '</div>';
        }).join('');
        if (stickBottom) streamBox.scrollTop = streamBox.scrollHeight;
      });
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 页面 03: 音乐播放与媒体库 (desktop/music.html)
// =========================================================
function getDesktopMusic() {
  const html = `
        <div class="r1-music-grid">
          <!-- 左 5 列：黑胶主控卡 + 同步歌词 -->
          <div class="r1-col" style="gap:20px;">
            <section class="r1-card">
              <div class="r1-row" style="gap:20px;flex-wrap:wrap;">
                <button type="button" onclick="R1.togglePlay()" class="r1-vinyl-btn" title="点击播放或暂停" aria-label="播放或暂停">
                  <div id="musicVinylDisc" class="r1-vinyl-disc vinyl-spinning vinyl-paused">
                    <div class="r1-vinyl-center">
                      <span class="r1-vinyl-label-txt" id="musicDiscLabel">R1 HI-FI</span>
                      <span class="r1-vinyl-hole"></span>
                    </div>
                  </div>
                </button>

                <div class="r1-col r1-flex-1" style="gap:8px;">
                  <div class="r1-row-wrap">
                    <span class="r1-badge r1-badge-primary r1-mono" id="musicFormat">STANDBY</span>
                    <span class="r1-mono r1-subtle" style="font-size:12px;" id="musicSpecText">--</span>
                  </div>
                  <h2 id="musicTitle" class="r1-truncate" style="margin:0;font-size:20px;font-weight:700;">未在播放</h2>
                  <p id="musicAlbum" class="r1-muted r1-truncate" style="margin:0;font-size:13px;">等待选择曲目</p>
                </div>
              </div>

              <!-- 播放进度滑块 -->
              <div class="r1-col" style="gap:4px;">
                <input id="musicSeekRange" type="range" min="0" max="1000" value="0" class="r1-slider" aria-label="歌曲播放进度" style="--val:0%;">
                <div class="r1-row-between r1-mono r1-subtle" style="font-size:12px;">
                  <span class="r1-primary-text" id="musicCurTime">00:00</span>
                  <span id="musicTotTime">00:00</span>
                </div>
              </div>

              <!-- 播控操作区 -->
              <div class="r1-row-between" style="flex-wrap:wrap;padding-top:10px;border-top:1px solid var(--r1-border-subtle);">
                <div class="r1-row" style="gap:10px;">
                  <button type="button" onclick="R1.prevTrack()" class="r1-btn r1-btn-outlined r1-btn-icon" aria-label="上一曲" title="上一曲">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE045;</span>
                  </button>
                  <button type="button" onclick="R1.togglePlay()" class="r1-btn r1-btn-primary r1-btn-icon-lg" aria-label="播放或暂停" title="播放 / 暂停">
                    <span class="material-symbols-outlined" style="font-size:26px;" id="musicPlayIcon" aria-hidden="true">&#xE037;</span>
                  </button>
                  <button type="button" onclick="R1.nextTrack()" class="r1-btn r1-btn-outlined r1-btn-icon" aria-label="下一曲" title="下一曲">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE044;</span>
                  </button>
                </div>

                <button type="button" onclick="R1.toggleLoopMode()" class="r1-btn r1-btn-outlined r1-btn-sm" title="切换循环模式">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE040;</span>
                  <span id="musicLoopLabel">列表循环</span>
                </button>
              </div>

              <!-- 音量与睡眠定时 -->
              <div class="r1-col" style="gap:12px;padding-top:12px;border-top:1px solid var(--r1-border-subtle);">
                <div class="r1-row" style="gap:8px;">
                  <span class="material-symbols-outlined r1-primary-text" style="font-size:18px;" aria-hidden="true">&#xE049;</span>
                  <button type="button" onclick="R1.stepVolume(-5)" class="r1-btn r1-btn-outlined r1-btn-icon-sm r1-mono" aria-label="降低音量">-</button>
                  <input id="musicVolRange" type="range" min="0" max="100" value="40" class="r1-slider r1-flex-1" aria-label="播放音量" style="--val:40%;">
                  <button type="button" onclick="R1.stepVolume(5)" class="r1-btn r1-btn-outlined r1-btn-icon-sm r1-mono" aria-label="提高音量">+</button>
                  <span class="r1-mono r1-primary-text" style="min-width:42px;text-align:right;font-size:12px;" id="musicVolLabel">40%</span>
                </div>

                <div class="r1-row-between" style="flex-wrap:wrap;gap:8px;">
                  <span class="r1-subtle" style="font-size:12px;">睡眠定时：<strong class="r1-primary-text r1-mono" id="musicSleepText">未开启</strong></span>
                  <div class="r1-row-wrap">
                    <button type="button" onclick="R1.setSleepTimer(15)" class="r1-chip">15 分</button>
                    <button type="button" onclick="R1.setSleepTimer(30)" class="r1-chip">30 分</button>
                    <button type="button" onclick="R1.setSleepTimer(60)" class="r1-chip">60 分</button>
                    <button type="button" onclick="R1.setSleepTimer(0)" class="r1-chip">关闭</button>
                  </div>
                </div>
              </div>
            </section>

            <!-- 同步 LRC 歌词视窗 -->
            <section class="r1-card" style="min-height:280px;">
              <div class="r1-card-head">
                <h2 class="r1-card-title">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE405;</span>
                  <span>同步歌词</span>
                </h2>
                <span class="r1-subtle" style="font-size:12px;">点击歌词跳转进度</span>
              </div>
              <div id="musicLyricsBox" class="r1-lyrics-viewport"></div>
            </section>
          </div>

          <!-- 右 7 列：统一媒体库工作区（在线搜歌 / 已存曲库） -->
          <section class="r1-card" style="min-height:620px;">
            <div class="r1-card-head">
              <div class="r1-seg" role="tablist" aria-label="音乐库视图切换">
                <button type="button" id="musicViewCloudBtn" role="tab" aria-selected="true" class="r1-seg-btn is-active">在线搜歌</button>
                <button type="button" id="musicViewLocalBtn" role="tab" aria-selected="false" class="r1-seg-btn">曲库与音频流</button>
              </div>
              <span class="r1-badge r1-mono" id="playlistCountText">共 0 首</span>
            </div>

            <!-- 视图 A：在线云音乐搜索 -->
            <div id="musicViewCloud" class="r1-col" style="gap:14px;">
              <div class="r1-row-between" style="flex-wrap:wrap;">
                <div class="r1-seg" aria-label="搜索音乐源">
                  <button type="button" id="cloudSrcNetease" onclick="R1.setCloudSource('netease')" class="r1-seg-btn is-active" aria-pressed="true">网易云</button>
                  <button type="button" id="cloudSrcQQ" onclick="R1.setCloudSource('qq')" class="r1-seg-btn" aria-pressed="false">QQ 音乐</button>
                  <button type="button" id="cloudSrcSongloft" onclick="R1.setCloudSource('songloft')" class="r1-seg-btn" aria-pressed="false">本地库</button>
                </div>
                <div class="r1-row" style="gap:8px;">
                  <span id="cloudLoginState" class="r1-badge">未登录</span>
                  <button type="button" onclick="R1.cloudSetCookie()" class="r1-btn r1-btn-tonal r1-btn-sm">登录 / 配置</button>
                </div>
              </div>

              <div class="r1-search-bar">
                <span class="material-symbols-outlined r1-primary-text" style="font-size:18px;" aria-hidden="true">&#xE8B6;</span>
                <input id="cloudSearchInput" type="text" placeholder="搜索歌名或歌手，按 Enter 立即搜索" aria-label="云音乐搜索框">
                <button type="button" onclick="R1.cloudSearch()" class="r1-btn r1-btn-primary r1-btn-sm">搜索</button>
              </div>

              <div id="cloudResults" class="r1-col" style="gap:8px;max-height:420px;overflow-y:auto;">
                <div class="r1-empty"><span style="font-size:13px;">输入歌名或歌手后点击搜索</span></div>
              </div>
            </div>

            <!-- 视图 B：已存曲库（本机 + 网络流） -->
            <div id="musicViewLocal" class="r1-col r1-hidden" style="gap:14px;">
              <div class="r1-row-between" style="flex-wrap:wrap;">
                <div class="r1-seg" id="musicSourceTabs" aria-label="曲库筛选">
                  <button type="button" data-src="all" onclick="R1.setMusicSource('all')" class="src-tab r1-seg-btn is-active">全部</button>
                  <button type="button" data-src="local" onclick="R1.setMusicSource('local')" class="src-tab r1-seg-btn">本机文件</button>
                  <button type="button" data-src="stream" onclick="R1.setMusicSource('stream')" class="src-tab r1-seg-btn">网络音频流</button>
                </div>
                <button type="button" id="btnAddStreamUrl" class="r1-btn r1-btn-tonal r1-btn-sm">添加音频流</button>
              </div>

              <div class="r1-search-bar">
                <span class="material-symbols-outlined r1-primary-text" style="font-size:18px;" aria-hidden="true">&#xE03D;</span>
                <input id="musicSearchInput" type="text" placeholder="按歌名或歌手过滤当前曲库..." aria-label="过滤本地曲库">
              </div>

              <div id="musicPlaylistBox" class="r1-col" style="gap:8px;max-height:380px;overflow-y:auto;"></div>
            </div>

            <!-- 底部统一：语音点歌音源优先级开关 -->
            <div class="r1-subcard r1-row-between" style="margin-top:auto;flex-wrap:wrap;gap:10px;">
              <span class="r1-subtle" style="font-size:12px;font-weight:600;">语音点歌参与音源</span>
              <div class="r1-row-wrap" style="gap:14px;">
                <label class="r1-row" style="gap:6px;cursor:pointer;font-size:13px;" for="mpSongloft">
                  <input id="mpSongloft" type="checkbox" onchange="R1.toggleMusicProvider('songloft')" style="accent-color:var(--r1-primary);width:15px;height:15px;">
                  <span>本地曲库</span>
                </label>
                <label class="r1-row" style="gap:6px;cursor:pointer;font-size:13px;" for="mpNetease">
                  <input id="mpNetease" type="checkbox" onchange="R1.toggleMusicProvider('netease')" style="accent-color:var(--r1-primary);width:15px;height:15px;">
                  <span>网易云</span>
                </label>
                <label class="r1-row" style="gap:6px;cursor:pointer;font-size:13px;" for="mpQq">
                  <input id="mpQq" type="checkbox" onchange="R1.toggleMusicProvider('qq')" style="accent-color:var(--r1-primary);width:15px;height:15px;">
                  <span>QQ 音乐</span>
                </label>
              </div>
            </div>
          </section>
        </div>`;

  const script = `
  <script>
    (function() {
      R1.cloudStatus();
      R1.loadMusicProviders();

      var viewCloudBtn = document.getElementById('musicViewCloudBtn');
      var viewLocalBtn = document.getElementById('musicViewLocalBtn');
      var viewCloud = document.getElementById('musicViewCloud');
      var viewLocal = document.getElementById('musicViewLocal');
      function switchMusicView(mode) {
        var isCloud = mode === 'cloud';
        viewCloud.classList.toggle('r1-hidden', !isCloud);
        viewLocal.classList.toggle('r1-hidden', isCloud);
        viewCloudBtn.classList.toggle('is-active', isCloud);
        viewLocalBtn.classList.toggle('is-active', !isCloud);
        viewCloudBtn.setAttribute('aria-selected', isCloud ? 'true' : 'false');
        viewLocalBtn.setAttribute('aria-selected', isCloud ? 'false' : 'true');
      }
      viewCloudBtn.addEventListener('click', function() { switchMusicView('cloud'); });
      viewLocalBtn.addEventListener('click', function() { switchMusicView('local'); });

      var cloudInput = document.getElementById('cloudSearchInput');
      cloudInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') R1.cloudSearch();
        else if (e.key === 'Escape') { cloudInput.value = ''; }
      });

      var searchInput = document.getElementById('musicSearchInput');
      var searchKw = '';
      var lastPlaylistSig = '';
      searchInput.addEventListener('input', function() {
        searchKw = this.value.trim().toLowerCase();
        renderPlaylist(R1.getState());
      });

      var seekRange = document.getElementById('musicSeekRange');
      var volRange = document.getElementById('musicVolRange');
      var seeking = false;
      var draggingVol = false;

      seekRange.addEventListener('pointerdown', function() { seeking = true; });
      seekRange.addEventListener('input', function() {
        this.style.setProperty('--val', (Number(this.value) / 10).toFixed(1) + '%');
      });
      seekRange.addEventListener('change', function() {
        seeking = false;
        R1.seekByRatio(Number(this.value) / 1000);
      });

      volRange.addEventListener('pointerdown', function() { draggingVol = true; });
      volRange.addEventListener('input', function() {
        this.style.setProperty('--val', this.value + '%');
        document.getElementById('musicVolLabel').textContent = this.value + '%';
      });
      volRange.addEventListener('change', function() {
        draggingVol = false;
        R1.setVolume(Number(this.value));
      });

      document.getElementById('musicPlaylistBox').addEventListener('click', function(e) {
        var rmBtn = e.target.closest ? e.target.closest('[data-remove-stream]') : null;
        if (rmBtn) {
          e.stopPropagation();
          var url = rmBtn.getAttribute('data-remove-stream');
          if (!url) return;
          R1.confirmModal({
            title: '移除网络音频流',
            desc: '确认从列表中移除该音频流链接？',
            confirmText: '确认移除',
            danger: true
          }).then(function(ok) {
            if (ok) R1.removeStream(url);
          });
          return;
        }
        var row = e.target.closest ? e.target.closest('[data-track-idx]') : null;
        if (row) {
          var idx = parseInt(row.getAttribute('data-track-idx'), 10);
          if (!isNaN(idx)) R1.selectTrack(idx);
        }
      });
      document.getElementById('musicPlaylistBox').addEventListener('keydown', function(e) {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        var row = e.target.closest ? e.target.closest('[data-track-idx]') : null;
        if (!row) return;
        e.preventDefault();
        var idx = parseInt(row.getAttribute('data-track-idx'), 10);
        if (!isNaN(idx)) R1.selectTrack(idx);
      });

      document.getElementById('btnAddStreamUrl').addEventListener('click', function() {
        R1.openModal({
          title: '添加网络音频流',
          desc: '输入音箱可直接拉取的 HTTP/HTTPS 音频流或电台链接。',
          confirmText: '添加并播放',
          fields: [
            { name: 'url', label: '音频流链接', type: 'url', mono: true, placeholder: 'http://example.com/stream.mp3' },
            { name: 'title', label: '曲目 / 电台名称', type: 'text', placeholder: '网络音频流', value: '网络音频流' }
          ]
        }).then(function(res) {
          if (!res || !(res.url || '').trim()) return;
          R1.addStream(res.url.trim(), (res.title || '网络音频流').trim());
        });
      });

      function renderPlaylist(st) {
        var box = document.getElementById('musicPlaylistBox');
        var list = R1.TRACKS.map(function(t, idx) { return { t: t, idx: idx }; }).filter(function(item) {
          var matchSrc = st.musicSource === 'all' || item.t.source === st.musicSource;
          var matchKw = !searchKw || item.t.title.toLowerCase().indexOf(searchKw) >= 0 || item.t.artist.toLowerCase().indexOf(searchKw) >= 0;
          return matchSrc && matchKw;
        });
        var sig = st.musicSource + '|' + st.trackIdx + '|' + searchKw + '|' +
          R1.TRACKS.map(function(t) { return t.path || t.url || ''; }).join('|');
        if (sig === lastPlaylistSig) return;
        lastPlaylistSig = sig;
        var keepScroll = box.scrollTop;
        document.getElementById('playlistCountText').textContent = '共 ' + list.length + ' 首';

        if (list.length === 0) {
          box.innerHTML = ''
            + '<div class="r1-empty">'
            + '  <span class="material-symbols-outlined r1-primary-text" style="font-size:28px;" aria-hidden="true">&#xE019;</span>'
            + '  <span style="font-size:14px;font-weight:600;color:var(--r1-fg);">当前曲库为空</span>'
            + '  <span class="r1-subtle" style="font-size:12px;">可将音频放入音箱 /sdcard/Music 或点击右上角添加网络音频流</span>'
            + '</div>';
          return;
        }

        box.innerHTML = list.map(function(item) {
          var t = item.t, idx = item.idx;
          var active = idx === st.trackIdx;
          var rm = t.source === 'stream'
            ? '<button type="button" data-remove-stream="' + R1.escapeHtml(t.url) + '" class="r1-btn r1-btn-danger r1-btn-icon-sm" title="移除该音频流" aria-label="移除该音频流">×</button>'
            : '';
          return ''
            + '<div role="button" tabindex="0" data-track-idx="' + idx + '" class="r1-track-row' + (active ? ' is-active' : '') + '">'
            + '  <div class="r1-row r1-flex-1" style="min-width:0;">'
            + '    <span class="r1-mono ' + (active ? 'r1-primary-text' : 'r1-subtle') + '" style="width:24px;font-size:12px;font-weight:600;flex-shrink:0;">' + String(idx + 1).padStart(2, '0') + '</span>'
            + '    <div class="r1-col" style="gap:2px;min-width:0;">'
            + '      <span class="r1-truncate ' + (active ? 'r1-primary-text' : '') + '" style="font-size:14px;font-weight:' + (active ? '600' : '500') + ';">' + R1.escapeHtml(t.subTitle) + '</span>'
            + '      <span class="r1-subtle r1-truncate" style="font-size:12px;">' + R1.escapeHtml(t.artist) + ' · ' + R1.escapeHtml(t.spec) + '</span>'
            + '    </div>'
            + '  </div>'
            + '  <div class="r1-row" style="gap:8px;flex-shrink:0;">'
            + '    <span class="r1-badge r1-badge-primary r1-mono">' + R1.escapeHtml(t.format) + '</span>'
            + rm
            + '  </div>'
            + '</div>';
        }).join('');
        box.scrollTop = keepScroll;
      }

      var lastRenderedTrack = '';
      function renderLyrics(st) {
        var tr = R1.getCurrentTrack();
        var box = document.getElementById('musicLyricsBox');
        var lyricSig = tr.id + '|' + (tr.lyrics ? tr.lyrics.length : 0);
        if (lastRenderedTrack !== lyricSig) {
          lastRenderedTrack = lyricSig;
          box.innerHTML = (tr.lyrics && tr.lyrics.length)
            ? tr.lyrics.map(function(line, i) {
                return '<div id="mLyric-' + i + '" onclick="R1.seekTo(' + line.t + ')" class="lyric-item">' + R1.escapeHtml(line.txt) + '</div>';
              }).join('')
            : '<div class="r1-subtle" style="margin:auto;font-size:13px;">暂无同步歌词</div>';
        }
        if (tr.lyrics && tr.lyrics.length) {
          var activeIdx = 0;
          for (var i = 0; i < tr.lyrics.length; i++) {
            if (st.currentTime >= tr.lyrics[i].t) activeIdx = i;
          }
          tr.lyrics.forEach(function(_, i) {
            var el = document.getElementById('mLyric-' + i);
            if (el) el.classList.toggle('active', i === activeIdx);
          });
        }
      }

      R1.onStateChange(function(st) {
        var tr = R1.getCurrentTrack();
        document.getElementById('musicTitle').textContent = tr.title;
        document.getElementById('musicDiscLabel').textContent = tr.id === 'idle' ? 'R1 HI-FI' : tr.title;
        document.getElementById('musicFormat').textContent = tr.format;
        document.getElementById('musicAlbum').textContent = tr.artist + (tr.album ? (' · ' + tr.album) : '');
        document.getElementById('musicSpecText').textContent = tr.spec;
        document.getElementById('musicCurTime').textContent = R1.formatTime(st.currentTime);
        document.getElementById('musicTotTime').textContent = R1.formatTime(tr.duration);

        if (!seeking) {
          var ratio = tr.duration > 0 ? Math.min(1, st.currentTime / tr.duration) : 0;
          seekRange.value = String(Math.round(ratio * 1000));
          seekRange.style.setProperty('--val', (ratio * 100).toFixed(1) + '%');
        }

        document.getElementById('musicPlayIcon').innerHTML = R1.playPauseGlyph(st.isPlaying, 22);
        document.getElementById('musicVinylDisc').classList.toggle('vinyl-paused', !st.isPlaying);
        document.getElementById('musicLoopLabel').textContent = R1.loopName(st.loopMode);

        if (!draggingVol) {
          var v = st.muted ? 0 : st.volume;
          document.getElementById('musicVolLabel').textContent = st.muted ? '静音' : (st.volume + '%');
          volRange.value = String(v);
          volRange.style.setProperty('--val', v + '%');
        }

        document.querySelectorAll('#musicSourceTabs .src-tab').forEach(function(btn) {
          var isAct = btn.getAttribute('data-src') === st.musicSource;
          btn.className = 'src-tab r1-seg-btn' + (isAct ? ' is-active' : '');
        });

        document.getElementById('musicSleepText').textContent = st.sleepRemainSec > 0 ? ('剩余 ' + R1.formatTime(st.sleepRemainSec)) : '未开启';

        renderPlaylist(st);
        renderLyrics(st);
      });
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 页面 04: 设置与运维中枢 (desktop/settings.html)
// 左侧二级分组目录 + 右侧单列受限宽度表单 + 悬浮确认 FAB & 原生 <dialog> Diff 审查
// =========================================================
function getDesktopSettings() {
  const html = `
        <div class="r1-settings-grid">
          <!-- 左侧二级导航目录 -->
          <nav class="r1-settings-nav" id="setTabNav" aria-label="设置分组">
            <button type="button" data-tab="llm" class="set-tab r1-set-nav-btn is-active">
              <span class="material-symbols-outlined" aria-hidden="true">&#xE7FD;</span>
              <span>AI 模型</span>
            </button>
            <button type="button" data-tab="tts" class="set-tab r1-set-nav-btn">
              <span class="material-symbols-outlined" aria-hidden="true">&#xE049;</span>
              <span>语音与识别</span>
            </button>
            <button type="button" data-tab="alarms" class="set-tab r1-set-nav-btn">
              <span class="material-symbols-outlined" aria-hidden="true">&#xE425;</span>
              <span>闹钟与定时</span>
            </button>
            <button type="button" data-tab="home-weather" class="set-tab r1-set-nav-btn">
              <span class="material-symbols-outlined" aria-hidden="true">&#xE2C7;</span>
              <span>家居与天气</span>
            </button>
            <button type="button" data-tab="ops" class="set-tab r1-set-nav-btn">
              <span class="material-symbols-outlined" aria-hidden="true">&#xE32D;</span>
              <span>应用与系统</span>
            </button>
            <button type="button" data-tab="logs" class="set-tab r1-set-nav-btn">
              <span class="material-symbols-outlined" aria-hidden="true">&#xE88E;</span>
              <span>日志与配置</span>
            </button>
          </nav>

          <!-- 右侧受限宽度表单内容区 -->
          <div class="r1-settings-content">

            <!-- 分组 1: AI 大模型配置 -->
            <section data-panel="llm" class="set-panel r1-card">
              <div class="r1-card-head">
                <h2 class="r1-card-title">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE7FD;</span>
                  <span>AI 大模型</span>
                </h2>
                <span id="llmKeyStatusBadge" class="r1-badge">待配置密钥</span>
              </div>

              <div class="r1-form-grid">
                <div class="r1-field">
                  <span class="r1-label">接口协议</span>
                  <div class="r1-input r1-mono" style="opacity:0.85;">OpenAI 兼容</div>
                </div>

                <div class="r1-field">
                  <label class="r1-label" for="cfgLlmModel">模型名称</label>
                  <div class="r1-row" style="gap:8px;">
                    <input id="cfgLlmModel" type="text" list="llmModelList" placeholder="例如 GLM-4.5-Flash" class="r1-input r1-mono r1-flex-1">
                    <button type="button" id="btnFetchModels" class="r1-btn r1-btn-tonal">获取</button>
                  </div>
                  <datalist id="llmModelList"></datalist>
                  <span id="llmModelHint" class="r1-hint"></span>
                </div>

                <div class="r1-field r1-col-full">
                  <label class="r1-label" for="cfgLlmBaseUrl">接口地址</label>
                  <input id="cfgLlmBaseUrl" type="text" list="llmUrlPresets" placeholder="https://api.openai.com" class="r1-input r1-mono">
                  <datalist id="llmUrlPresets">
                    <option value="https://open.bigmodel.cn/api/paas/v4/chat/completions"></option>
                    <option value="https://api.deepseek.com/v1/chat/completions"></option>
                    <option value="https://api.siliconflow.cn/v1/chat/completions"></option>
                    <option value="https://api.openai.com/v1/chat/completions"></option>
                    <option value="http://127.0.0.1:11434/v1/chat/completions"></option>
                  </datalist>
                  <span class="r1-hint">仅填写域名时自动补全 /v1/chat/completions</span>
                </div>

                <div class="r1-field">
                  <label class="r1-label" for="cfgLlmApiKey">API 密钥</label>
                  <input id="cfgLlmApiKey" type="password" placeholder="请输入 API Key" class="r1-input r1-mono" autocomplete="off">
                </div>

                <div class="r1-field">
                  <label class="r1-label" for="cfgLlmReasoning">思考深度</label>
                  <select id="cfgLlmReasoning" class="r1-select">
                    <option value="auto">自动（不干预）</option>
                    <option value="none">关闭</option>
                    <option value="low">低</option>
                    <option value="medium">中</option>
                    <option value="high">高</option>
                  </select>
                  <span class="r1-hint">推理模型回复过慢或无正文时选「关闭」</span>
                </div>

                <div class="r1-field r1-col-full">
                  <label class="r1-label" for="cfgLlmPrompt">系统人设</label>
                  <textarea id="cfgLlmPrompt" rows="3" class="r1-textarea"></textarea>
                </div>
              </div>

              <div id="llmTestResult" class="r1-result-box r1-hidden"></div>

              <div class="r1-row-between" style="padding-top:12px;border-top:1px solid var(--r1-border-subtle);">
                <span class="r1-hint">修改后点击右下角悬浮按钮审查并写入音箱</span>
                <button type="button" id="btnTestLlm" class="r1-btn r1-btn-tonal">测试连接</button>
              </div>
            </section>

            <!-- 分组 2: 语音合成 (TTS) 与语音识别 (ASR) -->
            <section data-panel="tts" class="set-panel r1-card" style="display:none;">
              <div class="r1-card-head">
                <h2 class="r1-card-title">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE049;</span>
                  <span>语音合成与识别</span>
                </h2>
                <button type="button" onclick="R1.stopSpeak()" class="r1-btn r1-btn-outlined r1-btn-sm">停止播报</button>
              </div>

              <div class="r1-form-grid">
                <div class="r1-field">
                  <label class="r1-label" for="cfgTtsEngine">语音合成引擎 (TTS)</label>
                  <select id="cfgTtsEngine" class="r1-select">
                    <option value="native">原厂语音</option>
                    <option value="openai">自建语音 (OpenAI 兼容)</option>
                    <option value="edge">Edge 语音</option>
                  </select>
                </div>

                <div class="r1-field">
                  <label class="r1-label" for="cfgTtsSpeed">
                    <span>播报语速</span>
                    <span id="ttsSpeedVal" class="r1-mono r1-primary-text">1.0x</span>
                  </label>
                  <input id="cfgTtsSpeed" type="range" min="0.6" max="1.8" step="0.1" value="1.0" class="r1-slider" style="--val:33%;">
                </div>

                <!-- 自定义 TTS 字段 -->
                <div id="ttsCustomFields" class="r1-col-full r1-form-grid" style="display:none;">
                  <div class="r1-field">
                    <label class="r1-label" for="cfgTtsVoice">音色</label>
                    <input id="cfgTtsVoice" type="text" list="ttsVoicePresets" placeholder="zh-CN-XiaoxiaoNeural" class="r1-input r1-mono">
                    <datalist id="ttsVoicePresets">
                      <option value="zh-CN-XiaoxiaoNeural"></option>
                      <option value="zh-CN-YunxiNeural"></option>
                      <option value="alloy"></option>
                      <option value="nova"></option>
                    </datalist>
                  </div>
                  <div class="r1-field">
                    <label class="r1-label" for="cfgTtsModel">语音模型</label>
                    <input id="cfgTtsModel" type="text" placeholder="模型名" class="r1-input r1-mono">
                  </div>
                  <div class="r1-field r1-col-full">
                    <label class="r1-label" for="cfgTtsUrl">语音接口地址</label>
                    <input id="cfgTtsUrl" type="text" placeholder="https://example.com/v1/audio/speech" class="r1-input r1-mono">
                  </div>
                  <div class="r1-field r1-col-full">
                    <label class="r1-label" for="cfgTtsKey">语音密钥（可选）</label>
                    <input id="cfgTtsKey" type="password" placeholder="服务需要 Bearer 令牌时填写" class="r1-input r1-mono" autocomplete="off">
                  </div>
                </div>

                <!-- 语音识别区块 -->
                <div id="asrBlock" class="r1-col-full r1-col" style="padding-top:14px;border-top:1px solid var(--r1-border-subtle);">
                  <div class="r1-field">
                    <label class="r1-label" for="cfgAsrEngine">语音识别引擎 (ASR)</label>
                    <select id="cfgAsrEngine" class="r1-select">
                      <option value="native">原厂识别</option>
                      <option value="self">自建 ASR 网关</option>
                    </select>
                  </div>

                  <div id="asrCustomFields" class="r1-form-grid" style="display:none;">
                    <div class="r1-field">
                      <label class="r1-label" for="cfgAsrUrl">ASR 网关地址</label>
                      <input id="cfgAsrUrl" type="text" placeholder="http://192.168.1.100:8720" class="r1-input r1-mono">
                    </div>
                    <div class="r1-field">
                      <label class="r1-label" for="cfgAsrToken">网关令牌 (X-API-Key)</label>
                      <input id="cfgAsrToken" type="password" placeholder="留空表示不修改" class="r1-input r1-mono" autocomplete="off">
                    </div>
                    <div class="r1-col-full r1-row-wrap">
                      <button type="button" id="btnTestAsrHealth" class="r1-btn r1-btn-tonal r1-btn-sm">查看网关用量</button>
                      <button type="button" id="btnTestAsrWav" class="r1-btn r1-btn-outlined r1-btn-sm">上传 WAV 识别测试</button>
                      <input type="file" id="asrWavFile" accept=".wav,audio/wav" class="r1-hidden">
                      <span id="cfgAsrTokenHint" class="r1-hint">16kHz · 单声道</span>
                    </div>
                    <div id="asrTestResult" class="r1-col-full r1-result-box r1-hidden"></div>
                  </div>
                </div>

                <!-- 三个二元交互开关 (M3 Switch) -->
                <div class="r1-col-full r1-col" style="gap:10px;padding-top:10px;border-top:1px solid var(--r1-border-subtle);">
                  <label class="r1-switch-row" for="cfgAutoSpeak">
                    <span class="r1-switch-meta">
                      <span class="r1-switch-title">自动播报回复</span>
                      <span class="r1-switch-desc">控制台或语音对话得到回复后自动通过音箱喇叭发声</span>
                    </span>
                    <input id="cfgAutoSpeak" type="checkbox" role="switch" class="r1-switch" checked>
                  </label>

                  <label class="r1-switch-row" for="cfgContinuous">
                    <span class="r1-switch-meta">
                      <span class="r1-switch-title">连续对话</span>
                      <span class="r1-switch-desc">播报结束后自动重新拾音，无需再次喊唤醒词</span>
                    </span>
                    <input id="cfgContinuous" type="checkbox" role="switch" class="r1-switch">
                  </label>

                  <label class="r1-switch-row" for="cfgEarlyEndpoint">
                    <span class="r1-switch-meta">
                      <span class="r1-switch-title">提前判停</span>
                      <span class="r1-switch-desc">说完静音 1.2 秒即结束录音，缩短响应延迟（需新版 L1）</span>
                    </span>
                    <input id="cfgEarlyEndpoint" type="checkbox" role="switch" class="r1-switch">
                  </label>
                </div>

                <div class="r1-field r1-col-full">
                  <label class="r1-label" for="cfgTtsSample">试听文本</label>
                  <div class="r1-row" style="gap:10px;">
                    <input id="cfgTtsSample" type="text" value="斐讯 R1 语音合成测试正常。" class="r1-input r1-flex-1">
                    <button type="button" id="btnTestTts" class="r1-btn r1-btn-tonal">立即试听</button>
                  </div>
                </div>
              </div>

              <div id="ttsTestResult" class="r1-result-box r1-hidden"></div>
            </section>

            <!-- 分组 3: 闹钟与睡眠定时 -->
            <section data-panel="alarms" class="set-panel r1-card" style="display:none;">
              <div class="r1-card-head">
                <h2 class="r1-card-title">
                  <span class="material-symbols-outlined" aria-hidden="true">&#xE425;</span>
                  <span>闹钟与睡眠定时</span>
                </h2>
                <span class="r1-badge r1-badge-primary r1-mono" id="sleepTimerBadge">睡眠定时: 未开启</span>
              </div>

              <div class="r1-subcard r1-row-between" style="flex-wrap:wrap;gap:10px;">
                <span style="font-size:13px;font-weight:500;">快捷睡眠倒计时</span>
                <div class="r1-row-wrap">
                  <button type="button" onclick="R1.setSleepTimer(15)" class="r1-chip">15 分钟</button>
                  <button type="button" onclick="R1.setSleepTimer(30)" class="r1-chip">30 分钟</button>
                  <button type="button" onclick="R1.setSleepTimer(60)" class="r1-chip">60 分钟</button>
                  <button type="button" onclick="R1.setSleepTimer(0)" class="r1-chip">关闭</button>
                </div>
              </div>

              <div class="r1-row" style="flex-wrap:wrap;gap:10px;">
                <input id="newAlarmTime" type="time" value="08:00" class="r1-input r1-mono" style="width:130px;" aria-label="闹钟时间">
                <input id="newAlarmLabel" type="text" placeholder="闹钟备注，例如：起床 / 喝水" class="r1-input r1-flex-1" aria-label="闹钟备注">
                <select id="newAlarmAction" class="r1-select" style="width:136px;" aria-label="触发动作">
                  <option value="speak">语音提醒</option>
                  <option value="weather">播报天气</option>
                </select>
                <button type="button" id="btnAddAlarm" class="r1-btn r1-btn-primary">添加闹钟</button>
              </div>

              <div id="alarmListBox" class="r1-col" style="gap:10px;"></div>

              <div class="r1-row-between r1-subtle r1-mono" style="font-size:12px;padding-top:10px;border-top:1px solid var(--r1-border-subtle);">
                <span>音箱触发时区：中国标准时间 (UTC+8)</span>
                <span id="alarmNowClock">北京时间 --:--:--</span>
              </div>
            </section>

            <!-- 分组 4: 家居与天气（合并为单列两张清晰卡片） -->
            <div data-panel="home-weather" class="set-panel r1-col" style="display:none;gap:20px;">
              <section class="r1-card">
                <div class="r1-card-head">
                  <h2 class="r1-card-title">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE429;</span>
                    <span>Home Assistant 智能家居</span>
                  </h2>
                  <span class="r1-badge" id="haStatusBadge">可选</span>
                </div>
                <div class="r1-form-grid">
                  <div class="r1-field">
                    <label class="r1-label" for="cfgHaUrl">服务地址</label>
                    <input id="cfgHaUrl" type="text" placeholder="http://192.168.1.10:8123" class="r1-input r1-mono">
                  </div>
                  <div class="r1-field">
                    <label class="r1-label" for="cfgHaToken">长期访问令牌</label>
                    <input id="cfgHaToken" type="password" placeholder="粘贴 HA Token" class="r1-input r1-mono" autocomplete="off">
                  </div>
                </div>
                <div id="haTestResult" class="r1-result-box r1-hidden"></div>
                <div class="r1-row" style="justify-content:flex-end;">
                  <button type="button" id="btnTestHa" class="r1-btn r1-btn-tonal">测试连接</button>
                </div>
              </section>

              <section class="r1-card">
                <div class="r1-card-head">
                  <h2 class="r1-card-title">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE2C7;</span>
                    <span>城市天气</span>
                  </h2>
                  <span class="r1-badge" id="weatherProviderBadge">未测试</span>
                </div>

                <div class="r1-form-grid">
                  <div class="r1-field">
                    <label class="r1-label" for="cfgWeatherCity">默认城市</label>
                    <input id="cfgWeatherCity" type="text" placeholder="例如 杭州 / 北京" class="r1-input">
                  </div>
                  <div class="r1-field">
                    <label class="r1-label" for="cfgWeatherHost">和风接口域名</label>
                    <input id="cfgWeatherHost" type="text" placeholder="devapi.qweather.com" class="r1-input r1-mono">
                  </div>
                </div>

                <details class="r1-details">
                  <summary>高级：和风天气 Ed25519 JWT 凭据管理</summary>
                  <div id="weatherJwtFields" class="r1-form-grid">
                    <div class="r1-field">
                      <label class="r1-label" for="cfgWeatherJwtIss">开发者 ID</label>
                      <input id="cfgWeatherJwtIss" type="text" placeholder="开发者 ID" class="r1-input r1-mono">
                    </div>
                    <div class="r1-field">
                      <label class="r1-label" for="cfgWeatherJwtProjectId">项目 ID</label>
                      <input id="cfgWeatherJwtProjectId" type="text" placeholder="项目 ID" class="r1-input r1-mono">
                    </div>
                    <div class="r1-field r1-col-full">
                      <label class="r1-label" for="cfgWeatherJwtKeyId">凭据 ID (kid)</label>
                      <input id="cfgWeatherJwtKeyId" type="text" placeholder="凭据 ID" class="r1-input r1-mono">
                    </div>
                    <div class="r1-field r1-col-full">
                      <label class="r1-label" for="cfgWeatherJwtKey">私钥 (Ed25519)</label>
                      <textarea id="cfgWeatherJwtKey" rows="3" placeholder="粘贴或生成私钥" class="r1-textarea r1-mono"></textarea>
                    </div>
                    <div class="r1-field r1-col-full">
                      <label class="r1-label" for="cfgWeatherJwtPub">公钥 (粘贴至和风控制台)</label>
                      <textarea id="cfgWeatherJwtPub" rows="3" readonly placeholder="生成或推导后自动显示" class="r1-textarea r1-mono"></textarea>
                    </div>
                    <div class="r1-col-full r1-row-wrap">
                      <button type="button" id="btnJwtKeygen" class="r1-btn r1-btn-tonal r1-btn-sm">生成密钥对</button>
                      <button type="button" id="btnJwtDownload" class="r1-btn r1-btn-outlined r1-btn-sm">下载密钥</button>
                      <button type="button" id="btnJwtPub" class="r1-btn r1-btn-outlined r1-btn-sm">推导公钥</button>
                      <button type="button" id="btnJwtCopyPub" class="r1-btn r1-btn-outlined r1-btn-sm">复制公钥</button>
                      <button type="button" id="btnJwtPreview" class="r1-btn r1-btn-outlined r1-btn-sm">签名预览</button>
                    </div>
                  </div>
                </details>

                <div id="weatherTestResult" class="r1-result-box r1-hidden"></div>

                <div class="r1-row-wrap" style="justify-content:flex-end;padding-top:10px;border-top:1px solid var(--r1-border-subtle);">
                  <button type="button" id="btnTestWeatherQw" class="r1-btn r1-btn-outlined r1-btn-sm">测和风</button>
                  <button type="button" id="btnTestWeatherWttr" class="r1-btn r1-btn-outlined r1-btn-sm">测通用源</button>
                  <button type="button" id="btnTestWeather" class="r1-btn r1-btn-tonal r1-btn-sm">自动测试</button>
                </div>
              </section>
            </div>

            <!-- 分组 5: 应用与系统维护 -->
            <div data-panel="ops" class="set-panel r1-col" style="display:none;gap:20px;">
              <section class="r1-card">
                <div class="r1-card-head">
                  <h2 class="r1-card-title">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE32D;</span>
                    <span>系统维护与连接</span>
                  </h2>
                </div>
                <div class="r1-row-wrap">
                  <button type="button" id="btnSpeakerHost" class="r1-btn r1-btn-tonal r1-btn-sm">音箱地址</button>
                  <button type="button" id="btnAccessToken" class="r1-btn r1-btn-tonal r1-btn-sm">访问令牌</button>
                  <button type="button" id="btnExportConfig" class="r1-btn r1-btn-outlined r1-btn-sm">导出配置</button>
                  <button type="button" id="btnImportConfig" class="r1-btn r1-btn-outlined r1-btn-sm">导入配置</button>
                  <input type="file" id="importConfigFile" accept="application/json,.json" class="r1-hidden">
                  <button type="button" onclick="doSystemClean()" class="r1-btn r1-btn-outlined r1-btn-sm">深度清理缓存</button>
                  <button type="button" id="btnClearCache" class="r1-btn r1-btn-danger r1-btn-sm">清除本机记录</button>
                  <button type="button" onclick="doSystemReboot()" class="r1-btn r1-btn-danger r1-btn-sm">重启音箱</button>
                </div>
              </section>

              <section class="r1-card">
                <div class="r1-card-head">
                  <h2 class="r1-card-title">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE5D8;</span>
                    <span>应用安装与管理</span>
                  </h2>
                  <div class="r1-row" style="gap:10px;">
                    <span id="appsCountBadge" class="r1-badge r1-mono">等待读取</span>
                    <button type="button" onclick="loadAppsList()" class="r1-btn r1-btn-tonal r1-btn-sm">刷新列表</button>
                  </div>
                </div>

                <input type="file" id="apkFileInput" accept=".apk" class="r1-hidden">
                <button type="button" id="apkDropZone" onclick="document.getElementById('apkFileInput').click()" class="r1-dropzone">
                  <span class="material-symbols-outlined r1-primary-text" style="font-size:28px;" aria-hidden="true">&#xE5D8;</span>
                  <span style="font-size:14px;font-weight:600;">点击选择或拖拽 .apk 安装包到此区域</span>
                  <span class="r1-hint">低内存流式上传并自动静默安装</span>
                </button>

                <div id="apkUploadProgress" class="r1-subcard r1-col r1-hidden" style="gap:8px;">
                  <span id="apkProgressText" class="r1-mono r1-primary-text" style="font-size:12px;">准备上传...</span>
                  <div style="width:100%;height:8px;border-radius:999px;background:var(--r1-surface-inset);overflow:hidden;">
                    <div id="apkProgressBar" style="height:100%;width:0%;background:var(--r1-primary);border-radius:999px;transition:width 0.2s ease;"></div>
                  </div>
                </div>

                <div id="installedAppsBox" class="r1-col" style="gap:8px;max-height:360px;overflow-y:auto;"></div>
              </section>
            </div>

            <!-- 分组 6: 实时日志与原始配置文件 -->
            <div data-panel="logs" class="set-panel r1-col" style="display:none;gap:20px;">
              <section class="r1-card">
                <div class="r1-card-head">
                  <h2 class="r1-card-title">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE88E;</span>
                    <span>实时运行日志</span>
                  </h2>
                  <button type="button" id="btnSyncLogs" class="r1-btn r1-btn-tonal r1-btn-sm">立即刷新</button>
                </div>
                <div id="logTerminalBox" class="r1-log-box"></div>
              </section>

              <section class="r1-card">
                <div class="r1-card-head">
                  <h2 class="r1-card-title">
                    <span class="material-symbols-outlined" aria-hidden="true">&#xE8C0;</span>
                    <span>原始 JSON 配置文件</span>
                  </h2>
                  <span class="r1-hint">与上方表单二选一 · 修改后点右下角确认写入</span>
                </div>
                <textarea id="cfgFileEditor" rows="16" spellcheck="false" class="r1-textarea r1-mono" aria-label="JSON 配置文件编辑器"></textarea>
              </section>
            </div>

          </div>
        </div>

        <!-- 未保存更改悬浮确认 FAB -->
        <button type="button" id="cfgFab" class="r1-fab" title="查看待写入的配置更改">
          <span id="cfgFabDot" class="r1-fab-dot" aria-hidden="true"></span>
          <span>审查并保存更改</span>
        </button>

        <!-- 原生 <dialog> Git Diff 审查弹窗 -->
        <dialog id="cfgReviewModal" class="r1-modal r1-modal-wide" closedby="any" aria-labelledby="cfgReviewTitle">
          <div class="r1-modal-head">
            <h1 id="cfgReviewTitle" class="r1-modal-title">待写入的配置更改</h1>
            <button type="button" id="cfgReviewClose" class="r1-btn r1-btn-ghost r1-btn-icon-sm" title="关闭" aria-label="关闭">×</button>
          </div>
          <div class="r1-modal-body">
            <p id="cfgReviewSub" style="margin:0;font-size:13px;">以下改动尚未写入音箱，确认后才会生效</p>
            <div id="cfgDiffBox" class="r1-diff-box"></div>
          </div>
          <div class="r1-modal-foot r1-modal-foot-split">
            <button type="button" id="cfgReviewDiscard" class="r1-btn r1-btn-danger">放弃更改</button>
            <div class="r1-row" style="gap:10px;">
              <button type="button" id="cfgReviewCancel" class="r1-btn r1-btn-outlined">返回编辑</button>
              <button type="button" id="cfgReviewConfirm" class="r1-btn r1-btn-primary">确认写入音箱</button>
            </div>
          </div>
        </dialog>`;

  const script = `
  <script>
    (function() {
      var savedSnapshot = null;
      var formDirty = false;
      var reviewDlg = document.getElementById('cfgReviewModal');
      if (reviewDlg) R1.bindLightDismiss(reviewDlg);

      // 左侧二级设置目录切换
      document.querySelectorAll('#setTabNav .set-tab').forEach(function(btn) {
        btn.addEventListener('click', function() {
          var tab = this.getAttribute('data-tab');
          document.querySelectorAll('#setTabNav .set-tab').forEach(function(b) {
            b.classList.toggle('is-active', b === btn);
          });
          document.querySelectorAll('.set-panel').forEach(function(p) {
            var show = p.getAttribute('data-panel') === tab;
            p.style.display = show ? 'flex' : 'none';
          });
          if (tab === 'ops' && R1.getState().online) loadAppsList();
          if (tab === 'logs') R1.fetchSpeakerLogs();
        });
      });

      function updateModelHint() {
        var hint = document.getElementById('llmModelHint');
        if (!hint) return;
        var url = document.getElementById('cfgLlmBaseUrl').value.trim();
        if (!url) { hint.textContent = '先填接口地址，再点「获取」拉取模型列表'; return; }
        var suffix = '/chat/completions';
        var base = url;
        while (base.length > 0 && base.charAt(base.length - 1) === '/') base = base.slice(0, -1);
        if (base.slice(-suffix.length) !== suffix) {
          var scheme = base.indexOf('://');
          if (base.indexOf('/', scheme + 3) < 0) base = base + '/v1' + suffix;
        }
        var models = base.slice(-suffix.length) === suffix ? base.slice(0, -suffix.length) : base;
        hint.textContent = '将请求 ' + models + '/models';
      }
      document.getElementById('cfgLlmBaseUrl').addEventListener('input', updateModelHint);

      function isAllStars(v) {
        if (!v) return false;
        for (var i = 0; i < v.length; i++) { if (v.charAt(i) !== '*') return false; }
        return true;
      }
      function secretValue(id) {
        var el = document.getElementById(id);
        if (!el) return '';
        var v = el.value.trim();
        return isAllStars(v) ? '' : v;
      }
      function applySecretMask(id, hasSaved) {
        var el = document.getElementById(id);
        if (!el) return;
        el.dataset.saved = hasSaved ? '1' : '0';
        if (hasSaved && !el.value) {
          el.value = '********';
          el.dataset.masked = '1';
        }
        if (!el.dataset.bound) {
          el.dataset.bound = '1';
          el.addEventListener('focus', function() {
            if (el.dataset.masked === '1') { el.value = ''; el.dataset.masked = '0'; }
          });
          el.addEventListener('blur', function() {
            if (!el.value && el.dataset.saved === '1') { el.value = '********'; el.dataset.masked = '1'; }
          });
        }
      }

      function applyVoiceFields() {
        var tts = document.getElementById('cfgTtsEngine').value;
        var asr = document.getElementById('cfgAsrEngine').value;
        document.getElementById('ttsCustomFields').style.display = (tts === 'native') ? 'none' : 'grid';
        document.getElementById('asrCustomFields').style.display = (asr === 'self') ? 'grid' : 'none';
      }
      document.getElementById('cfgTtsEngine').addEventListener('change', applyVoiceFields);
      document.getElementById('cfgAsrEngine').addEventListener('change', applyVoiceFields);

      function syncSpeedVisual(val) {
        var v = parseFloat(val) || 1.0;
        var pct = Math.round(((v - 0.6) / 1.2) * 100);
        document.getElementById('cfgTtsSpeed').style.setProperty('--val', Math.max(0, Math.min(100, pct)) + '%');
        document.getElementById('ttsSpeedVal').textContent = v.toFixed(1) + 'x';
      }
      document.getElementById('cfgTtsSpeed').addEventListener('input', function() { syncSpeedVisual(this.value); });

      function collectPatch() {
        var patch = {
          llmProvider: 'openai',
          llmModel: document.getElementById('cfgLlmModel').value.trim(),
          llmBaseUrl: document.getElementById('cfgLlmBaseUrl').value.trim(),
          reasoningEffort: document.getElementById('cfgLlmReasoning').value,
          llmSystemPrompt: document.getElementById('cfgLlmPrompt').value.trim(),
          ttsEngine: document.getElementById('cfgTtsEngine').value,
          ttsVoice: document.getElementById('cfgTtsVoice').value.trim(),
          ttsModel: document.getElementById('cfgTtsModel').value.trim(),
          ttsUrl: document.getElementById('cfgTtsUrl').value.trim(),
          ttsSpeed: parseFloat(document.getElementById('cfgTtsSpeed').value) || 1.0,
          asrEngine: document.getElementById('cfgAsrEngine').value,
          asrUrl: document.getElementById('cfgAsrUrl').value.trim(),
          asrToken: secretValue('cfgAsrToken'),
          autoSpeak: !!document.getElementById('cfgAutoSpeak').checked,
          continuousChat: !!document.getElementById('cfgContinuous').checked,
          earlyEndpoint: !!document.getElementById('cfgEarlyEndpoint').checked,
          haUrl: document.getElementById('cfgHaUrl').value.trim(),
          weatherCity: document.getElementById('cfgWeatherCity').value.trim(),
          weatherHost: document.getElementById('cfgWeatherHost').value.trim(),
          weatherAuth: 'jwt',
          weatherJwtIss: document.getElementById('cfgWeatherJwtIss').value.trim(),
          weatherJwtProjectId: document.getElementById('cfgWeatherJwtProjectId').value.trim(),
          weatherJwtKeyId: document.getElementById('cfgWeatherJwtKeyId').value.trim(),
          weatherJwtKey: document.getElementById('cfgWeatherJwtKey').value.trim(),
          weatherJwtPub: document.getElementById('cfgWeatherJwtPub').value.trim()
        };
        var keyVal = secretValue('cfgLlmApiKey');
        if (keyVal) patch.llmApiKey = keyVal;
        var ttsKey = secretValue('cfgTtsKey');
        if (ttsKey) patch.ttsKey = ttsKey;
        var haTok = secretValue('cfgHaToken');
        if (haTok) patch.haToken = haTok;
        return patch;
      }

      function syncFormFromState(st) {
        var c = st.config;
        document.getElementById('cfgLlmModel').value = c.llmModel || 'GLM-4.5-Flash';
        document.getElementById('cfgLlmReasoning').value = c.llmReasoning || 'none';
        document.getElementById('cfgLlmBaseUrl').value = c.llmBaseUrl || '';
        applySecretMask('cfgLlmApiKey', !!c.hasKey);
        document.getElementById('cfgLlmPrompt').value = c.llmSystemPrompt || '';

        var badge = document.getElementById('llmKeyStatusBadge');
        if (badge) {
          badge.textContent = c.hasKey ? ('已启用 · ' + (c.llmModel || 'LLM')) : '待配置密钥';
          badge.className = c.hasKey ? 'r1-badge r1-badge-success r1-mono' : 'r1-badge r1-badge-warn';
        }

        document.getElementById('cfgTtsEngine').value = c.ttsEngine || 'native';
        document.getElementById('cfgTtsVoice').value = c.ttsVoice || 'zh-CN-XiaoxiaoNeural';
        document.getElementById('cfgTtsModel').value = c.ttsModel || '';
        document.getElementById('cfgTtsUrl').value = c.ttsUrl || '';
        applySecretMask('cfgTtsKey', !!c.hasTtsKey);
        document.getElementById('cfgTtsSpeed').value = c.ttsSpeed || 1.0;
        syncSpeedVisual(c.ttsSpeed || 1.0);

        document.getElementById('cfgAsrEngine').value = c.asrEngine || 'native';
        applyVoiceFields();
        document.getElementById('cfgAsrUrl').value = c.asrUrl || '';
        applySecretMask('cfgAsrToken', !!c.hasAsrToken);
        var asrTokHint = document.getElementById('cfgAsrTokenHint');
        if (asrTokHint) asrTokHint.textContent = c.hasAsrToken ? '令牌已保存，留空不改' : '未设置令牌（需鉴权网关会返回 401）';

        document.getElementById('cfgAutoSpeak').checked = c.autoSpeak !== false;
        document.getElementById('cfgContinuous').checked = !!c.continuousChat;
        document.getElementById('cfgEarlyEndpoint').checked = !!c.earlyEndpoint;
        updateModelHint();

        document.getElementById('cfgHaUrl').value = c.haUrl || '';
        applySecretMask('cfgHaToken', !!c.hasHaToken);
        var haBadge = document.getElementById('haStatusBadge');
        if (haBadge) {
          haBadge.textContent = c.haUrl ? '已配置' : '可选';
          haBadge.className = c.haUrl ? 'r1-badge r1-badge-success' : 'r1-badge';
        }

        document.getElementById('cfgWeatherCity').value = c.weatherCity || '';
        document.getElementById('cfgWeatherHost').value = c.weatherHost || 'devapi.qweather.com';
        document.getElementById('cfgWeatherJwtIss').value = c.weatherJwtIss || '';
        document.getElementById('cfgWeatherJwtProjectId').value = c.weatherJwtProjectId || '';
        document.getElementById('cfgWeatherJwtKeyId').value = c.weatherJwtKeyId || '';
        document.getElementById('cfgWeatherJwtKey').value = c.weatherJwtKey || '';
        document.getElementById('cfgWeatherJwtPub').value = c.weatherJwtPub || '';

        savedSnapshot = JSON.stringify(collectPatch());
        refreshCfgDirty();
      }

      function loadConfigFile() {
        var box = document.getElementById('cfgFileEditor');
        if (!box || box.dataset.dirty === '1') return;
        R1.request('GET', '/api/v1/config/export', null, 10000)
          .then(function(j) {
            if (box.dataset.dirty === '1') return;
            box.value = JSON.stringify(j, null, 2);
            box.dataset.saved = box.value;
          })
          .catch(function() {});
      }

      function isFileDirty() {
        var fileBox = document.getElementById('cfgFileEditor');
        return !!(fileBox && fileBox.dataset.saved !== undefined && fileBox.value !== fileBox.dataset.saved);
      }
      function isFormChanged() {
        return savedSnapshot !== null && JSON.stringify(collectPatch()) !== savedSnapshot;
      }
      function refreshCfgDirty() {
        var fab = document.getElementById('cfgFab');
        if (!fab) return;
        var fileDirty = isFileDirty();
        if (fileDirty || isFormChanged()) {
          fab.classList.add('show');
          document.getElementById('cfgReviewSub').textContent = fileDirty
            ? '配置文件有未保存更改 · 确认后整份写入音箱（密钥已脱敏）'
            : '以下字段尚未写入音箱 · 确认后才会生效';
        } else {
          fab.classList.remove('show');
          if (reviewDlg && reviewDlg.open) reviewDlg.close();
        }
      }

      var CFG_SECRET_KEYS = ['llmApiKey', 'asrToken', 'ttsKey', 'haToken', 'weatherJwtKey'];
      var CFG_FIELD_LABELS = {
        llmProvider: '协议', llmModel: '模型', llmBaseUrl: '接口地址', reasoningEffort: '思考',
        llmSystemPrompt: '人设', llmApiKey: '密钥', ttsEngine: '语音合成', ttsVoice: '音色',
        ttsModel: '语音模型', ttsUrl: '语音地址', ttsKey: '语音密钥', ttsSpeed: '语速',
        asrEngine: '语音识别', asrUrl: '识别地址', asrToken: '网关令牌', autoSpeak: '自动播报',
        continuousChat: '连续对话', earlyEndpoint: '提前判停', haUrl: '家居地址', haToken: '家居令牌',
        weatherCity: '城市', weatherHost: '天气域名', weatherAuth: '天气鉴权', weatherJwtIss: '开发者 ID',
        weatherJwtProjectId: '项目 ID', weatherJwtKeyId: '凭据 ID', weatherJwtKey: '天气私钥', weatherJwtPub: '天气公钥'
      };
      function fmtDiffVal(key, v) {
        if (v === undefined || v === null || v === '') return '""';
        if (CFG_SECRET_KEYS.indexOf(key) >= 0) return '"••••••"';
        return typeof v === 'string' ? ('"' + v + '"') : String(v);
      }
      function diffLine(cls, text) {
        var span = document.createElement('span');
        span.className = 'cfg-diff-line ' + cls;
        span.textContent = text;
        return span.outerHTML;
      }
      function buildFormDiff() {
        var before = {};
        try { before = JSON.parse(savedSnapshot || '{}'); } catch (e) { before = {}; }
        var after = collectPatch();
        var keys = Object.keys(after);
        Object.keys(before).forEach(function(k) { if (keys.indexOf(k) < 0) keys.push(k); });
        var out = [
          diffLine('cfg-diff-file', '--- 当前配置（已保存）'),
          diffLine('cfg-diff-file', '+++ 待写入配置'),
          diffLine('cfg-diff-hunk', '@@ 改动的字段 @@')
        ];
        var changed = 0;
        keys.forEach(function(k) {
          if (JSON.stringify(before[k]) === JSON.stringify(after[k])) return;
          changed++;
          var label = CFG_FIELD_LABELS[k] || k;
          out.push(diffLine('cfg-diff-del', '- ' + label + ' (' + k + '): ' + fmtDiffVal(k, before[k])));
          out.push(diffLine('cfg-diff-add', '+ ' + label + ' (' + k + '): ' + fmtDiffVal(k, after[k])));
        });
        if (!changed) out.push(diffLine('cfg-diff-meta', '（没有检测到字段变化）'));
        return out.join('');
      }
      function maskCfgSecrets(text) {
        try {
          var obj = JSON.parse(text);
          (function walk(o) {
            if (!o || typeof o !== 'object') return;
            Object.keys(o).forEach(function(k) {
              if (CFG_SECRET_KEYS.indexOf(k) >= 0 && o[k]) o[k] = '••••••';
              else walk(o[k]);
            });
          })(obj);
          return JSON.stringify(obj, null, 2);
        } catch (e) { return text; }
      }
      function buildFileDiff() {
        var fileBox = document.getElementById('cfgFileEditor');
        var a = maskCfgSecrets(fileBox.dataset.saved || '').split(String.fromCharCode(10));
        var b = maskCfgSecrets(fileBox.value).split(String.fromCharCode(10));
        var n = a.length, m = b.length, dp = [], i, j;
        for (i = 0; i <= n; i++) { var row = []; for (j = 0; j <= m; j++) row.push(0); dp.push(row); }
        for (i = n - 1; i >= 0; i--) {
          for (j = m - 1; j >= 0; j--) {
            dp[i][j] = (a[i] === b[j]) ? (dp[i + 1][j + 1] + 1) : Math.max(dp[i + 1][j], dp[i][j + 1]);
          }
        }
        var ops = [];
        i = 0; j = 0;
        while (i < n && j < m) {
          if (a[i] === b[j]) { ops.push([' ', a[i]]); i++; j++; }
          else if (dp[i + 1][j] >= dp[i][j + 1]) { ops.push(['-', a[i]]); i++; }
          else { ops.push(['+', b[j]]); j++; }
        }
        while (i < n) ops.push(['-', a[i++]]);
        while (j < m) ops.push(['+', b[j++]]);
        var out = [
          diffLine('cfg-diff-file', '--- config.json（当前）'),
          diffLine('cfg-diff-file', '+++ config.json（待写入）'),
          diffLine('cfg-diff-hunk', '@@ -1,' + n + ' +1,' + m + ' @@')
        ];
        ops.forEach(function(op) {
          var cls = op[0] === '+' ? 'cfg-diff-add' : (op[0] === '-' ? 'cfg-diff-del' : 'cfg-diff-ctx');
          out.push(diffLine(cls, op[0] + ' ' + op[1]));
        });
        return out.join('');
      }

      function saveCfgChanges(btn) {
        var fileBox = document.getElementById('cfgFileEditor');
        btn.disabled = true;
        var job;
        if (isFileDirty()) {
          var parsed;
          try { parsed = JSON.parse(fileBox.value); }
          catch (e) { R1.toast('配置文件不是合法 JSON', 'err'); btn.disabled = false; return; }
          job = R1.request('POST', '/api/v1/config/import', parsed, 15000).then(function() {
            fileBox.dataset.dirty = '0';
            fileBox.dataset.saved = fileBox.value;
            R1.toast('配置文件已写入音箱', 'ok');
            return R1.syncFromSpeaker(true);
          });
        } else {
          savedSnapshot = null;
          job = R1.updateConfig(collectPatch()).then(function() {
            ['cfgLlmApiKey', 'cfgTtsKey', 'cfgHaToken', 'cfgAsrToken'].forEach(function(id) { applySecretMask(id, true); });
          });
        }
        Promise.resolve(job).catch(function(e) {
          R1.toast('保存失败：' + e.message, 'err');
        }).then(function() {
          btn.disabled = false;
          refreshCfgDirty();
        });
      }

      function openCfgReview() {
        document.getElementById('cfgDiffBox').innerHTML = isFileDirty() ? buildFileDiff() : buildFormDiff();
        if (reviewDlg && reviewDlg.showModal) reviewDlg.showModal();
      }
      function closeCfgReview() {
        if (reviewDlg && reviewDlg.open) reviewDlg.close();
      }
      document.getElementById('cfgFab').addEventListener('click', openCfgReview);
      document.getElementById('cfgReviewClose').addEventListener('click', closeCfgReview);
      document.getElementById('cfgReviewCancel').addEventListener('click', closeCfgReview);
      document.getElementById('cfgReviewDiscard').addEventListener('click', function() {
        var fileBox = document.getElementById('cfgFileEditor');
        if (isFileDirty()) {
          fileBox.value = fileBox.dataset.saved || '';
          fileBox.dataset.dirty = '0';
        }
        formDirty = false;
        syncFormFromState(R1.getState());
        closeCfgReview();
        refreshCfgDirty();
      });
      document.getElementById('cfgReviewConfirm').addEventListener('click', function() {
        closeCfgReview();
        saveCfgChanges(this);
      });

      // Ctrl+S 快捷打开配置审查窗
      document.addEventListener('keydown', function(e) {
        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
          if (isFileDirty() || isFormChanged()) {
            e.preventDefault();
            openCfgReview();
          }
        }
      });

      // 绑定脏检测
      [
        'cfgLlmModel', 'cfgLlmReasoning', 'cfgLlmBaseUrl', 'cfgLlmApiKey', 'cfgLlmPrompt',
        'cfgTtsEngine', 'cfgTtsVoice', 'cfgTtsModel', 'cfgTtsUrl', 'cfgTtsKey', 'cfgTtsSpeed',
        'cfgAutoSpeak', 'cfgContinuous', 'cfgEarlyEndpoint',
        'cfgAsrEngine', 'cfgAsrUrl', 'cfgAsrToken',
        'cfgHaUrl', 'cfgHaToken', 'cfgWeatherCity', 'cfgWeatherHost',
        'cfgWeatherJwtIss', 'cfgWeatherJwtProjectId', 'cfgWeatherJwtKeyId', 'cfgWeatherJwtKey'
      ].forEach(function(id) {
        var el = document.getElementById(id);
        if (!el) return;
        el.addEventListener('input', function() { formDirty = true; refreshCfgDirty(); });
        el.addEventListener('change', function() { formDirty = true; refreshCfgDirty(); });
      });

      var cfgFileEditor = document.getElementById('cfgFileEditor');
      if (cfgFileEditor) {
        cfgFileEditor.addEventListener('input', function() {
          cfgFileEditor.dataset.dirty = (cfgFileEditor.value === (cfgFileEditor.dataset.saved || '')) ? '0' : '1';
          refreshCfgDirty();
        });
      }

      function configSig(c) {
        return JSON.stringify([
          c.llmProvider, c.llmModel, c.llmReasoning, c.llmBaseUrl, c.llmSystemPrompt, c.hasKey,
          c.ttsEngine, c.ttsVoice, c.ttsModel, c.ttsUrl, c.ttsSpeed, c.autoSpeak, c.continuousChat, c.earlyEndpoint, c.hasTtsKey,
          c.asrEngine, c.asrUrl, c.hasAsrToken,
          c.haUrl, c.hasHaToken, c.weatherCity, c.weatherHost, c.hasWeatherKey, c.weatherAuth, c.weatherJwtProjectId, c.weatherJwtKeyId, c.hasWeatherJwtKey
        ]);
      }
      var lastCfgSig = '';
      R1.onStateChange(function(st) {
        var sig = configSig(st.config);
        if (sig === lastCfgSig) return;
        lastCfgSig = sig;
        if (formDirty && savedSnapshot !== null) return;
        formDirty = false;
        syncFormFromState(st);
        loadConfigFile();
      });

      function showResult(id, text, kind) {
        var box = document.getElementById(id);
        if (!box) return;
        box.className = 'r1-result-box ' + (kind === 'ok' ? 'is-ok' : (kind === 'err' ? 'is-err' : 'is-info'));
        box.textContent = text;
      }

      // 获取模型列表
      document.getElementById('btnFetchModels').addEventListener('click', function() {
        var hint = document.getElementById('llmModelHint');
        var url = document.getElementById('cfgLlmBaseUrl').value.trim();
        var key = secretValue('cfgLlmApiKey');
        if (!url) { R1.toast('请先填写接口地址', 'err'); return; }
        hint.textContent = '正在拉取模型列表…';
        R1.request('GET', '/api/v1/llm/models?baseUrl=' + encodeURIComponent(url) + (key ? '&apiKey=' + encodeURIComponent(key) : ''), null, 20000)
          .then(function(res) {
            var models = res.models || [];
            document.getElementById('llmModelList').innerHTML = models.map(function(m) {
              return '<option value="' + R1.escapeHtml(m) + '"></option>';
            }).join('');
            hint.textContent = models.length
              ? ('共获取到 ' + models.length + ' 个模型，点击输入框下拉选择')
              : ('接口可达，但未返回模型列表（' + res.endpoint + '）');
            R1.toast(models.length ? ('获取到 ' + models.length + ' 个模型') : '没有返回模型', models.length ? 'ok' : 'err');
          })
          .catch(function(e) {
            hint.textContent = '获取失败：' + e.message;
            R1.toast('获取模型失败：' + e.message, 'err');
          });
      });

      // 测试 LLM 连接
      document.getElementById('btnTestLlm').addEventListener('click', function() {
        var body = {
          baseUrl: document.getElementById('cfgLlmBaseUrl').value.trim(),
          apiKey: secretValue('cfgLlmApiKey'),
          model: document.getElementById('cfgLlmModel').value.trim()
        };
        showResult('llmTestResult', '正在请求 ' + (body.baseUrl || '（未填地址）') + ' …', 'info');
        R1.request('POST', '/api/v1/llm/test', body, 25000)
          .then(function(res) {
            showResult('llmTestResult', '连接成功 · ' + res.model + ' · ' + res.costMs + 'ms · 回复「' + (res.reply || '').trim() + '」｜' + res.endpoint, 'ok');
            R1.toast('大模型连接正常', 'ok');
          })
          .catch(function(e) {
            showResult('llmTestResult', '连接失败：' + e.message, 'err');
            R1.toast('测试失败：' + e.message, 'err');
          });
      });

      // TTS 试听
      document.getElementById('btnTestTts').addEventListener('click', function() {
        var text = document.getElementById('cfgTtsSample').value.trim() || '斐讯 R1 语音合成测试正常。';
        showResult('ttsTestResult', '正在合成并下发音箱播报…', 'info');
        R1.request('POST', '/api/v1/tts/test', { text: text }, 25000)
          .then(function(res) {
            if (res.engine === 'native') {
              showResult('ttsTestResult', '已用原厂语音播报（本地直出，无首音耗时）', 'ok');
            } else {
              var ms = (typeof res.firstAudioMs === 'number' && res.firstAudioMs >= 0) ? (res.firstAudioMs + 'ms') : '未测到';
              showResult('ttsTestResult', '已播报 · 首音 ' + ms + ' · 端点 ' + (res.endpoint || '（空）'), 'ok');
            }
            R1.toast('语音试听正常', 'ok');
          })
          .catch(function(e) {
            showResult('ttsTestResult', '试听失败：' + e.message, 'err');
            R1.toast('TTS 测试失败：' + e.message, 'err');
          });
      });

      // 自建 ASR 健康检查与 WAV 测试
      document.getElementById('btnTestAsrHealth').addEventListener('click', function() {
        var url = document.getElementById('cfgAsrUrl').value.trim();
        var tok = secretValue('cfgAsrToken');
        showResult('asrTestResult', '正在读取网关用量 /usage …', 'info');
        R1.request('GET', '/api/v1/asr/health?url=' + encodeURIComponent(url) + '&token=' + encodeURIComponent(tok), null, 15000)
          .then(function(res) {
            if (res.ok) {
              showResult('asrTestResult', '网关可用 · 端点 ' + res.endpoint + ' · 账单月 ' + (res.billingMonth || '?') + ' · 重置日 ' + (res.resetDate || '?') + (res.hasToken ? '' : ' · 未设置令牌'), 'ok');
              R1.toast('ASR 网关用量已读取', 'ok');
            } else {
              showResult('asrTestResult', (res.error || '用量读取失败') + ' · 端点 ' + res.endpoint, 'err');
            }
          })
          .catch(function(e) { showResult('asrTestResult', e.message, 'err'); });
      });

      document.getElementById('btnTestAsrWav').addEventListener('click', function() {
        document.getElementById('asrWavFile').click();
      });
      document.getElementById('asrWavFile').addEventListener('change', function() {
        var f = this.files && this.files[0];
        if (!f) return;
        if (f.size > 3 * 1024 * 1024) {
          showResult('asrTestResult', '文件超过 3MB 上限（约 10 秒 16k 单声道 WAV）', 'err');
          return;
        }
        showResult('asrTestResult', '正在上传并识别 ' + f.name + ' …', 'info');
        var reader = new FileReader();
        reader.onload = function() {
          var b64 = String(reader.result || '').split(',')[1] || '';
          R1.request('POST', '/api/v1/asr/test', { url: document.getElementById('cfgAsrUrl').value.trim(), audio: b64 }, 60000)
            .then(function(res) {
              if (res.ok) {
                var extra = [];
                if (res.durationMs) extra.push(res.durationMs + 'ms');
                if (res.engine) extra.push('引擎 ' + res.engine);
                showResult('asrTestResult', '识别成功 · "' + res.text + '"' + (extra.length ? (' · ' + extra.join(' · ')) : ''), 'ok');
                R1.toast('识别成功：' + res.text, 'ok');
              } else {
                showResult('asrTestResult', (res.error || '识别失败') + ' · ' + (res.endpoint || ''), 'err');
              }
            })
            .catch(function(e) { showResult('asrTestResult', e.message, 'err'); });
        };
        reader.readAsDataURL(f);
        this.value = '';
      });

      // Home Assistant 测试
      document.getElementById('btnTestHa').addEventListener('click', function() {
        showResult('haTestResult', '正在读取 /api/states …', 'info');
        R1.request('POST', '/api/v1/ha/test', {
          haUrl: document.getElementById('cfgHaUrl').value.trim(),
          haToken: secretValue('cfgHaToken')
        }, 15000)
          .then(function(res) {
            showResult('haTestResult', '连接成功 · 实体 ' + res.entities + ' 个 · 可语音控制 ' + res.controllable + ' 个', 'ok');
            R1.toast('Home Assistant 连接正常', 'ok');
          })
          .catch(function(e) { showResult('haTestResult', '连接失败：' + e.message, 'err'); });
      });

      // 和风 Ed25519 密钥与天气测试
      function downloadText(name, text) {
        var blob = new Blob([text], { type: 'text/plain' });
        var a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = name;
        a.click();
      }
      document.getElementById('btnJwtKeygen').addEventListener('click', function() {
        var existed = document.getElementById('cfgWeatherJwtKey').value.trim() || document.getElementById('cfgWeatherJwtPub').value.trim();
        var runGen = function() {
          showResult('weatherTestResult', '正在生成 Ed25519 密钥对…', 'info');
          R1.request('POST', '/api/v1/weather/keygen', { save: false }, 20000)
            .then(function(res) {
              if (!res.ok) { showResult('weatherTestResult', res.error, 'err'); return null; }
              document.getElementById('cfgWeatherJwtKey').value = res.privateKey;
              document.getElementById('cfgWeatherJwtPub').value = res.publicKeyPem;
              downloadText('r1-qweather-keys.txt', ['privateKey', res.privateKey, '', 'publicKey', res.publicKeyPem, ''].join(String.fromCharCode(10)));
              showResult('weatherTestResult', '密钥已生成并下载，请将公钥粘贴至和风控制台。', 'ok');
              savedSnapshot = null;
              return R1.updateConfig({ weatherJwtKey: res.privateKey, weatherJwtPub: res.publicKeyPem, weatherAuth: 'jwt' });
            })
            .then(function(saved) { if (saved) R1.toast('密钥已保存并下载', 'ok'); })
            .catch(function(e) { showResult('weatherTestResult', e.message, 'err'); });
        };
        if (existed) {
          R1.confirmModal({
            title: '重新生成和风密钥对',
            desc: '已有密钥。重新生成会覆盖音箱上的旧私钥，和风控制台原公钥也会失效。确认继续？',
            confirmText: '确认重新生成',
            danger: true
          }).then(function(ok) { if (ok) runGen(); });
        } else {
          runGen();
        }
      });

      document.getElementById('btnJwtDownload').addEventListener('click', function() {
        var priv = document.getElementById('cfgWeatherJwtKey').value.trim();
        var pub = document.getElementById('cfgWeatherJwtPub').value.trim();
        if (!priv && !pub) { R1.toast('当前没有可下载的密钥', 'err'); return; }
        downloadText('r1-qweather-keys.txt', ['privateKey', priv, '', 'publicKey', pub, ''].join(String.fromCharCode(10)));
        R1.toast('密钥文件已下载', 'ok');
      });

      document.getElementById('btnJwtPub').addEventListener('click', function() {
        R1.request('POST', '/api/v1/weather/pubkey', { weatherJwtKey: document.getElementById('cfgWeatherJwtKey').value.trim() }, 20000)
          .then(function(res) {
            if (!res.ok) { showResult('weatherTestResult', res.error, 'err'); return; }
            document.getElementById('cfgWeatherJwtPub').value = res.publicKeyPem;
            savedSnapshot = null;
            R1.updateConfig({ weatherJwtPub: res.publicKeyPem });
            showResult('weatherTestResult', '公钥推导成功 · base64：' + res.publicKeyBase64, 'ok');
          })
          .catch(function(e) { showResult('weatherTestResult', e.message, 'err'); });
      });

      document.getElementById('btnJwtCopyPub').addEventListener('click', function() {
        var el = document.getElementById('cfgWeatherJwtPub');
        if (!el.value.trim()) { R1.toast('还没有公钥可复制', 'err'); return; }
        el.removeAttribute('readonly');
        el.select();
        var ok = false;
        try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
        el.setAttribute('readonly', 'readonly');
        R1.toast(ok ? '公钥已复制' : '请手动选中复制', ok ? 'ok' : 'err');
      });

      document.getElementById('btnJwtPreview').addEventListener('click', function() {
        showResult('weatherTestResult', '正在签名预览…', 'info');
        R1.request('POST', '/api/v1/weather/jwt', {
          weatherJwtIss: document.getElementById('cfgWeatherJwtIss').value.trim(),
          weatherJwtProjectId: document.getElementById('cfgWeatherJwtProjectId').value.trim(),
          weatherJwtKeyId: document.getElementById('cfgWeatherJwtKeyId').value.trim(),
          weatherJwtKey: secretValue('cfgWeatherJwtKey')
        }, 20000)
          .then(function(res) {
            if (res.ok) {
              showResult('weatherTestResult', '签名成功 · header=' + res.header + ' · payload=' + res.payload + ' · token=' + res.token.substring(0, 40) + '…', 'ok');
              R1.toast('JWT 签发正常', 'ok');
            } else {
              showResult('weatherTestResult', res.error, 'err');
            }
          })
          .catch(function(e) { showResult('weatherTestResult', e.message, 'err'); });
      });

      function testWeather(provider) {
        var badge = document.getElementById('weatherProviderBadge');
        var city = document.getElementById('cfgWeatherCity').value.trim() || '北京';
        showResult('weatherTestResult', '正在查询 ' + city + ' 天气…', 'info');
        R1.request('POST', '/api/v1/weather/test', {
          city: city,
          provider: provider || 'auto',
          weatherHost: document.getElementById('cfgWeatherHost').value.trim(),
          weatherAuth: 'jwt',
          weatherJwtIss: document.getElementById('cfgWeatherJwtIss').value.trim(),
          weatherJwtProjectId: document.getElementById('cfgWeatherJwtProjectId').value.trim(),
          weatherJwtKeyId: document.getElementById('cfgWeatherJwtKeyId').value.trim(),
          weatherJwtKey: secretValue('cfgWeatherJwtKey')
        }, 30000)
          .then(function(res) {
            var name = res.provider === 'qweather' ? '和风天气' : (res.provider === 'wttr' ? '通用源 wttr.in' : '无可用源');
            var lines = ['来源：' + name + (res.degraded ? '（降级）' : '')];
            if (res.testedKey) lines.push('凭据：' + res.testedKey);
            if (res.detail) lines.push('过程：' + res.detail);
            if (res.report) lines.push('播报：' + res.report);
            showResult('weatherTestResult', lines.join(' ｜ '), res.ok ? 'ok' : 'err');
            if (badge) {
              badge.textContent = res.provider === 'qweather' ? '和风' : (res.provider === 'wttr' ? 'wttr' : (res.ok ? '自动' : '失败'));
              badge.className = res.ok ? 'r1-badge r1-badge-success' : 'r1-badge r1-badge-danger';
            }
          })
          .catch(function(e) { showResult('weatherTestResult', e.message, 'err'); });
      }
      document.getElementById('btnTestWeatherQw').addEventListener('click', function() { testWeather('qweather'); });
      document.getElementById('btnTestWeatherWttr').addEventListener('click', function() { testWeather('wttr'); });
      document.getElementById('btnTestWeather').addEventListener('click', function() { testWeather(null); });

      // 系统连接、导入导出与清缓存按钮
      document.getElementById('btnSpeakerHost').addEventListener('click', function() { R1.promptSpeakerHost(); });
      document.getElementById('btnAccessToken').addEventListener('click', function() { R1.promptToken(); });
      document.getElementById('btnSyncLogs').addEventListener('click', function() { R1.fetchSpeakerLogs(); R1.toast('正在刷新日志'); });
      document.getElementById('btnClearCache').addEventListener('click', function() {
        R1.confirmModal({
          title: '清除本机浏览器记录',
          desc: '仅清除当前浏览器缓存的对话与自定义音频流，音箱端配置不受影响。',
          confirmText: '确认清除',
          danger: true
        }).then(function(ok) { if (ok) R1.resetFactory(); });
      });

      document.getElementById('btnExportConfig').addEventListener('click', function() {
        var st = R1.getState();
        var base = (location.protocol === 'file:') ? ('http://' + (st.hostIp || '')) : '';
        var headers = st.token ? { 'X-Token': st.token } : {};
        R1.toast('正在导出音箱配置…');
        fetch(base + '/api/v1/config/export', { headers: headers })
          .then(function(r) { return r.json(); })
          .then(function(j) {
            if (!j || j.ok === false) throw new Error((j && j.error) || '导出失败');
            var blob = new Blob([JSON.stringify(j, null, 2)], { type: 'application/json' });
            var a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = 'r1-config-' + (new Date().toISOString().slice(0, 10)) + '.json';
            a.click();
            R1.toast('已导出配置文件', 'ok');
          })
          .catch(function(e) { R1.toast('导出失败：' + e.message, 'err'); });
      });

      var importFile = document.getElementById('importConfigFile');
      document.getElementById('btnImportConfig').addEventListener('click', function() { importFile.click(); });
      importFile.addEventListener('change', function() {
        var f = importFile.files && importFile.files[0];
        if (!f) return;
        var reader = new FileReader();
        reader.onload = function() {
          var data;
          try { data = JSON.parse(reader.result); } catch (e) { R1.toast('文件不是合法 JSON', 'err'); return; }
          R1.confirmModal({
            title: '导入并覆盖音箱配置',
            desc: '确认使用选中的 JSON 文件覆盖音箱当前配置？（访问令牌不会被覆盖）',
            confirmText: '确认导入',
            danger: true
          }).then(function(ok) {
            if (!ok) return;
            R1.request('POST', '/api/v1/config/import', data, 15000)
              .then(function() {
                R1.toast('配置已导入，正在同步…', 'ok');
                setTimeout(function() { R1.syncFromSpeaker(false); }, 500);
              })
              .catch(function(e) { R1.toast('导入失败：' + e.message, 'err'); });
          });
        };
        reader.readAsText(f);
        importFile.value = '';
      });

      // 应用管理与 APK 安装
      function loadAppsList() {
        var box = document.getElementById('installedAppsBox');
        var badge = document.getElementById('appsCountBadge');
        if (!box) return;
        box.innerHTML = '<div class="r1-subtle" style="text-align:center;padding:20px;">正在读取已安装应用...</div>';
        R1.fetchApps().then(function(res) {
          var apps = res.apps || [];
          if (badge) badge.textContent = '共 ' + apps.length + ' 个应用';
          if (!apps.length) {
            box.innerHTML = '<div class="r1-empty">没有读取到应用</div>';
            return;
          }
          box.innerHTML = apps.map(function(a) {
            var isSelf = a.packageName === 'com.r1.brain';
            var tags = '<span class="r1-badge ' + (a.isSystem ? '' : 'r1-badge-primary') + '">' + (a.isSystem ? '系统' : '第三方') + '</span>'
              + (a.isRunning ? '<span class="r1-badge r1-badge-success">运行中</span>' : '')
              + (isSelf ? '<span class="r1-badge r1-badge-warn">本机服务</span>' : '');
            var actions = (a.isRunning && !isSelf ? '<button type="button" data-app-stop="' + R1.escapeHtml(a.packageName) + '" class="r1-btn r1-btn-outlined r1-btn-sm">强停</button>' : '')
              + (!isSelf ? '<button type="button" data-app-uninstall="' + R1.escapeHtml(a.packageName) + '" data-app-name="' + R1.escapeHtml(a.name) + '" class="r1-btn r1-btn-danger r1-btn-sm">卸载</button>' : '');
            return ''
              + '<div class="r1-subcard r1-row-between" style="flex-wrap:wrap;gap:10px;">'
              + '  <div class="r1-col r1-flex-1" style="gap:4px;">'
              + '    <div class="r1-row-wrap">'
              + '      <strong style="font-size:14px;">' + R1.escapeHtml(a.name) + '</strong>'
              + tags
              + '    </div>'
              + '    <div class="r1-mono r1-subtle r1-truncate" style="font-size:12px;">' + R1.escapeHtml(a.packageName) + ' · v' + R1.escapeHtml(a.versionName) + ' · ' + R1.escapeHtml(a.sizeMb) + '</div>'
              + '  </div>'
              + '  <div class="r1-row" style="gap:8px;">' + actions + '</div>'
              + '</div>';
          }).join('');
        }).catch(function(err) {
          if (badge) badge.textContent = '读取失败';
          box.innerHTML = '<div class="r1-result-box is-err">读取应用列表失败：' + R1.escapeHtml(err.message || err) + '</div>';
        });
      }

      function handleApkFile(file) {
        if (!file) return;
        var progressBox = document.getElementById('apkUploadProgress');
        var progressBar = document.getElementById('apkProgressBar');
        var progressText = document.getElementById('apkProgressText');
        progressBox.classList.remove('r1-hidden');
        progressBar.style.width = '0%';
        progressText.textContent = '准备上传 ' + file.name + ' ...';

        R1.uploadApk(file, function(prog) {
          progressBar.style.width = prog.percent + '%';
          progressText.textContent = '流式上传 ' + prog.percent + '% (' + (prog.loaded / 1024).toFixed(0) + 'KB / ' + (prog.total / 1024).toFixed(0) + 'KB)';
        }).then(function(res) {
          progressText.textContent = res.selfUpdate ? '安装完成，服务正在重启...' : '安装完成';
          R1.toast(res.selfUpdate ? '已安装，正在重启' : '应用已安装', 'ok');
          setTimeout(function() { progressBox.classList.add('r1-hidden'); loadAppsList(); }, 2500);
        }).catch(function(err) {
          progressText.textContent = '安装失败：' + (err.message || err);
          R1.toast('安装失败：' + (err.message || err), 'err');
        });
      }

      var apkInput = document.getElementById('apkFileInput');
      apkInput.addEventListener('change', function(e) {
        if (e.target.files && e.target.files[0]) {
          handleApkFile(e.target.files[0]);
          e.target.value = '';
        }
      });
      var dropZone = document.getElementById('apkDropZone');
      dropZone.addEventListener('dragover', function(e) { e.preventDefault(); dropZone.classList.add('is-dragover'); });
      dropZone.addEventListener('dragleave', function() { dropZone.classList.remove('is-dragover'); });
      dropZone.addEventListener('drop', function(e) {
        e.preventDefault();
        dropZone.classList.remove('is-dragover');
        if (e.dataTransfer.files && e.dataTransfer.files[0]) handleApkFile(e.dataTransfer.files[0]);
      });

      window.loadAppsList = loadAppsList;
      document.getElementById('installedAppsBox').addEventListener('click', function(e) {
        var stopBtn = e.target.closest ? e.target.closest('[data-app-stop]') : null;
        if (stopBtn) { window.doStopApp(stopBtn.getAttribute('data-app-stop')); return; }
        var unBtn = e.target.closest ? e.target.closest('[data-app-uninstall]') : null;
        if (unBtn) { window.doUninstallApp(unBtn.getAttribute('data-app-uninstall'), unBtn.getAttribute('data-app-name') || ''); }
      });
      window.doStopApp = function(pkg) {
        R1.stopApp(pkg).then(function() { R1.toast('已强行停止 ' + pkg, 'ok'); setTimeout(loadAppsList, 800); })
          .catch(function(e) { R1.toast(e.message, 'err'); });
      };
      window.doUninstallApp = function(pkg, name) {
        R1.confirmModal({
          title: '卸载应用',
          desc: '确认彻底卸载「' + name + '」(' + pkg + ') 吗？卸载后将回收存储与运行内存。',
          confirmText: '确认卸载',
          danger: true
        }).then(function(ok) {
          if (!ok) return;
          R1.toast('正在静默卸载 ' + name + ' ...');
          R1.uninstallApp(pkg).then(function() {
            R1.toast('已卸载 ' + name, 'ok');
            setTimeout(loadAppsList, 1000);
          }).catch(function(e) { R1.toast('卸载失败：' + e.message, 'err'); });
        });
      };
      window.doSystemClean = function() {
        R1.toast('正在执行深度清理...');
        R1.cleanSystem().then(function(r) {
          R1.toast('清理完成：删除 ' + (r.deletedFiles || 0) + ' 个缓存，释放 ' + (r.freedMb || '0MB'), 'ok');
          R1.syncFromSpeaker(true);
        }).catch(function(e) { R1.toast(e.message, 'err'); });
      };
      window.doSystemReboot = function() {
        R1.confirmModal({
          title: '重启斐讯 R1 音箱',
          desc: '确认立即重启音箱硬件系统？重启约需 40 秒，期间网络连接与播报将短暂中断。',
          confirmText: '确认重启',
          danger: true
        }).then(function(ok) {
          if (!ok) return;
          R1.rebootSystem().then(function() { R1.toast('音箱正在重启，请稍候刷新页面', 'ok'); })
            .catch(function(e) { R1.toast(e.message, 'err'); });
        });
      };

      // 闹钟新增与渲染
      document.getElementById('btnAddAlarm').addEventListener('click', function() {
        var tm = document.getElementById('newAlarmTime').value || '08:00';
        var lb = document.getElementById('newAlarmLabel').value.trim() || '定时唤醒提醒';
        var act = document.getElementById('newAlarmAction').value || 'speak';
        R1.addAlarm({ time: tm, label: lb, repeat: '每天', action: act });
        document.getElementById('newAlarmLabel').value = '';
      });

      // 实时日志（修正 ERR 红 / WARN 黄 / INFO 绿 语义分级）
      var lastLogSig = '';
      var logStick = true;
      var logBox = document.getElementById('logTerminalBox');
      logBox.addEventListener('scroll', function() {
        logStick = (logBox.scrollHeight - logBox.scrollTop - logBox.clientHeight) < 40;
      });
      function renderLogs() {
        var list = R1.getLogs();
        var sig = (list || []).map(function(l) { return (l.time || '') + '|' + (l.source || '') + '|' + (l.msg || '').length; }).join(';');
        if (sig === lastLogSig) return;
        lastLogSig = sig;
        if (!list || !list.length) {
          logBox.innerHTML = '<div class="r1-subtle">暂无运行日志</div>';
          return;
        }
        logBox.innerHTML = list.map(function(l) {
          var lvlCls = l.level === 'ERR' ? 'r1-badge-danger' : (l.level === 'WARN' ? 'r1-badge-warn' : 'r1-badge-success');
          return ''
            + '<div class="r1-row" style="align-items:flex-start;gap:8px;">'
            + '  <span class="r1-subtle" style="flex-shrink:0;">' + R1.escapeHtml(l.time) + '</span>'
            + '  <span class="r1-badge ' + lvlCls + '" style="padding:1px 7px;font-size:11px;flex-shrink:0;">' + R1.escapeHtml(l.level) + '</span>'
            + '  <span class="r1-primary-text" style="flex-shrink:0;">[' + R1.escapeHtml(l.tag) + ']</span>'
            + '  <span style="word-break:break-word;">' + R1.escapeHtml(l.msg) + '</span>'
            + '</div>';
        }).join('');
        if (logStick) logBox.scrollTop = logBox.scrollHeight;
      }
      renderLogs();
      R1.onLog(renderLogs);

      var logsFetched = false;
      setInterval(function() { if (document.visibilityState !== 'hidden') R1.fetchSpeakerLogs(); }, 15000);

      var lastAlarmSig = '__init__';
      R1.onStateChange(function(s) {
        if (s.online && !logsFetched) { logsFetched = true; R1.fetchSpeakerLogs(); }
        document.getElementById('sleepTimerBadge').textContent = s.sleepRemainSec > 0 ? ('睡眠倒计时: ' + R1.formatTime(s.sleepRemainSec)) : '睡眠定时: 未开启';
        document.getElementById('alarmNowClock').textContent = '北京时间 ' + R1.nowTimeStr();

        var alarmSig = JSON.stringify(s.alarms || []);
        if (alarmSig === lastAlarmSig) return;
        lastAlarmSig = alarmSig;
        var alarmBox = document.getElementById('alarmListBox');
        if (!s.alarms || s.alarms.length === 0) {
          alarmBox.innerHTML = '<div class="r1-empty" style="min-height:110px;"><span>还没有设置闹钟</span></div>';
          return;
        }
        alarmBox.innerHTML = s.alarms.map(function(a) {
          var actTag = a.action === 'weather' ? '<span class="r1-badge r1-badge-primary">天气</span>' : '<span class="r1-badge">提醒</span>';
          return ''
            + '<div class="r1-subcard r1-row-between" style="flex-wrap:wrap;gap:12px;">'
            + '  <div class="r1-row" style="gap:14px;min-width:0;">'
            + '    <span class="r1-mono ' + (a.enabled ? 'r1-primary-text' : 'r1-subtle') + '" style="font-size:20px;font-weight:700;">' + R1.escapeHtml(a.time) + '</span>'
            + '    <div class="r1-col" style="gap:2px;min-width:0;">'
            + '      <div class="r1-row" style="gap:8px;"><span style="font-size:14px;font-weight:500;">' + R1.escapeHtml(a.label) + '</span>' + actTag + '</div>'
            + '      <span class="r1-subtle r1-mono" style="font-size:12px;">' + R1.escapeHtml(a.repeat || '每天') + ' · 北京时间</span>'
            + '    </div>'
            + '  </div>'
            + '  <div class="r1-row" style="gap:12px;">'
            + '    <input type="checkbox" role="switch" class="r1-switch" ' + (a.enabled ? 'checked' : '') + ' onchange="R1.toggleAlarm(' + a.id + ')" aria-label="切换闹钟 ' + R1.escapeHtml(a.time) + '">'
            + '    <button type="button" onclick="R1.deleteAlarm(' + a.id + ')" class="r1-btn r1-btn-danger r1-btn-sm">删除</button>'
            + '  </div>'
            + '</div>';
        }).join('');
      });
    })();
  </script>`;

  return { html, script };
}

module.exports = {
  home: getDesktopHome,
  chat: getDesktopChat,
  music: getDesktopMusic,
  settings: getDesktopSettings
};
