/**
 * 移动端 App 4 个独立页面的主体内容模板 (mobile-pages.js)
 * 正式打包生产版：
 *  - 严格遵循移动端竖屏单列紧凑卡片与拇指交互区设计
 *  - 所有按钮统一采用黑金浮雕触觉样式，杜绝纯黄底色按钮与 2px 直角方框
 *  - 严禁提供任何网页端语音拾音按钮或语音唤醒模拟输入，所有输入框均为纯文本指令输入栏
 *  - 零预置演示数据与零虚假曲目，全部对接 R1 真实接口 (/api/v1/*)，时间统一采用北京时间 (UTC+8)
 */

// =========================================================
// 移动端页面 01: 首页 (mobile/home.html)
// =========================================================
function getMobileHome() {
  const html = `
      <!-- Top Visual Area: Compact Master Acoustic Deck -->
      <section class="flex flex-col bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[24px] p-4 shadow-xl border border-primary/20 relative overflow-hidden backdrop-blur-md">
        <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/40 to-transparent"></div>
        <div class="flex items-center justify-between gap-space-sm mb-3">
          <div class="flex items-center gap-3 min-w-0">
            <div onclick="R1.togglePlay()" class="relative w-14 h-14 rounded-full overflow-hidden shrink-0 shadow-[0_0_16px_rgba(253,211,133,0.22),inset_0_2px_6px_rgba(0,0,0,0.9)] border border-primary/40 select-none group cursor-pointer">
              <div id="mHomeVinyl" class="absolute inset-0 rounded-full vinyl-spinning vinyl-paused" style="background:repeating-radial-gradient(circle at 50% 50%, rgba(255,255,255,.055) 0 .5px, rgba(0,0,0,0) .5px 2px),conic-gradient(from 200deg at 50% 50%, rgba(253,211,133,.22), rgba(253,211,133,0) 20%, rgba(255,255,255,.06) 45%, rgba(253,211,133,.16) 70%, rgba(253,211,133,0) 88%),radial-gradient(circle at 50% 50%, #16191d 0 26%, #0a0c0e 27% 100%)">
                <div class="absolute inset-0 m-auto w-[44%] h-[44%] rounded-full" style="background:radial-gradient(circle at 35% 30%, #f7deab, #dfb76c 48%, #8f6d2c 100%)"></div>
                <span class="absolute inset-0 m-auto w-[14%] h-[14%] rounded-full bg-[#0a0c0e]"></span>
              </div>
              <div class="absolute inset-0 rounded-full bg-[radial-gradient(circle_at_35%_35%,rgba(253,211,133,0.18)_0%,transparent_60%)] pointer-events-none"></div>
            </div>
            <div class="flex flex-col min-w-0">
              <div class="flex items-center gap-2">
                <span class="font-headline-sm text-headline-sm text-on-surface font-semibold tracking-tight truncate" id="mHomeTitle">未在播放</span>
                <span class="px-2 py-0.5 rounded-full bg-primary/15 text-primary border border-primary/30 font-label-xs text-label-xs font-mono" id="mHomeFormat">STANDBY</span>
              </div>
              <span class="font-body-sm text-body-sm text-secondary/80 truncate mt-0.5" id="mHomeAlbum">未在播放</span>
            </div>
          </div>

          <div class="flex items-end gap-[3px] h-7 px-2.5 py-1 bg-surface-container-lowest/80 rounded-full border border-primary/20 shadow-inner shrink-0" id="mHomeSpecBox">
            <div class="m-spec w-[3px] bg-primary rounded-full h-2 shadow-[0_0_6px_rgba(253,211,133,0.6)]"></div>
            <div class="m-spec w-[3px] bg-primary rounded-full h-3 shadow-[0_0_6px_rgba(253,211,133,0.6)]"></div>
            <div class="m-spec w-[3px] bg-primary/80 rounded-full h-2 shadow-[0_0_4px_rgba(253,211,133,0.5)]"></div>
            <div class="m-spec w-[3px] bg-primary rounded-full h-3 shadow-[0_0_6px_rgba(253,211,133,0.6)]"></div>
            <div class="m-spec w-[3px] bg-primary/70 rounded-full h-2"></div>
          </div>
        </div>

        <div class="flex flex-col gap-1.5 my-2">
          <div id="mHomeSeek" class="w-full h-1.5 bg-surface-container-lowest rounded-full overflow-hidden shadow-inner flex items-center p-0.5 border border-white/5 cursor-pointer">
            <div class="h-full bg-gradient-to-r from-primary-container to-primary rounded-full shadow-[0_0_8px_rgba(253,211,133,0.7)] transition-all duration-300" id="mHomeBar" style="width: 0%"></div>
          </div>
          <div class="flex justify-between items-center font-label-xs text-label-xs text-secondary/80 font-mono tabular-nums px-0.5">
            <span id="mHomeCurTime">00:00</span>
            <span class="text-primary/70 tracking-wider" id="mHomeSpecText">--</span>
            <span id="mHomeTotTime">00:00</span>
          </div>
        </div>

        <div class="flex items-center justify-between mt-2 pt-1">
          <a href="music.html" aria-label="播放列表" class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 text-primary active:scale-95 transition-all shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)] no-underline">
            <span class="material-symbols-outlined text-[18px] text-primary">&#xE03D;</span>
            <span class="font-label-xs text-label-xs font-mono text-primary">曲库</span>
          </a>
          <div class="flex items-center gap-3">
            <button onclick="R1.prevTrack()" aria-label="上一曲" class="w-10 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 flex items-center justify-center text-on-surface hover:text-primary active:scale-90 transition-all shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
              <span class="material-symbols-outlined text-[20px]">&#xE045;</span>
            </button>
            <button onclick="R1.togglePlay()" aria-label="播放/暂停" class="w-12 h-12 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 text-primary shadow-[0_2px_10px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.35)] flex items-center justify-center shrink-0 hover:border-primary/60 active:scale-95 active:shadow-inner transition-all cursor-pointer" id="playBtn">
              <span class="material-symbols-outlined text-[26px] text-primary flex items-center justify-center" id="playIcon">&#xE037;</span>
            </button>
            <button onclick="R1.nextTrack()" aria-label="下一曲" class="w-10 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 flex items-center justify-center text-on-surface hover:text-primary active:scale-90 transition-all shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
              <span class="material-symbols-outlined text-[20px]">&#xE044;</span>
            </button>
          </div>
          <button onclick="R1.toggleLoopMode()" aria-label="切换播放模式" class="flex items-center gap-1 px-3 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary active:scale-95 transition-all shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.25)]">
             <span class="material-symbols-outlined text-[15px] text-primary">&#xE040;</span>
             <span class="font-label-xs text-label-xs font-mono font-medium" id="mLoopText">列表循环</span>
           </button>
         </div>
       </section>

       <section class="grid grid-cols-2 gap-3 w-full">
         <div onclick="R1.promptSpeakerHost()" class="bg-surface-container-low/90 rounded-[22px] p-3.5 flex flex-col justify-between h-[116px] relative overflow-hidden border border-outline-variant/50 shadow-md cursor-pointer">
           <div class="flex items-center justify-between">
             <span class="font-headline-sm text-headline-sm text-on-surface font-medium">局域网地址</span>
             <span class="w-2.5 h-2.5 rounded-full bg-outline" id="mLanDot"></span>
           </div>
           <div class="flex flex-col gap-0.5">
             <span class="font-label-md text-label-md text-primary font-mono font-semibold truncate" id="mLanIp">--</span>
             <div class="flex items-center justify-between mt-0.5 pt-1 border-t border-white/5">
               <span class="font-label-xs text-label-xs text-secondary/70 font-mono">HTTP</span>
               <span class="font-label-xs text-label-xs text-secondary/70 font-mono">:8686</span>
             </div>
           </div>
         </div>
         <div class="bg-surface-container-low/90 rounded-[22px] p-3.5 flex flex-col justify-between h-[116px] border border-outline-variant/50 shadow-md">
           <div class="flex items-center justify-between">
             <span onclick="R1.toggleMute()" class="font-headline-sm text-headline-sm text-on-surface font-medium cursor-pointer">主音量</span>
             <span class="font-label-md text-label-md text-primary font-mono tabular-nums font-semibold px-2 py-0.5 rounded-full bg-primary/10 border border-primary/20" id="volVal">40</span>
           </div>
           <div class="flex items-center justify-between bg-surface-container-lowest rounded-full p-1 border border-outline-variant/40 shadow-inner">
             <button onclick="R1.stepVolume(-5)" class="w-8 h-8 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 shadow-[0_1px_3px_rgba(0,0,0,0.8),inset_0_1px_1px_rgba(253,211,133,0.25)] flex items-center justify-center text-primary active:scale-90 transition-all font-mono text-body-lg" id="volDec">-</button>
             <div class="flex-1 mx-2 h-1.5 bg-surface-container rounded-full overflow-hidden">
               <div class="h-full bg-gradient-to-r from-primary-container to-primary shadow-[0_0_6px_rgba(253,211,133,0.7)]" id="volBar" style="width: 40%"></div>
             </div>
             <button onclick="R1.stepVolume(5)" class="w-8 h-8 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 shadow-[0_1px_3px_rgba(0,0,0,0.8),inset_0_1px_1px_rgba(253,211,133,0.25)] flex items-center justify-center text-primary active:scale-90 transition-all font-mono text-body-lg" id="volInc">+</button>
            </div>
          </div>
          <!-- Quick Switch 3: Hardware Bluetooth Mode -->
          <div onclick="R1.toggleBluetooth()" class="bg-surface-container-low/90 rounded-[22px] p-3.5 flex flex-col justify-between h-[116px] border border-outline-variant/50 shadow-md cursor-pointer">
          <div class="flex items-center justify-between">
            <span class="font-headline-sm text-headline-sm text-on-surface font-medium">蓝牙</span>
            <span id="mBtDot" class="w-2.5 h-2.5 rounded-full bg-outline"></span>
          </div>
          <div class="flex flex-col gap-0.5">
            <span class="font-label-md text-label-md text-on-surface truncate font-medium" id="mBtDev">未连接</span>
            <div class="flex items-center justify-between mt-0.5 pt-1 border-t border-white/5">
              <span class="font-label-xs text-label-xs text-secondary/70 font-mono">音频</span>
              <span class="font-label-xs text-label-xs text-secondary/70 font-medium" id="mBtStatus">待命</span>
            </div>
          </div>
        </div>

        <!-- Quick Switch 4: Sleep Timer (音箱端真实睡眠定时) -->
        <div onclick="R1.cycleSleepTimer()" class="bg-surface-container-low/90 rounded-[22px] p-3.5 flex flex-col justify-between h-[116px] border border-outline-variant/50 shadow-md cursor-pointer">
          <div class="flex items-center justify-between">
            <span class="font-headline-sm text-headline-sm text-on-surface font-medium">睡眠定时</span>
            <span class="font-label-md text-label-md text-primary font-mono tabular-nums font-semibold px-2 py-0.5 rounded-full bg-primary/10 border border-primary/20" id="mSleepVal">关闭</span>
          </div>
          <div class="flex items-baseline justify-between pt-1 border-t border-white/5">
            <span class="font-label-xs text-label-xs text-secondary/80">到点停止</span>
            <span class="font-label-xs text-label-xs text-secondary/60 font-mono tracking-wider">15 · 30 · 60</span>
          </div>
        </div>
      </section>

      <!-- Recent Acoustic Dialog Log (Tactile Echo Cards) -->
      <section class="bg-surface-container-low/90 rounded-[22px] p-4 flex flex-col gap-3 shadow-md border border-outline-variant/50 relative">
        <div class="flex items-center justify-between pb-0.5">
          <div class="flex items-center gap-2 text-secondary">
            <span class="material-symbols-outlined text-primary text-[18px]">&#xE0BF;</span>
            <span class="font-label-xs text-label-xs tracking-wider uppercase font-mono text-on-surface-variant">最近</span>
          </div>
          <a href="chat.html" class="font-label-xs text-label-xs text-primary font-mono no-underline" id="mLastTime">对话 &rarr;</a>
        </div>
        <div class="flex flex-col gap-2.5">
          <div class="self-end max-w-[85%] bg-surface-container rounded-2xl rounded-tr-sm px-3.5 py-2 text-secondary/80 text-body-sm shadow-sm border border-white/5" id="mLastUser">还没有对话</div>
          <div class="self-start max-w-[92%] bg-surface-container-lowest rounded-2xl rounded-tl-sm p-3 flex flex-col gap-2 shadow-inner border border-outline-variant/30">
            <div class="flex items-start gap-2.5">
              <div class="w-2 h-2 rounded-full bg-primary mt-1.5 shrink-0 shadow-[0_0_6px_rgba(253,211,133,0.8)]"></div>
              <p class="text-body-sm text-on-surface leading-relaxed" id="mLastAi">在。</p>
            </div>
            <div class="flex items-center justify-between pt-1 border-t border-white/5">
              <span class="font-label-xs text-label-xs text-secondary/70 font-mono" id="mLastMeta">小讯</span>
              <button id="mBtnReplay" class="flex items-center gap-1 px-2.5 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-primary font-label-xs text-label-xs shadow-[0_1px_3px_rgba(0,0,0,0.7),inset_0_1px_1px_rgba(253,211,133,0.2)] active:scale-95 transition-all">
                <span class="material-symbols-outlined text-[14px]">&#xE042;</span>
                <span>播报响应</span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- High-Frequency Thumb Action Zone: Quick Instruction Chips & Input -->
      <section class="flex flex-col gap-2.5 w-full">
        <div class="flex items-center gap-2 overflow-x-auto pb-1 select-none">
          <button onclick="R1.sendCommand('把主音量调到 50')" class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-high/80 border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary shrink-0 active:scale-95 transition-all shadow-[0_1px_3px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
            <span class="material-symbols-outlined text-[14px] text-primary">&#xE049;</span>
            <span class="font-label-xs text-label-xs text-on-surface">音量 50</span>
          </button>
          <button onclick="R1.sendCommand('现在几点了')" class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-high/80 border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary shrink-0 active:scale-95 transition-all shadow-[0_1px_3px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
            <span class="material-symbols-outlined text-[14px] text-primary">&#xE425;</span>
            <span class="font-label-xs text-label-xs text-on-surface">几点了</span>
          </button>
          <button onclick="R1.sendCommand('系统体检')" class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-high/80 border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary shrink-0 active:scale-95 transition-all shadow-[0_1px_3px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
            <span class="material-symbols-outlined text-[14px] text-primary">&#xE88E;</span>
            <span class="font-label-xs text-label-xs text-on-surface">系统体检</span>
          </button>
          <button onclick="R1.setLight('wakeup')" class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-high/80 border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary shrink-0 active:scale-95 transition-all shadow-[0_1px_3px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
            <span class="material-symbols-outlined text-[14px] text-primary">&#xE0F0;</span>
            <span class="font-label-xs text-label-xs text-on-surface">开灯</span>
          </button>
          <button onclick="R1.setLight('off')" class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-high/80 border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary shrink-0 active:scale-95 transition-all shadow-[0_1px_3px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
            <span class="material-symbols-outlined text-[14px] text-primary">&#xE0F1;</span>
            <span class="font-label-xs text-label-xs text-on-surface">关灯</span>
          </button>
          <button onclick="R1.setLight('scan')" class="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-high/80 border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary shrink-0 active:scale-95 transition-all shadow-[0_1px_3px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.15)]">
            <span class="material-symbols-outlined text-[14px] text-primary">&#xE3F0;</span>
            <span class="font-label-xs text-label-xs text-on-surface">流光</span>
          </button>
        </div>
        <div class="flex items-center gap-2 flex-wrap px-1">
          <button type="button" id="castDlnaBadge" onclick="R1.toggleCast('dlna')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary font-label-xs text-label-xs font-mono">DLNA</button>
          <button type="button" id="castAirplayBadge" onclick="R1.toggleCast('airplay')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary font-label-xs text-label-xs font-mono">AirPlay</button>
          <button type="button" id="btPairBtn" onclick="R1.pairBluetooth()" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary font-label-xs text-label-xs font-mono">蓝牙配对</button>
        </div>
        <div class="font-label-xs text-label-xs text-secondary/70 font-mono px-1 min-h-[1em]" id="castHint"></div>

        <!-- 底部快捷纯文本输入框 (左侧静态键盘标识 &#xE312;，无任何语音拾音按钮) -->
        <div class="flex items-center gap-2 bg-surface-container-low/90 rounded-full pl-4 pr-1.5 py-1.5 border border-primary/30 shadow-md backdrop-blur-md">
          <span class="material-symbols-outlined text-primary/70 text-[18px] shrink-0">&#xE312;</span>
          <input id="mHomeInput" maxlength="500" type="text" placeholder="说点什么" class="flex-1 bg-transparent text-body-sm text-on-surface placeholder:text-secondary/50 focus:outline-none min-w-0 px-1 font-body-sm">
          <button id="mHomeSend" aria-label="发送指令" class="w-8 h-8 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 hover:border-primary/60 text-primary flex items-center justify-center shrink-0 shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.25)] active:scale-90 transition-all">
            <span class="material-symbols-outlined text-[18px] text-primary">&#xE5D8;</span>
          </button>
        </div>
      </section>

      <!-- Hardware Telemetry Bottom Rail -->
      <footer class="flex items-center justify-between px-3 pt-0.5 font-mono font-label-xs text-label-xs text-secondary/50 tabular-nums">
        <div class="flex items-center gap-1.5">
          <span class="w-1.5 h-1.5 rounded-full bg-tertiary shadow-[0_0_6px_rgba(99,241,180,0.7)]"></span>
          <span id="mFootUptime">UPTIME 00:00:00</span>
        </div>
        <div class="flex items-center gap-3">
          <span id="mFootMem">MEM --</span>
          <span class="text-primary/60" id="mFootDev">TCP :8686</span>
        </div>
      </footer>`;

  const script = `
  <script>
    (function() {
      R1.refreshCast();
      const inp = document.getElementById('mHomeInput');
      const btn = document.getElementById('mHomeSend');
      function send() {
        const v = inp.value.trim();
        if (!v) return;
        inp.value = '';
        R1.sendCommand(v, '移动端控制台');
      }
      btn.addEventListener('click', send);
      inp.addEventListener('keydown', e => { if (e.key === 'Enter') send(); });

      document.getElementById('mHomeSeek').addEventListener('click', function(e) {
        const r = this.getBoundingClientRect();
        R1.seekByRatio((e.clientX - r.left) / r.width);
      });

      document.getElementById('mBtnReplay').addEventListener('click', function() {
        const lastAi = [...R1.getState().chatHistory].reverse().find(x => x.role === 'assistant');
        if (lastAi) {
          R1.speak(lastAi.text);
        } else {
          R1.speak('在。');
        }
      });

      R1.onStateChange(function(st) {
        const tr = R1.getCurrentTrack();
        document.getElementById('mHomeTitle').textContent = tr.title;
        document.getElementById('mHomeFormat').textContent = tr.format;
        document.getElementById('mHomeAlbum').textContent = tr.artist + (tr.album ? (' · ' + tr.album) : '');
        document.getElementById('mHomeSpecText').textContent = tr.spec;
        document.getElementById('mHomeCurTime').textContent = R1.formatTime(st.currentTime);
        document.getElementById('mHomeTotTime').textContent = R1.formatTime(tr.duration);
        const pct = tr.duration > 0 ? ((st.currentTime / tr.duration) * 100).toFixed(1) : 0;
        document.getElementById('mHomeBar').style.width = pct + '%';
        document.getElementById('playIcon').innerHTML = R1.playPauseGlyph(st.isPlaying, 20);
        document.getElementById('mHomeVinyl').classList.toggle('vinyl-paused', !st.isPlaying);
        document.getElementById('mLoopText').textContent = R1.loopName(st.loopMode);

        document.getElementById('volVal').textContent = st.muted ? '静音' : st.volume;
        document.getElementById('volBar').style.width = (st.muted ? 0 : st.volume) + '%';

        document.getElementById('mLanIp').textContent = st.online ? st.ip : (st.hostIp || '点击配置');
        document.getElementById('mLanDot').className = st.online
          ? 'w-2.5 h-2.5 rounded-full bg-tertiary shadow-[0_0_8px_rgba(99,241,180,0.8)]'
          : 'w-2.5 h-2.5 rounded-full bg-outline';

        const btName = !st.online ? '未连接' : (!st.btSupported ? '不可用' : (st.btEnabled ? (st.btName || '已开启') : '已关闭'));
        document.getElementById('mBtDev').textContent = btName;
        document.getElementById('mBtStatus').textContent = !st.btEnabled ? '待命' : (st.btDiscoverable ? '可发现' : '已开启');
        document.getElementById('mBtDot').className = st.btEnabled
          ? 'w-2.5 h-2.5 rounded-full bg-tertiary shadow-[0_0_8px_rgba(99,241,180,0.8)]'
          : 'w-2.5 h-2.5 rounded-full bg-outline';

        document.getElementById('mSleepVal').textContent = st.sleepRemainSec > 0 ? R1.formatTime(st.sleepRemainSec) : '关闭';

        const lastUser = [...st.chatHistory].reverse().find(x => x.role === 'user');
        const lastAi = [...st.chatHistory].reverse().find(x => x.role === 'assistant');
        if (lastUser) {
          document.getElementById('mLastUser').textContent = '“' + lastUser.text + '”';
          document.getElementById('mLastUser').className = 'self-end max-w-[85%] bg-surface-container rounded-2xl rounded-tr-sm px-3.5 py-2 text-on-surface text-body-sm shadow-sm border border-white/5';
        } else {
          document.getElementById('mLastUser').textContent = '还没有对话';
        }
        if (lastAi) {
          document.getElementById('mLastAi').textContent = lastAi.text;
          document.getElementById('mLastMeta').textContent = lastAi.model || '小讯';
        }

        document.getElementById('mFootUptime').textContent = 'UPTIME ' + R1.formatUptime(st.uptimeSec);
        document.getElementById('mFootMem').textContent = st.memUsedMb > 0 ? ('MEM ' + st.memUsedMb + 'M/' + st.memTotalMb + 'M') : 'MEM --';
        document.getElementById('mFootDev').textContent = st.online ? (st.hardware + ' · :8686') : 'TCP :8686';
      });
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 移动端页面 02: 对话 (mobile/chat.html)
// =========================================================
function getMobileChat() {
  const html = `
      <!-- 顶部对话控制条 (无任何语音拾音/唤醒按钮) -->
      <section class="bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-3.5 shadow-md border border-primary/25 flex items-center justify-between">
        <div class="flex items-center gap-2.5">
          <div class="w-9 h-9 rounded-full bg-surface-container-lowest border border-primary/40 flex items-center justify-center text-primary">
            <span class="material-symbols-outlined text-[18px]">&#xE0CA;</span>
          </div>
          <div class="flex flex-col">
            <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">对话</span>
            <span class="font-label-xs text-label-xs text-primary font-mono" id="mChatModel">GLM-4.5-Flash</span>
          </div>
        </div>
        <div class="flex items-center gap-1.5">
          <button onclick="R1.clearChatHistory()" class="px-3 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/30 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">清空</button>
        </div>
      </section>

      <!-- 横向快捷指令 Chips -->
      <div class="flex items-center gap-2 overflow-x-auto pb-1">
        <button onclick="R1.sendCommand('把主音量调到 50')" class="px-3 py-1.5 rounded-full bg-surface-container-high/90 border border-primary/30 text-on-surface font-label-xs text-label-xs font-mono shrink-0">音量 50%</button>
        <button onclick="R1.sendCommand('现在几点了')" class="px-3 py-1.5 rounded-full bg-surface-container-high/90 border border-primary/30 text-on-surface font-label-xs text-label-xs font-mono shrink-0">现在几点了</button>
        <button onclick="R1.sendCommand('暂停播放')" class="px-3 py-1.5 rounded-full bg-surface-container-high/90 border border-primary/30 text-on-surface font-label-xs text-label-xs font-mono shrink-0">暂停播放</button>
        <button onclick="R1.sendCommand('系统体检')" class="px-3 py-1.5 rounded-full bg-surface-container-high/90 border border-primary/30 text-on-surface font-label-xs text-label-xs font-mono shrink-0">系统体检</button>
      </div>

      <!-- 聊天气泡滚动区 -->
      <section id="mChatStream" class="bg-surface-container-low/90 rounded-[22px] p-3.5 flex flex-col gap-3 shadow-md border border-outline-variant/50 min-h-[340px] max-h-[460px] overflow-y-auto"></section>

      <!-- 底部纯文本输入栏 (左侧静态键盘标识 &#xE312;，无任何语音拾音按钮) -->
      <section class="flex items-center gap-2 bg-surface-container-low/95 rounded-full pl-4 pr-1.5 py-1.5 border border-primary/35 shadow-lg">
        <span class="material-symbols-outlined text-primary/70 text-[18px] shrink-0">&#xE312;</span>
        <input id="mChatInput" maxlength="500" type="text" placeholder="说点什么" class="flex-1 bg-transparent text-body-sm text-on-surface placeholder:text-secondary/50 focus:outline-none px-1">
        <button id="mChatSend" class="w-9 h-9 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 hover:border-primary/60 text-primary flex items-center justify-center shrink-0 shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.25)] active:scale-90 transition-all">
          <span class="material-symbols-outlined text-[18px] text-primary">&#xE5D8;</span>
        </button>
      </section>`;

  const script = `
  <script>
    (function() {
      const box = document.getElementById('mChatStream');
      const inp = document.getElementById('mChatInput');
      const btn = document.getElementById('mChatSend');
      function send() {
        const v = inp.value.trim();
        if (!v) return;
        inp.value = '';
        stickBottom = true;
        R1.sendCommand(v, '移动端对话');
      }
      btn.addEventListener('click', send);
      inp.addEventListener('keydown', e => { if (e.key === 'Enter') send(); });

      let lastChatSig = '__init__';
      let stickBottom = true;
      box.addEventListener('scroll', function() {
        stickBottom = (box.scrollHeight - box.scrollTop - box.clientHeight) < 50;
      });
      box.addEventListener('click', function(e) {
        const btn = e.target.closest ? e.target.closest('[data-chat-action]') : null;
        if (!btn) return;
        const idx = parseInt(btn.getAttribute('data-idx'), 10);
        const item = R1.getState().chatHistory[idx];
        if (item && typeof item.text === 'string' && item.text) R1.speak(item.text);
      });

      R1.onStateChange(function(st) {
        const modeTxt = st.config.hasKey ? (st.config.autoSpeak ? '播报' : '静音') : '未配置';
        document.getElementById('mChatModel').textContent = (st.config.llmModel || 'GLM-4.5-Flash') + ' · ' + modeTxt;

        const sig = (st.chatHistory || []).map(function(item) {
          return item.id + '|' + (item.status || '') + '|' + (item.text || '').length;
        }).join(',');
        if (sig === lastChatSig) return;
        lastChatSig = sig;

        if (!st.chatHistory || st.chatHistory.length === 0) {
          box.innerHTML = '<div class="h-full min-h-[300px] py-8 px-4 flex items-center justify-center text-center font-body-sm text-body-sm text-secondary/70">还没有对话</div>';
          return;
        }
        box.innerHTML = st.chatHistory.map(function(item, idx) {
          if (item.role === 'user') {
            return \`
              <div class="self-end max-w-[86%] bg-surface-container rounded-2xl rounded-tr-sm px-3.5 py-2.5 text-on-surface text-body-sm shadow-sm border border-primary/20">
                <div class="font-label-xs text-label-xs text-primary/70 font-mono mb-0.5">\${R1.escapeHtml(item.time)}</div>
                <div>“\${R1.escapeHtml(item.text)}”</div>
              </div>\`;
          }
          return \`
            <div class="self-start max-w-[92%] bg-surface-container-lowest rounded-2xl rounded-tl-sm p-3 flex flex-col gap-2 shadow-inner border border-primary/25">
              <div class="flex items-start gap-2">
                <div class="w-2 h-2 rounded-full bg-primary mt-1.5 shrink-0"></div>
                <p class="text-body-sm text-on-surface leading-relaxed">\${R1.escapeHtml(item.text)}</p>
              </div>
              <div class="flex items-center justify-between pt-1 border-t border-white/5">
                <span class="font-label-xs text-label-xs text-secondary/70 font-mono">\${R1.escapeHtml(item.model)} · \${R1.escapeHtml(item.time)}</span>
                <button data-chat-action="speak" data-idx="\${idx}" class="flex items-center gap-1 px-2.5 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-primary font-label-xs text-label-xs">
                  <span class="material-symbols-outlined text-[13px]">&#xE042;</span>
                  <span>重播</span>
                </button>
              </div>
            </div>\`;
        }).join('');
        if (stickBottom) box.scrollTop = box.scrollHeight;
      });
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 移动端页面 03: 音乐 (mobile/music.html)
// =========================================================
function getMobileMusic() {
  const html = `
      <!-- 音源切换与导入栏 -->
      <div class="flex items-center justify-between gap-2 overflow-x-auto pb-1" id="mMusicSrcTabs">
        <div class="flex items-center gap-1.5">
          <button data-src="all" onclick="R1.setMusicSource('all')" class="m-src px-3 py-1.5 rounded-full bg-primary/20 border border-primary/50 text-primary font-label-xs text-label-xs font-mono shrink-0">全部音源</button>
          <button data-src="local" onclick="R1.setMusicSource('local')" class="m-src px-3 py-1.5 rounded-full bg-surface-container-high border border-white/10 text-secondary font-label-xs text-label-xs font-mono shrink-0">本机</button>
          <button data-src="stream" onclick="R1.setMusicSource('stream')" class="m-src px-3 py-1.5 rounded-full bg-surface-container-high border border-white/10 text-secondary font-label-xs text-label-xs font-mono shrink-0">网络</button>
        </div>
        <button id="mBtnAddStream" class="px-3 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 text-primary font-label-xs text-label-xs font-mono shrink-0">添加链接</button>
      </div>

      <!-- 云音乐（网易云）：登录 + 搜索 + 播放 -->
      <section class="flex flex-col bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-3.5 shadow-xl border border-primary/25 gap-2.5">
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-1.5 min-w-0 flex-wrap">
            <span class="material-symbols-outlined text-primary text-[18px]">&#xE405;</span>
            <span class="font-headline-sm text-headline-sm text-on-surface font-semibold shrink-0">云音乐</span>
            <button id="m-cloudSrcNetease" onclick="R1.setCloudSource('netease')" class="px-2 py-0.5 rounded-full font-label-xs text-label-xs font-mono shrink-0 border transition-all bg-primary/20 border-primary/60 text-primary font-bold">网易云</button>
            <button id="m-cloudSrcQQ" onclick="R1.setCloudSource('qq')" class="px-2 py-0.5 rounded-full font-label-xs text-label-xs font-mono shrink-0 border transition-all bg-surface-container-lowest border-primary/20 text-secondary">QQ</button>
<button id="m-cloudSrcSongloft" onclick="R1.setCloudSource('songloft')" class="px-2 py-0.5 rounded-full font-label-xs text-label-xs font-mono shrink-0 border transition-all bg-surface-container-lowest border-primary/20 text-secondary">本地库</button>
            <span id="mCloudLoginState" class="px-2 py-0.5 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary font-label-xs text-label-xs font-mono shrink-0">未登录</span>
          </div>
          <button onclick="R1.cloudSetCookie()" class="px-3 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95">登录</button>
        </div>

        <div class="flex items-center gap-2">
          <div class="bg-surface-container-lowest/90 rounded-full px-3 py-1.5 flex items-center gap-1.5 border border-primary/30 flex-1 min-w-0">
            <span class="material-symbols-outlined text-primary/70 text-[16px]">&#xE8B6;</span>
            <input id="mCloudSearchInput" type="text" placeholder="歌名 / 歌手" class="bg-transparent border-none text-body-sm text-on-surface placeholder:text-secondary/50 focus:outline-none w-full font-body-sm min-w-0">
          </div>
          <button onclick="R1.cloudSearch()" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-primary/25 via-primary/15 to-surface-container border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold shrink-0 active:scale-95">搜索</button>
        </div>
        <div id="mCloudResults" class="flex flex-col gap-1.5 max-h-56 overflow-y-auto"></div>
      </section>

      <!-- 移动端黑胶唱盘与同步歌词卡片 -->
      <section class="flex flex-col bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[24px] p-4 shadow-xl border border-primary/25 gap-3">
        <div class="flex items-center gap-4">
          <div onclick="R1.togglePlay()" class="relative w-20 h-20 rounded-full overflow-hidden shrink-0 shadow-[0_0_20px_rgba(253,211,133,0.25)] border border-primary/40 cursor-pointer">
            <div id="mMusicVinyl" class="absolute inset-0 rounded-full vinyl-spinning vinyl-paused" style="background:repeating-radial-gradient(circle at 50% 50%, rgba(255,255,255,.055) 0 .5px, rgba(0,0,0,0) .5px 2px),conic-gradient(from 200deg at 50% 50%, rgba(253,211,133,.22), rgba(253,211,133,0) 20%, rgba(255,255,255,.06) 45%, rgba(253,211,133,.16) 70%, rgba(253,211,133,0) 88%),radial-gradient(circle at 50% 50%, #16191d 0 26%, #0a0c0e 27% 100%)">
              <div class="absolute inset-0 m-auto w-[44%] h-[44%] rounded-full" style="background:radial-gradient(circle at 35% 30%, #f7deab, #dfb76c 48%, #8f6d2c 100%)"></div>
              <span class="absolute inset-0 m-auto w-[14%] h-[14%] rounded-full bg-[#0a0c0e]"></span>
            </div>
          </div>
          <div class="flex flex-col min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="font-headline-md text-headline-md text-on-surface font-semibold truncate" id="mMusicTitle">未在播放</span>
              <span class="px-2 py-0.5 rounded-full bg-primary/15 text-primary border border-primary/30 font-label-xs text-label-xs font-mono" id="mMusicFormat">STANDBY</span>
            </div>
            <span class="font-body-sm text-body-sm text-secondary/80 truncate mt-0.5" id="mMusicArtist">未在播放</span>
            <span class="font-label-xs text-label-xs text-primary/80 font-mono mt-1" id="mMusicSpec">--</span>
          </div>
        </div>

        <!-- 同步滚动歌词微窗 -->
        <div id="mMusicLyrics" class="h-28 overflow-y-auto bg-surface-container-lowest/80 rounded-2xl p-3 text-center flex flex-col gap-1.5 border border-white/5 lyrics-box"></div>

        <!-- 进度条 -->
        <div class="flex flex-col gap-1">
          <div id="mMusicSeek" class="w-full h-2 bg-surface-container-lowest rounded-full overflow-hidden p-0.5 border border-primary/20 cursor-pointer">
            <div class="h-full bg-gradient-to-r from-primary-container to-primary rounded-full" id="mMusicBar" style="width: 0%"></div>
          </div>
          <div class="flex justify-between font-label-xs text-label-xs font-mono text-secondary/80 tabular-nums">
            <span id="mMusicCur">00:00</span>
            <span id="mMusicTot">00:00</span>
          </div>
        </div>

        <!-- 播控键 -->
        <div class="flex items-center justify-around pt-1">
          <button onclick="R1.toggleLoopMode()" class="px-2.5 h-9 rounded-full bg-surface-container-high border border-primary/25 text-primary font-label-xs text-label-xs font-mono flex items-center gap-1">
            <span class="material-symbols-outlined text-[16px]">&#xE040;</span>
            <span id="mMusicLoopText">循环</span>
          </button>
          <button onclick="R1.prevTrack()" class="w-10 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-on-surface flex items-center justify-center">
            <span class="material-symbols-outlined text-[20px]">&#xE045;</span>
          </button>
          <button onclick="R1.togglePlay()" class="w-12 h-12 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/50 text-primary shadow-[0_0_12px_rgba(253,211,133,0.3)] flex items-center justify-center">
            <span class="material-symbols-outlined text-[26px] flex items-center justify-center" id="mMusicPlayIcon">&#xE037;</span>
          </button>
          <button onclick="R1.nextTrack()" class="w-10 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-on-surface flex items-center justify-center">
            <span class="material-symbols-outlined text-[20px]">&#xE044;</span>
          </button>
          <button onclick="R1.cycleSleepTimer()" class="px-2.5 h-9 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-primary font-label-xs text-label-xs font-mono" id="mMusicSleepBtn">定时</button>
        </div>
      </section>

      <!-- 曲目点播列表 -->
      <section class="bg-surface-container-low/90 rounded-[22px] p-3.5 flex flex-col gap-2.5 border border-outline-variant/50">
        <div class="flex items-center justify-between">
          <span class="font-headline-sm text-headline-sm text-on-surface font-medium">曲库</span>
          <span class="font-label-xs text-label-xs font-mono text-primary" id="mPlaylistCount">0 首</span>
        </div>
        <div class="flex items-center gap-3 flex-wrap">
          <span class="font-label-xs text-label-xs text-secondary/70 font-mono">点歌音源</span>
          <label class="flex items-center gap-1.5 cursor-pointer font-label-xs text-label-xs text-secondary"><input id="mMpSongloft" type="checkbox" class="w-4 h-4" onchange="R1.toggleMusicProvider('songloft')">本地库</label>
          <label class="flex items-center gap-1.5 cursor-pointer font-label-xs text-label-xs text-secondary"><input id="mMpNetease" type="checkbox" class="w-4 h-4" onchange="R1.toggleMusicProvider('netease')">网易云</label>
          <label class="flex items-center gap-1.5 cursor-pointer font-label-xs text-label-xs text-secondary"><input id="mMpQq" type="checkbox" class="w-4 h-4" onchange="R1.toggleMusicProvider('qq')">QQ</label>
        </div>
        <div id="mMusicList" class="flex flex-col gap-2"></div>
      </section>`;

  const script = `
  <script>
    (function() {
      R1.cloudStatus();
      R1.loadMusicProviders();
      document.getElementById('mMusicSeek').addEventListener('click', function(e) {
        const r = this.getBoundingClientRect();
        R1.seekByRatio((e.clientX - r.left) / r.width);
      });

      document.getElementById('mBtnAddStream').addEventListener('click', function() {
        const url = prompt('音频链接');
        if (!url || !url.trim()) return;
        const title = prompt('曲目名称:', '网络音频流') || '网络音频流';
        R1.addStream(url.trim(), title.trim());
      });

      let lastLyrSig = '';
      let lastMusicListSig = '';
      const mListEl = document.getElementById('mMusicList');
      if (mListEl) {
        mListEl.addEventListener('click', function(e) {
          const btn = e.target.closest ? e.target.closest('[data-remove-stream]') : null;
          if (!btn) return;
          e.stopPropagation();
          const url = btn.getAttribute('data-remove-stream');
          if (url && confirm('确认移除该音频流？')) R1.removeStream(url);
        });
      }
      R1.onStateChange(function(st) {
        const tr = R1.getCurrentTrack();
        document.getElementById('mMusicTitle').textContent = tr.title;
        document.getElementById('mMusicFormat').textContent = tr.format;
        document.getElementById('mMusicArtist').textContent = tr.artist + (tr.album ? (' · ' + tr.album) : '');
        document.getElementById('mMusicSpec').textContent = tr.spec;
        document.getElementById('mMusicCur').textContent = R1.formatTime(st.currentTime);
        document.getElementById('mMusicTot').textContent = R1.formatTime(tr.duration);
        const pct = tr.duration > 0 ? ((st.currentTime / tr.duration) * 100).toFixed(1) : 0;
        document.getElementById('mMusicBar').style.width = pct + '%';
        document.getElementById('mMusicPlayIcon').innerHTML = R1.playPauseGlyph(st.isPlaying, 20);
        document.getElementById('mMusicVinyl').classList.toggle('vinyl-paused', !st.isPlaying);
        document.getElementById('mMusicLoopText').textContent = R1.loopName(st.loopMode);
        document.getElementById('mMusicSleepBtn').textContent = st.sleepRemainSec > 0 ? R1.formatTime(st.sleepRemainSec) : '定时';

        document.querySelectorAll('#mMusicSrcTabs .m-src').forEach(btn => {
          const isAct = btn.getAttribute('data-src') === st.musicSource;
          btn.className = isAct
            ? 'm-src px-3 py-1.5 rounded-full bg-primary/20 border border-primary/50 text-primary font-label-xs text-label-xs font-mono shrink-0'
            : 'm-src px-3 py-1.5 rounded-full bg-surface-container-high border border-white/10 text-secondary font-label-xs text-label-xs font-mono shrink-0';
        });

        // 歌词同步
        const lBox = document.getElementById('mMusicLyrics');
        const sig = tr.id + '|' + (tr.lyrics ? tr.lyrics.length : 0);
        if (lastLyrSig !== sig) {
          lastLyrSig = sig;
          lBox.innerHTML = (tr.lyrics && tr.lyrics.length) ? tr.lyrics.map((l, i) => \`
            <div id="mLyr-\${i}" onclick="R1.seekTo(\${l.t})" class="lyric-item py-0.5 text-body-sm text-secondary cursor-pointer">\${R1.escapeHtml(l.txt)}</div>
          \`).join('') : '<div class="h-full flex items-center justify-center text-body-sm text-secondary/60">暂无歌词</div>';
        }
        let act = 0;
        if (tr.lyrics) {
          tr.lyrics.forEach((l, i) => { if (st.currentTime >= l.t) act = i; });
          tr.lyrics.forEach((_, i) => {
            const el = document.getElementById('mLyr-' + i);
            if (el) el.classList.toggle('active', i === act);
          });
        }

        // 曲目列表：只在列表/选中项/音源变化时重建，避免每秒刷新把滚动位置打回顶部
        const listSig = st.musicSource + '|' + st.trackIdx + '|' +
          R1.TRACKS.map(function (t) { return t.path || t.url || ''; }).join('|');
        if (listSig !== lastMusicListSig) {
          lastMusicListSig = listSig;
          const filtered = R1.TRACKS.map((t, idx) => ({ t, idx })).filter(x => st.musicSource === 'all' || x.t.source === st.musicSource);
          document.getElementById('mPlaylistCount').textContent = filtered.length + ' 首';
          const mList = document.getElementById('mMusicList');
          const keepScroll = mList.scrollTop;
          if (filtered.length === 0) {
            mList.innerHTML = '<div class="p-4 rounded-2xl bg-surface-container-lowest/60 border border-white/5 text-center font-body-sm text-body-sm text-secondary/70">曲库是空的</div>';
          } else {
            mList.innerHTML = filtered.map(({ t, idx }) => {
              const rm = t.source === 'stream'
                ? \`<button data-remove-stream="\${R1.escapeHtml(t.url)}" class="w-6 h-6 rounded-full bg-surface-container-high text-secondary">×</button>\` : '';
              return \`
                <div onclick="R1.selectTrack(\${idx})" class="flex items-center justify-between p-2.5 rounded-2xl cursor-pointer \${idx === st.trackIdx ? 'bg-primary/15 border border-primary/45' : 'bg-surface-container-lowest/70 border border-white/5'}">
                  <div class="flex items-center gap-2.5 min-w-0">
                    <span class="font-label-xs text-label-xs font-mono text-primary">\${String(idx + 1).padStart(2, '0')}</span>
                    <div class="flex flex-col min-w-0">
                      <span class="font-body-sm text-body-sm \${idx === st.trackIdx ? 'text-primary font-semibold' : 'text-on-surface'} truncate">\${R1.escapeHtml(t.subTitle)}</span>
                      <span class="font-label-xs text-label-xs text-secondary/70 truncate">\${R1.escapeHtml(t.artist)} · \${R1.escapeHtml(t.spec)}</span>
                    </div>
                  </div>
                  <div class="flex items-center gap-1.5 shrink-0">
                    <span class="px-2 py-0.5 rounded-full bg-surface-container-high border border-primary/25 font-label-xs text-label-xs font-mono text-primary">\${R1.escapeHtml(t.format)}</span>
                    \${rm}
                  </div>
                </div>
              \`;
            }).join('');
            mList.scrollTop = keepScroll;
          }
        }
      });
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 移动端页面 04: 设置 (mobile/settings.html)
// =========================================================
function getMobileSettings() {
  const html = `
      <!-- 面板与配置文件二选一：同一份配置的两种改法，避免两套改动互相覆盖 -->
      <section class="bg-surface-container-low/90 rounded-[22px] p-3 flex items-center gap-2 border border-primary/25">
        <span class="font-label-xs text-label-xs text-secondary/70 font-mono">改配置</span>
        <button type="button" id="mCfgModeForm" class="m-cfg-mode active px-4 py-1.5 rounded-full border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold">面板</button>
        <button type="button" id="mCfgModeFile" class="m-cfg-mode px-4 py-1.5 rounded-full border border-primary/20 text-secondary font-label-xs text-label-xs font-mono">配置文件</button>
        <span class="font-label-xs text-label-xs text-secondary/60 font-mono ml-auto">二选一</span>
      </section>

      <!-- AI 大模型设置卡片 (按钮全部采用黑金样式) -->
      <section data-mcfg="form" class="bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-4 shadow-md border border-primary/25 flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">模型</span>
          <span id="mLlmBadge" class="px-2.5 py-0.5 rounded-full bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono">待配密钥</span>
        </div>
        <input id="mCfgBaseUrl" type="text" placeholder="接口地址" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <input id="mCfgModel" type="text" placeholder="模型" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <select id="mCfgReasoning" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
          <option value="auto">思考：自动</option>
          <option value="none">思考：关闭</option>
          <option value="low">思考：低</option>
          <option value="medium">思考：中</option>
          <option value="high">思考：高</option>
        </select>
        <input id="mCfgKey" type="password" placeholder="密钥" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <textarea id="mCfgPrompt" rows="2" placeholder="人设" class="bg-surface-container-lowest rounded-2xl p-3 border border-primary/25 text-body-sm text-on-surface focus:outline-none"></textarea>
        <button id="mBtnTestLlm" class="btn-gold btn-gold-block">测试连接</button>
      </section>

      <!-- TTS 语音合成与试听卡片 -->
      <section data-mcfg="form" class="bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-4 shadow-md border border-primary/25 flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">语音</span>
          <button id="mBtnTestTts" class="btn-gold">试听</button>
        </div>
        <select id="mCfgTtsEngine" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
          <option value="native">原厂语音</option>
          <option value="openai">自建语音</option>
        </select>
        <div class="flex flex-col gap-2 pt-2 border-t border-white/5">
          <span class="font-label-xs text-label-xs text-secondary/70 font-mono">识别</span>
          <select id="mCfgAsrEngine" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
             <option value="native">原厂识别</option>
             <option value="self">自建识别</option>
           </select>
           <div id="mAsrCustom" class="flex items-center gap-2" style="display:none">
             <input id="mCfgAsrUrl" type="text" placeholder="网关地址 http://192.168.1.100:8720" class="flex-1 bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
              <input id="mCfgAsrToken" type="password" placeholder="网关令牌" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
             <button type="button" id="mBtnTestAsr" class="btn-gold shrink-0">用量</button>
           </div>
         </div>
         <div id="mTtsCustom" class="flex flex-col gap-3" style="display:none">
         <input id="mCfgVoice" type="text" list="mTtsVoicePresets" placeholder="音色" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <datalist id="mTtsVoicePresets">
          <option value="zh-CN-XiaoxiaoNeural"></option>
          <option value="zh-CN-YunxiNeural"></option>
          <option value="alloy"></option>
          <option value="nova"></option>
        </datalist>
        <input id="mCfgTtsModel" type="text" placeholder="语音模型" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <input id="mCfgTtsUrl" type="text" placeholder="语音地址" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <input id="mCfgTtsKey" type="password" placeholder="语音密钥" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
         </div>
         <div class="grid grid-cols-2 gap-2">
          <input id="mCfgTtsSpeed" type="number" step="0.1" min="0.5" max="2" placeholder="语速" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
          <label class="flex items-center justify-center gap-2 px-3 py-2 rounded-2xl bg-surface-container-lowest border border-primary/25 cursor-pointer">
            <input id="mCfgAutoSpeak" type="checkbox" class="w-4 h-4">
            <span class="font-label-xs text-label-xs text-secondary">自动播报</span>
          </label>
        </div>
        <label class="flex items-center justify-between gap-2 px-3 py-2 rounded-2xl bg-surface-container-lowest border border-primary/25 cursor-pointer">
          <span class="font-label-xs text-label-xs text-secondary">连续对话</span>
          <input id="mCfgContinuous" type="checkbox" class="w-4 h-4">
        </label>
        <label class="flex items-center justify-between gap-2 px-3 py-2 rounded-2xl bg-surface-container-lowest border border-primary/25 cursor-pointer">
          <span class="font-label-xs text-label-xs text-secondary">提前判停（说完 1.2 秒就收尾）</span>
          <input id="mCfgEarlyEndpoint" type="checkbox" class="w-4 h-4">
        </label>

      </section>

      <!-- 北京时区闹钟与睡眠定时卡片 -->
      <section data-mcfg="form" class="bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-4 shadow-md border border-primary/25 flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">闹钟</span>
          <span class="font-label-xs text-label-xs font-mono text-primary tabular-nums" id="mSleepLabel">未开启</span>
        </div>
        <div class="flex items-center gap-1.5">
          <button onclick="R1.setSleepTimer(15)" class="flex-1 py-1.5 rounded-full bg-surface-container-lowest border border-primary/25 text-primary font-label-xs text-label-xs font-mono">15分</button>
          <button onclick="R1.setSleepTimer(30)" class="flex-1 py-1.5 rounded-full bg-surface-container-lowest border border-primary/25 text-primary font-label-xs text-label-xs font-mono">30分</button>
          <button onclick="R1.setSleepTimer(60)" class="flex-1 py-1.5 rounded-full bg-surface-container-lowest border border-primary/25 text-primary font-label-xs text-label-xs font-mono">60分</button>
          <button onclick="R1.setSleepTimer(0)" class="flex-1 py-1.5 rounded-full bg-surface-container-lowest border border-white/10 text-secondary font-label-xs text-label-xs font-mono">关闭</button>
        </div>
        <div class="flex items-center gap-2">
          <input id="mNewAlarmTime" type="time" value="08:00" class="bg-surface-container-lowest rounded-2xl px-2.5 py-1.5 border border-primary/30 text-body-sm text-primary font-mono focus:outline-none">
          <input id="mNewAlarmLabel" type="text" placeholder="闹钟备注" class="flex-1 bg-surface-container-lowest rounded-2xl px-2.5 py-1.5 border border-primary/30 text-body-sm text-on-surface focus:outline-none min-w-0">
          <select id="mNewAlarmAction" class="bg-surface-container-lowest rounded-2xl px-2 py-1.5 border border-primary/30 text-body-sm text-primary font-mono focus:outline-none shrink-0">
            <option value="speak">提醒</option>
            <option value="weather">天气</option>
          </select>
          <button id="mBtnAddAlarm" class="px-3 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 text-primary font-label-xs text-label-xs font-mono shrink-0">添加</button>
        </div>
        <div id="mAlarmList" class="flex flex-col gap-2"></div>
      </section>

      <!-- 智能家居与天气卡片 -->
      <section data-mcfg="form" class="bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-4 shadow-md border border-primary/25 flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">家居与天气</span>
          <div class="flex items-center gap-2">
            <button type="button" id="mBtnTestWeatherQw" class="btn-gold">测和风</button>
            <button type="button" id="mBtnTestWeatherWttr" class="btn-gold">测通用</button>
          </div>
        </div>
        <input id="mCfgHaUrl" type="text" placeholder="家居地址" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <input id="mCfgHaToken" type="password" placeholder="家居令牌" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <div class="grid grid-cols-2 gap-2">
          <input id="mCfgWeatherCity" type="text" placeholder="城市" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
          <input id="mCfgWeatherHost" type="text" placeholder="接口域名" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        </div>
        <input id="mCfgWeatherJwtIss" type="text" placeholder="开发者 ID" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <input id="mCfgWeatherJwtProjectId" type="text" placeholder="项目 ID" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <input id="mCfgWeatherJwtKeyId" type="text" placeholder="凭据 ID" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
        <textarea id="mCfgWeatherJwtKey" rows="3" placeholder="私钥" class="bg-surface-container-lowest rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none"></textarea>

      </section>

      <!-- 移动端运维中枢与应用管理卡片 -->
      <section data-mcfg="form" class="bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-4 shadow-md border border-primary/25 flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-primary text-[18px]">&#xE32D;</span>
            <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">应用</span>
          </div>
        </div>
        <div class="flex items-center gap-1.5">
          <button onclick="mDoSystemClean()" class="flex-1 py-1.5 rounded-full bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono active:scale-95">深度清理</button>
          <button onclick="mDoSystemReboot()" class="flex-1 py-1.5 rounded-full bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono active:scale-95">重启音箱</button>
          <button onclick="mLoadAppsList()" class="flex-1 py-1.5 rounded-full bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono active:scale-95">刷新应用</button>
        </div>
        <input type="file" id="mApkFileInput" accept=".apk" class="hidden">
        <button onclick="document.getElementById('mApkFileInput').click()" class="w-full py-2.5 rounded-2xl border border-dashed border-primary/35 hover:border-primary/60 bg-surface-container-lowest/80 text-primary font-label-xs text-label-xs font-mono flex items-center justify-center gap-2 active:scale-95">
          <span class="material-symbols-outlined text-primary text-[18px]">&#xE5D8;</span>
          <span>安装应用</span>
        </button>
        <div id="mApkUploadProgress" style="display:none" class="flex-col gap-1 p-2 rounded-xl bg-surface-container-lowest border border-primary/30">
          <span id="mApkProgressText" class="font-mono text-[10px] text-primary">正在流式上传...</span>
          <div class="w-full h-1.5 rounded-full bg-surface-container-high overflow-hidden">
            <div id="mApkProgressBar" class="h-full bg-primary rounded-full transition-all duration-200" style="width: 0%;"></div>
          </div>
        </div>
        <div class="flex items-center justify-between text-[11px] font-mono text-secondary/70">
          <span>已安装</span>
          <span id="mAppsCountBadge">--</span>
        </div>
        <div id="mInstalledAppsBox" class="flex flex-col gap-1.5 max-h-64 overflow-y-auto pr-1"></div>
      </section>

      <!-- 实时日志与系统维护 -->
      <section data-mcfg="form" class="bg-surface-container-low/90 rounded-[22px] p-4 shadow-md border border-outline-variant/50 flex flex-col gap-2.5">
        <div class="flex items-center justify-between">
          <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">日志</span>
          <div class="flex items-center gap-2">
            <button id="mBtnSpeakerHost" class="font-label-xs text-label-xs font-mono text-primary">音箱地址</button>
            <button id="mBtnAccessToken" class="font-label-xs text-label-xs font-mono text-primary">访问令牌</button>
            <button id="mBtnRefreshLogs" class="font-label-xs text-label-xs font-mono text-primary">刷新</button>
             <button id="mBtnClearCache" class="font-label-xs text-label-xs font-mono text-error">清除记录</button>
          </div>
        </div>
        <div id="mLogBox" class="log-terminal rounded-2xl p-2.5 border border-primary/20 h-36 overflow-y-auto font-mono text-[10px] flex flex-col gap-1"></div>
      </section>

      <!-- 直接编辑配置文件：与面板二选一，避免两套未保存改动互相覆盖 -->
      <section data-mcfg="file" style="display:none" class="bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[22px] p-4 shadow-md border border-primary/25 flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">配置文件</span>
          <span class="font-label-xs text-label-xs text-secondary/60 font-mono">与面板二选一 · 改完点右下对勾</span>
        </div>
        <textarea id="mCfgFileEditor" rows="12" spellcheck="false" class="bg-surface-container-lowest rounded-2xl p-3 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none leading-relaxed"></textarea>
      </section>

      <button type="button" id="mCfgFab" class="cfg-fab" title="查看配置更改并确认写入">
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"></path></svg>
        <span id="mCfgFabDot" class="cfg-fab-dot"></span>
      </button>

      <div id="mCfgReviewModal" class="cfg-review-mask" style="display:none">
        <div class="cfg-review-card">
          <div class="cfg-review-head">
            <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">待写入的配置更改</span>
            <button type="button" id="mCfgReviewClose" class="cfg-review-x" title="关闭" aria-label="关闭">×</button>
          </div>
          <div id="mCfgReviewSub" class="cfg-review-sub">以下改动尚未写入音箱，确认后才会生效</div>
          <div id="mCfgDiffBox" class="cfg-diff"></div>
          <div class="cfg-review-foot">
            <button type="button" id="mCfgReviewDiscard" class="cfg-bar-ghost">放弃更改</button>
            <button type="button" id="mCfgReviewCancel" class="cfg-bar-ghost">返回</button>
            <button type="button" id="mCfgReviewConfirm" class="btn-gold">确认写入</button>
          </div>
        </div>
      </div>`;

  const script = `
  <script>
    (function() {
      R1.fetchSpeakerLogs();

      // 用 var：applyConfigToForm 定义在它前面、运行时会写它，let 会触发暂时性死区
      var savedSnapshot = null;

      function mobileCfgSig(c) {
        return JSON.stringify([
          c.llmProvider, c.llmModel, c.llmBaseUrl, c.llmSystemPrompt, c.hasKey,
          c.ttsEngine, c.ttsVoice, c.ttsUrl, c.ttsModel, c.autoSpeak, c.continuousChat, c.earlyEndpoint, c.ttsSpeed,
          c.haUrl, c.hasHaToken, c.weatherCity, c.weatherHost, c.hasWeatherKey
        ]);
      }
      let lastCfgSig = mobileCfgSig(R1.getState().config);
      let formDirty = false;
      // 密钥输入框：已保存显示圆点，聚焦清空，失焦未填则恢复圆点
      var M_SECRET_MASK = '********';
      function mIsMasked(v) { if (!v) return false; for (var i = 0; i < v.length; i++) { if (v.charAt(i) !== '*') return false; } return true; }
      function mSecretValue(id) {
        const el = document.getElementById(id);
        if (!el) return '';
        return mIsMasked(el.value.trim()) ? '' : el.value.trim();
      }
      function mApplySecretMask(id, hasSaved) {
        const el = document.getElementById(id);
        if (!el) return;
        el.dataset.saved = hasSaved ? '1' : '0';
        if (hasSaved && !el.value) {
          el.value = '********';
          el.dataset.masked = '1';
        }
        if (!el.dataset.bound) {
          el.dataset.bound = '1';
          el.addEventListener('focus', function () {
            if (el.dataset.masked === '1') { el.value = ''; el.dataset.masked = '0'; }
          });
          el.addEventListener('blur', function () {
            if (!el.value && el.dataset.saved === '1') {
              el.value = '********';
              el.dataset.masked = '1';
            }
          });
        }
      }

      function applyConfigToForm(st) {
        const c = st.config;
        document.getElementById('mCfgBaseUrl').value = c.llmBaseUrl || '';
        document.getElementById('mCfgModel').value = c.llmModel || '';
        document.getElementById('mCfgReasoning').value = c.llmReasoning || 'none';
        document.getElementById('mCfgPrompt').value = c.llmSystemPrompt || '';
        document.getElementById('mCfgTtsEngine').value = c.ttsEngine || 'native';
        mApplyVoiceFields();
        document.getElementById('mCfgAsrEngine').value = c.asrEngine || 'native';
        mApplyVoiceFields();
        document.getElementById('mCfgAsrUrl').value = c.asrUrl || '';
        mApplySecretMask('mCfgAsrToken', !!c.hasAsrToken);
        document.getElementById('mCfgVoice').value = c.ttsVoice || 'zh-CN-XiaoxiaoNeural';
        document.getElementById('mCfgTtsModel').value = c.ttsModel || '';
        document.getElementById('mCfgTtsUrl').value = c.ttsUrl || '';
        document.getElementById('mCfgTtsKey').placeholder = 'TTS 密钥（可选）';
        mApplySecretMask('mCfgTtsKey', !!c.hasTtsKey);
        document.getElementById('mCfgTtsSpeed').value = c.ttsSpeed || 1.0;
        document.getElementById('mCfgAutoSpeak').checked = !!c.autoSpeak;
        document.getElementById('mCfgContinuous').checked = !!c.continuousChat;
        document.getElementById('mCfgEarlyEndpoint').checked = !!c.earlyEndpoint;
        document.getElementById('mCfgHaUrl').value = c.haUrl || '';
        mApplySecretMask('mCfgHaToken', !!c.hasHaToken);
        document.getElementById('mCfgWeatherCity').value = c.weatherCity || '';
        document.getElementById('mCfgWeatherHost').value = c.weatherHost || '';
        document.getElementById('mCfgWeatherJwtIss').value = c.weatherJwtIss || '';
        document.getElementById('mCfgWeatherJwtProjectId').value = c.weatherJwtProjectId || '';
        document.getElementById('mCfgWeatherJwtKeyId').value = c.weatherJwtKeyId || '';
        document.getElementById('mCfgWeatherJwtKey').value = c.weatherJwtKey || '';
                mApplySecretMask('mCfgKey', !!c.hasKey);
        savedSnapshot = JSON.stringify(mCollectPatch());
        mRefreshCfgDirty();
      }

      // 三个模块的字段收成一份补丁，确认时一次性写入，互不覆盖
      function mCollectPatch() {
        const patch = {
          llmProvider: 'openai',
          llmBaseUrl: document.getElementById('mCfgBaseUrl').value.trim(),
          llmModel: document.getElementById('mCfgModel').value.trim(),
          reasoningEffort: document.getElementById('mCfgReasoning').value,
          llmSystemPrompt: document.getElementById('mCfgPrompt').value.trim(),
          ttsEngine: document.getElementById('mCfgTtsEngine').value,
          asrEngine: document.getElementById('mCfgAsrEngine').value,
          asrUrl: document.getElementById('mCfgAsrUrl').value.trim(),
          asrToken: mSecretValue('mCfgAsrToken'),
          ttsVoice: document.getElementById('mCfgVoice').value.trim(),
          ttsModel: document.getElementById('mCfgTtsModel').value.trim(),
          ttsUrl: document.getElementById('mCfgTtsUrl').value.trim(),
          ttsSpeed: parseFloat(document.getElementById('mCfgTtsSpeed').value) || 1.0,
          autoSpeak: document.getElementById('mCfgAutoSpeak').checked,
          continuousChat: document.getElementById('mCfgContinuous').checked,
          earlyEndpoint: document.getElementById('mCfgEarlyEndpoint').checked,
          haUrl: document.getElementById('mCfgHaUrl').value.trim(),
          weatherCity: document.getElementById('mCfgWeatherCity').value.trim(),
          weatherHost: document.getElementById('mCfgWeatherHost').value.trim(),
          weatherAuth: 'jwt',
          weatherJwtIss: document.getElementById('mCfgWeatherJwtIss').value.trim(),
          weatherJwtProjectId: document.getElementById('mCfgWeatherJwtProjectId').value.trim(),
          weatherJwtKeyId: document.getElementById('mCfgWeatherJwtKeyId').value.trim(),
          weatherJwtKey: document.getElementById('mCfgWeatherJwtKey').value.trim()
        };
        const k = mSecretValue('mCfgKey');
        if (k) patch.llmApiKey = k;
        const tk = mSecretValue('mCfgTtsKey');
        if (tk) patch.ttsKey = tk;
        const tok = mSecretValue('mCfgHaToken');
        if (tok) patch.haToken = tok;
        return patch;
      }

      function mLoadConfigFile() {
        const box = document.getElementById('mCfgFileEditor');
        if (!box || box.dataset.dirty === '1') return;
        R1.request('GET', '/api/v1/config/export', null, 10000)
          .then(function (j) {
            if (box.dataset.dirty === '1') return;
            box.value = JSON.stringify(j, null, 2);
            box.dataset.saved = box.value;
          })
          .catch(function () {});
      }

      // 面板与配置文件二选一：切换只改可见区块，未保存内容仍留在 DOM 里
      function mSetCfgMode(mode) {
        var isForm = (mode === 'form');
        document.querySelectorAll('[data-mcfg]').forEach(function (sec) {
          sec.style.display = (sec.getAttribute('data-mcfg') === mode) ? '' : 'none';
        });
        var on = 'm-cfg-mode active px-4 py-1.5 rounded-full border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold';
        var off = 'm-cfg-mode px-4 py-1.5 rounded-full border border-primary/20 text-secondary font-label-xs text-label-xs font-mono';
        document.getElementById('mCfgModeForm').className = isForm ? on : off;
        document.getElementById('mCfgModeFile').className = isForm ? off : on;
        if (!isForm) mLoadConfigFile();
      }
      document.getElementById('mCfgModeForm').addEventListener('click', function () { mSetCfgMode('form'); });
      document.getElementById('mCfgModeFile').addEventListener('click', function () { mSetCfgMode('file'); });

      function isFileDirty() {
        var fileBox = document.getElementById('mCfgFileEditor');
        return !!(fileBox && fileBox.dataset.saved !== undefined && fileBox.value !== fileBox.dataset.saved);
      }
      function isFormChanged() {
        return savedSnapshot !== null && JSON.stringify(mCollectPatch()) !== savedSnapshot;
      }
      function mRefreshCfgDirty() {
        var fab = document.getElementById('mCfgFab');
        if (!fab) return;
        var fileDirty = isFileDirty();
        if (fileDirty || isFormChanged()) {
          fab.classList.add('show');
          document.getElementById('mCfgReviewSub').textContent = fileDirty
            ? '配置文件有未保存的更改 · 确认后整份写入音箱（密钥不展示）'
            : '以下字段尚未写入音箱 · 确认后才会生效';
        } else {
          fab.classList.remove('show');
          document.getElementById('mCfgReviewModal').style.display = 'none';
        }
      }

      var mSecretKeys = ['llmApiKey', 'asrToken', 'ttsKey', 'haToken', 'weatherJwtKey'];
      var mFieldLabels = {
        llmProvider: '协议', llmModel: '模型', llmBaseUrl: '接口地址', reasoningEffort: '思考',
        llmSystemPrompt: '人设', llmApiKey: '密钥', ttsEngine: '语音合成', ttsVoice: '音色',
        ttsModel: '语音模型', ttsUrl: '语音地址', ttsKey: '语音密钥', ttsSpeed: '语速',
        asrEngine: '语音识别', asrUrl: '识别地址', asrToken: '网关令牌', autoSpeak: '自动播报',
        continuousChat: '连续对话', earlyEndpoint: '提前判停', haUrl: '家居地址', haToken: '家居令牌',
        weatherCity: '城市', weatherHost: '天气域名', weatherAuth: '天气鉴权', weatherJwtIss: '开发者 ID',
        weatherJwtProjectId: '项目 ID', weatherJwtKeyId: '凭据 ID', weatherJwtKey: '天气私钥'
      };
      function mEsc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
      function mFmtVal(k, v) {
        if (v === undefined || v === null || v === '') return '""';
        if (mSecretKeys.indexOf(k) >= 0) return '"••••••"';
        return typeof v === 'string' ? '"' + v + '"' : String(v);
      }
      function mDiffLine(cls, text) {
        var span = document.createElement('span');
        span.className = 'cfg-diff-line ' + cls;
        span.textContent = text;
        return span.outerHTML;
      }
      function mBuildFormDiff() {
        var before = {};
        try { before = JSON.parse(savedSnapshot || '{}'); } catch (e) { before = {}; }
        var after = mCollectPatch();
        var keys = Object.keys(after);
        Object.keys(before).forEach(function (k) { if (keys.indexOf(k) < 0) keys.push(k); });
        var out = [
          mDiffLine('cfg-diff-file', '--- 当前配置（已保存）'),
          mDiffLine('cfg-diff-file', '+++ 待写入配置'),
          mDiffLine('cfg-diff-hunk', '@@ 改动的字段 @@')
        ];
        var changed = 0;
        keys.forEach(function (k) {
          if (JSON.stringify(before[k]) === JSON.stringify(after[k])) return;
          changed++;
          var label = mFieldLabels[k] || k;
          out.push(mDiffLine('cfg-diff-del', '- ' + label + ' (' + k + '): ' + mFmtVal(k, before[k])));
          out.push(mDiffLine('cfg-diff-add', '+ ' + label + ' (' + k + '): ' + mFmtVal(k, after[k])));
        });
        if (!changed) out.push(mDiffLine('cfg-diff-meta', '（没有检测到字段变化）'));
        return out.join('');
      }
      function mMaskSecrets(text) {
        try {
          var obj = JSON.parse(text);
          (function walk(o) {
            if (!o || typeof o !== 'object') return;
            Object.keys(o).forEach(function (k) {
              if (mSecretKeys.indexOf(k) >= 0 && o[k]) o[k] = '••••••';
              else walk(o[k]);
            });
          })(obj);
          return JSON.stringify(obj, null, 2);
        } catch (e) { return text; }
      }
      function mBuildFileDiff() {
        var fileBox = document.getElementById('mCfgFileEditor');
        var a = mMaskSecrets(fileBox.dataset.saved || '').split(String.fromCharCode(10));
        var b = mMaskSecrets(fileBox.value).split(String.fromCharCode(10));
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
        while (i < n) { ops.push(['-', a[i++]]); }
        while (j < m) { ops.push(['+', b[j++]]); }
        var out = [
          mDiffLine('cfg-diff-file', '--- config.json（当前）'),
          mDiffLine('cfg-diff-file', '+++ config.json（待写入）'),
          mDiffLine('cfg-diff-hunk', '@@ -1,' + n + ' +1,' + m + ' @@')
        ];
        ops.forEach(function (op) {
          var cls = op[0] === '+' ? 'cfg-diff-add' : (op[0] === '-' ? 'cfg-diff-del' : 'cfg-diff-ctx');
          out.push(mDiffLine(cls, op[0] + ' ' + op[1]));
        });
        return out.join('');
      }
      function mBuildCfgDiff() { return isFileDirty() ? mBuildFileDiff() : mBuildFormDiff(); }

      function mSaveChanges(btn) {
        var fileBox = document.getElementById('mCfgFileEditor');
        btn.disabled = true;
        var job;
        if (isFileDirty()) {
          var parsed;
          try { parsed = JSON.parse(fileBox.value); }
          catch (e) { R1.toast('配置文件不是合法 JSON', 'err'); btn.disabled = false; return; }
          job = R1.request('POST', '/api/v1/config/import', parsed, 15000).then(function () {
            fileBox.dataset.dirty = '0';
            fileBox.dataset.saved = fileBox.value;
            R1.toast('配置文件已写入音箱', 'ok');
            return R1.syncFromSpeaker(true);
          });
        } else {
          savedSnapshot = null;
          job = R1.updateConfig(mCollectPatch()).then(function () {
            ['mCfgKey', 'mCfgTtsKey', 'mCfgHaToken', 'mCfgAsrToken'].forEach(function (id) { mApplySecretMask(id, true); });
          });
        }
        Promise.resolve(job).catch(function (e) {
          R1.toast('保存失败：' + e.message, 'err');
        }).then(function () { btn.disabled = false; mRefreshCfgDirty(); });
      }

      function mCloseReview() { document.getElementById('mCfgReviewModal').style.display = 'none'; }
      document.getElementById('mCfgFab').addEventListener('click', function () {
        document.getElementById('mCfgDiffBox').innerHTML = mBuildCfgDiff();
        document.getElementById('mCfgReviewModal').style.display = 'flex';
      });
      document.getElementById('mCfgReviewClose').addEventListener('click', mCloseReview);
      document.getElementById('mCfgReviewCancel').addEventListener('click', mCloseReview);
      document.getElementById('mCfgReviewModal').addEventListener('click', function (e) {
        if (e.target === this) mCloseReview();
      });
      document.getElementById('mCfgReviewDiscard').addEventListener('click', function () {
        var fileBox = document.getElementById('mCfgFileEditor');
        if (isFileDirty()) {
          fileBox.value = fileBox.dataset.saved || '';
          fileBox.dataset.dirty = '0';
        }
        formDirty = false;
        applyConfigToForm(R1.getState());
        mCloseReview();
        mRefreshCfgDirty();
      });
      document.getElementById('mCfgReviewConfirm').addEventListener('click', function () {
        mCloseReview();
        mSaveChanges(this);
      });
      
       function mApplyVoiceFields() {
         const tts = document.getElementById('mCfgTtsEngine').value;
         const asr = document.getElementById('mCfgAsrEngine').value;
         document.getElementById('mTtsCustom').style.display = tts === 'native' ? 'none' : '';
         document.getElementById('mAsrCustom').style.display = asr === 'self' ? '' : 'none';
       }
       document.getElementById('mCfgTtsEngine').addEventListener('change', mApplyVoiceFields);
       document.getElementById('mCfgAsrEngine').addEventListener('change', mApplyVoiceFields);
       document.getElementById('mBtnSpeakerHost').addEventListener('click', function() {
         if (location.port === '8686') { R1.toast('当前就开在这台音箱上'); return; }
         R1.promptSpeakerHost();
       });
       document.getElementById('mBtnAccessToken').addEventListener('click', function() { R1.promptToken(); });
       document.getElementById('mBtnRefreshLogs').addEventListener('click', function() { R1.fetchSpeakerLogs(); });
       document.getElementById('mBtnClearCache').addEventListener('click', function() {
         if (confirm('只清除这台浏览器里的对话和音频链接，音箱上的配置不动。继续？')) R1.resetFactory();
       });

      applyConfigToForm(R1.getState());
      mLoadConfigFile();
      [
        'mCfgBaseUrl', 'mCfgModel', 'mCfgKey', 'mCfgPrompt',
        'mCfgTtsEngine', 'mCfgAsrEngine', 'mCfgAsrUrl', 'mCfgAsrToken', 'mCfgVoice', 'mCfgTtsModel', 'mCfgTtsUrl', 'mCfgTtsSpeed', 'mCfgAutoSpeak', 'mCfgContinuous', 'mCfgEarlyEndpoint',
        'mCfgHaUrl', 'mCfgHaToken', 'mCfgWeatherCity', 'mCfgWeatherHost',
        'mCfgWeatherJwtIss', 'mCfgWeatherJwtProjectId', 'mCfgWeatherJwtKeyId', 'mCfgWeatherJwtKey'
      ].forEach(function (id) {
        const el = document.getElementById(id);
        if (!el) return;
        el.addEventListener('input', function () { formDirty = true; mRefreshCfgDirty(); });
        el.addEventListener('change', function () { formDirty = true; mRefreshCfgDirty(); });
      });
      const mCfgFileEditor = document.getElementById('mCfgFileEditor');
      if (mCfgFileEditor) {
        mCfgFileEditor.addEventListener('input', function () {
          mCfgFileEditor.dataset.dirty = (mCfgFileEditor.value === (mCfgFileEditor.dataset.saved || '')) ? '0' : '1';
          mRefreshCfgDirty();
        });
      }
      R1.onStateChange(function (st) {
        const sig = mobileCfgSig(st.config);
        if (sig === lastCfgSig) return;
        lastCfgSig = sig;
        if (formDirty && savedSnapshot !== null) return;
        formDirty = false;
        applyConfigToForm(st);
        mLoadConfigFile();
      });

      let logsFetched = false;
      R1.onStateChange(function (st) {
        if (st.online && !logsFetched) {
          logsFetched = true;
          R1.fetchSpeakerLogs();
        }
      });

      document.getElementById('mBtnTestLlm').addEventListener('click', function () {
        const body = {
          baseUrl: document.getElementById('mCfgBaseUrl').value.trim(),
          apiKey: mSecretValue('mCfgKey'),
          model: document.getElementById('mCfgModel').value.trim()
        };
        R1.toast('正在测试连接…');
        R1.request('POST', '/api/v1/llm/test', body, 25000)
          .then(function (res) { R1.toast('连接正常 · ' + res.costMs + 'ms · ' + (res.reply || '').trim(), 'ok'); })
          .catch(function (e) { R1.toast('测试失败：' + e.message, 'err'); });
      });

      // 试听走真实测试接口：原厂语音没有首音耗时，不能显示 undefinedms
      document.getElementById('mBtnTestTts').addEventListener('click', function () {
        R1.toast('正在合成并播放…');
        R1.request('POST', '/api/v1/tts/test', { text: '斐讯 R1 语音合成测试正常。' }, 25000)
          .then(function (res) {
            if (res.engine === 'native') {
              R1.toast('已用原厂语音播报（没有首音耗时）', 'ok');
            } else {
              const ms = (typeof res.firstAudioMs === 'number' && res.firstAudioMs >= 0) ? (res.firstAudioMs + 'ms') : '未测到';
              R1.toast('已播放 · 首音 ' + ms, 'ok');
            }
          })
          .catch(function (e) { R1.toast('试听失败：' + e.message, 'err'); });
      });

      // 自建 ASR 网关健康检查
      const mBtnTestAsr = document.getElementById('mBtnTestAsr');
      if (mBtnTestAsr) {
        mBtnTestAsr.addEventListener('click', function() {
          const url = document.getElementById('mCfgAsrUrl').value.trim();
          const tok = mSecretValue('mCfgAsrToken');
          R1.toast('正在读取网关用量 …');
          R1.request('GET', '/api/v1/asr/health?url=' + encodeURIComponent(url) + '&token=' + encodeURIComponent(tok), null, 15000)
            .then(function(res) {
              if (res.ok) {
                R1.toast('用量 · 账单月 ' + (res.billingMonth || '?') + ' · 重置 ' + (res.resetDate || '?') + (res.hasToken ? '' : ' · 未设令牌'), 'ok');
              } else {
                R1.toast('网关不可用：' + (res.error || ''), 'err');
              }
            })
            .catch(function(e) { R1.toast('网关不可用：' + e.message, 'err'); });
        });
      }

      // 移动端天气测试：分别测「和风」和「通用源」，避免分不清是谁答的
      function mTestWeather(provider) {
        const city = document.getElementById('mCfgWeatherCity').value.trim() || '北京';
        R1.toast('正在测试 ' + (provider === 'qweather' ? '和风' : '通用源') + ' · ' + city + ' ...');
        R1.request('POST', '/api/v1/weather/test', {
          city: city,
          provider: provider,
          weatherHost: document.getElementById('mCfgWeatherHost').value.trim(),
          weatherAuth: 'jwt',
          weatherJwtIss: document.getElementById('mCfgWeatherJwtIss').value.trim(),
          weatherJwtProjectId: document.getElementById('mCfgWeatherJwtProjectId').value.trim(),
          weatherJwtKeyId: document.getElementById('mCfgWeatherJwtKeyId').value.trim(),
          weatherJwtKey: mSecretValue('mCfgWeatherJwtKey')
        }, 30000)
          .then(function(res) {
            if (res.ok) {
              R1.toast((res.provider === 'qweather' ? '和风' : 'wttr') + '：' + (res.report || ''), 'ok');
            } else {
              R1.toast('失败：' + (res.detail || '没有结果'), 'err');
            }
          })
          .catch(function(err) {
            R1.toast('天气测试失败：' + (err.message || err), 'err');
          });
      }
      const mBtnTestWeatherQw = document.getElementById('mBtnTestWeatherQw');
      if (mBtnTestWeatherQw) mBtnTestWeatherQw.addEventListener('click', function() { mTestWeather('qweather'); });
      const mBtnTestWeatherWttr = document.getElementById('mBtnTestWeatherWttr');
      if (mBtnTestWeatherWttr) mBtnTestWeatherWttr.addEventListener('click', function() { mTestWeather('wttr'); });

      // 移动端运维中枢与应用管理
      function mLoadAppsList() {
        const box = document.getElementById('mInstalledAppsBox');
        const badge = document.getElementById('mAppsCountBadge');
        if (!box) return;
        box.innerHTML = '<div class="text-secondary/60 text-center py-4 font-mono text-label-xs">读取应用列表中...</div>';
        R1.fetchApps().then(function(res) {
          const apps = res.apps || [];
          if (badge) badge.textContent = '共 ' + apps.length + ' 个';
          if (!apps.length) {
            box.innerHTML = '<div class="text-secondary/60 text-center py-4 font-mono text-label-xs">没有应用</div>';
            return;
          }
          box.innerHTML = apps.map(function(a) {
            const isSelf = a.packageName === 'com.r1.brain';
            return \`
              <div class="flex items-center justify-between p-2 rounded-xl bg-surface-container-lowest border border-white/5">
                <div class="flex flex-col min-w-0 pr-2">
                  <div class="flex items-center gap-1.5">
                    <span class="text-on-surface font-semibold text-label-sm truncate">\${R1.escapeHtml(a.name)}</span>
                    <span class="px-1.5 py-0.2 rounded text-[9px] font-mono \${a.isSystem ? 'bg-white/5 text-secondary/60' : 'bg-primary/20 text-primary'}">\${a.isSystem ? '系统' : '第三方'}</span>
                    \${isSelf ? '<span class="px-1.5 py-0.2 rounded text-[9px] font-mono bg-amber-500/20 text-amber-300">核心</span>' : ''}
                  </div>
                  <div class="font-mono text-[10px] text-secondary/60 truncate">
                    \${R1.escapeHtml(a.packageName)} · \${R1.escapeHtml(a.sizeMb)}
                  </div>
                </div>
                <div class="flex items-center gap-1 shrink-0">
                  \${a.isRunning && !isSelf ? \`<button onclick="mDoStopApp('\${a.packageName}')" class="px-2 py-0.5 rounded-full bg-surface-container-high text-secondary hover:text-primary font-mono text-[10px]">停</button>\` : ''}
                  \${!isSelf ? \`<button onclick="mDoUninstallApp('\${a.packageName}', '\${R1.escapeHtml(a.name)}')" class="px-2 py-0.5 rounded-full bg-surface-container-lowest border border-error/40 text-error font-mono text-[10px]">删</button>\` : ''}
                </div>
              </div>
            \`;
          }).join('');
        }).catch(function(err) {
          if (badge) badge.textContent = '失败';
          box.innerHTML = '<div class="text-error text-center py-4 font-mono text-label-xs">读取失败: ' + (err.message || err) + '</div>';
        });
      }

      function mHandleApkFile(file) {
        if (!file) return;
        const progressBox = document.getElementById('mApkUploadProgress');
        const progressBar = document.getElementById('mApkProgressBar');
        const progressText = document.getElementById('mApkProgressText');
        if (progressBox) progressBox.style.display = 'flex';
        if (progressBar) progressBar.style.width = '0%';
        if (progressText) progressText.textContent = '准备流式上传 ' + file.name + ' ...';

        R1.uploadApk(file, function(prog) {
          if (progressBar) progressBar.style.width = prog.percent + '%';
          if (progressText) progressText.textContent = '上传 ' + prog.percent + '% (' + (prog.loaded / 1024).toFixed(0) + 'KB)';
        }).then(function(res) {
          if (progressText) progressText.textContent = res.selfUpdate ? '升级成功！服务重启中...' : '安装完成！';
          R1.toast(res.selfUpdate ? '已安装，正在重启' : '已安装', 'ok');
          setTimeout(function() {
            if (progressBox) progressBox.style.display = 'none';
            mLoadAppsList();
          }, 2500);
        }).catch(function(err) {
          if (progressText) progressText.textContent = '安装失败：' + (err.message || err);
          R1.toast('安装失败：' + (err.message || err), 'err');
        });
      }

      const mApkInput = document.getElementById('mApkFileInput');
      if (mApkInput) {
        mApkInput.addEventListener('change', function(e) {
          if (e.target.files && e.target.files[0]) {
            mHandleApkFile(e.target.files[0]);
            e.target.value = '';
          }
        });
      }

      window.mLoadAppsList = mLoadAppsList;
      window.mDoStopApp = function(pkg) {
        R1.stopApp(pkg).then(function() {
          R1.toast('已停止 ' + pkg, 'ok');
          setTimeout(mLoadAppsList, 800);
        }).catch(function(e) { R1.toast(e.message, 'err'); });
      };
      window.mDoUninstallApp = function(pkg, name) {
        if (!confirm('确认彻底卸载应用「' + name + '」吗？')) return;
        R1.toast('正在卸载 ' + name + ' ...');
        R1.uninstallApp(pkg).then(function() {
          R1.toast('已卸载 ' + name, 'ok');
          setTimeout(mLoadAppsList, 1000);
        }).catch(function(e) { R1.toast('卸载失败：' + e.message, 'err'); });
      };
      window.mDoSystemClean = function() {
        R1.toast('正在深度清理...');
        R1.cleanSystem().then(function(r) {
          R1.toast('清理完成，释放 ' + (r.freedMb || '0MB'), 'ok');
          R1.syncFromSpeaker(true);
        }).catch(function(e) { R1.toast(e.message, 'err'); });
      };
      window.mDoSystemReboot = function() {
        if (!confirm('确认立即重启斐讯 R1 音箱硬件？')) return;
        R1.rebootSystem().then(function() {
          R1.toast('音箱系统正在重启...', 'ok');
        }).catch(function(e) { R1.toast(e.message, 'err'); });
      };

      if (R1.getState().online) {
        mLoadAppsList();
      }

      document.getElementById('mBtnAddAlarm').addEventListener('click', () => {
        const tm = document.getElementById('mNewAlarmTime').value || '08:00';
        const lb = document.getElementById('mNewAlarmLabel').value.trim() || '定时闹钟';
        const act = document.getElementById('mNewAlarmAction').value || 'speak';
        R1.addAlarm({ time: tm, label: lb, repeat: '每天', action: act });
        document.getElementById('mNewAlarmLabel').value = '';
      });

      let lastAlarmSig = '__init__';
      R1.onStateChange(function(st) {
        document.getElementById('mLlmBadge').textContent = st.config.hasKey ? '已启用 Key' : '待配密钥';
        document.getElementById('mSleepLabel').textContent = st.sleepRemainSec > 0 ? R1.formatTime(st.sleepRemainSec) : '未开启';
        const alarmSig = JSON.stringify(st.alarms || []);
        if (alarmSig === lastAlarmSig) return;
        lastAlarmSig = alarmSig;
        const alarmBox = document.getElementById('mAlarmList');
        if (!st.alarms || st.alarms.length === 0) {
          alarmBox.innerHTML = '<div class="p-3 rounded-2xl bg-surface-container-lowest/60 border border-white/5 text-center font-body-sm text-body-sm text-secondary/70">没有闹钟</div>';
          return;
        }
        alarmBox.innerHTML = st.alarms.map(a => \`
          <div class="flex items-center justify-between p-2.5 rounded-2xl bg-surface-container-lowest border border-white/5">
            <div>
              <span class="font-mono text-primary font-bold mr-2 tabular-nums">\${R1.escapeHtml(a.time)}</span>
              <span class="text-body-sm text-on-surface">\${R1.escapeHtml(a.label)}</span>
            </div>
            <div class="flex items-center gap-1.5">
              <button onclick="R1.toggleAlarm(\${a.id})" class="px-2.5 py-0.5 rounded-full font-label-xs text-label-xs font-mono \${a.enabled ? 'bg-primary/20 text-primary border border-primary/40' : 'bg-surface-container text-secondary'}">\${a.enabled ? '启用' : '停用'}</button>
              <button onclick="R1.deleteAlarm(\${a.id})" class="w-6 h-6 rounded-full bg-surface-container-high text-secondary">×</button>
            </div>
          </div>
        \`).join('');
      });

      let lastLogSig = '';
      let logStick = true;
      const logBox = document.getElementById('mLogBox');
      if (logBox) {
        logBox.addEventListener('scroll', function() {
          logStick = (logBox.scrollHeight - logBox.scrollTop - logBox.clientHeight) < 30;
        });
      }
      function renderLogs() {
        const b = document.getElementById('mLogBox');
        const list = R1.getLogs();
        const sig = (list || []).map(function (l) { return (l.time || '') + '|' + (l.source || '') + '|' + (l.msg || '').length; }).join(';');
        if (sig === lastLogSig) return;
        lastLogSig = sig;
        b.innerHTML = list.map(l => \`<div class="text-secondary/90"><span class="text-primary">[\${R1.escapeHtml(l.tag)}]</span> \${R1.escapeHtml(l.msg)}</div>\`).join('');
        if (logStick) b.scrollTop = b.scrollHeight;
      }
      renderLogs();
      R1.onLog(renderLogs);
      setInterval(function () {
        if (document.visibilityState !== 'hidden') R1.fetchSpeakerLogs();
      }, 15000);
    })();
  </script>`;

  return { html, script };
}

module.exports = {
  home: getMobileHome,
  chat: getMobileChat,
  music: getMobileMusic,
  settings: getMobileSettings
};
