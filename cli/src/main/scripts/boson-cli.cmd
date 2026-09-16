@echo off
rem
rem Runs boson-cli from a directory holding bin\ and lib\: a Boson distribution, or target\dist.
rem
rem Java is the bundled JRE when there is one, then %JAVA_HOME%, then java on the PATH. JAVA_OPTS is
rem passed to it. Without a bundled JRE, Java 17 or later is required and is checked for.

setlocal

rem Resolve the directory holding this script, then its parent.
pushd "%~dp0.."
set "BASEDIR=%CD%"
popd

rem Each test is a statement of its own: a nested if inside a parenthesised block would need
rem delayed expansion to read the variable it just set. For the same reason there is no goto or
rem call here - this file has Unix line endings, which cmd.exe seeks through unreliably.
set "JAVA="
set "BUNDLED="
if exist "%BASEDIR%\jre\bin\java.exe" set "JAVA=%BASEDIR%\jre\bin\java.exe"
if exist "%BASEDIR%\jre\bin\java.exe" set "BUNDLED=1"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA set "JAVA=java"

rem With no bundled runtime this is the portable package, so the Java found above is the user's.
rem Check it: a too-old JVM otherwise fails with UnsupportedClassVersionError, which names a
rem bytecode level rather than the thing to fix. Java 8 reports 1.8.0_x, whose leading 1 correctly
rem compares as older than 17.
set "JAVA_VER="
if not defined BUNDLED for /f "tokens=3" %%v in ('"%JAVA%" -version 2^>^&1 ^| findstr /i "version"') do if not defined JAVA_VER set "JAVA_VER=%%~v"
if not defined BUNDLED if not defined JAVA_VER echo Error: no Java runtime found. 1>&2
if not defined BUNDLED if not defined JAVA_VER echo Hint: install Java 17 or later, or point JAVA_HOME at one. 1>&2
if not defined BUNDLED if not defined JAVA_VER exit /b 1
set "JAVA_MAJOR="
if defined JAVA_VER for /f "delims=." %%a in ("%JAVA_VER%") do set "JAVA_MAJOR=%%a"
if defined JAVA_MAJOR if %JAVA_MAJOR% LSS 17 echo Error: Boson needs Java 17 or later, but found Java %JAVA_MAJOR%. 1>&2
if defined JAVA_MAJOR if %JAVA_MAJOR% LSS 17 echo Hint: install Java 17 or later, or point JAVA_HOME at one. 1>&2
if defined JAVA_MAJOR if %JAVA_MAJOR% LSS 17 exit /b 1

rem The serial collector: a command runs briefly and needs no parallel GC threads to start.
"%JAVA%" -XX:+UseSerialGC %JAVA_OPTS% -cp "%BASEDIR%\lib\*" io.bosonnetwork.cli.BosonCli %*
exit /b %ERRORLEVEL%
