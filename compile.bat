@echo off
echo ============================================
echo SIGINT Radar - Compilacion del Proyecto
echo ============================================
echo.

cd /d "%~dp0"

echo [1/3] Limpiando proyecto...
call gradlew.bat clean

echo.
echo [2/3] Compilando APK Debug...
call gradlew.bat assembleDebug

echo.
echo [3/3] Verificando resultado...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo ============================================
    echo     COMPILACION EXITOSA!
    echo ============================================
    echo.
    echo APK generado en:
    echo app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo.
    echo ============================================
    echo     ERROR EN LA COMPILACION
    echo ============================================
    echo.
    echo Revisa los errores arriba
    echo.
)

pause

