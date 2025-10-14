@echo off
echo ==========================================
echo SOLUCION DEFINITIVA - ERROR GRADLE TASKS
echo ==========================================
cd /d "%~dp0"

echo.
echo Este script resolvera: "Unable to find Gradle tasks to build"
echo.
pause

echo.
echo [1/8] Cerrando Android Studio si esta abierto...
echo Por favor, CIERRA Android Studio ahora si esta abierto.
echo.
pause

echo.
echo [2/8] Deteniendo procesos de Gradle...
call gradlew.bat --stop
timeout /t 3 /nobreak > nul

echo.
echo [3/8] Eliminando cache del proyecto...
if exist .gradle rmdir /s /q .gradle
if exist .idea rmdir /s /q .idea
if exist .kotlin rmdir /s /q .kotlin
if exist build rmdir /s /q build
if exist app\build rmdir /s /q app\build

echo.
echo [4/8] Eliminando archivos de configuracion del IDE...
if exist *.iml del /q *.iml
if exist app\*.iml del /q app\*.iml

echo.
echo [5/8] Limpiando con Gradle...
call gradlew.bat clean

echo.
echo [6/8] Verificando estructura del proyecto...
call gradlew.bat projects

echo.
echo [7/8] Listando tareas disponibles...
call gradlew.bat tasks

echo.
echo [8/8] Compilando el proyecto...
call gradlew.bat assembleDebug

echo.
echo ==========================================
echo COMPLETADO!
echo ==========================================
echo.
echo Ahora ABRE Android Studio y:
echo 1. Abre este proyecto
echo 2. File ^> Sync Project with Gradle Files
echo 3. Build ^> Rebuild Project
echo.
echo El error deberia estar resuelto.
echo ==========================================
pause

