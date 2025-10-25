@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Unified, hardened launcher for Nexus Converter (Windows)
rem - Prefers JAR next to this script; falls back to .\target\
rem - Requires Java 17+
rem - Helpful diagnostics via --doctor / --dry-run
rem - Optional auto-build via --build when pom.xml and Maven are available

set "SCRIPT_DIR=%~dp0"
set "DOCTOR=0"
set "DRY_RUN=0"
set "AUTO_BUILD=0"
set "APP_ARGS="

:parse_args
if "%~1"=="" goto after_parse
if "%~1"=="--doctor" set DOCTOR=1& shift & goto parse_args
if "%~1"=="--dry-run" set DRY_RUN=1& shift & goto parse_args
if "%~1"=="--build" set AUTO_BUILD=1& shift & goto parse_args
if "%~1"=="--no-build" set AUTO_BUILD=0& shift & goto parse_args
if "%~1"=="--help" goto :usage
if "%~1"=="-h" goto :usage
if "%~1"=="--" shift & goto collect_rest
set APP_ARGS=!APP_ARGS! ""%~1""
shift
goto parse_args

:collect_rest
if "%~1"=="" goto after_parse
set APP_ARGS=!APP_ARGS! ""%~1""
shift
goto collect_rest

:after_parse

rem --- Java checks ---
where java >nul 2>&1
if errorlevel 1 (
  echo [ERROR] 'java' not found in PATH. Please install Java (JDK 17+).
  exit /b 1
)

set "SPEC="
set "MAJOR="
for /f "usebackq tokens=1,* delims==" %%A in (`java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version"`) do (
  set "SPEC=%%B"
)
if defined SPEC (
  set "SPEC=!SPEC: =!"
  for /f "tokens=1 delims=." %%M in ("!SPEC!") do set "MAJOR=%%M"
)
if not defined MAJOR (
  for /f "tokens=3 delims= " %%V in ('java -version 2^>^&1 ^| findstr /r /c:"version"') do set "JV=%%~V"
  for /f "tokens=1 delims=." %%M in ("!JV:"=!") do set "MAJOR=%%M"
)
if not defined MAJOR set MAJOR=0
set /a MAJOR_INT=%MAJOR%
if %MAJOR_INT% LSS 17 (
  echo [ERROR] Java 17+ required. Detected specification version: !SPEC!
  exit /b 1
)

rem --- Functions ---
goto find_jar_entry

:find_jar
set "JAR="
for /f "delims=" %%F in ('dir /b /a:-d "%SCRIPT_DIR%nexus-converter-*.jar" ^| findstr /vi "-sources.jar" ^| findstr /vi "-javadoc.jar"') do (
  set "JAR=%SCRIPT_DIR%%%F"
  goto :eof
)
if exist "%SCRIPT_DIR%target\" (
  for /f "delims=" %%F in ('dir /b /a:-d "%SCRIPT_DIR%target\nexus-converter-*.jar" ^| findstr /vi "-sources.jar" ^| findstr /vi "-javadoc.jar"') do (
    set "JAR=%SCRIPT_DIR%target\%%F"
    goto :eof
  )
)
goto :eof

:attempt_build
if not "%AUTO_BUILD%"=="1" goto :eof
if not exist "%SCRIPT_DIR%pom.xml" goto :eof
where mvn >nul 2>&1 || goto :eof
echo [INFO] No runnable JAR found. Attempting Maven build (skip tests)...
pushd "%SCRIPT_DIR%" >nul
mvn -B -q -DskipTests=true package
set "ERR=%ERRORLEVEL%"
popd >nul
if not "%ERR%"=="0" (
  echo [WARN] Maven build failed (code %ERR%).
)
goto :eof

:doctor
echo.
echo [INFO] Environment diagnostics
if defined SPEC (
  echo   Java spec version: !SPEC!
) else (
  echo   Java spec version: (unknown)
)
where mvn >nul 2>&1 && echo   Maven: found || echo   Maven: not found
if exist "%SCRIPT_DIR%pom.xml" (
  echo   Project: pom.xml present
) else (
  echo   Project: no pom.xml in script directory
)
call :find_jar
if defined JAR (
  for %%Z in ("!JAR!") do echo   Runnable JAR: %%~nxZ
) else (
  echo   Runnable JAR: not detected in "%SCRIPT_DIR%" or "%SCRIPT_DIR%target\"
)
goto :eof

:find_jar_entry
call :find_jar
if not defined JAR (
  call :attempt_build
  call :find_jar
)

if "%DOCTOR%"=="1" (
  call :doctor
  if not "%DRY_RUN%"=="1" exit /b 0
)

if not defined JAR (
  echo [ERROR] Could not find a runnable JAR (nexus-converter-*.jar).
  echo         Place the JAR next to this script or build with Maven.
  echo         Hint: nexus-converter.bat --build -- ^<app-args^>
  exit /b 1
)

echo [INFO] Launching Nexus Converter
for %%Z in ("%JAR%") do echo [INFO] Using JAR: %%~nxZ
echo [INFO] Arguments: %APP_ARGS%

if "%DRY_RUN%"=="1" (
  echo [OK] Dry run complete — no execution performed.
  exit /b 0
)

java -jar "%JAR%" %APP_ARGS%
set "CODE=%ERRORLEVEL%"
echo.
if "%CODE%"=="0" (
  echo [OK] Nexus Converter finished.
) else (
  echo [ERROR] Nexus Converter exited with code %CODE%.
)
exit /b %CODE%

:usage
echo Nexus Converter Launcher
echo.
echo Usage: %~n0 [--build^|--no-build] [--dry-run] [--doctor] [--help] [--] [app-args...]
echo.
echo Options:
echo   --build       If no runnable JAR is found, attempt 'mvn -q -DskipTests=true package'.
echo   --no-build    Never attempt to build automatically (default).
echo   --dry-run     Show what would run (Java/JAR/args) without executing the app.
echo   --doctor      Print environment diagnostics (Java/Maven/JAR detection) and exit.
echo   -h, --help    Show this help and exit.
echo   --            Treat the rest of the arguments as application arguments.
exit /b 0
