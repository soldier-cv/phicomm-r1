/**
 * 桌面端 4 个独立页面的主体内容模板 (desktop-pages.js)
 * 正式打包生产版：
 *  - 所有按钮统一采用黑金浮雕触觉样式 (bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 text-primary)
 *  - 严禁提供任何网页端语音拾音按钮或语音唤醒模拟输入，所有输入栏均为纯文本指令输入栏
 *  - 杜绝任何直角方框 (.rounded)，统一采用 rounded-full / rounded-2xl 圆润曲线
 *  - 零预置演示数据与零虚假测试曲目，所有状态对接 R1 真实 API (/api/v1/*)，时间统一采用北京时间 (UTC+8)
 */

// =========================================================
// 页面 01: 首页 (desktop/home.html)
// =========================================================
function getDesktopHome() {
  const html = `
          <!-- SECTION 1: TOP COMMAND TACTILE INSET DOCK -->
          <section class="w-full bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-5 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md flex flex-col gap-4">
            <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/50 to-transparent pointer-events-none"></div>
            <!-- Pure Text Command Input Bar with Perfectly Aligned Circular Send Button -->
            <div class="bg-surface-container-lowest/90 rounded-full pl-5 pr-1.5 py-1.5 flex items-center gap-3 shadow-inner border border-primary/30 backdrop-blur-md">
              <span class="material-symbols-outlined text-primary/70 text-[20px]">&#xE312;</span>
              <input class="flex-1 bg-transparent border-none text-body-md text-on-surface placeholder:text-secondary/50 focus:outline-none py-1.5 font-body-md" id="cmdInput" maxlength="500" placeholder="说点什么" type="text">
              <div class="flex items-center gap-2 pr-1">
                <button onclick="document.getElementById('cmdInput').value=''" class="hidden sm:inline font-label-xs text-label-xs text-secondary/60 hover:text-primary px-2.5 py-1 rounded-full bg-surface-container border border-outline-variant/30 uppercase font-mono transition-colors">ESC 清空</button>
                <button aria-label="发送指令" class="w-10 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary flex items-center justify-center shrink-0 shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)] hover:shadow-[0_0_12px_rgba(253,211,133,0.25)] active:scale-95 active:shadow-inner transition-all" id="sendBtn">
                  <span class="material-symbols-outlined text-[20px] text-primary">&#xE5D8;</span>
                </button>
              </div>
            </div>

            <!-- Active Conversation Trace Strip -->
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1 items-stretch">
              <!-- 最近用户指令气泡 -->
              <div class="bg-surface-container rounded-2xl rounded-tr-sm p-3.5 text-on-surface shadow-sm border border-white/5 flex flex-col justify-between gap-1.5 w-full">
                <div class="flex items-center justify-between">
                  <span class="font-label-xs text-label-xs text-secondary/70 font-mono" id="homeLastUserSrc">你</span>
                  <span class="font-label-xs text-label-xs font-mono text-secondary/60 tabular-nums" id="homeLastUserTime">待命</span>
                </div>
                <p class="font-body-md text-body-md text-secondary/80 leading-relaxed" id="homeLastUserText">还没有对话</p>
              </div>

              <!-- 最近助手响应气泡 -->
              <div class="bg-surface-container-lowest/90 rounded-2xl rounded-tl-sm p-3.5 flex flex-col justify-between gap-2 shadow-inner border border-primary/20 w-full">
                <div class="flex items-start gap-2.5 min-w-0">
                  <div class="w-2.5 h-2.5 rounded-full bg-primary mt-1.5 shrink-0 shadow-[0_0_8px_rgba(253,211,133,0.9)]"></div>
                  <div class="flex flex-col gap-0.5 flex-1 min-w-0">
                    <div class="flex items-center justify-between">
                      <span class="font-label-xs text-label-xs text-primary font-mono font-medium" id="homeLastAiMeta">小讯</span>
                      <span class="font-label-xs text-label-xs font-mono text-outline font-medium" id="homeLastAiStatus">就绪</span>
                    </div>
                    <p class="font-body-md text-body-md text-on-surface leading-relaxed mt-0.5" id="homeLastAiText">在。</p>
                  </div>
                </div>
                <div class="flex items-center justify-between pt-1 border-t border-white/5">
                  <a href="chat.html" class="font-label-xs text-label-xs font-mono text-secondary/70 hover:text-primary no-underline transition-colors">对话 &rarr;</a>
                  <button id="btnReplayHome" class="flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container-lowest border border-primary/35 hover:border-primary/60 text-primary font-label-xs text-label-xs shadow-[0_2px_6px_rgba(0,0,0,0.5),inset_0_1px_1px_rgba(255,255,255,0.1)] hover:shadow-[0_0_10px_rgba(253,211,133,0.25)] active:scale-95 transition-all">
                    <span class="material-symbols-outlined text-[14px] text-primary">&#xE042;</span>
                    <span class="font-mono font-medium">播报响应</span>
                  </button>
                </div>
              </div>
            </div>
          </section>

          <!-- SECTION 2: DUAL-CORE WORKFLOW (LEFT: ACOUSTIC STREAMER, RIGHT: HARDWARE CONTROL MATRIX) -->
          <div class="grid grid-cols-1 lg:grid-cols-12 gap-space-lg w-full items-stretch">
            <!-- LEFT: ACOUSTIC STREAMING DISC & TRANSPORT (7 COLUMNS) -->
            <div class="lg:col-span-7 flex flex-col justify-between gap-4 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md">
              <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/45 to-transparent pointer-events-none"></div>
              <!-- Header meta row -->
              <div class="flex items-center justify-between pb-2 border-b border-white/5">
                <div class="flex items-center gap-2">
                  <span class="font-label-xs text-label-xs uppercase tracking-widest text-secondary/70 font-mono">音源</span>
                  <span class="px-2.5 py-0.5 rounded-full bg-primary/15 text-primary border border-primary/30 font-label-xs text-label-xs font-mono font-medium" id="homeSourceBadge">音箱本机</span>
                  <span class="font-label-xs text-label-xs text-tertiary font-mono" id="homeSpecBadge">--</span>
                </div>
                <div class="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-surface-container-lowest/80 border border-primary/20">
                  <span class="w-1.5 h-1.5 rounded-full bg-primary animate-pulse"></span>
                  <span class="font-label-xs text-label-xs font-mono text-primary/80 uppercase" id="homeDspActiveTag">IDLE</span>
                </div>
              </div>

              <!-- Turntable Disc & Spectrum Track Identity -->
              <div class="flex flex-col sm:flex-row items-center gap-6 py-2">
                <!-- Luxury Concentric Vinyl Disc with Anisotropic Sheen -->
                <div onclick="R1.togglePlay()" class="relative w-40 h-40 rounded-full flex items-center justify-center shrink-0 shadow-[0_8px_32px_rgba(0,0,0,0.9),0_0_24px_rgba(253,211,133,0.25)] p-0.5 border border-primary/40 group overflow-hidden bg-surface-container-lowest cursor-pointer" role="img" title="点击播放/暂停">
                  <div id="homeVinylDisc" class="absolute inset-0.5 rounded-full transition-transform duration-500 vinyl-spinning vinyl-paused" style="background:repeating-radial-gradient(circle at 50% 50%, rgba(255,255,255,.055) 0 .5px, rgba(0,0,0,0) .5px 2px),conic-gradient(from 200deg at 50% 50%, rgba(253,211,133,.22), rgba(253,211,133,0) 20%, rgba(255,255,255,.06) 45%, rgba(253,211,133,.16) 70%, rgba(253,211,133,0) 88%),radial-gradient(circle at 50% 50%, #16191d 0 27%, #0a0c0e 28% 100%)">
                    <div class="absolute inset-0 m-auto w-[44%] h-[44%] rounded-full flex flex-col items-center justify-center gap-[1px]" style="background:radial-gradient(circle at 35% 30%, #f7deab, #dfb76c 48%, #8f6d2c 100%);box-shadow:0 0 10px rgba(253,211,133,.35), inset 0 0 4px rgba(0,0,0,.35)">
                      <span class="font-mono text-[8px] font-bold tracking-[0.12em] text-[#3a2a08] leading-none truncate max-w-[46px]" id="homeDiscCenterLabel">R1 HI-FI</span>
                      <span class="w-[9px] h-[9px] rounded-full bg-[#0a0c0e] shadow-[0_0_0_1px_rgba(253,211,133,.35)] shrink-0 mt-0.5"></span>
                    </div>
                  </div>
                  <div class="absolute inset-0 rounded-full pointer-events-none" style="box-shadow:inset 0 0 14px rgba(0,0,0,.85), inset 0 1px 0 rgba(253,211,133,.18)"></div>
                </div>

                <!-- Typography & Enhanced 8-Band Live Sonic Spectrum Analyzer -->
                <div class="flex flex-col justify-between flex-1 gap-3 min-w-0">
                  <div class="flex items-start justify-between gap-3">
                    <div class="flex flex-col min-w-0 flex-1">
                      <div class="flex items-center gap-2">
                        <span class="font-headline-lg text-headline-lg text-on-surface font-semibold tracking-tight truncate min-w-0 flex-1" id="homeTrackTitle">未在播放</span>
                        <span class="px-2 py-0.5 rounded-full bg-gradient-to-r from-primary/20 to-primary/10 text-primary border border-primary/40 font-label-xs text-label-xs font-mono font-medium shadow-[0_0_8px_rgba(253,211,133,0.15)]" id="homeTrackFormat">STANDBY</span>
                      </div>
                      <p class="font-body-md text-body-md text-secondary/80 truncate mt-1" id="homeTrackAlbum">未在播放</p>
                    </div>
                    <div class="flex items-end gap-[3px] h-8 px-3 py-1.5 bg-surface-container-lowest/90 rounded-full border border-primary/30 shadow-[inset_0_1px_4px_rgba(0,0,0,0.8),0_0_12px_rgba(253,211,133,0.12)]" id="homeSpectrumBox">
                      <div class="spec-bar w-[3px] bg-primary rounded-full h-2 shadow-[0_0_6px_rgba(253,211,133,0.8)]"></div>
                      <div class="spec-bar w-[3px] bg-primary rounded-full h-3 shadow-[0_0_6px_rgba(253,211,133,0.8)]"></div>
                      <div class="spec-bar w-[3px] bg-primary/80 rounded-full h-2 shadow-[0_0_4px_rgba(253,211,133,0.6)]"></div>
                      <div class="spec-bar w-[3px] bg-primary rounded-full h-3.5 shadow-[0_0_8px_rgba(253,211,133,0.9)]"></div>
                      <div class="spec-bar w-[3px] bg-primary rounded-full h-2.5 shadow-[0_0_6px_rgba(253,211,133,0.8)]"></div>
                      <div class="spec-bar w-[3px] bg-primary/70 rounded-full h-2"></div>
                      <div class="spec-bar w-[3px] bg-primary rounded-full h-3 shadow-[0_0_6px_rgba(253,211,133,0.7)]"></div>
                      <div class="spec-bar w-[3px] bg-primary rounded-full h-2 shadow-[0_0_6px_rgba(253,211,133,0.7)]"></div>
                    </div>
                  </div>

                  <!-- 当前同步歌词预览行 (使用音符图标 &#xE019;，绝不使用语音拾音图标) -->
                  <div class="px-3.5 py-1.5 rounded-2xl bg-surface-container-lowest/70 border border-white/5 flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary text-[15px]">&#xE019;</span>
                    <span class="font-body-sm text-body-sm text-primary/90 truncate" id="homeLyricLine">暂无歌词</span>
                  </div>

                  <!-- Progress Slider & Tabular Time -->
                  <div class="flex flex-col gap-1.5 pt-1">
                    <div id="homeSeekTrack" class="w-full h-2 bg-surface-container-lowest rounded-full overflow-hidden shadow-inner flex items-center p-0.5 border border-primary/20 cursor-pointer" title="点击任意位置跳转播放进度">
                      <div class="h-full bg-gradient-to-r from-primary-container to-primary rounded-full shadow-[0_0_10px_rgba(253,211,133,0.85)] transition-all duration-300" id="homeProgressBar" style="width: 0%"></div>
                    </div>
                    <div class="flex justify-between items-center font-label-xs text-label-xs text-secondary/80 font-mono tabular-nums px-0.5">
                      <span class="text-primary font-medium" id="homeCurTime">00:00</span>
                      <span class="text-primary/70 tracking-wider font-mono" id="homeCenterSpec">--</span>
                      <span id="homeTotTime">00:00</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Tactile Master Controls -->
              <div class="pt-2 flex items-center justify-between gap-3 border-t border-white/5">
                <a href="music.html" class="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 text-primary active:scale-95 transition-all shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)] hover:shadow-[0_0_12px_rgba(253,211,133,0.2)] no-underline shrink-0">
                  <span class="material-symbols-outlined text-[18px] text-primary">&#xE03D;</span>
                  <span class="font-label-xs text-label-xs font-mono font-medium text-primary">曲库</span>
                </a>
                <div class="flex items-center gap-3.5">
                  <button onclick="R1.prevTrack()" aria-label="上一曲" class="w-10 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 flex items-center justify-center text-on-surface hover:text-primary active:scale-90 transition-all shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)]" title="上一曲">
                    <span class="material-symbols-outlined text-[22px]">&#xE045;</span>
                  </button>
                  <button onclick="R1.togglePlay()" aria-label="播放/暂停" class="w-12 h-12 rounded-2xl bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 text-primary shadow-[0_2px_10px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.35)] flex items-center justify-center shrink-0 hover:border-primary/60 active:scale-95 active:shadow-inner transition-all cursor-pointer" id="playBtn" title="播放/暂停">
                    <span class="material-symbols-outlined text-[24px] text-primary flex items-center justify-center" id="playIcon">&#xE037;</span>
                  </button>
                  <button onclick="R1.nextTrack()" aria-label="下一曲" class="w-10 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 flex items-center justify-center text-on-surface hover:text-primary active:scale-90 transition-all shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)]" title="下一曲">
                    <span class="material-symbols-outlined text-[22px]">&#xE044;</span>
                  </button>
                </div>
                <button onclick="R1.toggleLoopMode()" class="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary active:scale-95 transition-all shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)] hover:shadow-[0_0_12px_rgba(253,211,133,0.3)] shrink-0" title="切换播放模式">
                  <span class="material-symbols-outlined text-[16px] text-primary">&#xE040;</span>
                  <span class="font-label-xs text-label-xs font-mono font-medium" id="loopModeText">列表循环</span>
                </button>
              </div>
            </div>

            <!-- RIGHT: ACOUSTIC & ENVIRONMENT TILES (5 COLUMNS) -->
            <div class="lg:col-span-5 flex flex-col justify-between gap-4">
              <!-- 4 Tactile Tiles Matrix -->
              <div class="grid grid-cols-2 gap-3">
                <!-- Tile 1: LAN Endpoint -->
                <div onclick="R1.promptSpeakerHost()" class="bg-surface-container-low/90 rounded-[24px] p-4 flex flex-col justify-between h-[126px] relative overflow-hidden border border-primary/25 hover:border-primary/45 shadow-md cursor-pointer transition-all" title="点击设置音箱连接地址">
                  <div class="flex items-center justify-between">
                    <span class="font-headline-sm text-headline-sm text-on-surface font-medium">局域网地址</span>
                    <span class="w-2.5 h-2.5 rounded-full bg-outline" id="lanDot"></span>
                  </div>
                  <div class="flex flex-col gap-0.5">
                    <span class="font-label-md text-label-md text-primary font-mono font-semibold truncate" id="lanIp">--</span>
                    <div class="flex items-center justify-between mt-0.5 pt-1 border-t border-white/5">
                      <span class="font-label-xs text-label-xs text-secondary/70 font-mono">HTTP</span>
                      <span class="font-label-xs text-label-xs text-secondary/70 font-mono">:8686</span>
                    </div>
                  </div>
                </div>

                <!-- Tile 2: Master Output Volume Step Control -->
                <div class="bg-surface-container-low/90 rounded-[24px] p-4 flex flex-col justify-between h-[126px] border border-primary/25 shadow-md">
                  <div class="flex items-center justify-between">
                    <span class="font-headline-sm text-headline-sm text-on-surface font-medium cursor-pointer" onclick="R1.toggleMute()" title="点击静音/恢复">主音量</span>
                    <span class="font-label-md text-label-md text-primary font-mono tabular-nums font-semibold px-2 py-0.5 rounded-full bg-primary/10 border border-primary/20" id="volVal">40</span>
                  </div>
                  <div class="flex items-center justify-between bg-surface-container-lowest rounded-full p-1 border border-outline-variant/40 shadow-inner">
                    <button onclick="R1.stepVolume(-5)" class="w-7 h-7 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container-lowest border border-primary/35 flex items-center justify-center text-primary font-mono text-body-lg active:scale-90 transition-all shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_0_rgba(255,255,255,0.2)] hover:border-primary/60" id="volDec">-</button>
                    <div id="volBarTrack" class="flex-1 mx-2 h-2 bg-surface-container-lowest rounded-full overflow-hidden p-0.5 border border-primary/20 shadow-inner cursor-pointer" title="点击调节硬件 STREAM_MUSIC 音量">
                      <div class="h-full bg-gradient-to-r from-primary-container via-primary to-primary-fixed-dim rounded-full shadow-[0_0_8px_rgba(253,211,133,0.8)]" id="volBar" style="width: 40%"></div>
                    </div>
                    <button onclick="R1.stepVolume(5)" class="w-7 h-7 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container-lowest border border-primary/35 flex items-center justify-center text-primary font-mono text-body-lg active:scale-90 transition-all shadow-[0_1px_4px_rgba(0,0,0,0.6),inset_0_1px_0_rgba(255,255,255,0.2)] hover:border-primary/60" id="volInc">+</button>
                  </div>
                </div>

                <!-- Tile 3: Hardware Bluetooth Gateway -->
                <div onclick="R1.toggleBluetooth()" class="bg-surface-container-low/90 rounded-[24px] p-4 flex flex-col justify-between h-[126px] border border-outline-variant/50 hover:border-primary/30 shadow-md cursor-pointer transition-all">
                  <div class="flex items-center justify-between">
                    <span class="font-headline-sm text-headline-sm text-on-surface font-medium">蓝牙</span>
                    <span class="w-2.5 h-2.5 rounded-full bg-outline" id="btDot"></span>
                  </div>
                  <div class="flex flex-col gap-0.5">
                    <span class="font-label-md text-label-md text-on-surface truncate font-medium" id="btDevName">未连接蓝牙设备</span>
                    <div class="flex items-center justify-between mt-0.5 pt-1 border-t border-white/5">
                      <span class="font-label-xs text-label-xs text-secondary/70 font-mono">音频</span>
                      <span class="font-label-xs text-label-xs text-secondary/70 font-medium" id="btStateLabel">待命</span>
                    </div>
                  </div>
                </div>

                <!-- Tile 4: Sleep Timer (音箱端真实定时) -->
                <div onclick="R1.cycleSleepTimer()" class="bg-surface-container-low/90 rounded-[24px] p-4 flex flex-col justify-between h-[126px] border border-outline-variant/50 hover:border-primary/30 shadow-md cursor-pointer transition-all" title="点击循环切换 15 / 30 / 60 分钟 / 关闭">
                  <div class="flex items-center justify-between">
                    <span class="font-headline-sm text-headline-sm text-on-surface font-medium">睡眠定时</span>
                    <span class="font-label-md text-label-md text-primary font-mono tabular-nums font-semibold px-2 py-0.5 rounded-full bg-primary/10 border border-primary/20" id="sleepVal">关闭</span>
                  </div>
                  <div class="flex items-center justify-between gap-2 pt-1 border-t border-white/5">
                    <span class="font-label-xs text-label-xs text-secondary/80 truncate" id="sleepDesc">到点停止</span>
                    <span class="font-label-xs text-label-xs text-secondary/60 font-mono tracking-wider shrink-0">15 · 30 · 60</span>
                  </div>
                </div>
              </div>

              <!-- Quick Command Pad (走音箱端本地意图，非 LLM) -->
              <div class="flex-1 bg-gradient-to-b from-surface-container-high/80 to-surface-container-low/90 rounded-[24px] p-4 shadow-md border border-primary/20 flex flex-col justify-between gap-2.5">
                <div class="flex items-center justify-between">
                  <span class="font-label-xs text-label-xs text-secondary/70 uppercase tracking-widest font-mono">快捷</span>
                </div>
                <div class="grid grid-cols-3 gap-2">
                  <button onclick="R1.sendCommand('系统体检', '快捷指令')" class="py-2 px-3 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container hover:from-surface-container-highest hover:to-surface-container-high border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary font-label-xs text-label-xs font-mono transition-all text-center shadow-[0_2px_6px_rgba(0,0,0,0.5),inset_0_1px_1px_rgba(255,255,255,0.1)] active:scale-95">系统体检</button>
                  <button onclick="R1.sendCommand('现在几点了', '快捷指令')" class="py-2 px-3 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container hover:from-surface-container-highest hover:to-surface-container-high border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary font-label-xs text-label-xs font-mono transition-all text-center shadow-[0_2px_6px_rgba(0,0,0,0.5),inset_0_1px_1px_rgba(255,255,255,0.1)] active:scale-95">北京时间</button>
                  <button onclick="R1.sendCommand('30分钟后停止播放', '快捷指令')" class="py-2 px-3 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container hover:from-surface-container-highest hover:to-surface-container-high border border-primary/30 hover:border-primary/50 text-secondary hover:text-primary font-label-xs text-label-xs font-mono transition-all text-center shadow-[0_2px_6px_rgba(0,0,0,0.5),inset_0_1px_1px_rgba(255,255,255,0.1)] active:scale-95">30分钟睡眠</button>
                </div>
                <div class="flex items-center gap-1.5 flex-wrap pt-0.5">
                  <span class="font-label-xs text-label-xs text-secondary/60 font-mono mr-1">灯环</span>
                  <button onclick="R1.setLight('wakeup')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary hover:text-primary hover:border-primary/55 font-label-xs text-label-xs font-mono transition-all active:scale-95">唤醒色</button>
                  <button onclick="R1.setLight('loading')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary hover:text-primary hover:border-primary/55 font-label-xs text-label-xs font-mono transition-all active:scale-95">呼吸</button>
                  <button onclick="R1.setLight('dormant')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary hover:text-primary hover:border-primary/55 font-label-xs text-label-xs font-mono transition-all active:scale-95">休眠</button>
                  <button onclick="R1.setLight('scan')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary hover:text-primary hover:border-primary/55 font-label-xs text-label-xs font-mono transition-all active:scale-95">流光</button>
                  <button onclick="R1.setLight('off')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary hover:text-primary hover:border-primary/55 font-label-xs text-label-xs font-mono transition-all active:scale-95">关闭</button>
                </div>
                <div class="flex items-center gap-2 flex-wrap pt-0.5" id="castRow">
                  <span class="font-label-xs text-label-xs text-secondary/60 font-mono mr-1">投屏</span>
                  <button type="button" id="castDlnaBadge" onclick="R1.toggleCast('dlna')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary font-label-xs text-label-xs font-mono">DLNA</button>
                  <button type="button" id="castAirplayBadge" onclick="R1.toggleCast('airplay')" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary font-label-xs text-label-xs font-mono">AirPlay</button>
                  <button type="button" id="btPairBtn" onclick="R1.pairBluetooth()" class="px-2.5 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/50 text-secondary font-label-xs text-label-xs font-mono">蓝牙配对</button>
                </div>
                <div class="font-label-xs text-label-xs text-secondary/70 font-mono px-1 min-h-[1em]" id="castHint"></div>
              </div>
            </div>
          </div>

          <!-- SECTION 3: BOTTOM TELEMETRY & HARDWARE DIAGNOSTICS CAPSULE RACK -->
          <footer class="w-full bg-surface-container-low/90 rounded-[24px] p-4 shadow-md border border-outline-variant/40 flex flex-col gap-2.5">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-tertiary shadow-[0_0_6px_rgba(99,241,180,0.7)]"></span>
                <span class="font-label-xs text-label-xs text-secondary/70 uppercase tracking-widest font-mono">设备</span>
              </div>
              <span class="font-label-xs text-label-xs font-mono text-primary/60" id="footDevice">--</span>
            </div>
            <!-- Metric Badges Ribbon -->
            <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-2">
              <div class="p-3 rounded-2xl bg-surface-container-lowest/80 flex flex-col gap-1 border border-white/5 shadow-inner">
                <span class="font-label-xs text-label-xs text-secondary/60 uppercase font-mono">运行</span>
                <span class="font-label-md text-label-md font-mono text-on-surface font-medium tabular-nums" id="footUptime">00:00:00</span>
              </div>
              <div class="p-3 rounded-2xl bg-surface-container-lowest/80 flex flex-col gap-1 border border-white/5 shadow-inner">
                <span class="font-label-xs text-label-xs text-secondary/60 uppercase font-mono">内存</span>
                <div class="flex items-center justify-between">
                  <span class="font-label-md text-label-md font-mono text-tertiary font-medium" id="footRam">-- MB</span>
                  <span class="font-label-xs text-label-xs font-mono text-secondary/50" id="footRamTotal">/ --</span>
                </div>
              </div>
              <div class="p-3 rounded-2xl bg-surface-container-lowest/80 flex flex-col gap-1 border border-white/5 shadow-inner">
                <span class="font-label-xs text-label-xs text-secondary/60 uppercase font-mono">时间</span>
                <span class="font-label-md text-label-md font-mono text-primary font-medium tabular-nums" id="footCnTime">--:--:--</span>
              </div>
              <div class="p-3 rounded-2xl bg-surface-container-lowest/80 flex flex-col gap-1 border border-white/5 shadow-inner">
                <span class="font-label-xs text-label-xs text-secondary/60 uppercase font-mono">地址</span>
                <span class="font-label-md text-label-md font-mono text-tertiary font-medium" id="footIp">--</span>
              </div>
              <div class="col-span-2 sm:col-span-1 p-3 rounded-2xl bg-surface-container-lowest/80 flex flex-col gap-1 border border-white/5 shadow-inner">
                <span class="font-label-xs text-label-xs text-secondary/60 uppercase font-mono">温度</span>
                <span class="font-label-md text-label-md font-mono text-on-surface-variant font-medium" id="footTemp">-- °C</span>
              </div>
            </div>
          </footer>`;

  const script = `
  <script>
    (function() {
      R1.refreshCast();
      const cmdInput = document.getElementById('cmdInput');
      const sendBtn = document.getElementById('sendBtn');
      function submitCmd() {
        const v = cmdInput.value.trim();
        if (!v) return;
        cmdInput.value = '';
        R1.sendCommand(v, 'Web 控制台');
      }
      sendBtn.addEventListener('click', submitCmd);
      cmdInput.addEventListener('keydown', e => { if (e.key === 'Enter') submitCmd(); });

      // 进度条与音量条点击跳转
      document.getElementById('homeSeekTrack').addEventListener('click', function(e) {
        const r = this.getBoundingClientRect();
        R1.seekByRatio((e.clientX - r.left) / r.width);
      });
      document.getElementById('volBarTrack').addEventListener('click', function(e) {
        const r = this.getBoundingClientRect();
        R1.setVolume(((e.clientX - r.left) / r.width) * 100);
        R1.toast('主音量已调至 ' + R1.getState().volume + '%');
      });

      document.getElementById('btnReplayHome').addEventListener('click', function() {
        const st = R1.getState();
        const lastAi = [...st.chatHistory].reverse().find(x => x.role === 'assistant');
        if (lastAi) {
          R1.speak(lastAi.text);
        } else {
          R1.speak('在。');
        }
      });

      R1.onStateChange(function(st) {
        const tr = R1.getCurrentTrack();
        // 最近交互气泡
        const lastUser = [...st.chatHistory].reverse().find(x => x.role === 'user');
        const lastAi = [...st.chatHistory].reverse().find(x => x.role === 'assistant');
        if (lastUser) {
          document.getElementById('homeLastUserSrc').textContent = lastUser.source || '控制台指令';
          document.getElementById('homeLastUserTime').textContent = lastUser.time || '';
          document.getElementById('homeLastUserText').textContent = '“' + lastUser.text + '”';
          document.getElementById('homeLastUserText').className = 'font-body-md text-body-md text-on-surface leading-relaxed';
        } else {
          document.getElementById('homeLastUserSrc').textContent = '你';
          document.getElementById('homeLastUserTime').textContent = '待命';
          document.getElementById('homeLastUserText').textContent = '还没有对话';
          document.getElementById('homeLastUserText').className = 'font-body-md text-body-md text-secondary/70 leading-relaxed';
        }
        if (lastAi) {
          document.getElementById('homeLastAiMeta').textContent = lastAi.model || '小讯';
          const aiStatus = document.getElementById('homeLastAiStatus');
          aiStatus.textContent = lastAi.status || '已回复';
          aiStatus.className = 'font-label-xs text-label-xs font-mono font-medium text-tertiary';
          document.getElementById('homeLastAiText').textContent = '“' + lastAi.text + '”';
        } else {
          document.getElementById('homeLastAiMeta').textContent = '小讯';
          const aiStatusIdle = document.getElementById('homeLastAiStatus');
          aiStatusIdle.textContent = st.online ? '在线' : '离线';
          aiStatusIdle.className = 'font-label-xs text-label-xs font-mono font-medium ' + (st.online ? 'text-tertiary' : 'text-outline');
          document.getElementById('homeLastAiText').textContent = '在。';
        }

        // 唱片台
        document.getElementById('homeTrackTitle').textContent = tr.title;
        document.getElementById('homeDiscCenterLabel').textContent = tr.id === 'idle' ? 'R1 HI-FI' : tr.title;
        document.getElementById('homeTrackFormat').textContent = tr.format;
        document.getElementById('homeTrackAlbum').textContent = tr.artist + (tr.album ? (' · ' + tr.album) : '');
        document.getElementById('homeSpecBadge').textContent = tr.spec;
        document.getElementById('homeCurTime').textContent = R1.formatTime(st.currentTime);
        document.getElementById('homeTotTime').textContent = R1.formatTime(tr.duration);
        const pct = tr.duration > 0 ? ((st.currentTime / tr.duration) * 100).toFixed(1) : 0;
        document.getElementById('homeProgressBar').style.width = pct + '%';
        document.getElementById('playIcon').innerHTML = R1.playPauseGlyph(st.isPlaying, 20);
        document.getElementById('homeVinylDisc').classList.toggle('vinyl-paused', !st.isPlaying);

        // 当前歌词行
        let curLyric = '';
        if (tr.lyrics && tr.lyrics.length) {
          curLyric = tr.lyrics[0].txt;
          for (let i = 0; i < tr.lyrics.length; i++) {
            if (st.currentTime >= tr.lyrics[i].t) curLyric = tr.lyrics[i].txt;
          }
        } else if (tr.id !== 'idle') {
          curLyric = '暂无歌词';
        } else {
          curLyric = '暂无歌词';
        }
        document.getElementById('homeLyricLine').textContent = curLyric;
        document.getElementById('homeCenterSpec').textContent = tr.spec;
        document.getElementById('homeDspActiveTag').textContent = !st.online ? '离线' : (st.player.hasTrack ? (st.isPlaying ? '播放中' : '已暂停') : '空闲');
        document.getElementById('loopModeText').textContent = R1.loopName(st.loopMode);

        // 硬件磁贴
        document.getElementById('volVal').textContent = st.muted ? '静音' : st.volume;
        document.getElementById('volBar').style.width = (st.muted ? 0 : st.volume) + '%';

        document.getElementById('lanIp').textContent = st.online ? st.ip : (st.hostIp || '点击配置');
        document.getElementById('lanDot').className = st.online
          ? 'w-2.5 h-2.5 rounded-full bg-tertiary shadow-[0_0_8px_rgba(99,241,180,0.8)]'
          : 'w-2.5 h-2.5 rounded-full bg-outline';

        const btName = !st.online ? '未连接' : (!st.btSupported ? '不可用' : (st.btEnabled ? (st.btName || '已开启') : '已关闭'));
        document.getElementById('btDevName').textContent = btName;
        document.getElementById('btStateLabel').textContent = !st.btEnabled ? '待命' : (st.btDiscoverable ? '可发现' : '已开启');
        document.getElementById('btDot').className = st.btEnabled
          ? 'w-2.5 h-2.5 rounded-full bg-tertiary shadow-[0_0_8px_rgba(99,241,180,0.8)]'
          : 'w-2.5 h-2.5 rounded-full bg-outline';

        document.getElementById('sleepVal').textContent = st.sleepRemainSec > 0 ? R1.formatTime(st.sleepRemainSec) : '关闭';

        // 底部真实遥测与北京时间
        document.getElementById('footUptime').textContent = R1.formatUptime(st.uptimeSec);
        document.getElementById('footRam').textContent = st.memUsedMb > 0 ? (st.memUsedMb + ' MB') : '-- MB';
        document.getElementById('footRamTotal').textContent = '/ ' + (st.memTotalMb || '--') + 'M';
        document.getElementById('footCnTime').textContent = R1.nowTimeStr();
        document.getElementById('footIp').textContent = st.online ? st.ip : '--';
        document.getElementById('footDevice').textContent = st.online ? (st.hardware + ' · ' + st.androidRelease) : '北京时间';
        document.getElementById('footTemp').textContent = st.socTemp !== '--' ? (st.socTemp + ' °C') : '-- °C';
      });

      // 8段频谱实时跳动（仅在播放时跳动，静止时保持低位待命）
      setInterval(() => {
        const st = R1.getState();
        const bars = document.querySelectorAll('#homeSpectrumBox .spec-bar');
        bars.forEach((b, i) => {
          if (st.isPlaying) {
            const h = Math.floor(Math.random() * 20) + 8;
            b.style.height = h + 'px';
          } else {
            b.style.height = [6, 8, 6, 10, 8, 6, 8, 6][i] + 'px';
          }
        });
      }, 180);
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 页面 02: 智能对话与指令中枢 (desktop/chat.html)
// =========================================================
function getDesktopChat() {
  const html = `
          <!-- 顶部状态与操作栏 (不含任何语音拾音或唤醒按钮) -->
          <section class="w-full bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-5 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md flex flex-col gap-4">
            <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/50 to-transparent pointer-events-none"></div>
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div class="flex items-center gap-3.5">
                <div class="w-11 h-11 rounded-2xl bg-surface-container-lowest border border-primary/40 flex items-center justify-center text-primary shadow-[0_0_16px_rgba(253,211,133,0.2)]">
                  <span class="material-symbols-outlined text-[24px]">&#xE0CA;</span>
                </div>
                <div>
                  <div class="flex items-center gap-2.5">
                    <h1 class="font-headline-md text-headline-md text-on-surface font-semibold tracking-tight">对话</h1>
                    <span class="px-2.5 py-0.5 rounded-full bg-primary/15 text-primary border border-primary/30 font-label-xs text-label-xs font-mono" id="chatModelBadge">小讯</span>
                  </div>
                </div>
              </div>

              <div class="flex items-center gap-2.5">
                <button id="btnToggleAutoSpeak" class="flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95 transition-all">
                  <span class="material-symbols-outlined text-[16px]">&#xE049;</span>
                  <span id="autoSpeakText">自动播报: 开</span>
                </button>
                <button onclick="R1.clearChatHistory()" class="flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-outline-variant/40 hover:border-error/50 text-secondary hover:text-error font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95 transition-all">
                  <span>清空</span>
                </button>
              </div>
            </div>

            <!-- 快捷指令胶囊栏 (Prompt Chips) -->
            <div class="flex items-center gap-2 overflow-x-auto pt-2 border-t border-white/5 pb-1">
              <span class="font-label-xs text-label-xs font-mono text-outline uppercase shrink-0">快捷</span>
              <button onclick="R1.sendCommand('把主音量调到 50')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">音量调到 50%</button>
              <button onclick="R1.sendCommand('调高音量')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">调高音量</button>
              <button onclick="R1.sendCommand('调低音量')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">调低音量</button>
              <button onclick="R1.sendCommand('现在几点了')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">现在几点了</button>
              <button onclick="R1.sendCommand('暂停播放')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">暂停播放</button>
              <button onclick="R1.sendCommand('下一首')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">下一首</button>
              <button onclick="R1.sendCommand('30分钟后定时停止播放')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">30 分钟后停止</button>
              <button onclick="R1.sendCommand('系统体检')" class="px-3 py-1.5 rounded-full bg-surface-container-lowest/90 border border-primary/25 hover:border-primary/60 text-on-surface hover:text-primary font-label-xs text-label-xs font-mono shrink-0 active:scale-95 transition-all">系统体检</button>
            </div>
          </section>

          <!-- 对话历史流主容器 -->
          <section class="w-full bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md flex flex-col justify-between gap-4">
            <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/45 to-transparent pointer-events-none"></div>

            <div id="chatStreamBox" class="flex flex-col gap-3.5 min-h-[380px] max-h-[500px] overflow-y-auto pr-1">
              <!-- 由 JS 动态渲染真实对话历史或空状态引导 -->
            </div>

            <!-- 底部常驻纯文本输入坞 (左侧采用静态键盘标识 &#xE312;，无任何语音拾音按钮) -->
            <div class="pt-3 border-t border-white/5">
              <div class="bg-surface-container-lowest/90 rounded-full pl-5 pr-1.5 py-1.5 flex items-center gap-3 shadow-inner border border-primary/30 backdrop-blur-md">
                <span class="material-symbols-outlined text-primary/70 text-[20px]">&#xE312;</span>
                <input class="flex-1 bg-transparent border-none text-body-md text-on-surface placeholder:text-secondary/50 focus:outline-none py-1.5 font-body-md" id="chatPageInput" maxlength="500" placeholder="说点什么" type="text">
                <button id="chatPageSendBtn" class="px-5 h-10 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 hover:border-primary/60 text-primary font-label-md text-label-md font-mono font-semibold flex items-center gap-1.5 shrink-0 shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)] hover:shadow-[0_0_12px_rgba(253,211,133,0.25)] active:scale-95 active:shadow-inner transition-all">
                  <span>发送</span>
                  <span class="material-symbols-outlined text-[18px] text-primary">&#xE5D8;</span>
                </button>
              </div>
            </div>
          </section>`;

  const script = `
  <script>
    (function() {
      const streamBox = document.getElementById('chatStreamBox');
      const input = document.getElementById('chatPageInput');
      const sendBtn = document.getElementById('chatPageSendBtn');
      const btnAutoSpeak = document.getElementById('btnToggleAutoSpeak');

      function send() {
        const v = input.value.trim();
        if (!v) return;
        input.value = '';
        stickBottom = true;
        R1.sendCommand(v, 'Web 对话终端');
      }
      sendBtn.addEventListener('click', send);
      input.addEventListener('keydown', e => { if (e.key === 'Enter') send(); });

      btnAutoSpeak.addEventListener('click', () => {
        const st = R1.getState();
        R1.updateConfig({ autoSpeak: !st.config.autoSpeak });
      });

      function copyText(txt) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(txt).catch(function () { R1.toast('复制失败，请手动选择文本', 'err'); });
        } else {
          const ta = document.createElement('textarea');
          ta.value = txt;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.select();
          try { document.execCommand('copy'); } catch (e) {}
          document.body.removeChild(ta);
        }
        R1.toast('已复制文本到剪贴板');
      }
      window.copyChatText = copyText;

      let lastChatSig = '__init__';
      let stickBottom = true;
      streamBox.addEventListener('scroll', function() {
        stickBottom = (streamBox.scrollHeight - streamBox.scrollTop - streamBox.clientHeight) < 60;
      });
      streamBox.addEventListener('click', function(e) {
        const btn = e.target.closest ? e.target.closest('[data-chat-action]') : null;
        if (!btn) return;
        const idx = parseInt(btn.getAttribute('data-idx'), 10);
        const item = R1.getState().chatHistory[idx];
        if (!item || typeof item.text !== 'string' || !item.text) return;
        if (btn.getAttribute('data-chat-action') === 'copy') copyText(item.text);
        else R1.speak(item.text);
      });

      R1.onStateChange(function(st) {
        const modeBadge = st.config.hasKey ? (st.config.llmModel || 'GLM-4.5-Flash') : ((st.config.llmModel || 'GLM-4.5-Flash') + ' · 待配密钥');
        document.getElementById('chatModelBadge').textContent = modeBadge;
        document.getElementById('autoSpeakText').textContent = '自动播报: ' + (st.config.autoSpeak ? '开' : '关');

        // 每秒都会收到状态通知，但只有历史真正变化时才重建 DOM；
        // 用户向上翻看旧消息时不再强制拉回底部。
        const sig = (st.chatHistory || []).map(function(item) {
          return item.id + '|' + (item.status || '') + '|' + (item.text || '').length;
        }).join(',');
        if (sig === lastChatSig) return;
        lastChatSig = sig;

        if (!st.chatHistory || st.chatHistory.length === 0) {
          streamBox.innerHTML = \`
            <div class="h-full min-h-[340px] py-12 px-6 rounded-2xl bg-surface-container-lowest/60 border border-white/5 flex flex-col items-center justify-center text-center gap-3">
              <div class="w-12 h-12 rounded-full bg-surface-container-high border border-primary/35 flex items-center justify-center text-primary shadow-[0_0_16px_rgba(253,211,133,0.15)]">
                <span class="material-symbols-outlined text-[24px]">&#xE0CA;</span>
              </div>
              <div class="flex flex-col gap-1">
                <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">还没有对话</span>
              </div>
            </div>\`;
          return;
        }

        streamBox.innerHTML = st.chatHistory.map(function(item, idx) {
          if (item.role === 'user') {
            return \`
              <div class="self-end max-w-[78%] bg-surface-container rounded-2xl rounded-tr-sm p-4 text-on-surface shadow-md border border-primary/20 flex flex-col gap-1.5">
                <div class="flex items-center justify-between gap-6">
                  <span class="font-label-xs text-label-xs text-primary/80 font-mono">\${R1.escapeHtml(item.source || '用户指令')}</span>
                  <span class="font-label-xs text-label-xs font-mono text-secondary/60 tabular-nums">\${R1.escapeHtml(item.time)}</span>
                </div>
                <p class="font-body-md text-body-md text-on-surface leading-relaxed">“\${R1.escapeHtml(item.text)}”</p>
              </div>\`;
          }
          return \`
            <div class="self-start max-w-[84%] bg-surface-container-lowest/90 rounded-2xl rounded-tl-sm p-4 flex flex-col gap-2.5 shadow-inner border border-primary/25">
              <div class="flex items-start gap-3">
                <div class="w-2.5 h-2.5 rounded-full bg-primary mt-1.5 shrink-0 shadow-[0_0_8px_rgba(253,211,133,0.9)]"></div>
                <div class="flex flex-col gap-1 flex-1">
                  <div class="flex items-center justify-between gap-4">
                    <span class="font-label-xs text-label-xs text-primary font-mono font-medium">\${R1.escapeHtml(item.model || '小讯')}</span>
                    <span class="font-label-xs text-label-xs font-mono text-tertiary font-medium tabular-nums">\${R1.escapeHtml(item.status || '已执行')} · \${R1.escapeHtml(item.time)}</span>
                  </div>
                  <p class="font-body-md text-body-md text-on-surface leading-relaxed mt-0.5">\${R1.escapeHtml(item.text)}</p>
                </div>
              </div>
              <div class="flex items-center justify-end gap-2 pt-1.5 border-t border-white/5">
                <button data-chat-action="copy" data-idx="\${idx}" class="flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-container-high/80 border border-outline-variant/40 hover:border-primary/40 text-secondary hover:text-primary font-label-xs text-label-xs font-mono transition-all">
                  <span>复制文本</span>
                </button>
                <button data-chat-action="speak" data-idx="\${idx}" class="flex items-center gap-1.5 px-3 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container-lowest border border-primary/35 hover:border-primary/60 text-primary font-label-xs text-label-xs shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95 transition-all">
                  <span class="material-symbols-outlined text-[14px] text-primary">&#xE042;</span>
                  <span class="font-mono font-medium">重播</span>
                </button>
              </div>
            </div>\`;
        }).join('');
        if (stickBottom) streamBox.scrollTop = streamBox.scrollHeight;
      });
    })();
  </script>`;

  return { html, script };
}

// =========================================================
// 页面 03: 音乐与黑胶唱片台 (desktop/music.html)
// =========================================================
function getDesktopMusic() {
  const html = `
          <!-- 顶部音源导航与导入栏 -->
          <section class="w-full bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-5 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md flex flex-wrap items-center justify-between gap-4">
            <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/50 to-transparent pointer-events-none"></div>
            <div class="flex items-center gap-2 flex-wrap" id="musicSourceTabs">
              <button data-src="all" onclick="R1.setMusicSource('all')" class="src-tab px-3.5 py-1.5 rounded-full bg-gradient-to-b from-primary/25 via-primary/15 to-surface-container border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold transition-all">全部音源</button>
              <button data-src="local" onclick="R1.setMusicSource('local')" class="src-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono transition-all">本机</button>
              <button data-src="stream" onclick="R1.setMusicSource('stream')" class="src-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono transition-all">网络</button>
            </div>

            <div class="flex items-center gap-2.5 flex-wrap">
              <button id="btnAddStreamUrl" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)] active:scale-95 transition-all">添加链接</button>
              <div class="bg-surface-container-lowest/90 rounded-full px-4 py-1.5 flex items-center gap-2 border border-primary/30 shadow-inner w-64">
                <span class="material-symbols-outlined text-primary/70 text-[18px]">&#xE03D;</span>
                <input id="musicSearchInput" type="text" placeholder="搜索" class="bg-transparent border-none text-body-sm text-on-surface placeholder:text-secondary/50 focus:outline-none w-full font-body-sm">
              </div>
            </div>
          </section>

          <!-- 云音乐（网易云）：Cookie 登录 + 搜索 + 播放 -->
          <section class="w-full bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-5 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col gap-3">
            <div class="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-white/5">
              <div class="flex items-center gap-2">
                <span class="material-symbols-outlined text-primary text-[20px]">&#xE405;</span>
                <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">云音乐</span>
                <button id="cloudSrcNetease" onclick="R1.setCloudSource('netease')" class="px-2.5 py-0.5 rounded-full font-label-xs text-label-xs font-mono shrink-0 border transition-all bg-primary/20 border-primary/60 text-primary font-bold">网易云</button>
                <button id="cloudSrcQQ" onclick="R1.setCloudSource('qq')" class="px-2.5 py-0.5 rounded-full font-label-xs text-label-xs font-mono shrink-0 border transition-all bg-surface-container-lowest border-primary/20 text-secondary hover:text-primary">QQ 音乐</button>
<button id="cloudSrcSongloft" onclick="R1.setCloudSource('songloft')" class="px-2.5 py-0.5 rounded-full font-label-xs text-label-xs font-mono shrink-0 border transition-all bg-surface-container-lowest border-primary/20 text-secondary hover:text-primary">本地库</button>
                <span id="cloudLoginState" class="px-2.5 py-0.5 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary font-label-xs text-label-xs font-mono shrink-0">未登录</span>
              </div>
              <button onclick="R1.cloudSetCookie()" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_8px_rgba(0,0,0,0.6)] active:scale-95">登录</button>
            </div>
            <div class="flex items-center gap-2">
              <div class="bg-surface-container-lowest/90 rounded-full px-4 py-1.5 flex items-center gap-2 border border-primary/30 shadow-inner flex-1">
                <span class="material-symbols-outlined text-primary/70 text-[18px]">&#xE8B6;</span>
                <input id="cloudSearchInput" type="text" placeholder="歌名或歌手" class="bg-transparent border-none text-body-sm text-on-surface placeholder:text-secondary/50 focus:outline-none w-full font-body-sm">
              </div>
              <button onclick="R1.cloudSearch()" class="px-4 py-1.5 rounded-full bg-gradient-to-b from-primary/25 via-primary/15 to-surface-container border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold active:scale-95">搜索</button>
            </div>
            <div id="cloudResults" class="flex flex-col gap-1.5 max-h-64 overflow-y-auto"></div>
          </section>

          <div class="grid grid-cols-1 lg:grid-cols-12 gap-space-lg w-full items-stretch">
            <!-- 左 7 列：黑胶转盘 + 播控 + 同步滚动 LRC 歌词视窗 -->
            <div class="lg:col-span-7 flex flex-col justify-between gap-4">
              <!-- 黑胶主控卡 -->
              <div class="flex flex-col gap-4 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md">
                <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/45 to-transparent pointer-events-none"></div>
                <div class="flex flex-col sm:flex-row items-center gap-6">
                  <!-- 黑胶转盘 -->
                  <div onclick="R1.togglePlay()" class="relative w-40 h-40 rounded-full flex items-center justify-center shrink-0 shadow-[0_8px_32px_rgba(0,0,0,0.9),0_0_24px_rgba(253,211,133,0.25)] p-0.5 border border-primary/40 cursor-pointer bg-surface-container-lowest">
                    <div id="musicVinylDisc" class="absolute inset-0.5 rounded-full vinyl-spinning vinyl-paused" style="background:repeating-radial-gradient(circle at 50% 50%, rgba(255,255,255,.055) 0 .5px, rgba(0,0,0,0) .5px 2px),conic-gradient(from 200deg at 50% 50%, rgba(253,211,133,.22), rgba(253,211,133,0) 20%, rgba(255,255,255,.06) 45%, rgba(253,211,133,.16) 70%, rgba(253,211,133,0) 88%),radial-gradient(circle at 50% 50%, #16191d 0 27%, #0a0c0e 28% 100%)">
                      <div class="absolute inset-0 m-auto w-[44%] h-[44%] rounded-full flex flex-col items-center justify-center gap-[1px]" style="background:radial-gradient(circle at 35% 30%, #f7deab, #dfb76c 48%, #8f6d2c 100%);box-shadow:0 0 10px rgba(253,211,133,.35), inset 0 0 4px rgba(0,0,0,.35)">
                        <span class="font-mono text-[8px] font-bold tracking-[0.12em] text-[#3a2a08] leading-none truncate max-w-[46px]" id="musicDiscLabel">R1 HI-FI</span>
                        <span class="w-[9px] h-[9px] rounded-full bg-[#0a0c0e] shadow-[0_0_0_1px_rgba(253,211,133,.35)] shrink-0 mt-0.5"></span>
                      </div>
                    </div>
                  </div>

                  <!-- 歌曲信息与进度 -->
                  <div class="flex flex-col flex-1 w-full gap-3 min-w-0">
                    <div class="flex items-start justify-between gap-2">
                      <div class="min-w-0">
                        <div class="flex items-center gap-2">
                          <span class="font-headline-lg text-headline-lg text-on-surface font-semibold truncate" id="musicTitle">未在播放</span>
                          <span class="px-2.5 py-0.5 rounded-full bg-primary/15 text-primary border border-primary/35 font-label-xs text-label-xs font-mono" id="musicFormat">STANDBY</span>
                        </div>
                        <p class="font-body-md text-body-md text-secondary/80 truncate mt-1" id="musicAlbum">未在播放</p>
                      </div>
                    </div>

                    <!-- 进度条 -->
                    <div class="flex flex-col gap-1.5 pt-2">
                      <div id="musicSeekTrack" class="w-full h-2 bg-surface-container-lowest rounded-full overflow-hidden shadow-inner flex items-center p-0.5 border border-primary/25 cursor-pointer">
                        <div class="h-full bg-gradient-to-r from-primary-container to-primary rounded-full shadow-[0_0_10px_rgba(253,211,133,0.85)]" id="musicProgressBar" style="width: 0%"></div>
                      </div>
                      <div class="flex justify-between items-center font-label-xs text-label-xs text-secondary/80 font-mono tabular-nums">
                        <span class="text-primary font-medium" id="musicCurTime">00:00</span>
                        <span class="text-primary/70 font-mono" id="musicSpecText">--</span>
                        <span id="musicTotTime">00:00</span>
                      </div>
                    </div>

                    <!-- 播控按钮组 -->
                    <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 pt-2 border-t border-white/5">
                      <div class="flex items-center gap-2.5 flex-wrap">
                        <button onclick="R1.prevTrack()" class="w-9 h-9 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 flex items-center justify-center text-on-surface hover:text-primary active:scale-90 transition-all">
                          <span class="material-symbols-outlined text-[20px]">&#xE045;</span>
                        </button>
                        <button onclick="R1.togglePlay()" class="w-11 h-11 rounded-2xl bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/50 text-primary shadow-[0_2px_10px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(253,211,133,0.3)] flex items-center justify-center active:scale-95 transition-all">
                          <span class="material-symbols-outlined text-[24px] flex items-center justify-center" id="musicPlayIcon">&#xE037;</span>
                        </button>
                        <button onclick="R1.nextTrack()" class="w-9 h-9 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 flex items-center justify-center text-on-surface hover:text-primary active:scale-90 transition-all">
                          <span class="material-symbols-outlined text-[20px]">&#xE044;</span>
                        </button>
                        <button onclick="R1.toggleLoopMode()" class="px-3 h-9 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-primary font-label-xs text-label-xs font-mono flex items-center gap-1 active:scale-95 transition-all">
                          <span class="material-symbols-outlined text-[15px]">&#xE040;</span>
                          <span id="musicLoopLabel">列表循环</span>
                        </button>
                      </div>

                      <!-- 音量调节 -->
                      <div class="flex items-center gap-2">
                        <button onclick="R1.stepVolume(-5)" class="w-7 h-7 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container-lowest border border-primary/35 text-primary font-mono">-</button>
                        <span class="font-label-xs text-label-xs font-mono text-primary min-w-[42px] text-center" id="musicVolLabel">40%</span>
                        <button onclick="R1.stepVolume(5)" class="w-7 h-7 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container-lowest border border-primary/35 text-primary font-mono">+</button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 实时同步 LRC 歌词视窗 (使用唱片图标 &#xE019;，无语音拾音图标) -->
              <div class="flex-1 flex flex-col justify-between gap-3 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-5 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md">
                <div class="flex items-center justify-between pb-2 border-b border-white/5">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary text-[18px]">&#xE019;</span>
                    <span class="font-headline-sm text-headline-sm text-on-surface font-medium">歌词</span>
                  </div>
                </div>
                <div id="musicLyricsBox" class="h-52 overflow-y-auto flex flex-col gap-2.5 py-4 px-4 text-center lyrics-box">
                  <!-- 动态渲染歌词 -->
                </div>
              </div>
            </div>

            <!-- 右 5 列：曲目列表 + 睡眠定时 -->
            <div class="lg:col-span-5 flex flex-col justify-between gap-4">
              <!-- 歌单卡片 -->
              <div class="flex-1 flex flex-col justify-between gap-3 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-5 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md">
                <div class="flex items-center justify-between pb-2 border-b border-white/5">
                  <span class="font-headline-sm text-headline-sm text-on-surface font-medium">曲库</span>
                  <span class="font-label-xs text-label-xs font-mono text-primary" id="playlistCountText">共 0 首</span>
                </div>
                <div class="flex items-center gap-3 flex-wrap">
                  <span class="font-label-xs text-label-xs text-secondary/70 font-mono">点歌音源</span>
                  <label class="flex items-center gap-1.5 cursor-pointer font-label-xs text-label-xs text-secondary"><input id="mpSongloft" type="checkbox" class="w-4 h-4" onchange="R1.toggleMusicProvider('songloft')">本地曲库</label>
                  <label class="flex items-center gap-1.5 cursor-pointer font-label-xs text-label-xs text-secondary"><input id="mpNetease" type="checkbox" class="w-4 h-4" onchange="R1.toggleMusicProvider('netease')">网易云</label>
                  <label class="flex items-center gap-1.5 cursor-pointer font-label-xs text-label-xs text-secondary"><input id="mpQq" type="checkbox" class="w-4 h-4" onchange="R1.toggleMusicProvider('qq')">QQ 音乐</label>
                </div>
                <div id="musicPlaylistBox" class="flex flex-col gap-2 h-[280px] overflow-y-auto pr-1">
                  <!-- 动态渲染曲目 -->
                </div>
              </div>

              <!-- 睡眠定时 (音箱端真实定时，到点自动停止播放) -->
              <div class="bg-gradient-to-b from-surface-container-high/80 to-surface-container-low/90 rounded-[24px] p-5 shadow-md border border-primary/20 flex flex-col gap-3">
                <div class="flex items-center justify-between">
                  <span class="font-label-xs text-label-xs text-secondary/70 uppercase tracking-widest font-mono">睡眠定时</span>
                  <span class="font-label-xs text-label-xs text-primary font-mono tabular-nums" id="musicSleepText">未开启</span>
                </div>
                <div class="grid grid-cols-4 gap-2">
                  <button onclick="R1.setSleepTimer(15)" class="py-2 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/30 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95 transition-all">15 分</button>
                  <button onclick="R1.setSleepTimer(30)" class="py-2 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/30 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95 transition-all">30 分</button>
                  <button onclick="R1.setSleepTimer(60)" class="py-2 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/30 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95 transition-all">60 分</button>
                  <button onclick="R1.setSleepTimer(0)" class="py-2 rounded-full bg-surface-container-high border border-outline-variant/40 text-secondary hover:text-primary font-label-xs text-label-xs font-mono active:scale-95 transition-all">关闭</button>
                </div>
              </div>
            </div>
          </div>`;

  const script = `
  <script>
    (function() {
      R1.cloudStatus();
      R1.loadMusicProviders();
      const searchInput = document.getElementById('musicSearchInput');
      let searchKw = '';
      let lastPlaylistSig = '';
      searchInput.addEventListener('input', function() {
        searchKw = this.value.trim().toLowerCase();
        renderPlaylist(R1.getState());
      });

      document.getElementById('musicPlaylistBox').addEventListener('click', function(e) {
        const btn = e.target.closest ? e.target.closest('[data-remove-stream]') : null;
        if (!btn) return;
        e.stopPropagation();
        const url = btn.getAttribute('data-remove-stream');
        if (url && confirm('确认移除该音频流？')) R1.removeStream(url);
      });

      // 添加外部音频流 URL
      document.getElementById('btnAddStreamUrl').addEventListener('click', function() {
        const url = prompt('音频链接');
        if (!url || !url.trim()) return;
        const title = prompt('请输入曲目或电台名称:', '网络音频流') || '网络音频流';
        R1.addStream(url.trim(), title.trim());
      });

      document.getElementById('musicSeekTrack').addEventListener('click', function(e) {
        const r = this.getBoundingClientRect();
        R1.seekByRatio((e.clientX - r.left) / r.width);
      });

      function renderPlaylist(st) {
        const box = document.getElementById('musicPlaylistBox');
        const list = R1.TRACKS.map((t, idx) => ({ t, idx })).filter(item => {
          const matchSrc = st.musicSource === 'all' || item.t.source === st.musicSource;
          const matchKw = !searchKw || item.t.title.toLowerCase().includes(searchKw) || item.t.artist.toLowerCase().includes(searchKw);
          return matchSrc && matchKw;
        });
        const sig = st.musicSource + '|' + st.trackIdx + '|' + searchKw + '|' +
          R1.TRACKS.map(function (t) { return t.path || t.url || ''; }).join('|');
        if (sig === lastPlaylistSig) return;
        lastPlaylistSig = sig;
        const keepScroll = box.scrollTop;
        document.getElementById('playlistCountText').textContent = '共 ' + list.length + ' 首';
        if (list.length === 0) {
          box.innerHTML = \`
            <div class="h-full p-6 rounded-2xl bg-surface-container-lowest/60 border border-white/5 text-center flex flex-col items-center justify-center gap-2">
              <span class="material-symbols-outlined text-primary/70 text-[24px]">&#xE019;</span>
              <span class="font-body-sm text-body-sm text-on-surface font-medium">曲库是空的</span>
            </div>\`;
          return;
        }
        box.innerHTML = list.map(({ t, idx }) => {
          const active = idx === st.trackIdx;
          const rm = t.source === 'stream'
            ? \`<button data-remove-stream="\${R1.escapeHtml(t.url)}" class="w-6 h-6 rounded-full bg-surface-container-high border border-outline-variant/40 text-secondary hover:text-error flex items-center justify-center" title="移除该音频流">×</button>\` : '';
          return \`
            <div onclick="R1.selectTrack(\${idx})" class="flex items-center justify-between gap-2 p-3 rounded-2xl cursor-pointer transition-all \${active ? 'bg-gradient-to-r from-primary/20 via-primary/10 to-surface-container-lowest border border-primary/50 shadow-[0_0_14px_rgba(253,211,133,0.15)]' : 'bg-surface-container-lowest/70 hover:bg-surface-container-high/60 border border-white/5'}">
              <div class="flex items-center gap-3 min-w-0">
                <span class="font-label-sm text-label-sm font-mono \${active ? 'text-primary font-bold' : 'text-outline'} w-6 shrink-0">\${String(idx + 1).padStart(2, '0')}</span>
                <div class="flex flex-col min-w-0">
                  <span class="font-body-md text-body-md \${active ? 'text-primary font-semibold' : 'text-on-surface'} truncate">\${R1.escapeHtml(t.subTitle)}</span>
                  <span class="font-label-xs text-label-xs text-secondary/70 truncate">\${R1.escapeHtml(t.artist)} · \${R1.escapeHtml(t.spec)}</span>
                </div>
              </div>
              <div class="flex items-center gap-2 shrink-0">
                <span class="px-2 py-0.5 rounded-full bg-surface-container-high border border-primary/25 font-label-xs text-label-xs font-mono text-primary">\${R1.escapeHtml(t.format)}</span>
                \${rm}
              </div>
            </div>\`;
        }).join('');
        box.scrollTop = keepScroll;
      }

      let lastRenderedTrack = '';
      function renderLyrics(st) {
        const tr = R1.getCurrentTrack();
        const box = document.getElementById('musicLyricsBox');
        const lyricSig = tr.id + '|' + (tr.lyrics ? tr.lyrics.length : 0);
        if (lastRenderedTrack !== lyricSig) {
          lastRenderedTrack = lyricSig;
          box.innerHTML = (tr.lyrics && tr.lyrics.length) ? tr.lyrics.map((line, i) => \`
            <div id="mLyric-\${i}" onclick="R1.seekTo(\${line.t})" class="lyric-item cursor-pointer py-1 font-body-md text-body-md text-secondary hover:text-on-surface transition-all">
              \${R1.escapeHtml(line.txt)}
            </div>
          \`).join('') : '<div class="h-full flex items-center justify-center font-body-sm text-body-sm text-secondary/60">暂无歌词</div>';
        }
        let activeIdx = 0;
        if (tr.lyrics) {
          for (let i = 0; i < tr.lyrics.length; i++) {
            if (st.currentTime >= tr.lyrics[i].t) activeIdx = i;
          }
          tr.lyrics.forEach((_, i) => {
            const el = document.getElementById('mLyric-' + i);
            if (el) {
              const isAct = i === activeIdx;
              el.classList.toggle('active', isAct);
            }
          });
        }
      }

      R1.onStateChange(function(st) {
        const tr = R1.getCurrentTrack();
        document.getElementById('musicTitle').textContent = tr.title;
        document.getElementById('musicDiscLabel').textContent = tr.id === 'idle' ? 'R1 HI-FI' : tr.title;
        document.getElementById('musicFormat').textContent = tr.format;
        document.getElementById('musicAlbum').textContent = tr.artist + (tr.album ? (' · ' + tr.album) : '');
        document.getElementById('musicSpecText').textContent = tr.spec;
        document.getElementById('musicCurTime').textContent = R1.formatTime(st.currentTime);
        document.getElementById('musicTotTime').textContent = R1.formatTime(tr.duration);
        const pct = tr.duration > 0 ? ((st.currentTime / tr.duration) * 100).toFixed(1) : 0;
        document.getElementById('musicProgressBar').style.width = pct + '%';
        document.getElementById('musicPlayIcon').innerHTML = R1.playPauseGlyph(st.isPlaying, 20);
        document.getElementById('musicVinylDisc').classList.toggle('vinyl-paused', !st.isPlaying);
        document.getElementById('musicVolLabel').textContent = st.muted ? '静音' : (st.volume + '%');
        document.getElementById('musicLoopLabel').textContent = R1.loopName(st.loopMode);

        // 音源 Tab 高亮
        document.querySelectorAll('#musicSourceTabs .src-tab').forEach(btn => {
          const isAct = btn.getAttribute('data-src') === st.musicSource;
          btn.className = isAct
            ? 'src-tab px-3.5 py-1.5 rounded-full bg-gradient-to-b from-primary/25 via-primary/15 to-surface-container border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold transition-all'
            : 'src-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono transition-all';
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
// 页面 04: 设置与系统控制中枢 (desktop/settings.html)
// =========================================================
function getDesktopSettings() {
  const html = `
          <!-- 顶部设置模块分类切换栏 -->
          <section class="w-full bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-5 shadow-xl border border-primary/25 relative overflow-hidden backdrop-blur-md flex flex-wrap items-center justify-between gap-4">
            <div class="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-primary/50 to-transparent pointer-events-none"></div>
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-2xl bg-surface-container-lowest border border-primary/40 flex items-center justify-center text-primary">
                <span class="material-symbols-outlined text-[22px]">&#xE8C0;</span>
              </div>
              <div>
                <h1 class="font-headline-md text-headline-md text-on-surface font-semibold">设置</h1>
              </div>
            </div>

            <div class="flex items-center gap-2 flex-wrap" id="setTabNav">
              <button data-tab="all" class="set-tab px-3.5 py-1.5 rounded-full bg-gradient-to-b from-primary/25 via-primary/15 to-surface-container border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold">全部</button>
              <button data-tab="llm" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">模型</button>
              <button data-tab="tts" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">语音</button>
              <button data-tab="alarms" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">闹钟</button>
              <button data-tab="ha" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">家居</button>
              <button data-tab="weather" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">天气</button>
              <button data-tab="file" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">配置文件</button>
              <button data-tab="ops" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">应用</button>
              <button data-tab="logs" class="set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono">日志</button>
            </div>
          </section>

          <!-- 双列配置机架网格 (等高对齐 items-stretch) -->
          <div class="grid grid-cols-1 lg:grid-cols-12 gap-space-lg w-full items-stretch">
            <!-- 1. AI 大模型配置卡 (6 列) -->
            <section data-panel="llm" class="set-panel lg:col-span-6 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col justify-between gap-4">
              <div class="flex flex-col gap-4">
                <div class="flex items-center justify-between pb-2 border-b border-white/5">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary text-[20px]">&#xE7FD;</span>
                    <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">模型</span>
                  </div>
                  <span id="llmKeyStatusBadge" class="px-3 py-1 rounded-full bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono">待配置密钥</span>
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">协议</label>
                    <div class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono">OpenAI 兼容</div>
                  </div>
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">模型</label>
                    <div class="flex items-center gap-2">
                      <input id="cfgLlmModel" type="text" list="llmModelList" placeholder="模型名" class="flex-1 min-w-0 bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                      <button id="btnFetchModels" class="btn-gold shrink-0">获取</button>
                    </div>
                    <datalist id="llmModelList"></datalist>
                    <span id="llmModelHint" class="font-label-xs text-label-xs text-secondary/50"></span>
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">思考</label>
                    <select id="cfgLlmReasoning" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                      <option value="auto">自动（不干预）</option>
                      <option value="none">关闭</option>
                      <option value="low">低</option>
                      <option value="medium">中</option>
                      <option value="high">高</option>
                    </select>
                    <span class="font-label-xs text-label-xs text-secondary/50">推理型模型会先思考再作答，思考同样消耗输出额度；答不出正文时改成「关闭」</span>
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">地址</label>
                    <input id="cfgLlmBaseUrl" type="text" list="llmUrlPresets" placeholder="https://api.openai.com" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                    <datalist id="llmUrlPresets">
                      <option value="https://open.bigmodel.cn/api/paas/v4/chat/completions"></option>
                      <option value="https://api.deepseek.com/v1/chat/completions"></option>
                      <option value="https://api.siliconflow.cn/v1/chat/completions"></option>
                      <option value="https://api.openai.com/v1/chat/completions"></option>
                      <option value="http://127.0.0.1:11434/v1/chat/completions"></option>
                    </datalist>
                    <span class="font-label-xs text-label-xs text-secondary/50">只填域名会补全路径</span>
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">密钥</label>
                    <input id="cfgLlmApiKey" type="password" placeholder="请输入 API Key" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">人设</label>
                    <textarea id="cfgLlmPrompt" rows="2" class="bg-surface-container-lowest/90 rounded-2xl p-3 border border-primary/25 text-body-sm text-on-surface focus:outline-none"></textarea>
                  </div>
                </div>

                <div id="llmTestResult" class="hidden px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-white/10 font-label-xs text-label-xs font-mono leading-relaxed"></div>
              </div>

              <div class="flex items-center justify-end gap-3 pt-2 border-t border-white/5">
                <button id="btnTestLlm" class="btn-gold">测试连接</button>
              </div>
            </section>

            <!-- 2. TTS 语音合成引擎配置卡 (6 列) -->
            <section data-panel="tts" class="set-panel lg:col-span-6 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col justify-between gap-4">
              <div class="flex flex-col gap-4">
                <div class="flex items-center justify-between pb-2 border-b border-white/5">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary text-[20px]">&#xE049;</span>
                    <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">语音</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <button onclick="R1.stopSpeak()" class="btn-gold">停止播报</button>
                  </div>
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">合成</label>
                    <select id="cfgTtsEngine" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                      <option value="native">原厂语音</option>
                      <option value="openai">自建语音</option>
                      <option value="edge">Edge</option>
                    </select>
                  </div>
                  <div id="asrBlock" class="sm:col-span-2 flex flex-col gap-2 pt-3 mt-1 border-t border-white/5">
                     <label class="font-label-xs text-label-xs text-secondary/80 font-mono">识别</label>
                     <select id="cfgAsrEngine" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                       <option value="native">原厂识别</option>
                       <option value="self">自建识别</option>
                     </select>
                     <div id="asrCustomFields" class="flex flex-col gap-2" style="display:none">
                       <input id="cfgAsrUrl" type="text" placeholder="网关地址，如 http://192.168.1.100:8720" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                       <input id="cfgAsrToken" type="password" placeholder="网关令牌（X-API-Key，留空表示不改）" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                       <div class="flex flex-wrap items-center gap-2">
                         <button type="button" id="btnTestAsrHealth" class="btn-gold">查看用量</button>
                         <button type="button" id="btnTestAsrWav" class="btn-gold">识别文件</button>
                         <input type="file" id="asrWavFile" accept=".wav,audio/wav" class="hidden">
                         <span id="cfgAsrTokenHint" class="font-label-xs text-label-xs text-secondary/50">16kHz · 单声道</span>
                       </div>
                       <div id="asrTestResult" class="hidden px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-white/10 font-label-xs text-label-xs font-mono leading-relaxed"></div>
                     </div>
                   </div>
                   <div id="ttsCustomFields" class="sm:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-3" style="display:none">
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">音色</label>
                    <input id="cfgTtsVoice" type="text" list="ttsVoicePresets" placeholder="音色" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                    <datalist id="ttsVoicePresets">
                      <option value="zh-CN-XiaoxiaoNeural"></option>
                      <option value="zh-CN-YunxiNeural"></option>
                      <option value="alloy"></option>
                      <option value="nova"></option>
                    </datalist>
                  </div>
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">语音模型</label>
                    <input id="cfgTtsModel" type="text" placeholder="模型" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">语音地址</label>
                    <input id="cfgTtsUrl" type="text" placeholder="https://example.com/v1/audio/speech" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">语音密钥</label>
                    <input id="cfgTtsKey" type="password" placeholder="可选" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                  </div>
                  </div>
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">播报语速: <span id="ttsSpeedVal" class="text-primary">1.0x</span></label>
                    <input id="cfgTtsSpeed" type="range" min="0.6" max="1.8" step="0.1" value="1.0" class="w-full range-gold mt-2">
                  </div>
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">自动播报</label>
                    <select id="cfgAutoSpeak" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                      <option value="true">开</option>
                      <option value="false">关</option>
                    </select>
                  </div>
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">连续对话</label>
                    <select id="cfgContinuous" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                      <option value="false">关</option>
                      <option value="true">开</option>
                    </select>
                  </div>
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono" title="说完静音 1.2 秒就收尾，不等原厂判断（需新版 L1）">提前判停</label>
                    <select id="cfgEarlyEndpoint" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                      <option value="false">关</option>
                      <option value="true">开</option>
                    </select>
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">试听</label>
                    <input id="cfgTtsSample" type="text" value="斐讯 R1 语音合成测试正常。" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                  </div>
                </div>

                <div id="ttsTestResult" class="hidden px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-white/10 font-label-xs text-label-xs font-mono leading-relaxed"></div>
              </div>

              <div class="flex items-center justify-end gap-3 pt-2 border-t border-white/5">
                <button id="btnTestTts" class="btn-gold">试听</button>
              </div>
            </section>

            <!-- 3. 定时闹钟与睡眠倒计时 (6 列) -->
            <section data-panel="alarms" class="set-panel lg:col-span-6 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col justify-between gap-4">
              <div class="flex flex-col gap-4">
                <div class="flex items-center justify-between pb-2 border-b border-white/5">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary text-[20px]">&#xE425;</span>
                    <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">闹钟</span>
                  </div>
                  <span class="font-label-xs text-label-xs font-mono text-primary tabular-nums" id="sleepTimerBadge">未开启</span>
                </div>

                <!-- 快捷睡眠倒计时 -->
                <div class="flex items-center justify-between gap-2 p-3 rounded-2xl bg-surface-container-lowest/80 border border-white/5">
                  <span class="font-label-xs text-label-xs font-mono text-secondary/80">睡眠</span>
                  <div class="flex items-center gap-1.5">
                    <button onclick="R1.setSleepTimer(15)" class="px-2.5 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/30 text-primary font-label-xs text-label-xs font-mono">15分</button>
                    <button onclick="R1.setSleepTimer(30)" class="px-2.5 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/30 text-primary font-label-xs text-label-xs font-mono">30分</button>
                    <button onclick="R1.setSleepTimer(60)" class="px-2.5 py-1 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/30 text-primary font-label-xs text-label-xs font-mono">60分</button>
                    <button onclick="R1.setSleepTimer(0)" class="px-2.5 py-1 rounded-full bg-surface-container-high border border-outline-variant/40 text-secondary font-label-xs text-label-xs font-mono">关闭</button>
                  </div>
                </div>

                <!-- 快速添加闹钟栏 (黑金按钮) -->
                <div class="flex items-center gap-2">
                  <input id="newAlarmTime" type="time" value="08:00" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/30 text-body-sm text-primary font-mono focus:outline-none">
                  <input id="newAlarmLabel" type="text" placeholder="备注" class="flex-1 bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/30 text-body-sm text-on-surface focus:outline-none">
                  <select id="newAlarmAction" class="bg-surface-container-lowest/90 rounded-2xl px-3 py-2 border border-primary/30 text-body-sm text-primary font-mono focus:outline-none">
                    <option value="speak">语音提醒</option>
                    <option value="weather">播报天气</option>
                  </select>
                  <button id="btnAddAlarm" class="px-4 py-2 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/45 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold shrink-0 shadow-[0_2px_8px_rgba(0,0,0,0.6),inset_0_1px_1px_rgba(255,255,255,0.15)] active:scale-95 transition-all">添加</button>
                </div>

                <!-- 闹钟列表 -->
                <div id="alarmListBox" class="flex flex-col gap-2 h-40 overflow-y-auto pr-1"></div>
              </div>

              <div class="flex items-center justify-between pt-2 border-t border-white/5 font-label-xs text-label-xs text-secondary/70 font-mono">
                <span>北京时间</span>
                <span id="alarmNowClock">--:--:--</span>
              </div>
            </section>

            <!-- 4. 智能家居 Home Assistant (6 列) -->
            <section data-panel="ha" class="set-panel lg:col-span-6 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col justify-between gap-4">
              <div class="flex flex-col gap-4">
                <div class="flex items-center justify-between pb-2 border-b border-white/5">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary text-[20px]">&#xE429;</span>
                    <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">家居</span>
                  </div>
                  <span class="font-label-xs text-label-xs font-mono text-secondary/70" id="haStatusBadge">可选</span>
                </div>

                <div class="flex flex-col gap-3">
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">地址</label>
                    <input id="cfgHaUrl" type="text" placeholder="http://192.168.1.10:8123" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                  </div>
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">令牌</label>
                    <input id="cfgHaToken" type="password" placeholder="令牌" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                </div>

                <div id="haTestResult" class="hidden px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-white/10 font-label-xs text-label-xs font-mono leading-relaxed"></div>
              </div>

              <div class="flex items-center justify-end gap-3 pt-2 border-t border-white/5">
                <button type="button" id="btnTestHa" class="btn-gold">测试连接</button>
              </div>
            </section>

            <!-- 5. 城市天气 (6 列) -->
            <section data-panel="weather" class="set-panel lg:col-span-6 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col justify-between gap-4">
              <div class="flex flex-col gap-4">
                <div class="flex items-center justify-between pb-2 border-b border-white/5">
                  <div class="flex items-center gap-2">
                    <span class="material-symbols-outlined text-primary text-[20px]">&#xE2C7;</span>
                    <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">城市天气</span>
                  </div>
                  <span class="px-2.5 py-0.5 rounded-full bg-surface-container-lowest border border-primary/30 text-secondary font-label-xs text-label-xs font-mono" id="weatherProviderBadge">未测试</span>
                </div>

                <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div class="flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">所在城市</label>
                    <input id="cfgWeatherCity" type="text" placeholder="城市" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface focus:outline-none">
                  </div>
                  <div id="weatherJwtFields" class="sm:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div class="flex flex-col gap-1">
                      <label class="font-label-xs text-label-xs text-secondary/80 font-mono">开发者 ID</label>
                      <input id="cfgWeatherJwtIss" type="text" placeholder="开发者 ID" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                    </div>
                    <div class="flex flex-col gap-1">
                      <label class="font-label-xs text-label-xs text-secondary/80 font-mono">项目 ID</label>
                      <input id="cfgWeatherJwtProjectId" type="text" placeholder="项目 ID" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                    </div>
                    <div class="flex flex-col gap-1">
                      <label class="font-label-xs text-label-xs text-secondary/80 font-mono">凭据 ID</label>
                      <input id="cfgWeatherJwtKeyId" type="text" placeholder="凭据 ID" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                    </div>
                    <div class="sm:col-span-2 flex flex-col gap-1">
                      <label class="font-label-xs text-label-xs text-secondary/80 font-mono">私钥</label>
                      <textarea id="cfgWeatherJwtKey" rows="3" placeholder="私钥" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none"></textarea>
                    </div>
                    <div class="sm:col-span-2 flex flex-col gap-1">
                      <label class="font-label-xs text-label-xs text-secondary/80 font-mono">公钥</label>
                      <textarea id="cfgWeatherJwtPub" rows="4" readonly placeholder="公钥" class="bg-surface-container-lowest/60 rounded-2xl px-3.5 py-2 border border-primary/20 text-body-sm text-primary font-mono focus:outline-none"></textarea>
                    </div>
                    <div class="sm:col-span-2 flex flex-wrap items-center gap-2">
                      <button type="button" id="btnJwtKeygen" class="px-3.5 py-1.5 rounded-full bg-surface-container-lowest border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95">生成密钥</button>
                      <button type="button" id="btnJwtDownload" class="px-3.5 py-1.5 rounded-full bg-surface-container-lowest border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95">下载密钥</button>
                      <button type="button" id="btnJwtPub" class="px-3.5 py-1.5 rounded-full bg-surface-container-lowest border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95">推导公钥</button>
                      <button type="button" id="btnJwtCopyPub" class="px-3.5 py-1.5 rounded-full bg-surface-container-lowest border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95">复制公钥</button>
                      <button type="button" id="btnJwtPreview" class="px-3.5 py-1.5 rounded-full bg-surface-container-lowest border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono active:scale-95">预览</button>
                    </div>
                  </div>
                  <div class="sm:col-span-2 flex flex-col gap-1">
                    <label class="font-label-xs text-label-xs text-secondary/80 font-mono">接口域名</label>
                    <input id="cfgWeatherHost" type="text" placeholder="devapi.qweather.com" class="bg-surface-container-lowest/90 rounded-2xl px-3.5 py-2 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none">
                  </div>
                </div>

                <div id="weatherTestResult" class="hidden px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-white/10 font-label-xs text-label-xs font-mono leading-relaxed"></div>
              </div>

              <div class="flex items-center justify-between gap-3 pt-2 border-t border-white/5">
                <span class="font-label-xs text-label-xs text-secondary/60 font-mono"></span>
                <div class="flex items-center gap-2 flex-wrap">
                  <button type="button" id="btnTestWeatherQw" class="btn-gold">测和风</button>
                  <button type="button" id="btnTestWeatherWttr" class="btn-gold">测通用</button>
                  <button type="button" id="btnTestWeather" class="btn-gold">自动</button>
                </div>
              </div>
            </section>

            <!-- 5. 斐讯 R1 运维中枢与应用管理 (12 列全宽) -->
            <section data-panel="ops" class="set-panel lg:col-span-12 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col gap-5">
              <div class="flex flex-wrap items-center justify-between gap-3 pb-2 border-b border-white/5">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary text-[20px]">&#xE32D;</span>
                  <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">应用</span>
                </div>
                <div class="flex items-center gap-2 flex-wrap">
                  <button onclick="doSystemClean()" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">清理</button>
                  <button onclick="doSystemReboot()" class="px-3.5 py-1.5 rounded-full bg-surface-container-lowest border border-error/40 hover:border-error/70 text-error font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">重启</button>
                  <button onclick="loadAppsList()" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">刷新</button>
                </div>
              </div>

              <!-- 上传 APK 拖拽区与进度条 -->
              <div class="flex flex-col gap-2">
                <input type="file" id="apkFileInput" accept=".apk" class="hidden">
                <div id="apkDropZone" onclick="document.getElementById('apkFileInput').click()" class="border border-dashed border-primary/35 hover:border-primary/60 rounded-2xl p-5 bg-surface-container-lowest/80 text-center cursor-pointer transition-all flex flex-col items-center justify-center gap-2">
                  <span class="material-symbols-outlined text-primary text-[28px]">&#xE5D8;</span>
                  <div class="text-body-sm text-on-surface font-semibold">安装应用</div>
                </div>
                <div id="apkUploadProgress" style="display:none" class="flex-col gap-1.5 p-3 rounded-2xl bg-surface-container-lowest border border-primary/30">
                  <div class="flex items-center justify-between font-mono text-label-xs text-primary">
                    <span id="apkProgressText">正在流式上传...</span>
                  </div>
                  <div class="w-full h-2 rounded-full bg-surface-container-high overflow-hidden">
                    <div id="apkProgressBar" class="h-full bg-primary rounded-full transition-all duration-200" style="width: 0%;"></div>
                  </div>
                </div>
              </div>

              <!-- 已安装应用清单 -->
              <div class="flex flex-col gap-2">
                <div class="flex items-center justify-between text-label-xs font-mono text-secondary/80">
                  <span>已安装</span>
                  <span id="appsCountBadge">正在加载...</span>
                </div>
                <div id="installedAppsBox" class="flex flex-col gap-2 max-h-80 overflow-y-auto pr-1"></div>
              </div>
            </section>

            <!-- 5. 实时 Logcat 日志终端与系统维护 (12 列全宽) -->
            <section data-panel="logs" class="set-panel lg:col-span-12 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col gap-4">
              <div class="flex flex-wrap items-center justify-between gap-3 pb-2 border-b border-white/5">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary text-[20px]">&#xE88E;</span>
                  <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">日志</span>
                </div>
                <div class="flex items-center gap-2 flex-wrap">
                  <button id="btnSpeakerHost" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">音箱地址</button>
                  <button id="btnAccessToken" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">访问令牌</button>
                  <button id="btnSyncLogs" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/40 hover:border-primary/60 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">刷新日志</button>
                  <button id="btnExportConfig" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">导出配置</button>
                  <button id="btnImportConfig" class="px-3.5 py-1.5 rounded-full bg-gradient-to-b from-surface-container-high to-surface-container border border-primary/35 text-primary font-label-xs text-label-xs font-mono shadow-[0_2px_6px_rgba(0,0,0,0.5)] active:scale-95">导入配置</button>
                  <input type="file" id="importConfigFile" accept="application/json,.json" style="display:none">
                  <button id="btnClearCache" class="px-3.5 py-1.5 rounded-full bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono active:scale-95">清除本机记录</button>
                </div>
              </div>

              <!-- 实时 Logcat 视窗 -->
              <div id="logTerminalBox" class="log-terminal rounded-2xl p-4 border border-primary/20 h-56 overflow-y-auto font-mono text-label-sm flex flex-col gap-1.5 shadow-inner"></div>
            </section>

            <!-- 配置文件：和上面的表单改的是同一份配置，同样不立即生效 -->
            <section data-panel="file" style="display:none" class="set-panel lg:col-span-12 bg-gradient-to-b from-surface-container-high/90 to-surface-container-low/95 rounded-[26px] p-6 shadow-xl border border-primary/25 relative overflow-hidden flex flex-col gap-4">
              <div class="flex items-center justify-between pb-2 border-b border-white/5">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-primary text-[20px]">&#xE88E;</span>
                  <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">配置文件</span>
                </div>
                <span class="font-label-xs text-label-xs text-secondary/60 font-mono">与面板二选一 · 改完点右下角对勾确认</span>
              </div>
              <textarea id="cfgFileEditor" rows="18" spellcheck="false" class="bg-surface-container-lowest/90 rounded-2xl p-3.5 border border-primary/25 text-body-sm text-on-surface font-mono focus:outline-none leading-relaxed"></textarea>
            </section>
          </div>

          <!-- 改动浮出确认按钮：带红点对勾，点开按 git diff 方式查看待写入内容 -->
          <button type="button" id="cfgFab" class="cfg-fab" title="查看配置更改并确认写入">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 6 9 17l-5-5"></path></svg>
            <span id="cfgFabDot" class="cfg-fab-dot"></span>
          </button>

          <div id="cfgReviewModal" class="cfg-review-mask" style="display:none">
            <div class="cfg-review-card">
              <div class="cfg-review-head">
                <span class="font-headline-sm text-headline-sm text-on-surface font-semibold">待写入的配置更改</span>
                <button type="button" id="cfgReviewClose" class="cfg-review-x" title="关闭" aria-label="关闭">×</button>
              </div>
              <div id="cfgReviewSub" class="cfg-review-sub">以下改动尚未写入音箱，确认后才会生效</div>
              <div id="cfgDiffBox" class="cfg-diff"></div>
              <div class="cfg-review-foot">
                <button type="button" id="cfgReviewDiscard" class="cfg-bar-ghost">放弃更改</button>
                <button type="button" id="cfgReviewCancel" class="cfg-bar-ghost">返回</button>
                <button type="button" id="cfgReviewConfirm" class="btn-gold">确认写入</button>
              </div>
            </div>
          </div>`;

  const script = `
  <script>
    (function() {
      // 子标签过滤
      document.querySelectorAll('#setTabNav .set-tab').forEach(btn => {
        btn.addEventListener('click', function() {
          const tab = this.getAttribute('data-tab');
          document.querySelectorAll('#setTabNav .set-tab').forEach(b => {
            b.className = b === this
              ? 'set-tab px-3.5 py-1.5 rounded-full bg-gradient-to-b from-primary/25 via-primary/15 to-surface-container border border-primary/60 text-primary font-label-xs text-label-xs font-mono font-bold'
              : 'set-tab px-3.5 py-1.5 rounded-full bg-surface-container-high border border-primary/20 text-secondary hover:text-primary font-label-xs text-label-xs font-mono';
          });
          document.querySelectorAll('.set-panel').forEach(p => {
            const panel = p.getAttribute('data-panel');
            // 配置文件与面板是「二选一」：全部视图不显示它，只有专门切到配置文件才露出
            const show = (tab === 'all') ? (panel !== 'file') : (panel === tab);
            p.style.display = show ? 'flex' : 'none';
          });
        });
      });

      function updateModelHint() {
        const hint = document.getElementById('llmModelHint');
        if (!hint) return;
        const url = document.getElementById('cfgLlmBaseUrl').value.trim();
        if (!url) {
          hint.textContent = '先填地址，再点「获取模型」';
          return;
        }
        // 不用正则（模板字符串会把 \/ 吃掉），改用字符串裁切，逻辑与后端 LlmClient 一致
        const suffix = '/chat/completions';
        let base = url;
        while (base.length > 0 && base.charAt(base.length - 1) === '/') base = base.slice(0, -1);
        if (base.slice(-suffix.length) !== suffix) {
          const scheme = base.indexOf('://');
          if (base.indexOf('/', scheme + 3) < 0) base = base + '/v1' + suffix;
        }
        let models = base;
        if (models.slice(-suffix.length) === suffix) models = models.slice(0, -suffix.length);
        hint.textContent = '会请求 ' + models + '/models';
      }
      document.getElementById('cfgLlmBaseUrl').addEventListener('input', updateModelHint);

      function syncFormFromState(st) {
        const c = st.config;
        document.getElementById('cfgLlmModel').value = c.llmModel || 'GLM-4.5-Flash';
        document.getElementById('cfgLlmReasoning').value = c.llmReasoning || 'none';
        document.getElementById('cfgLlmBaseUrl').value = c.llmBaseUrl || '';
        applySecretMask('cfgLlmApiKey', !!c.hasKey);
        document.getElementById('cfgLlmPrompt').value = c.llmSystemPrompt || '';

        const badge = document.getElementById('llmKeyStatusBadge');
        if (badge) {
          badge.textContent = c.hasKey ? ('已启用 · ' + (c.llmModel || 'LLM')) : '待配置密钥';
          badge.className = c.hasKey
            ? 'px-3 py-1 rounded-full bg-primary/15 border border-primary/40 text-primary font-label-xs text-label-xs font-mono'
            : 'px-3 py-1 rounded-full bg-surface-container-lowest border border-outline-variant/40 text-secondary font-label-xs text-label-xs font-mono';
        }

        document.getElementById('cfgTtsEngine').value = c.ttsEngine || 'native';
        applyVoiceFields();
        document.getElementById('cfgTtsVoice').value = c.ttsVoice || 'zh-CN-XiaoxiaoNeural';
        document.getElementById('cfgTtsModel').value = c.ttsModel || '';
        document.getElementById('cfgTtsUrl').value = c.ttsUrl || '';
        document.getElementById('cfgTtsKey').placeholder = '服务需要 Bearer 令牌时填';
        applySecretMask('cfgTtsKey', !!c.hasTtsKey);
        document.getElementById('cfgTtsSpeed').value = c.ttsSpeed || 1.0;
        document.getElementById('ttsSpeedVal').textContent = (c.ttsSpeed || 1.0) + 'x';
        document.getElementById('cfgAsrEngine').value = c.asrEngine || 'native';
        applyVoiceFields();
        document.getElementById('cfgAsrUrl').value = c.asrUrl || '';
        applySecretMask('cfgAsrToken', !!c.hasAsrToken);
        const asrTokHint = document.getElementById('cfgAsrTokenHint'); if (asrTokHint) asrTokHint.textContent = c.hasAsrToken ? '令牌已保存，留空不改' : '未设置令牌（网关会返回 401）';
        document.getElementById('cfgAutoSpeak').value = String(c.autoSpeak !== false);
        document.getElementById('cfgContinuous').value = String(!!c.continuousChat);
        document.getElementById('cfgEarlyEndpoint').value = String(!!c.earlyEndpoint);
        updateModelHint();

        document.getElementById('cfgHaUrl').value = c.haUrl || '';
        applySecretMask('cfgHaToken', !!c.hasHaToken);
        document.getElementById('cfgWeatherCity').value = c.weatherCity || '';
        document.getElementById('cfgWeatherHost').value = c.weatherHost || 'devapi.qweather.com';
        document.getElementById('cfgWeatherJwtIss').value = c.weatherJwtIss || '';
        document.getElementById('cfgWeatherJwtProjectId').value = c.weatherJwtProjectId || '';
        document.getElementById('cfgWeatherJwtKeyId').value = c.weatherJwtKeyId || '';
        document.getElementById('cfgWeatherJwtKey').value = c.weatherJwtKey || '';
        document.getElementById('cfgWeatherJwtPub').value = c.weatherJwtPub || '';
        // 表单刚按已保存状态填好，此刻的内容就是「未改动」的基准
        savedSnapshot = JSON.stringify(collectPatch());
        refreshCfgDirty();
      }

      // 配置文件文本框：拉音箱上的完整配置（含密钥），和表单改的是同一份
      function loadConfigFile() {
        const box = document.getElementById('cfgFileEditor');
        if (!box || box.dataset.dirty === '1') return;
        R1.request('GET', '/api/v1/config/export', null, 10000)
          .then(function (j) {
            if (box.dataset.dirty === '1') return;
            box.value = JSON.stringify(j, null, 2);
            box.dataset.saved = box.value;
          })
          .catch(function () {});
      }

      // 以实际内容为准判断是否需要浮出确认按钮；表单和「配置文件」改的是同一份配置的两种改法
      function isFileDirty() {
        const fileBox = document.getElementById('cfgFileEditor');
        return !!(fileBox && fileBox.dataset.saved !== undefined && fileBox.value !== fileBox.dataset.saved);
      }
      function isFormChanged() {
        return savedSnapshot !== null && JSON.stringify(collectPatch()) !== savedSnapshot;
      }
      function refreshCfgDirty() {
        const fab = document.getElementById('cfgFab');
        if (!fab) return;
        const fileDirty = isFileDirty();
        if (fileDirty || isFormChanged()) {
          fab.classList.add('show');
          document.getElementById('cfgReviewSub').textContent = fileDirty
            ? '配置文件有未保存的更改 · 确认后整份写入音箱（密钥不展示）'
            : '以下字段尚未写入音箱 · 确认后才会生效';
        } else {
          fab.classList.remove('show');
          document.getElementById('cfgReviewModal').style.display = 'none';
        }
      }

      // 密钥类字段只显示占位符，绝不在差异弹窗里回显明文
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
      function escDiff(s) {
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      }
      function fmtDiffVal(key, v) {
        if (v === undefined || v === null || v === '') return '""';
        if (CFG_SECRET_KEYS.indexOf(key) >= 0) return '"••••••"';
        if (typeof v === 'string') return '"' + v + '"';
        return String(v);
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
        Object.keys(before).forEach(function (k) { if (keys.indexOf(k) < 0) keys.push(k); });
        var out = [
          diffLine('cfg-diff-file', '--- 当前配置（已保存）'),
          diffLine('cfg-diff-file', '+++ 待写入配置'),
          diffLine('cfg-diff-hunk', '@@ 改动的字段 @@')
        ];
        var changed = 0;
        keys.forEach(function (k) {
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
            Object.keys(o).forEach(function (k) {
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
        while (i < n) { ops.push(['-', a[i++]]); }
        while (j < m) { ops.push(['+', b[j++]]); }
        var out = [
          diffLine('cfg-diff-file', '--- config.json（当前）'),
          diffLine('cfg-diff-file', '+++ config.json（待写入）'),
          diffLine('cfg-diff-hunk', '@@ -1,' + n + ' +1,' + m + ' @@')
        ];
        ops.forEach(function (op) {
          var cls = op[0] === '+' ? 'cfg-diff-add' : (op[0] === '-' ? 'cfg-diff-del' : 'cfg-diff-ctx');
          out.push(diffLine(cls, op[0] + ' ' + op[1]));
        });
        return out.join('');
      }
      function buildCfgDiff() { return isFileDirty() ? buildFileDiff() : buildFormDiff(); }

      // 真正写入：表单补丁或整份配置文件，二选一
      function saveCfgChanges(btn) {
        var fileBox = document.getElementById('cfgFileEditor');
        btn.disabled = true;
        var job;
        if (isFileDirty()) {
          var parsed;
          try { parsed = JSON.parse(fileBox.value); }
          catch (e) { R1.toast('配置文件不是合法 JSON：' + e.message, 'err'); btn.disabled = false; return; }
          job = R1.request('POST', '/api/v1/config/import', parsed, 15000).then(function () {
            fileBox.dataset.dirty = '0';
            fileBox.dataset.saved = fileBox.value;
            R1.toast('配置文件已写入音箱', 'ok');
            return R1.syncFromSpeaker(true);
          });
        } else {
          // 整份表单一并提交，避免只存一个模块把其它模块的改动冲掉
          savedSnapshot = null;
          job = R1.updateConfig(collectPatch()).then(function () {
            ['cfgLlmApiKey', 'cfgTtsKey', 'cfgHaToken'].forEach(function (id) { applySecretMask(id, true); });
          });
        }
        Promise.resolve(job).catch(function (e) {
          R1.toast('保存失败：' + e.message, 'err');
        }).then(function () { btn.disabled = false; refreshCfgDirty(); });
      }

      function closeCfgReview() { document.getElementById('cfgReviewModal').style.display = 'none'; }
      document.getElementById('cfgFab').addEventListener('click', function () {
        document.getElementById('cfgDiffBox').innerHTML = buildCfgDiff();
        document.getElementById('cfgReviewModal').style.display = 'flex';
      });
      document.getElementById('cfgReviewClose').addEventListener('click', closeCfgReview);
      document.getElementById('cfgReviewCancel').addEventListener('click', closeCfgReview);
      document.getElementById('cfgReviewModal').addEventListener('click', function (e) {
        if (e.target === this) closeCfgReview();
      });
      document.getElementById('cfgReviewDiscard').addEventListener('click', function () {
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
      document.getElementById('cfgReviewConfirm').addEventListener('click', function () {
        closeCfgReview();
        saveCfgChanges(this);
      });

      
       function applyVoiceFields() {
         const tts = document.getElementById('cfgTtsEngine').value;
         const asr = document.getElementById('cfgAsrEngine').value;
         document.getElementById('ttsCustomFields').style.display = tts === 'native' ? 'none' : '';
         document.getElementById('asrCustomFields').style.display = asr === 'self' ? '' : 'none';
       }
       document.getElementById('cfgTtsEngine').addEventListener('change', applyVoiceFields);
       document.getElementById('cfgAsrEngine').addEventListener('change', applyVoiceFields);

       document.getElementById('btnSpeakerHost').addEventListener('click', function() {
         if (location.port === '8686') {
           R1.toast('当前就开在这台音箱上');
           return;
         }
         R1.promptSpeakerHost();
       });
       document.getElementById('btnAccessToken').addEventListener('click', function() { R1.promptToken(); });
       document.getElementById('btnSyncLogs').addEventListener('click', function() { R1.fetchSpeakerLogs(); });
       document.getElementById('btnClearCache').addEventListener('click', function() {
         if (confirm('只清除这台浏览器里的对话和音频链接，音箱上的配置不动。继续？')) R1.resetFactory();
       });

      // 先声明再初始化：syncFormFromState 会写 savedSnapshot，
      // 若等到这里才 var 声明，提升的 undefined 会把刚建立的基准冲掉
      var savedSnapshot = null;
      let lastCfgSig = configSig(R1.getState().config);
      let formDirty = false;

      syncFormFromState(R1.getState());
      loadConfigFile();
      R1.fetchSpeakerLogs();

      // 密钥输入框的统一行为：已保存就显示圆点；点进去清空，失焦且没填就恢复圆点
      var SECRET_MASK = '********';
      function isMaskedValue(v) {
        if (!v) return false;
        for (var mi = 0; mi < v.length; mi++) {
          if (v.charAt(mi) !== '*') return false;
        }
        return true;
      }
      function secretValue(id) {
        const el = document.getElementById(id);
        if (!el) return '';
        return isMaskedValue(el.value.trim()) ? '' : el.value.trim();
      }
      function applySecretMask(id, hasSaved) {
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
            if (el.dataset.masked === '1') {
              el.value = '';
              el.dataset.masked = '0';
            }
          });
          el.addEventListener('blur', function () {
            if (!el.value && el.dataset.saved === '1') {
              el.value = '********';
              el.dataset.masked = '1';
            }
          });
        }
      }

      // 设备配置异步到达时刷新表单；用户正在编辑时不要覆盖输入
      function configSig(c) {
        return JSON.stringify([
          c.llmProvider, c.llmModel, c.llmBaseUrl, c.llmSystemPrompt, c.hasKey,
          c.ttsEngine, c.ttsVoice, c.ttsModel, c.ttsUrl, c.ttsSpeed, c.autoSpeak, c.continuousChat, c.earlyEndpoint, c.hasTtsKey,
        c.asrEngine, c.asrUrl,
          c.haUrl, c.hasHaToken, c.weatherCity, c.weatherHost, c.hasWeatherKey, c.weatherAuth, c.weatherJwtProjectId, c.weatherJwtKeyId, c.hasWeatherJwtKey
        ]);
      }
      // 用 var：syncFormFromState 定义在它前面、运行时会读写它，let 会触发暂时性死区
      // 配置状态变量已前移到首次同步之前声明
      [
        'cfgLlmModel', 'cfgLlmBaseUrl', 'cfgLlmApiKey', 'cfgLlmPrompt',
        'cfgTtsEngine', 'cfgTtsVoice', 'cfgTtsModel', 'cfgTtsUrl', 'cfgTtsKey', 'cfgTtsSpeed', 'cfgAutoSpeak', 'cfgContinuous', 'cfgEarlyEndpoint',
        'cfgAsrEngine', 'cfgAsrUrl',
        'cfgHaUrl', 'cfgHaToken', 'cfgWeatherCity', 'cfgWeatherHost', 'cfgWeatherJwtIss', 'cfgWeatherJwtProjectId', 'cfgWeatherJwtKeyId', 'cfgWeatherJwtKey'
      ].forEach(function (id) {
        const el = document.getElementById(id);
        if (!el) return;
        el.addEventListener('input', function () { formDirty = true; refreshCfgDirty(); });
        el.addEventListener('change', function () { formDirty = true; refreshCfgDirty(); });
      });
      const cfgFileEditor = document.getElementById('cfgFileEditor');
      if (cfgFileEditor) {
        cfgFileEditor.addEventListener('input', function () {
          // 和已加载的原文比对，而不是「只要输入过就算改过」：
          // 程序填值也会触发 input，误标脏会让确认条永远走文件分支
          cfgFileEditor.dataset.dirty = (cfgFileEditor.value === (cfgFileEditor.dataset.saved || '')) ? '0' : '1';
          refreshCfgDirty();
        });
      }
      R1.onStateChange(function (st) {
        const sig = configSig(st.config);
        if (sig === lastCfgSig) return;
        lastCfgSig = sig;
        // 正在编辑时不覆盖输入；保存后 savedSnapshot 被清空，这里才会重新对齐
        if (formDirty && savedSnapshot !== null) return;
        formDirty = false;
        syncFormFromState(st);
        loadConfigFile();
      });

      let logsFetched = false;
      R1.onStateChange(function (st) {
        if (st.online && !logsFetched) {
          logsFetched = true;
          R1.fetchSpeakerLogs();
        }
      });

      document.getElementById('cfgTtsSpeed').addEventListener('input', function() {
        document.getElementById('ttsSpeedVal').textContent = this.value + 'x';
      });

      // 各模块不再各自落盘：表单和「配置文件」文本框改的是同一份配置，
      // 统一收进 collectPatch()，由页面底部的确认条一次性写入。
      function collectPatch() {
        const patch = {
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
          autoSpeak: document.getElementById('cfgAutoSpeak').value === 'true',
          continuousChat: document.getElementById('cfgContinuous').value === 'true',
          earlyEndpoint: document.getElementById('cfgEarlyEndpoint').value === 'true',
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
        const keyVal = secretValue('cfgLlmApiKey');
        if (keyVal) patch.llmApiKey = keyVal;
        const ttsKey = secretValue('cfgTtsKey');
        if (ttsKey) patch.ttsKey = ttsKey;
        const haTok = secretValue('cfgHaToken');
        if (haTok) patch.haToken = haTok;
        return patch;
      }

      // 获取模型列表：用页面上当前的值（未保存也可以测）
      document.getElementById('btnFetchModels').addEventListener('click', function() {
        const hint = document.getElementById('llmModelHint');
        const url = document.getElementById('cfgLlmBaseUrl').value.trim();
        const key = secretValue('cfgLlmApiKey');
        if (!url) { R1.toast('先填接口地址'); return; }
        hint.textContent = '正在拉取模型列表…';
        R1.request('GET', '/api/v1/llm/models?baseUrl=' + encodeURIComponent(url)
            + (key ? '&apiKey=' + encodeURIComponent(key) : ''), null, 20000)
          .then(function(res) {
            const models = res.models || [];
            const list = document.getElementById('llmModelList');
            list.innerHTML = models.map(function(m) { return '<option value="' + R1.escapeHtml(m) + '"></option>'; }).join('');
            hint.textContent = models.length
              ? ('共 ' + models.length + ' 个模型，点输入框下拉选择（来自 ' + res.endpoint + '）')
              : ('接口通了，但没有返回模型（' + res.endpoint + '）');
            R1.toast(models.length ? ('获取到 ' + models.length + ' 个模型') : '没有返回模型', models.length ? 'ok' : 'err');
          })
          .catch(function(e) {
            hint.textContent = '获取失败：' + e.message;
            R1.toast('获取模型失败：' + e.message, 'err');
          });
      });

      // 测试连接：真发一条 8 token 的请求
      document.getElementById('btnTestLlm').addEventListener('click', function() {
        const box = document.getElementById('llmTestResult');
        const body = {
          baseUrl: document.getElementById('cfgLlmBaseUrl').value.trim(),
          apiKey: secretValue('cfgLlmApiKey'),
          model: document.getElementById('cfgLlmModel').value.trim()
        };
        box.classList.remove('hidden');
        box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed';
        box.textContent = '正在请求 ' + (body.baseUrl || '（未填地址）') + ' …';
        R1.request('POST', '/api/v1/llm/test', body, 25000)
          .then(function(res) {
            box.textContent = '✅ 通了 · ' + res.model + ' · ' + res.costMs + 'ms · 回复「' + (res.reply || '').trim() + '」｜地址：' + res.endpoint;
            R1.toast('大模型连接正常', 'ok');
          })
          .catch(function(e) {
            box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
            box.textContent = '❌ ' + e.message;
            R1.toast('测试失败：' + e.message, 'err');
          });
      });

      // TTS 测试：真合成一句并播放。原厂语音不走 HTTP 合成，没有首音耗时，不能拼出 undefinedms

      // TTS 测试：真合成一句并播放，结果里写明请求的是哪个端点
      document.getElementById('btnTestTts').addEventListener('click', function() {
        const box = document.getElementById('ttsTestResult');
        const text = document.getElementById('cfgTtsSample').value.trim() || '斐讯 R1 语音合成测试正常。';
        box.classList.remove('hidden');
        box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed';
        box.textContent = '正在合成并播放…';
        R1.request('POST', '/api/v1/tts/test', { text: text }, 25000)
          .then(function(res) {
            if (res.engine === 'native') {
              box.textContent = '✅ 已用原厂语音播报（不经过 HTTP 合成，没有首音耗时）';
            } else {
              const ms = (typeof res.firstAudioMs === 'number' && res.firstAudioMs >= 0)
                ? (res.firstAudioMs + 'ms') : '未测到';
              box.textContent = '✅ 已播放 · 首音 ' + ms + ' · 端点 ' + (res.endpoint || '（空）');
            }
            R1.toast('TTS 正常', 'ok');
          })
          .catch(function(e) {
            box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
            box.textContent = '❌ ' + e.message;
            R1.toast('TTS 测试失败：' + e.message, 'err');
          });
      });

      // 自建 ASR：健康检查
      function asrBox(text, ok) {
        const box = document.getElementById('asrTestResult');
        box.classList.remove('hidden');
        box.className = ok
          ? 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed'
          : 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
        box.textContent = text;
      }

      document.getElementById('btnTestAsrHealth').addEventListener('click', function() {
        const url = document.getElementById('cfgAsrUrl').value.trim();
        const tok = secretValue('cfgAsrToken');
        asrBox('正在读取网关用量 /usage …', true);
        R1.request('GET', '/api/v1/asr/health?url=' + encodeURIComponent(url) + '&token=' + encodeURIComponent(tok), null, 15000)
          .then(function(res) {
            if (res.ok) {
              asrBox('✅ 网关可用 · 端点 ' + res.endpoint
                + ' · 账单月 ' + (res.billingMonth || '?')
                + ' · 重置日 ' + (res.resetDate || '?')
                + (res.hasToken ? '' : ' · ⚠ 未设置令牌'), true);
              R1.toast('ASR 网关用量已读取', 'ok');
            } else {
              asrBox('❌ ' + (res.error || '用量读取失败') + ' · 端点 ' + res.endpoint, false);
            }
          })
          .catch(function(e) { asrBox('❌ ' + e.message, false); });
      });

      // 自建 ASR：上传一段 WAV 真跑一次识别
      document.getElementById('btnTestAsrWav').addEventListener('click', function() {
        document.getElementById('asrWavFile').click();
      });
      document.getElementById('asrWavFile').addEventListener('change', function() {
        const f = this.files && this.files[0];
        if (!f) return;
        if (f.size > 3 * 1024 * 1024) {
          asrBox('❌ 文件太大（上限 3MB，约 10 秒 16k 单声道）', false);
          return;
        }
        asrBox('正在上传并识别 ' + f.name + ' …', true);
        const reader = new FileReader();
        reader.onload = function() {
          const b64 = String(reader.result || '').split(',')[1] || '';
          R1.request('POST', '/api/v1/asr/test', {
            url: document.getElementById('cfgAsrUrl').value.trim(),
            audio: b64
          }, 60000)
            .then(function(res) {
              if (res.ok) {
                const extra = [];
                if (res.durationMs) extra.push(res.durationMs + 'ms');
                if (res.engine) extra.push('引擎 ' + res.engine);
                asrBox('✅ ' + (res.via || '') + ' · "' + res.text + '"'
                  + (extra.length ? ' · ' + extra.join(' · ') : ''), true);
                R1.toast('识别成功：' + res.text, 'ok');
              } else {
                asrBox('❌ ' + (res.error || '识别失败') + ' · 端点 ' + (res.endpoint || ''), false);
              }
            })
            .catch(function(e) { asrBox('❌ ' + e.message, false); });
        };
        reader.readAsDataURL(f);
        this.value = '';
      });

      document.getElementById('btnTestHa').addEventListener('click', function() {
        const box = document.getElementById('haTestResult');
        box.classList.remove('hidden');
        box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed';
        box.textContent = '正在读取 /api/states …';
        R1.request('POST', '/api/v1/ha/test', {
          haUrl: document.getElementById('cfgHaUrl').value.trim(),
          haToken: document.getElementById('cfgHaToken').value.trim()
        }, 15000)
          .then(function(res) {
            box.textContent = '✅ 连上了 · 实体 ' + res.entities + ' 个 · 可语音控制的 ' + res.controllable + ' 个';
            R1.toast('Home Assistant 连接正常', 'ok');
          })
          .catch(function(e) {
            box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
            box.textContent = '❌ ' + e.message;
          });
      });

      // 天气字段并入 collectPatch()，不再单独落盘

      // 生成 Ed25519 密钥对：私钥存设备，公钥贴到和风控制台
      function downloadText(name, text) {
        const blob = new Blob([text], { type: 'text/plain' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = name;
        a.click();
      }
      document.getElementById('btnJwtKeygen').addEventListener('click', function() {
        const existed = document.getElementById('cfgWeatherJwtKey').value.trim()
          || document.getElementById('cfgWeatherJwtPub').value.trim();
        if (existed && !confirm('已有密钥。重新生成会覆盖音箱上的旧私钥，旧公钥在和风那边也会失效。先下载一份再继续？')) return;
        const box = document.getElementById('weatherTestResult');
        box.classList.remove('hidden');
        box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed';
        box.textContent = '正在生成密钥对…';
        R1.request('POST', '/api/v1/weather/keygen', { save: false }, 20000)
          .then(function(res) {
            if (!res.ok) { box.textContent = '❌ ' + res.error; return null; }
            document.getElementById('cfgWeatherJwtKey').value = res.privateKey;
            document.getElementById('cfgWeatherJwtPub').value = res.publicKeyPem;
            downloadText('r1-qweather-keys.txt', ['privateKey', res.privateKey, '', 'publicKey', res.publicKeyPem, ''].join(String.fromCharCode(10)));
            box.textContent = '已生成。密钥文件已下载，请把公钥贴到和风。';
            // 密钥是这里生成的，不是用户手改的，直接落盘，不走底部确认条
            savedSnapshot = null;
            return R1.updateConfig({ weatherJwtKey: res.privateKey, weatherJwtPub: res.publicKeyPem, weatherAuth: 'jwt' });
          })
          .then(function(saved) {
            if (!saved) return;
            R1.toast('密钥已保存，文件已下载', 'ok');
          })
          .catch(function(e) { box.textContent = '❌ ' + e.message; });
      });

      // 从私钥推导公钥（导入现成私钥时用）
      document.getElementById('btnJwtDownload').addEventListener('click', function() {
        const priv = document.getElementById('cfgWeatherJwtKey').value.trim();
        const pub = document.getElementById('cfgWeatherJwtPub').value.trim();
        if (!priv && !pub) { R1.toast('还没有密钥', 'err'); return; }
        downloadText('r1-qweather-keys.txt', ['privateKey', priv, '', 'publicKey', pub, ''].join(String.fromCharCode(10)));
        R1.toast('已下载', 'ok');
      });
      document.getElementById('btnJwtPub').addEventListener('click', function() {
        const box = document.getElementById('weatherTestResult');
        box.classList.remove('hidden');
        R1.request('POST', '/api/v1/weather/pubkey', {
          weatherJwtKey: document.getElementById('cfgWeatherJwtKey').value.trim()
        }, 20000)
          .then(function(res) {
            if (!res.ok) {
              box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
              box.textContent = '❌ ' + res.error;
              return;
            }
            document.getElementById('cfgWeatherJwtPub').value = res.publicKeyPem;
            savedSnapshot = null;
            R1.updateConfig({ weatherJwtPub: res.publicKeyPem });
            box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed';
            box.textContent = '✅ 公钥已推导出来，裸 base64：' + res.publicKeyBase64;
          })
          .catch(function(e) { box.textContent = '❌ ' + e.message; });
      });

      // 复制公钥：http 页面没有 clipboard API 时退回选中文本
      document.getElementById('btnJwtCopyPub').addEventListener('click', function() {
        const el = document.getElementById('cfgWeatherJwtPub');
        if (!el.value.trim()) { R1.toast('上面还没有公钥', 'err'); return; }
        el.removeAttribute('readonly');
        el.select();
        let ok = false;
        try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
        el.setAttribute('readonly', 'readonly');
        R1.toast(ok ? '公钥已复制' : '请手动选中复制', ok ? 'ok' : 'err');
      });

      // JWT 预览：只签发，不发请求，用来确认三样凭据是否配套
      document.getElementById('btnJwtPreview').addEventListener('click', function() {
        const box = document.getElementById('weatherTestResult');
        box.classList.remove('hidden');
        box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed';
        box.textContent = '正在签名…';
        R1.request('POST', '/api/v1/weather/jwt', {
          weatherJwtIss: document.getElementById('cfgWeatherJwtIss').value.trim(),
          weatherJwtProjectId: document.getElementById('cfgWeatherJwtProjectId').value.trim(),
          weatherJwtKeyId: document.getElementById('cfgWeatherJwtKeyId').value.trim(),
          weatherJwtKey: secretValue('cfgWeatherJwtKey')
        }, 20000)
          .then(function(res) {
            if (res.ok) {
              box.textContent = '✅ 签名成功 · header=' + res.header + ' · payload=' + res.payload
                + ' · token 前 40 位 ' + res.token.substring(0, 40) + '…';
              R1.toast('JWT 签发正常', 'ok');
            } else {
              box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
              box.textContent = '❌ ' + res.error;
            }
          })
          .catch(function(e) { box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed'; box.textContent = '❌ ' + e.message; });
      });

      // 天气测试：每个源单独一个按钮，结果里写清「谁答的 / 谁失败了 / 失败原因」
      function testWeather(provider) {
        const box = document.getElementById('weatherTestResult');
        const badge = document.getElementById('weatherProviderBadge');
        const city = document.getElementById('cfgWeatherCity').value.trim() || '北京';
        const body = {
          city: city,
          provider: provider || 'auto',
          weatherHost: document.getElementById('cfgWeatherHost').value.trim(),
          weatherAuth: 'jwt',
          weatherJwtIss: document.getElementById('cfgWeatherJwtIss').value.trim(),
          weatherJwtProjectId: document.getElementById('cfgWeatherJwtProjectId').value.trim(),
          weatherJwtKeyId: document.getElementById('cfgWeatherJwtKeyId').value.trim(),
          weatherJwtKey: secretValue('cfgWeatherJwtKey')
        };
        box.classList.remove('hidden');
        box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed';
        box.textContent = '正在查询 ' + city + ' …';
        // 测试用的是表单里的值，不写配置；要保留改动请点底部确认条
        R1.request('POST', '/api/v1/weather/test', body, 30000)
          .then(function(res) {
            const name = res.provider === 'qweather' ? '和风天气'
                       : res.provider === 'wttr' ? '通用源 wttr.in'
                       : '没有源成功';
            const lines = [];
            lines.push('来源：' + name + (res.degraded ? '（降级）' : ''));
            if (res.testedKey) lines.push('用的 Key：' + res.testedKey);
            if (res.detail) lines.push('过程：' + res.detail);
            if (res.report) lines.push('内容：' + res.report);
            box.textContent = lines.join(' ｜ ');
            box.className = res.ok
              ? 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-primary/30 text-primary font-label-xs text-label-xs font-mono leading-relaxed'
              : 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
            if (badge) {
              badge.textContent = res.provider === 'qweather' ? '和风'
                                : res.provider === 'wttr' ? 'wttr'
                                : res.ok ? '自动' : '失败';
            }
          })
          .catch(function(e) {
            box.className = 'px-3.5 py-2 rounded-2xl bg-surface-container-lowest border border-error/40 text-error font-label-xs text-label-xs font-mono leading-relaxed';
            box.textContent = '❌ ' + e.message;
          });
      }
      document.getElementById('btnTestWeatherQw').addEventListener('click', function() { testWeather('qweather'); });
      document.getElementById('btnTestWeatherWttr').addEventListener('click', function() { testWeather('wttr'); });
      document.getElementById('btnTestWeather').addEventListener('click', function() { testWeather(null); });

      // 运维中枢与应用管理
      function loadAppsList() {
        const box = document.getElementById('installedAppsBox');
        const badge = document.getElementById('appsCountBadge');
        if (!box) return;
        box.innerHTML = '<div class="text-secondary/60 text-center py-6 font-mono text-body-sm">正在读取音箱已安装应用列表...</div>';
        R1.fetchApps().then(function(res) {
          const apps = res.apps || [];
          if (badge) badge.textContent = '共 ' + apps.length + ' 个应用';
          if (!apps.length) {
            box.innerHTML = '<div class="text-secondary/60 text-center py-6 font-mono text-body-sm">没有应用</div>';
            return;
          }
          box.innerHTML = apps.map(function(a) {
            const isSelf = a.packageName === 'com.r1.brain';
            return \`
              <div class="flex items-center justify-between p-3 rounded-2xl bg-surface-container-lowest/80 border border-primary/15 hover:border-primary/35 transition-all">
                <div class="flex items-center gap-3 min-w-0">
                  <div class="w-9 h-9 rounded-xl bg-surface-container-high flex items-center justify-center text-primary font-mono text-label-sm font-bold shrink-0">
                    \${(a.name || 'A').charAt(0).toUpperCase()}
                  </div>
                  <div class="flex flex-col min-w-0">
                    <div class="flex items-center gap-2">
                      <span class="text-on-surface font-semibold text-body-sm truncate">\${R1.escapeHtml(a.name)}</span>
                      <span class="px-2 py-0.5 rounded-full text-[10px] font-mono \${a.isSystem ? 'bg-white/5 text-secondary/70' : 'bg-primary/20 text-primary'}">\${a.isSystem ? '系统' : '第三方'}</span>
                      \${a.isRunning ? '<span class="px-2 py-0.5 rounded-full text-[10px] font-mono bg-emerald-500/20 text-emerald-400">运行中</span>' : ''}
                      \${isSelf ? '<span class="px-2 py-0.5 rounded-full text-[10px] font-mono bg-amber-500/20 text-amber-300">本机</span>' : ''}
                    </div>
                    <div class="font-mono text-label-xs text-secondary/70 truncate">
                      \${R1.escapeHtml(a.packageName)} · v\${R1.escapeHtml(a.versionName)} · \${R1.escapeHtml(a.sizeMb)}
                    </div>
                  </div>
                </div>
                <div class="flex items-center gap-2 shrink-0">
                  \${a.isRunning && !isSelf ? \`<button onclick="doStopApp('\${a.packageName}')" class="px-3 py-1 rounded-full bg-surface-container-high border border-primary/25 hover:border-primary/45 text-secondary hover:text-primary font-label-xs text-label-xs font-mono active:scale-95">强停</button>\` : ''}
                  \${!isSelf ? \`<button onclick="doUninstallApp('\${a.packageName}', '\${R1.escapeHtml(a.name)}')" class="px-3 py-1 rounded-full bg-surface-container-lowest border border-error/40 hover:border-error/70 text-error font-label-xs text-label-xs font-mono active:scale-95">卸载</button>\` : ''}
                </div>
              </div>
            \`;
          }).join('');
        }).catch(function(err) {
          if (badge) badge.textContent = '读取失败';
          box.innerHTML = '<div class="text-error text-center py-6 font-mono text-body-sm">读取应用列表失败：' + (err.message || err) + '</div>';
        });
      }

      function handleApkFile(file) {
        if (!file) return;
        const progressBox = document.getElementById('apkUploadProgress');
        const progressBar = document.getElementById('apkProgressBar');
        const progressText = document.getElementById('apkProgressText');
        if (progressBox) progressBox.style.display = 'flex';
        if (progressBar) progressBar.style.width = '0%';
        if (progressText) progressText.textContent = '准备流式上传 ' + file.name + ' ...';

        R1.uploadApk(file, function(prog) {
          if (progressBar) progressBar.style.width = prog.percent + '%';
          if (progressText) progressText.textContent = '正在极低内存流式上传 ' + prog.percent + '% (' + (prog.loaded / 1024).toFixed(0) + 'KB / ' + (prog.total / 1024).toFixed(0) + 'KB)';
        }).then(function(res) {
          if (progressText) progressText.textContent = res.selfUpdate ? '已安装，正在重启' : '已安装';
          R1.toast(res.selfUpdate ? '已安装，正在重启' : '已安装', 'ok');
          setTimeout(function() {
            if (progressBox) progressBox.style.display = 'none';
            loadAppsList();
          }, 2500);
        }).catch(function(err) {
          if (progressText) progressText.textContent = '安装失败：' + (err.message || err);
          R1.toast('安装失败：' + (err.message || err), 'err');
        });
      }

      const apkInput = document.getElementById('apkFileInput');
      if (apkInput) {
        apkInput.addEventListener('change', function(e) {
          if (e.target.files && e.target.files[0]) {
            handleApkFile(e.target.files[0]);
            e.target.value = '';
          }
        });
      }

      const dropZone = document.getElementById('apkDropZone');
      if (dropZone) {
        dropZone.addEventListener('dragover', function(e) {
          e.preventDefault();
          dropZone.classList.add('border-primary');
        });
        dropZone.addEventListener('dragleave', function() {
          dropZone.classList.remove('border-primary');
        });
        dropZone.addEventListener('drop', function(e) {
          e.preventDefault();
          dropZone.classList.remove('border-primary');
          if (e.dataTransfer.files && e.dataTransfer.files[0]) {
            handleApkFile(e.dataTransfer.files[0]);
          }
        });
      }

      window.loadAppsList = loadAppsList;
      window.doStopApp = function(pkg) {
        R1.stopApp(pkg).then(function() {
          R1.toast('已强行停止 ' + pkg, 'ok');
          setTimeout(loadAppsList, 800);
        }).catch(function(e) { R1.toast(e.message, 'err'); });
      };
      window.doUninstallApp = function(pkg, name) {
        if (!confirm('确认彻底卸载应用「' + name + '」(' + pkg + ') 吗？\\n卸载后将自动回收存储与运行内存。')) return;
        R1.toast('正在静默卸载 ' + name + ' ...');
        R1.uninstallApp(pkg).then(function() {
          R1.toast('已成功卸载 ' + name, 'ok');
          setTimeout(loadAppsList, 1000);
        }).catch(function(e) { R1.toast('卸载失败：' + e.message, 'err'); });
      };
      window.doSystemClean = function() {
        R1.toast('正在执行系统深度清理...');
        R1.cleanSystem().then(function(r) {
          R1.toast('深度清理完成：已清理 ' + (r.deletedFiles || 0) + ' 个缓存包，释放 ' + (r.freedMb || '0MB'), 'ok');
          R1.syncFromSpeaker(true);
        }).catch(function(e) { R1.toast(e.message, 'err'); });
      };
      window.doSystemReboot = function() {
        if (!confirm('警告：确认立即重启斐讯 R1 音箱硬件系统？\\n重启过程约需 40 秒，音箱声学环与网络将重新初始化。')) return;
        R1.rebootSystem().then(function() {
          R1.toast('音箱系统正在重启，稍候请刷新页面', 'ok');
        }).catch(function(e) { R1.toast(e.message, 'err'); });
      };

      if (R1.getState().online) {
        loadAppsList();
      }

      document.getElementById('btnAddAlarm').addEventListener('click', () => {
        const tm = document.getElementById('newAlarmTime').value || '08:00';
        const lb = document.getElementById('newAlarmLabel').value.trim() || '定时唤醒提醒';
        const act = document.getElementById('newAlarmAction').value || 'speak';
        R1.addAlarm({ time: tm, label: lb, repeat: '每天', action: act });
        document.getElementById('newAlarmLabel').value = '';
      });

      // 从音箱导出真实配置（含 Key），而不是浏览器缓存里的副本
      document.getElementById('btnExportConfig').addEventListener('click', () => {
        const st = R1.getState();
        const base = (location.protocol === 'file:') ? ('http://' + (st.hostIp || '')) : '';
        const headers = st.token ? { 'X-Token': st.token } : {};
        R1.toast('正在从音箱读取配置…');
        fetch(base + '/api/v1/config/export', { headers: headers })
          .then(function (r) { return r.json(); })
          .then(function (j) {
            if (!j || j.ok === false) throw new Error((j && j.error) || '导出失败');
            const blob = new Blob([JSON.stringify(j, null, 2)], { type: 'application/json' });
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = 'r1-config-' + (new Date().toISOString().slice(0, 10)) + '.json';
            a.click();
            R1.toast('已从音箱导出配置文件', 'ok');
          })
          .catch(function (e) { R1.toast('导出失败：' + e.message, 'err'); });
      });

      const importFile = document.getElementById('importConfigFile');
      document.getElementById('btnImportConfig').addEventListener('click', () => importFile.click());
      importFile.addEventListener('change', () => {
        const f = importFile.files && importFile.files[0];
        if (!f) return;
        const reader = new FileReader();
        reader.onload = function () {
          let data;
          try { data = JSON.parse(reader.result); } catch (e) { R1.toast('文件不是合法 JSON', 'err'); return; }
          if (!confirm('确认用该文件覆盖音箱当前配置？（访问令牌不会被覆盖）')) return;
          const st = R1.getState();
          const base = (location.protocol === 'file:') ? ('http://' + (st.hostIp || '')) : '';
          const headers = { 'Content-Type': 'application/json' };
          if (st.token) headers['X-Token'] = st.token;
          fetch(base + '/api/v1/config/import', { method: 'POST', headers: headers, body: JSON.stringify(data) })
            .then(function (r) { return r.json(); })
            .then(function (j) {
              if (!j || j.ok === false) throw new Error((j && j.error) || '导入失败');
              R1.toast('配置已导入，正在刷新…', 'ok');
              setTimeout(function () { R1.syncFromSpeaker(false); }, 500);
            })
            .catch(function (e) { R1.toast('导入失败：' + e.message, 'err'); });
        };
        reader.readAsText(f);
        importFile.value = '';
      });

      let lastLogSig = '';
      let logStick = true;
      const logBox = document.getElementById('logTerminalBox');
      if (logBox) {
        logBox.addEventListener('scroll', function() {
          logStick = (logBox.scrollHeight - logBox.scrollTop - logBox.clientHeight) < 40;
        });
      }

      function renderLogs() {
        const box = document.getElementById('logTerminalBox');
        const list = R1.getLogs();
        const sig = (list || []).map(function (l) { return (l.time || '') + '|' + (l.source || '') + '|' + (l.msg || '').length; }).join(';');
        if (sig === lastLogSig) return;
        lastLogSig = sig;
        if (!list || list.length === 0) {
          box.innerHTML = '<div class="text-secondary/60">没有日志</div>';
          return;
        }
        box.innerHTML = list.map(l => \`
          <div class="flex items-start gap-2.5">
            <span class="text-secondary/50 shrink-0 tabular-nums">\${R1.escapeHtml(l.time)}</span>
            <span class="px-2 py-0.5 rounded-full text-[10px] \${l.level === 'WARN' || l.level === 'ERR' ? 'text-primary bg-primary/15' : 'text-tertiary bg-tertiary/10'} shrink-0">\${R1.escapeHtml(l.level)}</span>
            <span class="text-primary/80 shrink-0">[\${R1.escapeHtml(l.tag)}]</span>
            <span class="text-on-surface">\${R1.escapeHtml(l.msg)}</span>
          </div>
        \`).join('');
        if (logStick) box.scrollTop = box.scrollHeight;
      }
      renderLogs();
      R1.onLog(renderLogs);
      setInterval(function () {
        if (document.visibilityState !== 'hidden') R1.fetchSpeakerLogs();
      }, 15000);

      R1.onStateChange(function(s) {
        const sb = document.getElementById('sleepTimerBadge');
        sb.textContent = s.sleepRemainSec > 0 ? ('睡眠倒计时: ' + R1.formatTime(s.sleepRemainSec)) : '睡眠定时: 未开启';

        const nowClock = document.getElementById('alarmNowClock');
        if (nowClock) nowClock.textContent = '北京时间 ' + R1.nowTimeStr();

        const badge = document.getElementById('llmKeyStatusBadge');
        if (badge) {
          badge.textContent = s.config.hasKey ? ('已启用 · ' + (s.config.llmModel || 'LLM')) : '待配置密钥';
        }

        const alarmBox = document.getElementById('alarmListBox');
        if (!s.alarms || s.alarms.length === 0) {
          alarmBox.innerHTML = '<div class="h-full p-4 rounded-2xl bg-surface-container-lowest/60 border border-white/5 flex items-center justify-center text-center font-body-sm text-body-sm text-secondary/70">没有闹钟</div>';
          return;
        }
        alarmBox.innerHTML = s.alarms.map(a => \`
          <div class="flex items-center justify-between p-3 rounded-2xl bg-surface-container-lowest/85 border \${a.enabled ? 'border-primary/35' : 'border-white/5'}">
            <div class="flex items-center gap-3 min-w-0">
              <span class="font-headline-sm text-headline-sm font-mono tabular-nums \${a.enabled ? 'text-primary font-bold' : 'text-secondary/50'}">\${R1.escapeHtml(a.time)}</span>
              <div class="flex flex-col min-w-0">
                <span class="font-body-sm text-body-sm text-on-surface truncate">\${R1.escapeHtml(a.label)}</span>
                <span class="font-label-xs text-label-xs text-secondary/60 font-mono">\${R1.escapeHtml(a.repeat)} · 北京时间</span>
              </div>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <button onclick="R1.toggleAlarm(\${a.id})" class="px-2.5 py-1 rounded-full font-label-xs text-label-xs font-mono \${a.enabled ? 'bg-primary/20 text-primary border border-primary/40' : 'bg-surface-container text-secondary border border-white/10'}">\${a.enabled ? '已启用' : '已停用'}</button>
              <button onclick="R1.deleteAlarm(\${a.id})" class="w-7 h-7 rounded-full bg-surface-container-high text-secondary hover:text-error flex items-center justify-center">×</button>
            </div>
          </div>
        \`).join('');
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
