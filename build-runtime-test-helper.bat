@echo off
setlocal
pushd "%~dp0"
call gradlew.bat -PruntimeAcceptance :runtime-test-paper:jar --console=plain
set "piriBuildExit=%ERRORLEVEL%"
if not "%piriBuildExit%"=="0" goto :done
if not exist "dist\test-only" mkdir "dist\test-only"
for %%F in ("runtime-test-support\paper\build\libs\piri-runtime-test-paper-*.jar") do copy /Y "%%~fF" "dist\test-only\piri-runtime-test-paper.jar" >nul
echo Runtime test helper built: dist\test-only\piri-runtime-test-paper.jar
:done
if not "%piriBuildExit%"=="0" echo Runtime test helper build failed.
popd
if /I not "%~1"=="--no-pause" pause
exit /b %piriBuildExit%
