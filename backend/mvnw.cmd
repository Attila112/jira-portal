@echo off
rem Egyszeru Maven wrapper Windowsra: elso futaskor letolti a Mavent a felhasznalo .m2 mappajaba.
setlocal
set "MVN_VER=3.9.11"
set "MVN_BASE=%USERPROFILE%\.m2\wrapper"
set "MVN_HOME=%MVN_BASE%\apache-maven-%MVN_VER%"
if not exist "%MVN_HOME%\bin\mvn.cmd" (
  echo Maven %MVN_VER% letoltese ide: %MVN_BASE%
  if not exist "%MVN_BASE%" mkdir "%MVN_BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VER%/apache-maven-%MVN_VER%-bin.zip'; $z=Join-Path $env:TEMP 'apache-maven-%MVN_VER%-bin.zip'; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $z; Expand-Archive -Force -Path $z -DestinationPath '%MVN_BASE%'; Remove-Item $z"
  if errorlevel 1 (
    echo A Maven letoltese nem sikerult. Ellenorizd az internetkapcsolatot vagy a proxyt.
    exit /b 1
  )
)
call "%MVN_HOME%\bin\mvn.cmd" %*
