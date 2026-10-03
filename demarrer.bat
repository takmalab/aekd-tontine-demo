@echo off
rem ===========================================================================
rem  AEKD-Tontine - demarrage de l'environnement de developpement
rem
rem  Ordre volontaire (memoire limitee du poste) :
rem    1. compilation du backend (jar)
rem    2. frontend Angular, dans sa propre fenetre, puis attente du port 4200
rem    3. backend Spring Boot, dans sa propre fenetre, puis attente du port 8080
rem    4. ouverture du navigateur
rem
rem  Options :
rem    demarrer.bat --sans-compilation   reutilise le jar existant
rem    demarrer.bat --sans-navigateur    n'ouvre pas le navigateur
rem
rem  Pour arreter : fermer les deux fenetres "AEKD Frontend" et "AEKD Backend".
rem ===========================================================================
setlocal EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"

rem Outils Windows appeles par leur chemin complet : si Git Bash ou d'autres outils
rem Unix sont dans le PATH, "timeout" ou "find" designeraient d'autres programmes.
set "SYS=%SystemRoot%\System32"
set "NETSTAT=%SYS%\netstat.exe"
set "FINDSTR=%SYS%\findstr.exe"
set "WHERE=%SYS%\where.exe"
set "TIMEOUT=%SYS%\timeout.exe"

set "SKIP_BUILD=0"
set "SKIP_BROWSER=0"
for %%A in (%*) do (
    if /I "%%~A"=="--sans-compilation" set "SKIP_BUILD=1"
    if /I "%%~A"=="--sans-navigateur" set "SKIP_BROWSER=1"
)

echo.
echo  === AEKD-Tontine : demarrage ===
echo.

rem --- Verifications prealables ----------------------------------------------
"%WHERE%" java >nul 2>&1 || (echo [ERREUR] Java est introuvable. Installez Java 21. & goto :fail)
"%WHERE%" npx >nul 2>&1 || (echo [ERREUR] Node.js/npx est introuvable. & goto :fail)

"%NETSTAT%" -ano | "%FINDSTR%" /R /C:":5432 .*LISTENING" >nul
if errorlevel 1 (
    echo [ATTENTION] PostgreSQL ne semble pas demarre sur le port 5432.
    echo             Demarrez-le ^(service PostgreSQL ou "docker compose up -d"^) puis relancez.
    goto :fail
)

"%NETSTAT%" -ano | "%FINDSTR%" /R /C:":8080 .*LISTENING" >nul
if not errorlevel 1 (
    echo [ERREUR] Le port 8080 est deja utilise : un backend tourne peut-etre deja.
    echo          Fermez-le ^(fenetre "AEKD Backend" ou processus java^) puis relancez.
    goto :fail
)

rem --- 1. Compilation du backend ----------------------------------------------
if "%SKIP_BUILD%"=="1" goto :find_jar
"%WHERE%" mvn >nul 2>&1 || (echo [ERREUR] Maven ^(mvn^) est introuvable. & goto :fail)
echo [1/4] Compilation du backend ^(1 a 3 minutes^)...
pushd backend
rem "clean" garantit que le jar correspond au code source actuel.
call mvn -q clean package -DskipTests
if errorlevel 1 (
    popd
    echo [ERREUR] La compilation du backend a echoue.
    goto :fail
)
popd

:find_jar
set "JAR="
for %%F in (backend\target\tontine-backend-*.jar) do set "JAR=%%~fF"
if not defined JAR (
    echo [ERREUR] Jar du backend introuvable. Relancez sans --sans-compilation.
    goto :fail
)

rem --- 2. Frontend ------------------------------------------------------------
"%NETSTAT%" -ano | "%FINDSTR%" /R /C:":4200 .*LISTENING" >nul
if not errorlevel 1 (
    echo [2/4] Frontend deja demarre sur le port 4200.
) else (
    echo [2/4] Demarrage du frontend ^(fenetre "AEKD Frontend", 1 a 4 minutes^)...
    start "AEKD Frontend" cmd /k "cd /d "%~dp0frontend" && npx ng serve"
    call :wait_port 4200 420 "frontend"
    if errorlevel 1 goto :fail
)

rem --- 3. Backend -------------------------------------------------------------
echo [3/4] Demarrage du backend ^(fenetre "AEKD Backend", 1 a 2 minutes^)...
start "AEKD Backend" cmd /k "cd /d "%~dp0backend" && java -Xmx512m -jar "%JAR%""
call :wait_port 8080 300 "backend"
if errorlevel 1 goto :fail

rem --- 4. Navigateur ----------------------------------------------------------
echo [4/4] Application prete : http://localhost:4200
if "%SKIP_BROWSER%"=="0" start "" "http://localhost:4200"
echo.
echo  Pour arreter : fermez les fenetres "AEKD Frontend" et "AEKD Backend".
echo.
endlocal
exit /b 0

rem ---------------------------------------------------------------------------
rem  :wait_port <port> <delai max en secondes> <nom>
rem ---------------------------------------------------------------------------
:wait_port
set /a "WAITED=0"
:wait_loop
"%NETSTAT%" -ano | "%FINDSTR%" /R /C:":%~1 .*LISTENING" >nul
if not errorlevel 1 (
    echo        %~3 pret ^(port %~1^).
    exit /b 0
)
if !WAITED! GEQ %~2 (
    echo [ERREUR] Le %~3 n'a pas demarre en %~2 secondes. Consultez sa fenetre.
    exit /b 1
)
rem timeout refuse de tourner sans console interactive : ping sert alors de pause.
"%TIMEOUT%" /t 3 /nobreak >nul 2>&1
if errorlevel 1 "%SYS%\PING.EXE" -n 4 127.0.0.1 >nul
set /a "WAITED+=3"
goto :wait_loop

:fail
echo.
echo  Demarrage interrompu.
pause
endlocal
exit /b 1
