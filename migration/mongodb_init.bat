@echo off
chcp 65001 >nul
echo ========================================
echo   FoxNAS MongoDB 数据库初始化脚本
echo ========================================
echo.

REM 检查 mongosh 是否可用
where mongosh >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 正在使用 mongosh 初始化数据库...
    mongosh --file "%~dp0mongodb_init.js"
    goto :end
)

REM 检查 mongo 是否可用（旧版本）
where mongo >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 正在使用 mongo 初始化数据库...
    mongo < "%~dp0mongodb_init.js"
    goto :end
)

REM MongoDB 不在 PATH 中，尝试常见安装路径
echo MongoDB 命令行工具不在 PATH 中，尝试查找...
echo.

set MONGO_PATHS=^
"C:\Program Files\MongoDB\Server\7.0\bin\mongosh.exe" ^
"C:\Program Files\MongoDB\Server\6.0\bin\mongosh.exe" ^
"C:\Program Files\MongoDB\Server\5.0\bin\mongosh.exe" ^
"C:\Program Files\MongoDB\Server\4.4\bin\mongo.exe" ^
"C:\mongodb\bin\mongosh.exe" ^
"C:\mongodb\bin\mongo.exe"

for %%p in (%MONGO_PATHS%) do (
    if exist %%p (
        echo 找到 MongoDB: %%p
        %%p --file "%~dp0mongodb_init.js"
        goto :end
    )
)

echo.
echo [错误] 未找到 MongoDB 命令行工具！
echo.
echo 请执行以下操作之一：
echo   1. 确保 MongoDB 已安装
echo   2. 将 MongoDB bin 目录添加到 PATH 环境变量
echo   3. 手动运行: mongosh --file "%~dp0mongodb_init.js"
echo.
echo 或者使用 MongoDB Compass 图形界面手动创建数据库
echo.
pause
exit /b 1

:end
echo.
echo ========================================
echo   初始化完成！
echo ========================================
pause
