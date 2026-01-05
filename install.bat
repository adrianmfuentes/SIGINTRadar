@echo off
REM ============================================
REM SIGINT Radar - Compilacion e Instalacion
REM ============================================

cd /d "%~dp0"

REM Configurar JAVA_HOME para JDK 17
set "JAVA_HOME=C:\Program Files\Java\jdk-17"

echo ============================================
echo Compilando e Instalando SIGINT Radar
echo ============================================
echo.

REM Compilar
call compile.bat

echo.
echo ============================================
echo Instalando en dispositivo...
echo ============================================
echo.

REM Verificar que adb esta disponible
where adb >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: ADB no encontrado en PATH
    echo.
    echo Asegurate de que Android SDK Platform-Tools esta instalado
    echo y agregado al PATH del sistema.
    echo.
    pause
    exit /b 1
)

REM Verificar dispositivos conectados
adb devices
echo.

REM Instalar APK
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo Instalando APK...
    adb install -r "app\build\outputs\apk\debug\app-debug.apk"

    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ============================================
        echo   INSTALACION EXITOSA!
        echo ============================================
        echo.
        echo La app SIGINT Radar esta lista para usar
        echo.
    ) else (
        echo.
        echo ERROR: No se pudo instalar el APK
        echo Verifica que el dispositivo este conectado
        echo.
    )
) else (
    echo ERROR: APK no encontrado
    echo Ejecuta compile.bat primero
    echo.
)

pause

