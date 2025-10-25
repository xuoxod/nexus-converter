@echo off
setlocal

REM Script to run the nexus-converter application on Windows

REM --- User Requirements ---
REM This script requires Java (JRE or JDK) to be installed and the 'java'
REM command to be available in the system's PATH.

REM Check if java command exists
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: 'java' command not found in your PATH. >&2
    echo Please install Java (JRE or JDK) and ensure it's added to your system's PATH. >&2
    pause
    exit /b 1
)

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
REM Ensure SCRIPT_DIR has a trailing backslash for concatenation
if not "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR%\"

REM Assume the target directory is relative to the script's location
REM If distributing, the JAR should ideally be next to this script.
set "JAR_DIR=%SCRIPT_DIR%"
REM If running directly from the build workspace, uncomment the next line:
REM set "JAR_DIR=%SCRIPT_DIR%target\"

REM Find the executable JAR file (newest one, excluding sources/javadoc)
set "JAR_FILE="
pushd "%JAR_DIR%" || ( echo Error: Cannot access directory '%JAR_DIR%'. >&2 & pause & exit /b 1 )
for /f "delims=" %%i in ('dir /b /od "nexus-converter-*.jar" 2^>nul ^| findstr /v /i /c:"-sources.jar" /c:"-javadoc.jar"') do (
    set "JAR_FILE=%%~fi"
)
popd

REM Check if the JAR file was found
if not defined JAR_FILE (
    echo Error: Could not find the nexus-converter-*.jar in "%JAR_DIR%" >&2
    echo Make sure the JAR file is in the same directory as this script, >&2
    echo or run 'mvn package' if running from the project workspace. >&2
    pause
    exit /b 1
)

if not exist "%JAR_FILE%" (
    echo Error: Found path does not exist or is not a file: "%JAR_FILE%" >&2
    pause
    exit /b 1
)

REM Run the Java application, passing all script arguments ("%*") to the JAR
echo --- Running Nexus Converter ---
echo Using JAR: %JAR_FILE%
echo Arguments: %*
echo.
java -jar "%JAR_FILE%" %*

REM Pause at the end if the script was likely double-clicked (no arguments)
if [%1]==[] (
    echo.
    echo --- Execution Finished ---
    pause
)

endlocal
