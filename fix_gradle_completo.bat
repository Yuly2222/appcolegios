@echo off
echo ========================================
echo SINCRONIZACION FORZADA DE GRADLE
echo ========================================
cd /d "%~dp0"

echo.
echo [1/4] Cerrando daemons de Gradle...
call gradlew.bat --stop
timeout /t 2 /nobreak > nul

echo.
echo [2/4] Eliminando cache de Gradle del usuario...
if exist "%USERPROFILE%\.gradle\caches" (
    echo Limpiando cache global...
    rmdir /s /q "%USERPROFILE%\.gradle\caches"
)

echo.
echo [3/4] Eliminando directorios de build del proyecto...
if exist .gradle rmdir /s /q .gradle
if exist build rmdir /s /q build
if exist app\build rmdir /s /q app\build

echo.
echo [4/4] Descargando dependencias y compilando...
call gradlew.bat clean build --refresh-dependencies --no-daemon

echo.
echo ========================================
echo Proceso completado
echo ========================================
pause

