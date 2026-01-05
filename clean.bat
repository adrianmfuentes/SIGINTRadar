@echo off
REM ============================================
REM SIGINT Radar - Limpieza de Cache y Build
REM ============================================

cd /d "%~dp0"

echo ============================================
echo Limpiando Cache de Gradle y Build
echo ============================================
echo.

echo [1/5] Deteniendo Gradle Daemon...
call gradlew.bat --stop

echo.
echo [2/5] Limpiando proyecto...
call gradlew.bat clean --no-daemon

echo.
echo [3/5] Eliminando .gradle local...
if exist ".gradle" (
    rmdir /s /q ".gradle"
    echo - Carpeta .gradle eliminada
) else (
    echo - .gradle no existe
)

echo.
echo [4/5] Eliminando cache de transformaciones...
if exist "%USERPROFILE%\.gradle\caches\transforms" (
    rmdir /s /q "%USERPROFILE%\.gradle\caches\transforms"
    echo - Cache de transformaciones eliminado
) else (
    echo - Cache de transformaciones no existe
)

echo.
echo [5/5] Eliminando build outputs...
if exist "app\build" (
    rmdir /s /q "app\build"
    echo - Build outputs eliminados
) else (
    echo - Build outputs no existen
)

echo.
echo ============================================
echo   LIMPIEZA COMPLETADA
echo ============================================
echo.
echo Ahora puedes compilar con compile.bat
echo.

pause

