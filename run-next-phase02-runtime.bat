@echo off
setlocal
cd /d "%~dp0"

echo [1/3] Building production and runtime helper jars...
call gradlew.bat test packagePiriJars :runtime-test-paper:build :runtime-test-client:build --console=plain
if errorlevel 1 goto :fail

echo [2/3] Running NEXT Phase 02 real Paper+Fabric acceptance...
py -3 runtime-test-support\run_next_phase02.py
if errorlevel 1 goto :fail

echo [3/3] NEXT Phase 02 runtime PASS
exit /b 0

:fail
echo NEXT Phase 02 runtime FAILED
echo Check runtime-evidence\NEXT_PHASE_02\result.json and latest attempt logs.
exit /b 1
