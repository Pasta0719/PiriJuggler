@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Building production jars...
call gradlew.bat test packagePiriJars --console=plain
if errorlevel 1 goto :fail

echo [2/4] Building runtime helpers...
call gradlew.bat -PruntimeAcceptance=true -PruntimeScenario=god02-main -PruntimeEvidencePhase=GOD_PHASE_02 :runtime-test-client:build :runtime-test-paper:build --console=plain
if errorlevel 1 goto :fail

echo [3/4] Running GOD Phase 02 real-client acceptance...
py runtime-test-support\run_god_phase02.py
if errorlevel 1 (
  python runtime-test-support\run_god_phase02.py
  if errorlevel 1 goto :fail
)

echo [4/4] GOD Phase 02 runtime PASS
echo Evidence: runtime-evidence\GOD_PHASE_02\result.json
exit /b 0

:fail
echo GOD Phase 02 runtime FAILED
echo Check runtime-evidence\GOD_PHASE_02\result.json and latest attempt logs.
exit /b 1
