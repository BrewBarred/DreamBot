@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Folder containing this bat (ends with \)
set "HERE=%~dp0"

REM ETAbot root is 4 levels up from ...\scripts\main\world\map\
for %%I in ("%HERE%..\..\..\..") do set "ETABOT=%%~fI"

REM Classpath (IntelliJ default output)
set "CP=%ETABOT%\out\production\ETAbot"

REM Use java (not javaw) to show the real error
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

echo [run_map] HERE=%HERE%
echo [run_map] ETABOT=%ETABOT%
echo [run_map] CP=%CP%
echo [run_map] JAVA_EXE=%JAVA_EXE%
echo.

"%JAVA_EXE%" -cp "%CP%" main.world.map.ExplvsMap

echo.
echo [run_map] ExitCode=%ERRORLEVEL%
pause
endlocal
