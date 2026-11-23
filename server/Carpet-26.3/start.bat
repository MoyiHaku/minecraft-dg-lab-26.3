@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
title Minecraft 26.3 - Carpet Server
pushd "%~dp0" || exit /b 1
if not exist "runtime\bin\java.exe" goto missing
if not exist "tools\server-start.jar" goto missing
"runtime\bin\java.exe" -Xms12m -Xmx64m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Djava.net.useSystemProxies=true -jar "tools\server-start.jar"
set "result=%ERRORLEVEL%"
if not "%result%"=="0" pause
popd
exit /b %result%

:missing
echo 请先完整解压 ZIP，并保留 runtime 和 tools 文件夹。
echo Extract the entire ZIP before starting the server.
pause
popd
exit /b 1
