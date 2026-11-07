#!/bin/bash
# 无需 Gradle / Android Studio 的最小 APK 构建脚本（Git Bash 下运行）
# 依赖：tools/android/bt/android-15（build-tools 35）与 tools/android/plat/android-35（android.jar）
# 若 tools/android 已被清理，先下载并解压到对应目录：
#   https://dl.google.com/android/repository/build-tools_r35.0.1_windows.zip  -> tools/android/bt/
#   https://dl.google.com/android/repository/platform-35_r02.zip              -> tools/android/plat/
set -e
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
JDIR="$(dirname "$(command -v javac)")"
BT="$ROOT/tools/android/bt/android-15"
PLAT="$ROOT/tools/android/plat/android-35"
OUT="$ROOT/release/中国象棋-1.0.1.apk"
mkdir -p android/build/classes android/build/dex

echo "[1/8] 编译资源"
"$BT/aapt2.exe" compile --dir android/res -o android/build/res.zip

echo "[2/8] 链接基础 APK"
"$BT/aapt2.exe" link -o android/build/base.apk \
    -I "$PLAT/android.jar" \
    --manifest android/AndroidManifest.xml \
    --min-sdk-version 24 --target-sdk-version 35 \
    android/build/res.zip

echo "[3/8] 编译 Java 源码（共用桌面版规则引擎与 AI）"
"$JDIR/javac" --release 11 -encoding UTF-8 \
    -cp "$PLAT/android.jar" \
    -d android/build/classes \
    android/src/chess/*.java android/src/cn/biolmz/xiangqi/*.java

echo "[4/8] 转换为 classes.dex"
CLASSES="$(find android/build/classes -name '*.class' | tr '\n' ' ')"
"$JDIR/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --lib "$PLAT/android.jar" --min-api 24 \
    --output android/build/dex $CLASSES

echo "[5/8] 合并 dex 到 APK"
(cd android/build/dex && zip -q -u ../base.apk classes.dex)

echo "[6/8] 四字节对齐"
"$BT/zipalign.exe" -f 4 android/build/base.apk android/build/aligned.apk

echo "[7/8] 签名密钥"
if [ ! -f android/build/debug.keystore ]; then
    "$JDIR/keytool" -genkeypair \
        -keystore android/build/debug.keystore -alias xiangqi \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass xiangqi -keypass xiangqi \
        -dname "CN=Xiangqi, OU=Dev, O=Biolmz, L=CN, ST=CN, C=CN" >/dev/null 2>&1
fi

echo "[8/8] 签名并校验"
"$JDIR/java" -jar "$BT/lib/apksigner.jar" sign \
    --ks android/build/debug.keystore \
    --ks-pass pass:xiangqi --key-pass pass:xiangqi \
    --out "$OUT" android/build/aligned.apk
"$JDIR/java" -jar "$BT/lib/apksigner.jar" verify "$OUT"
echo "OK: $OUT"
ls -lh "$OUT"
