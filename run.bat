@echo off
setlocal

echo Looking for the SQLite JDBC jar in this folder...
set SQLITE_JAR=
for %%f in (sqlite-jdbc*.jar) do set SQLITE_JAR=%%f

if "%SQLITE_JAR%"=="" (
    echo.
    echo [ERROR] No sqlite-jdbc-*.jar found in this folder.
    echo Download it from:
    echo   https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.0.0/sqlite-jdbc-3.46.0.0.jar
    echo and save it in the SAME folder as this script and BorrowEaseGUI.java
    echo.
    pause
    exit /b 1
)

echo Found: %SQLITE_JAR%
echo.
echo Compiling...
javac -cp "%SQLITE_JAR%" *.java
if errorlevel 1 (
    echo.
    echo [ERROR] Compile failed. See the messages above.
    pause
    exit /b 1
)

echo.
echo Running BorrowEase...
echo.
java -cp ".;%SQLITE_JAR%" BorrowEaseGUI

echo.
pause
