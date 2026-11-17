@echo off
REM ============================================================================
REM  Builds CivicTrack into ONE runnable JAR that contains the website + API.
REM  Needs (once, with internet): JDK 21, Maven, Node.js. Output: backend\target\civictrack-backend-1.0.0.jar
REM ============================================================================
setlocal
cd /d "%~dp0"

echo [1/4] Installing frontend packages...
cd frontend
call npm install || goto :fail
echo [2/4] Building the React app...
call npm run build || goto :fail
cd ..

echo [3/4] Copying the website into the backend...
if exist backend\src\main\resources\static rmdir /s /q backend\src\main\resources\static
mkdir backend\src\main\resources\static
xcopy /e /i /y frontend\dist\* backend\src\main\resources\static\ >nul || goto :fail

echo [4/4] Packaging the JAR with Maven...
cd backend
call mvn -q clean package -DskipTests || goto :fail
cd ..

echo.
echo  DONE. Start the software with:  run-standalone.bat
pause
exit /b 0
:fail
echo.
echo  BUILD FAILED - see the message above.
pause
exit /b 1
