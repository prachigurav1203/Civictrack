@echo off
REM Runs CivicTrack as a standalone program (embedded database, no MySQL needed).
REM Data folder: backend\data   Uploaded photos: backend\uploads
setlocal
cd /d "%~dp0backend"
if not exist target\civictrack-backend-1.0.0.jar (
  echo The JAR is not built yet. Run build-standalone.bat first.
  pause & exit /b 1
)
echo Starting CivicTrack on http://localhost:8080  (close this window to stop)
start "" http://localhost:8080
java -jar target\civictrack-backend-1.0.0.jar --spring.profiles.active=standalone
pause
