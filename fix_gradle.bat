@echo off
echo ========================================
echo SOLUCION RAPIDA - Sincronizar Gradle
echo ========================================
cd /d "%~dp0"

echo.
echo Paso 1: Deteniendo daemons de Gradle...
call gradlew.bat --stop

echo.
echo Paso 2: Listando tareas disponibles...
call gradlew.bat tasks --all

echo.
echo Paso 3: Sincronizando dependencias...
call gradlew.bat --refresh-dependencies

echo.
echo Paso 4: Compilando proyecto...
call gradlew.bat build

echo.
echo ========================================
echo Si funcionó, el proyecto está listo!
echo Si hay errores, ejecuta: clean_build.bat
echo ========================================
pause

