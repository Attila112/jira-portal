@echo off
rem Fejlesztoi inditas ket ablakban: backend (8080) + frontend (5173).
rem   start-dev.cmd        -> mock Jira (szimulalt adatok, jelszo: demo)
rem   start-dev.cmd jira   -> valodi Jira (application.yml / JIRA_URL)
setlocal
rem Belepes utan a Vite fejlesztoi szerverre iranyitunk vissza.
if not defined PORTAL_FRONTEND_URL set "PORTAL_FRONTEND_URL=http://localhost:5173"
set "PROFILE=mock"
if /I "%1"=="jira" set "PROFILE=default"
rem Valodi Jira eseten a Windows tanusitvanytarat hasznaljuk (ceges belso CA).
if /I "%1"=="jira" if not defined JIRA_TRUST_STORE_TYPE set "JIRA_TRUST_STORE_TYPE=Windows-ROOT"
start "Jira Portal - backend (%PROFILE%)" cmd /k "cd /d %~dp0backend && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=%PROFILE%"
start "Jira Portal - frontend" cmd /k "cd /d %~dp0frontend && (if not exist node_modules npm install) && npm run dev"
echo Ha mindketto elindult: http://localhost:5173
