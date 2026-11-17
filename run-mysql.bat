@echo off
REM Runs the built JAR against your MySQL server (set DB_PASSWORD first, e.g.  setx DB_PASSWORD yourpassword).
setlocal
cd /d "%~dp0backend"
if not exist target\civictrack-backend-1.0.0.jar ( echo Run build-standalone.bat first. & pause & exit /b 1 )
start "" http://localhost:8080
java -jar target\civictrack-backend-1.0.0.jar
pause
