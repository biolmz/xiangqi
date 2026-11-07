# 中国象棋

纯 Java 实现的中国象棋，Windows / macOS / Android 三端可玩。桌面端与安卓端共用同一套规则引擎和 AI，没有第三方依赖。

安装页面：<https://biolmz.github.io/xiangqi/>

## 下载

| 平台 | 文件 | 大小 | 说明 |
|---|---|---|---|
| Windows | [Xiangqi-1.0.1-setup.exe](https://github.com/biolmz/xiangqi/releases/latest/download/Xiangqi-1.0.1-setup.exe) | 28.5 MB | 安装向导，内嵌裁剪后的 Java 运行时，无需另装 Java |
| macOS | [Xiangqi-1.0.1.dmg](https://github.com/biolmz/xiangqi/releases/latest/download/Xiangqi-1.0.1.dmg) | 184 KB | 磁盘映像，需系统已装 Java 17+ |
| macOS | [Xiangqi-1.0.1-mac.zip](https://github.com/biolmz/xiangqi/releases/latest/download/Xiangqi-1.0.1-mac.zip) | 225 KB | 备用包，dmg 挂载异常时使用 |
| Android | [Xiangqi-1.0.1.apk](https://github.com/biolmz/xiangqi/releases/latest/download/Xiangqi-1.0.1.apk) | 40.5 KB | Android 7.0（API 24）及以上 |

SHA-256 校验值与各平台的安装步骤见 [安装页面](https://biolmz.github.io/xiangqi/)。

## 特点

- **完整规则**：蹩马腿、塞象眼、炮翻山、过河兵、飞将（白脸将）、将死与困毙全部实现。着法生成用 perft 对过标准值——初始局面第 1/2/3 层分别是 44 / 1920 / 79666。
- **不会兜圈**：局面用 Zobrist 增量指纹记录（走进、撤销都是 O(1) 异或更新）。同一局面出现三次判和；单方连续将军判长将负；连续 8 步同方将军另有兜底裁决。AI 在搜索中遇到重复局面会按和棋或长将负分处理，并对"原路退回"的着法降权，所以不会用同一招反复将军刷步数。
- **三档对手**：Negamax + Alpha-Beta + 迭代加深 + 时间上限，MVV-LVA 排序。简单／普通／困难三档。
- **中文棋谱**：按「炮二平五」记法实时记录，可回看、可悔棋，将军有独立提示。
- **共用内核**：`chess.Board` 与 `chess.AI` 是纯 Java、无 UI 依赖，桌面 Swing 与 Android Canvas 各自只做绘制与交互。

## 从源码构建

需要 JDK 17 或更高版本。

**桌面程序（jar）**

```bash
javac -encoding UTF-8 -d build/classes src/chess/*.java
jar cfe build/jar/Xiangqi.jar chess.Main -C build/classes .
java -jar build/jar/Xiangqi.jar
```

Windows 上也可以直接双击 `build.bat`。

**Windows 安装包（.exe）**

用 `jpackage` 生成，需要 WiX 3.x 的 `candle.exe` / `light.exe` 在 PATH 中：

```bat
jpackage --type exe --name 中国象棋 --app-version 1.0.1 ^
  --input build/jar --main-jar Xiangqi.jar --main-class chess.Main ^
  --icon build/icon.ico --win-shortcut --win-menu ^
  --add-modules java.base,java.desktop ^
  --jlink-options "--strip-debug --no-header-files --no-man-pages"
```

`--add-modules` + `--jlink-options` 能把运行时从 121 MB 压到 74 MB。

**macOS**

在 Mac 上双击 `mac/build-mac.command`，它会调用本机的 jpackage，生成自带运行时的 `.app` 与原生 `.dmg`。

仓库里 `mac/make_dmg.py` 与 `mac/make_zip.py` 是在非 macOS 环境下交叉制作的脚本：前者用 ISO9660 + Rock Ridge 承载 `.app`（Rock Ridge 的 NM 记录保留中文文件名，PX 记录保留 Unix 权限），后者显式写入 `0755` 保证启动器可执行。

**Android**

`android/build-apk.sh` 走 aapt2 → javac → d8 → zipalign → apksigner，不需要 Gradle。首次运行会下载 build-tools 与 platform-35。

## 自测

```bash
javac -encoding UTF-8 -cp build/jar/Xiangqi.jar -d build/tests tests/SelfTest.java tests/LoopTest.java
java -cp "build/jar/Xiangqi.jar;build/tests" SelfTest    # perft 与规则断言
java -cp "build/jar/Xiangqi.jar;build/tests" LoopTest    # 指纹一致性、长将裁决、AI 自对弈
```

## 项目结构

```
src/chess/         桌面端：规则引擎、AI、Swing 界面、音效
android/           安卓端：AndroidManifest、MainActivity（Canvas 自绘）、构建脚本
mac/               macOS .app 骨架、dmg / zip 打包脚本、使用说明
tests/             perft 与规则断言、循环规避测试、截图辅助
docs/              安装页面（GitHub Pages）
build.bat          Windows 一键编译
```

## 已知限制

- macOS 的 `.app` 未做 Apple 代码签名与公证，首次打开会被 Gatekeeper 拦一次，右键「打开」即可放行。
- Windows 安装包未做代码签名，SmartScreen 会提示"未知发布者"。
- macOS 版需要系统已安装 Java 17+；如果想要自带运行时的版本，在 Mac 上用 `mac/build-mac.command` 自行生成。

## License

MIT，见 [LICENSE](LICENSE)。
