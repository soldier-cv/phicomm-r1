# 8080 配置网页的接口清单（实测）

> 数据来源：GET `http://<音箱IP>:8080/` 返回的 29,341 字节 HTML（UTF-8，中文 928 字），
> 我解析了页面里所有 `fetch(...)` 调用。**只读了 `/alarm_list` 和 `/qr/wx` 两个纯 GET 接口做验证，没有调用任何会改状态的接口。**

## 它归谁

| 项 | 结论 | 证据 |
|---|---|---|
| 页面挂在哪个进程 | **`com.phicomm.speaker.device`（uid 10008）** | `/proc/net/tcp6` 里 8080 的 uid 列 = `10008`，而 speaker.device 的 uid 实测就是 10008 |
| player 包参与了吗 | **没有** | player 的 `classes.dex` 里 `NanoHTTPD`=0、`HttpServer`=0，`8080` 只命中一条内网旧地址 `http://10.30.4.12:8080/...` |
| player 现在是谁 | **原厂** | `/data/app/com.phicomm.speaker.player-2/base.apk` = **1,160,423 字节**，和 `/system/app/EchoService/EchoService.apk` 完全同尺寸、同签名 `[105ffce5]`、`lastUpdateTime=2023-06-22` |
| 服务器框架 | 作者自写（不是 NanoHTTPD） | 页面里的 WebSocket 字符串 `Server started on port 8080`，但没有 NanoHTTPD 的类名 |
| 鉴权 | **无** | 局域网内任何人可直接访问，没有口令 |

> ⚠️ **这一条修正了参考项目给的印象**：`shj4588` 那个项目的 Web 控制台是放在 **player 包**里的，
> 而你这台机器上的 8080 **在 device 包（MOD 自己）里**，player 还是原厂的。
> 两个不同作者的实现，不能混为一谈。

## 接口分三类

### A. 只读（安全，随便调）

| 接口 | 返回 | 实测 |
|---|---|---|
| `GET /alarm_list` | `{"code":200,"list":[...]}` | ✅ `{"code":200,"list":[]}` |
| `GET /get_qr_key?source=netease\|qq\|wx` | `{"key":...}` | 未调（会向网易/QQ 申请二维码） |
| `GET /get_qr_img?key=&source=` | `{"img":"data:image/..."}` | 未调 |
| `GET /check_qr?key=&source=` | `{"code":800/802/803}` | 未调 |
| `GET /qw_pubkey?gen=0&path=` | `{"code":200,"pub":"..."}` | 未调（`gen=1` 会**生成并覆盖私钥**，危险） |
| `GET /qr/wx`、`/qr/gzh`、`/qr/ali` | JPEG 图片（打赏码、公众号） | ✅ 19,711 字节 JPEG |

### B. 改配置（会写设备）

| 接口 | 大量参数 |
|---|---|
| `GET /save_config` | `api_url`、`qq_api_url`、`music_order`、`nd_url`、`nd_user`、`nd_pass`、`nd_bitrate`、`qweather_url`、`qw_kid`、`qw_project_id`、`qw_iss`、`qw_private_key_path`、`ha_url`、`ha_token`、`ha_agent`、`ai_enabled`、`ai_provider`、`ai_base_url`、`ai_api_key`、`ai_model`、`ai_temperature`、`ai_max_tokens` |

### C. 动设备（会立刻改变音箱行为）

| 接口 | 干什么 |
|---|---|
| `GET /set_voice?speaker=FEMALE\|SWEET\|MALE\|CHILDREN\|LZL` | 切发音人 |
| `GET /set_wakeup?word=` | 改唤醒词（4-6 字） |
| `GET /set_tts?speed=&pitch=` | 改语速音调 |
| `GET /toggle_dormant` | 进入/退出休眠 |
| `GET /toggle_bass_boost` | 低音增强开关 |
| `GET /toggle_ambient_light` | 氛围灯开关 |
| `GET /toggle_bluetooth` | 蓝牙模式开关 |
| `GET /alarm_add?h=&m=&label=&repeat=&type=` | 加闹钟（`type=1` 是定时播放） |
| `GET /alarm_del?id=` / `alarm_edit?id=&h=&m=` / `alarm_toggle?id=` | 改闹钟 |
| `GET /voice_del?vid=` | 删语音备忘 |

## 路由规律

页面的六个标签走 `history.pushState` 换路径，**但返回的都是同一份 HTML**：

```
/     → R1功能配置
/2    → 音乐源
/3    → 和风天气
/4    → HomeAssistant
/5    → AI大模型
/6    → 定时任务
```

（`/1` 也有映射，但 `/` 是默认页）

## 对我们设计的意义

1. **这套接口设计得其实不差** —— 全部 GET + query string，简洁，任何 HTTP 客户端都能调。
2. **但它没有鉴权、没有 CSRF 防护、没有并发控制**。局域网里任何人一个 `curl` 就能改唤醒词、改 AI Key、开关蓝牙。
3. **如果我们要做自己的控制台，这 22 个接口就是现成的协议**，不用逆向。可以直接照着实现，或者直接调它（在我方界面上做包装）。
4. **风险点**：`/qw_pubkey?gen=1` 会覆盖私钥文件；`/save_config` 可以把 AI Key 指向任意地址。这两条在自建控制台时必须做权限控制。
