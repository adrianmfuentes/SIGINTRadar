@echo off
echo ============================================
echo SIGINT Radar - Compilacion del Proyecto
echo ============================================
echo.

cd /d "%~dp0"

REM Configurar JAVA_HOME para usar JDK 17 (requerido por AGP 8.2.0)
set "JAVA_HOME=C:\Program Files\Java\jdk-17"

echo Configuracion:
echo - JAVA_HOME: %JAVA_HOME%
echo.

REM Verificar que JDK 17 existe
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JDK 17 no encontrado en %JAVA_HOME%
    echo.
    echo Por favor instala JDK 17 o actualiza la ruta JAVA_HOME en este script.
    echo.
    pause
    exit /b 1
)

echo Verificando version de Java...
"%JAVA_HOME%\bin\java.exe" -version
echo.

echo [1/4] Deteniendo daemons de Gradle...
call gradlew.bat --stop

echo.
echo [2/4] Limpiando proyecto...
call gradlew.bat clean --no-daemon

echo.
echo [3/4] Compilando APK Debug...
call gradlew.bat assembleDebug --no-daemon

echo.
echo [4/4] Verificando resultado...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo ============================================
    echo     COMPILACION EXITOSA!
    echo ============================================
    echo.
    echo APK generado en:
    echo %CD%\app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Tamaño del APK:
    for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do echo %%~zA bytes
    echo.
    echo Para instalar en dispositivo:
    echo adb install -r app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo.
    echo ============================================
    echo     ERROR EN LA COMPILACION
    echo ============================================
    echo.
    echo Revisa los errores arriba.
    echo.
    echo Soluciones comunes:
    echo 1. Verifica que JDK 17 este instalado
    echo 2. Limpia el cache: gradlew clean --stop
    echo 3. Elimina la carpeta .gradle y vuelve a compilar
    echo.
)

pause

