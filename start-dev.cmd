@echo off
rem Jira Portal - fejlesztoi inditas ket ablakban: backend (8080) + frontend (5173).
rem   start-dev.cmd        -> valodi Jira (servicedesk.bnref.hu)
rem   start-dev.cmd mock   -> szimulalt adatok, Jira nelkul (jelszo: demo)
setlocal
cd /d "%~dp0"

where java >nul 2>nul || (echo [HIBA] Nem talalhato Java. Telepits JDK 25-ot vagy ujabbat. & pause & exit /b 1)
where npm >nul 2>nul || (echo [HIBA] Nem talalhato Node.js / npm. Telepits Node.js 20 vagy ujabb verziot. & pause & exit /b 1)

rem Belepes utan a Vite fejlesztoi szerverre iranyitunk vissza.
if not defined PORTAL_FRONTEND_URL set "PORTAL_FRONTEND_URL=http://localhost:5173"

set "PROFILE=default"
set "LABEL=Jira"
if /I "%~1"=="mock" set "PROFILE=mock"
if /I "%~1"=="mock" set "LABEL=mock"

if /I "%PROFILE%"=="mock" goto start
rem Valodi Jira: a Windows tanusitvanytarat hasznaljuk (ceges tanusitvany).
if not defined JIRA_TRUST_STORE_TYPE set "JIRA_TRUST_STORE_TYPE=Windows-ROOT"
if exist "backend\config\application.yml" goto start
echo.
echo [FIGYELEM] Hianyzik a backend\config\application.yml fajl, ezert a "Belepes Jiraval" gomb nem lesz elerheto.
echo            Masold le a backend\config\application.yml.example fajlt application.yml neven,
echo            es ird bele a Client ID-t es a Client secretet. Addig csak a jelszavas belepes mukodik.
echo.

:start
echo Inditas: %LABEL% mod
start "Jira Portal - backend (%LABEL%)" cmd /k "cd /d %~dp0backend && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=%PROFILE%"
start "Jira Portal - frontend" cmd /k "cd /d %~dp0frontend && (if not exist node_modules npm install) && npm run dev"
echo Ha mindket ablak elindult, nyisd meg: http://localhost:5173
