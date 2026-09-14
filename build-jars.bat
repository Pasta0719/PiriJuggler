@echo off
setlocal
pushd "%~dp0"
call gradlew.bat test packagePiriJars --console=plain
set "piriBuildExit=%ERRORLEVEL%"
if "%piriBuildExit%"=="0" (echo Build successful. Paper and Fabric JARs are in the dist folder.) else (echo Build failed. See the Gradle error above.)
popd
if /I not "%~1"=="--no-pause" pause
exit /b %piriBuildExit%
