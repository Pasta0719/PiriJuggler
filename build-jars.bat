@echo off
setlocal
pushd "%~dp0"
call gradlew.bat test packagePiriJars --console=plain
set "piriBuildExit=%ERRORLEVEL%"
if not "%piriBuildExit%"=="0" goto :done
call build-runtime-test-helper.bat --no-pause
set "piriBuildExit=%ERRORLEVEL%"
:done
if "%piriBuildExit%"=="0" (
  echo Build successful. Paper and Fabric JARs are in the dist folder.
  echo Runtime test helper and Vault provider are in dist\test-only.
) else (
  echo Build failed. See the Gradle error above.
)
popd
if /I not "%~1"=="--no-pause" pause
exit /b %piriBuildExit%
