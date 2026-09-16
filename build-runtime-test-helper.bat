@echo off
setlocal
pushd "%~dp0"
call gradlew.bat -PruntimeAcceptance :runtime-test-paper:jar :runtime-test-vault:jar :runtime-test-client:remapJar --console=plain
set "piriBuildExit=%ERRORLEVEL%"
if not "%piriBuildExit%"=="0" goto :done
if not exist "dist\test-only" mkdir "dist\test-only"
for %%F in ("runtime-test-support\paper\build\libs\piri-runtime-test-paper-*.jar") do copy /Y "%%~fF" "dist\test-only\piri-runtime-test-paper.jar" >nul
for %%F in ("runtime-test-support\vault\build\libs\piri-runtime-test-vault-*.jar") do copy /Y "%%~fF" "dist\test-only\piri-runtime-test-vault.jar" >nul
for %%F in ("runtime-test-support\client\build\libs\piri-runtime-test-client-*.jar") do (
  echo %%~nxF | findstr /I /V /C:"-sources.jar" >nul && copy /Y "%%~fF" "dist\test-only\piri-runtime-test-client.jar" >nul
)
echo Runtime test helper built: dist\test-only\piri-runtime-test-paper.jar
echo Runtime test client helper built: dist\test-only\piri-runtime-test-client.jar
echo Runtime test Vault provider built: dist\test-only\piri-runtime-test-vault.jar
:done
if not "%piriBuildExit%"=="0" echo Runtime test helper build failed.
popd
if /I not "%~1"=="--no-pause" pause
exit /b %piriBuildExit%
