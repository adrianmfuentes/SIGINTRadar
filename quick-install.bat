@echo off
REM ============================================
REM SIGINT Radar - Quick Build & Install
REM ============================================

cd /d "%~dp0"

REM Configurar JAVA_HOME para JDK 17
set "JAVA_HOME=C:\Program Files\Java\jdk-17"

echo ============================================
echo  SIGINT Radar - Build Rapido
echo ============================================
echo.

echo Compilando...
call gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo.
    echo BUILD OK! Instalando...
    adb install -r "app\build\outputs\apk\debug\app-debug.apk"

    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ============================================
        echo   LISTO! Abriendo app...
        echo ============================================
        adb shell am start -n com.sigint.radar/.MainActivity
    )
) else (
    echo.
    echo ERROR en compilacion
)

timeout /t 3

