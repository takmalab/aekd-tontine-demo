@echo off
rem ===========================================================================
rem  AEKD-Tontine - rendre l'application locale accessible a des testeurs a
rem  distance, via un tunnel HTTPS Cloudflare. Details : scripts\partager.ps1
rem
rem  Options :
rem    partager.bat --sans-compilation   reutilise les builds existants
rem
rem  Pour arreter : fermer les fenetres "AEKD Backend (partage)" et "AEKD Tunnel".
rem ===========================================================================
setlocal
cd /d "%~dp0"
set "OPTS="
for %%A in (%*) do (
    if /I "%%~A"=="--sans-compilation" set "OPTS=-SansCompilation"
)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\partager.ps1" %OPTS%
pause
endlocal
