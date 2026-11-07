#!/bin/bash
# 在 macOS 上双击运行：用 jpackage 生成自带 Java 运行时的 .app 与 .dmg 安装包
set -e
cd "$(dirname "$0")"
mkdir -p dist

if [ ! -f payload/Xiangqi.jar ]; then
    echo "缺少 payload/Xiangqi.jar"
    exit 1
fi
if ! command -v jpackage >/dev/null 2>&1; then
    osascript -e 'display dialog "需要 JDK 17 或更高版本（含 jpackage）。可先执行：brew install --cask temurin" buttons {"好"} default button 1 with title "中国象棋"' >/dev/null 2>&1
    exit 1
fi

echo "正在生成自带运行时的应用…"
jpackage --type app-image \
    --name "中国象棋" \
    --input payload \
    --main-jar Xiangqi.jar \
    --main-class chess.Main \
    --icon payload/icon.icns \
    --app-version 1.0.1 \
    --vendor "Xiangqi Studio" \
    --add-modules java.base,java.desktop \
    --jlink-options "--strip-debug --no-header-files --no-man-pages" \
    --java-options "-Dfile.encoding=UTF-8" \
    --dest dist

echo "正在打包 dmg…"
jpackage --type dmg \
    --name "中国象棋" \
    --app-image "dist/中国象棋.app" \
    --icon payload/icon.icns \
    --app-version 1.0.1 \
    --vendor "Xiangqi Studio" \
    --dest dist

echo "完成："
echo "  dist/中国象棋.app          自带运行时，无需另装 Java"
echo "  dist/中国象棋-1.0.1.dmg    可分发的磁盘映像"
open dist
