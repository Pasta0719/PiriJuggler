@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Ensuring verified Paper 1.21 build 130...
powershell -NoProfile -ExecutionPolicy Bypass -File runtime-test-support\ensure-paper-1.21-130.ps1
if errorlevel 1 goto :fail

echo [2/4] Building production and runtime helper jars...
call gradlew.bat test packagePiriJars :runtime-test-paper:build :runtime-test-client:build --console=plain
if errorlevel 1 goto :fail

echo [3/4] Running NEXT Phase 02 real Paper+Fabric acceptance...
py -3 runtime-test-support\run_next_phase02.py
if errorlevel 1 goto :fail

echo [4/4] NEXT Phase 02 runtime PASS
exit /b 0

:fail
echo NEXT Phase 02 runtime FAILED
echo Check runtime-evidence\NEXT_PHASE_02\result.json and latest attempt logs.
exit /b 1
