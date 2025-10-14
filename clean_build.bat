@echo off
echo ========================================
echo Limpiando cache de Gradle completamente
echo ========================================
cd /d "%~dp0"

echo.
echo [1/6] Eliminando directorio app\build...
if exist app\build rmdir /s /q app\build
echo Completado.

echo.
echo [2/6] Eliminando directorio build...
if exist build rmdir /s /q build
echo Completado.

echo.
echo [3/6] Eliminando cache .gradle...
if exist .gradle rmdir /s /q .gradle
echo Completado.

echo.
echo [4/6] Ejecutando gradlew clean...
call gradlew.bat clean
echo Completado.

echo.
echo [5/6] Sincronizando proyecto con Gradle...
call gradlew.bat --refresh-dependencies
echo Completado.

echo.
echo [6/6] Compilando proyecto en modo debug...
call gradlew.bat assembleDebug
echo Completado.

echo.
echo ========================================
echo Proceso finalizado
echo ========================================
pause
