@echo off
rem
rem Runs boson-cli from a directory holding bin\ and lib\: a Boson distribution, or target\dist.
rem
rem Java is the bundled JRE when there is one, then %JAVA_HOME%, then java on the PATH. JAVA_OPTS is
rem passed to it.

setlocal

rem Resolve the directory holding this script, then its parent.
pushd "%~dp0.."
set "BASEDIR=%CD%"
popd

rem Each test is a statement of its own: a nested if inside a parenthesised block would need
rem delayed expansion to read the variable it just set.
set "JAVA="
if exist "%BASEDIR%\jre\bin\java.exe" set "JAVA=%BASEDIR%\jre\bin\java.exe"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA set "JAVA=java"

rem The serial collector: a command runs briefly and needs no parallel GC threads to start.
"%JAVA%" -XX:+UseSerialGC %JAVA_OPTS% -cp "%BASEDIR%\lib\*" io.bosonnetwork.cli.BosonCli %*
exit /b %ERRORLEVEL%
