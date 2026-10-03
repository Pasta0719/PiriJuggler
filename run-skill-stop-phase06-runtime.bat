@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Ensuring verified Paper 1.21 build 130...
powershell -NoProfile -ExecutionPolicy Bypass -File runtime-test-support\ensure-paper-1.21-130.ps1
if errorlevel 1 goto :fail

echo [2/4] Building, testing, and exporting Phase06 vectors/economy...
call gradlew.bat test packagePiriJars :common:exportSkillStopPhase06Vectors :common:exportSkillStopEconomy -PruntimeAcceptance=true -PruntimeScenario=skill06-main -PruntimeEvidencePhase=SKILL_STOP_PHASE_06 :runtime-test-paper:build :runtime-test-client:build --console=plain
if errorlevel 1 goto :fail
set SKILL_STOP_PRODUCTION_MODELS=build\skill-stop-production-models.json
node skill-stop-phases\research\verify-economy.cjs > build\skill-stop-phase06-economy.json
if errorlevel 1 goto :fail

echo [3/4] Running Phase06 real Paper/Fabric final acceptance...
python runtime-test-support\run_skill_stop_phase06.py
if errorlevel 1 goto :fail

echo [4/4] SKILL STOP Phase06 PASS
exit /b 0
:fail
echo SKILL STOP Phase06 FAILED
exit /b 1
