@echo off

echo ==========================================
echo      Starting SchemConvert Build Process
echo ==========================================

REM Ensure we are in the project root (parent of this script)
pushd "%~dp0.."

REM --- Step 1: Build the Java JAR ---
echo.
echo [1/3] Building Java JAR with Gradle...
echo.
call scripts\gradlew.bat clean shadowJar
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle build failed!
    exit /b 1
)

REM --- Step 2: Build the Python Executable ---
echo.
echo [2/3] Building Python Executable...
echo.
call scripts\build_executable.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Python executable build failed!
    exit /b 1
)

echo ==========================================
echo           Build Complete!
echo ==========================================
echo Java JAR location: build\libs\SchemConvert-1.3.0-all.jar
echo Python EXE location: build_artifacts\dist\convert_all.exe

popd
pause
