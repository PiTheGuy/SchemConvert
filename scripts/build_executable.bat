@echo off
pushd "%~dp0"
if not exist "convert_all.py" (
    echo Error: convert_all.py not found!
    exit /b 1
)

if not exist "..\build_artifacts" mkdir ..\build_artifacts

echo Building executable...
pyinstaller --onefile --clean ^
    --distpath ../build_artifacts/dist ^
    --workpath ../build_artifacts/build ^
    --specpath ../build_artifacts ^
    --name convert_all ^
    convert_all.py

if %ERRORLEVEL% EQU 0 (
    echo Build successful! Executable is in ..\build_artifacts\dist\convert_all.exe
) else (
    echo Build failed!
)
popd
