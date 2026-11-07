@echo off
chcp 65001 >nul
setlocal

rem 重新编译源码并生成可直接运行的 jar（需要 JDK 17 或更高版本）
where javac >nul 2>nul
if errorlevel 1 (
  echo 未找到 javac，请安装 JDK 17+ 并加入 PATH。
  pause
  exit /b 1
)

if not exist "build\classes" mkdir "build\classes"
if not exist "build\jar" mkdir "build\jar"

javac --release 17 -encoding UTF-8 -d build\classes src\chess\*.java
if errorlevel 1 (
  echo 编译失败。
  pause
  exit /b 1
)

jar --create --file build\jar\Xiangqi.jar --main-class chess.Main -C build\classes .
if errorlevel 1 (
  echo 打包失败。
  pause
  exit /b 1
)

echo.
echo 已生成 build\jar\Xiangqi.jar
echo 运行方式（请使用 JDK 17+ 的 java）：
echo   "%%JAVA_HOME%%\bin\java" -jar build\jar\Xiangqi.jar
echo.
echo 若需要重新生成 exe 安装包，请安装 WiX 3.x 并把 candle.exe / light.exe
echo 加入 PATH 后执行 jpackage --type exe（详细参数见项目说明）。
echo.
pause
