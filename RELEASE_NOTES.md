## 中国象棋 1.0.1

纯 Java 实现的中国象棋，Windows / macOS / Android 三端可用。桌面端与安卓端共用同一套规则引擎与 AI，无第三方依赖。

安装页面（含 SHA-256 校验值与分平台步骤）：<https://biolmz.github.io/xiangqi/>

### 下载

| 平台 | 文件 | 大小 | 说明 |
|---|---|---|---|
| Windows | `Xiangqi-1.0.1-setup.exe` | 28.5 MB | 安装向导，内嵌裁剪后的 Java 运行时，无需另装 Java |
| macOS | `Xiangqi-1.0.1.dmg` | 184 KB | 磁盘映像，需系统已装 Java 17+ |
| macOS | `Xiangqi-1.0.1-mac.zip` | 225 KB | 备用包，dmg 挂载异常时使用 |
| Android | `Xiangqi-1.0.1.apk` | 40.5 KB | Android 7.0（API 24）及以上 |

### 这一版的重点：不再兜圈

电脑反复用同一招将军、把局面拖进循环，是这个版本主要修掉的问题。做法分三层：

- **规则层**：局面用 Zobrist 增量指纹记录（走进、撤销均为 O(1) 异或更新）。同一局面第三次出现判和；单方连续将军判长将负；连续 8 步同方将军另有兜底裁决。
- **搜索层**：AI 拿到本局历史指纹，搜索中遇到已出现过的局面会按和棋或长将负分估值，并对"原路退回"的着法降权，因此不会为了拖延而重复将军。
- **界面层**：状态栏实时提示"局面重复 N 次"，玩家能看见裁决依据。

### 其它

- 完整规则：蹩马腿、塞象眼、炮翻山、过河兵、飞将（白脸将）、将死与困毙。着法生成用 perft 对过标准值，初始局面第 1/2/3 层为 44 / 1920 / 79666。
- 三档电脑对手：Negamax + Alpha-Beta + 迭代加深 + 时间上限，MVV-LVA 排序。
- 中文棋谱（「炮二平五」记法）、悔棋、将军提示。

### 已知限制

- 三端产物均未做代码签名：Windows 会弹 SmartScreen，macOS 首次打开需右键「打开」放行 Gatekeeper。这是签名证书的成本问题，不影响程序本身。
- macOS 的 dmg 是在非 macOS 环境下用 ISO9660 + Rock Ridge 交叉制作的（Rock Ridge 保留中文名与启动器的可执行权限）。若个别机器挂载异常，用同目录的 zip，或解压后在 Mac 上运行 `mac/build-mac.command` 生成自带运行时的原生包。

### 校验

```
a361446a5904f76961c394fa5b4be6ea0699c795989bcc20ce9655f6a2066c47  Xiangqi-1.0.1-setup.exe
2da578807d306c99157e4009521d378389353df83e216114af7f3314406e3b13  Xiangqi-1.0.1.dmg
c65f81f172e91f94657d051dbdf7d95f94bcc98dd339743bfae13242283b9123  Xiangqi-1.0.1-mac.zip
1cd395ce73aebacb95fdfed80dce590edaca7558ca55721c2fd5e69a0bf9499c  Xiangqi-1.0.1.apk
```
