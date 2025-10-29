@echo off
setlocal
set SCRIPT_DIR=%~dp0
pushd "%SCRIPT_DIR%android" >nul
call gradlew.bat %*
set EXIT_CODE=%ERRORLEVEL%
popd >nul
exit /b %EXIT_CODE%
