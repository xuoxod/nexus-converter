@echo off
setlocal enabledelayedexpansion

echo ✨ Welcome to the Nexus Converter Launcher! ✨
echo.

REM --- Configuration ---
set "SCRIPT_DIR=%~dp0"
REM Ensure SCRIPT_DIR has a trailing backslash for concatenation
if not "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR%\"
set "RUN_SCRIPT_NAME=run-nexus-converter.bat"
set "RUN_SCRIPT_PATH=%SCRIPT_DIR%%RUN_SCRIPT_NAME%"
set "JAR_NAME_PATTERN=nexus-converter-*.jar"

REM --- Helper Functions (using labels) ---
goto :check_java

:print_error
    echo ❌ Error: %~1
    goto :eof

:print_info
    echo ℹ️  %~1
    goto :eof

:print_success
    echo ✅ %~1
    goto :eof

REM --- 1. Check for Java ---
:check_java
    call :print_info "Checking for Java installation..."
    where java >nul 2>nul
    if %errorlevel% neq 0 (
        call :print_error "'java' command not found in your PATH."
        echo    Please install Java (JRE or JDK version 11 or later)
        echo    and ensure the 'java' command is accessible.
        echo    Visit: https://www.java.com/ or https://adoptium.net/
        goto :exit_script_error
    ) else (
        REM Getting Java version reliably in batch is tricky, just confirm presence
        call :print_success "Java found."
    )
    goto :check_jar

REM --- 2. Check for the main application JAR ---
:check_jar
    call :print_info "Looking for the application JAR (%JAR_NAME_PATTERN%)..."
    set "JAR_FILE="
    pushd "%SCRIPT_DIR%" >nul 2>nul || ( call :print_error "Cannot access directory '%SCRIPT_DIR%'."; goto :exit_script_error )
    for /f "delims=" %%i in ('dir /b /od "%JAR_NAME_PATTERN%" 2^>nul ^| findstr /v /i /c:"-sources.jar" /c:"-javadoc.jar"') do (
        set "JAR_FILE=%%~fi"
    )
    popd

    if not defined JAR_FILE (
        call :print_error "Application JAR (%JAR_NAME_PATTERN%) not found in '%SCRIPT_DIR%'"
        echo    Please ensure the application JAR file is in the same directory as this script.
        echo    If you built from source, run the packaging script first ('package_for_distribution.sh').
        goto :exit_script_error
    ) else (
        for %%f in ("%JAR_FILE%") do set JAR_BASENAME=%%~nxf
        call :print_success "Application JAR found: !JAR_BASENAME!"
    )
    goto :check_run_script

REM --- 3. Check for the run script ---
:check_run_script
    call :print_info "Looking for the execution script (%RUN_SCRIPT_NAME%)..."
    if not exist "%RUN_SCRIPT_PATH%" (
        call :print_error "Execution script '%RUN_SCRIPT_NAME%' not found in '%SCRIPT_DIR%'"
        echo    This script is required to run the application. Please ensure it's present.
        goto :exit_script_error
    ) else (
        call :print_success "Execution script found: %RUN_SCRIPT_NAME%"
    )
    goto :run_app

REM --- 4. Ready to Run ---
:run_app
    echo.
    call :print_info "All checks passed! Preparing to launch Nexus Converter..."
    echo    Using JAR: !JAR_BASENAME!
    echo    Arguments passed: %*
    echo  REM Bell sound!
    echo.

    REM Execute the actual run script, passing all arguments
    call "%RUN_SCRIPT_PATH%" %*

    set APP_EXIT_CODE=%errorlevel% REM Capture exit code
    echo.
    if %APP_EXIT_CODE% equ 0 ( call :print_success "Nexus Converter finished." ) else ( call :print_error "Nexus Converter exited with code %APP_EXIT_CODE%." )
    goto :exit_script %APP_EXIT_CODE%

REM --- Exit Points ---
:exit_script_error
    echo. & echo Launcher script finished with errors. & pause & exit /b 1

:exit_script
    if [%1]==[] ( echo. & pause ) REM Pause only if launcher itself was likely double-clicked
    exit /b %1