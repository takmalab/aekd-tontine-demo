# ===========================================================================
#  AEKD-Tontine - partage de l'application locale avec des testeurs a distance
#
#  1. compile le frontend Angular en production (ng build)
#  2. compile le backend (jar)
#  3. demarre le backend, qui sert aussi le frontend : une seule adresse (port 8080)
#  4. ouvre un tunnel HTTPS Cloudflare (gratuit, sans compte) vers ce port
#  5. affiche l'adresse publique https://....trycloudflare.com et la copie
#
#  Options :
#    -SansCompilation   reutilise le frontend et le jar deja compiles
#
#  Lance par partager.bat. Pour arreter : fermer les fenetres
#  "AEKD Backend (partage)" et "AEKD Tunnel".
# ===========================================================================
param([switch]$SansCompilation)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$ToolsDir = Join-Path $Root 'tools'
$FrontendDist = Join-Path $Root 'frontend\dist\tontine-frontend\browser'
$Cloudflared = Join-Path $ToolsDir 'cloudflared.exe'
$CloudflaredUrl = 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe'
$TunnelLog = Join-Path $ToolsDir 'tunnel.log'
$UrlFile = Join-Path $ToolsDir 'url-publique.txt'

function Fail($message) {
    Write-Host ''
    Write-Host "[ERREUR] $message" -ForegroundColor Red
    Write-Host ''
    Write-Host ' Partage interrompu.'
    exit 1
}

function Test-PortListening([int]$port) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
}

function Wait-Port([int]$port, [int]$maxSeconds, [string]$name) {
    $waited = 0
    while (-not (Test-PortListening $port)) {
        if ($waited -ge $maxSeconds) { Fail "Le $name n'a pas demarre en $maxSeconds secondes. Consultez sa fenetre." }
        Start-Sleep -Seconds 3
        $waited += 3
    }
    Write-Host "       $name pret (port $port)."
}

Write-Host ''
Write-Host ' === AEKD-Tontine : partage pour tests a distance ===' -ForegroundColor Cyan
Write-Host ''

# --- Verifications prealables ----------------------------------------------
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail 'Java est introuvable. Installez Java 21.' }
if (-not (Test-PortListening 5432)) {
    Fail 'PostgreSQL ne semble pas demarre sur le port 5432. Demarrez-le (service PostgreSQL ou "docker compose up -d") puis relancez.'
}
if (Test-PortListening 8080) {
    Fail 'Le port 8080 est deja utilise (un backend tourne peut-etre deja, par ex. via demarrer.bat). Fermez-le puis relancez.'
}

# --- 1. Frontend (build de production) ----------------------------------------
if ($SansCompilation -and (Test-Path (Join-Path $FrontendDist 'index.html'))) {
    Write-Host '[1/5] Frontend : build existant reutilise.'
} else {
    if (-not (Get-Command npx -ErrorAction SilentlyContinue)) { Fail 'Node.js/npx est introuvable.' }
    Write-Host '[1/5] Compilation du frontend en production (1 a 3 minutes)...'
    Push-Location (Join-Path $Root 'frontend')
    & npx ng build --configuration production
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { Fail 'La compilation du frontend a echoue.' }
}

# --- 2. Backend (jar) -------------------------------------------------------
$jar = Get-ChildItem (Join-Path $Root 'backend\target') -Filter 'tontine-backend-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if ($SansCompilation -and $jar) {
    Write-Host '[2/5] Backend : jar existant reutilise.'
} else {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Fail 'Maven (mvn) est introuvable.' }
    Write-Host '[2/5] Compilation du backend (1 a 3 minutes)...'
    Push-Location (Join-Path $Root 'backend')
    & mvn -q clean package -DskipTests
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { Fail 'La compilation du backend a echoue.' }
    $jar = Get-ChildItem (Join-Path $Root 'backend\target') -Filter 'tontine-backend-*.jar' |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
}
if (-not $jar) { Fail 'Jar du backend introuvable.' }

# --- 3. Outil de tunnel ------------------------------------------------------
if (-not (Test-Path $Cloudflared)) {
    Write-Host '[3/5] Telechargement de cloudflared (une seule fois, ~60 Mo)...'
    New-Item -ItemType Directory -Force $ToolsDir | Out-Null
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    try {
        Invoke-WebRequest -Uri $CloudflaredUrl -OutFile $Cloudflared -UseBasicParsing
    } catch {
        Remove-Item $Cloudflared -ErrorAction SilentlyContinue
        Fail "Telechargement de cloudflared impossible : $($_.Exception.Message)"
    }
} else {
    Write-Host '[3/5] cloudflared deja present.'
}

# --- 4. Backend + frontend sur le port 8080 ----------------------------------
Write-Host '[4/5] Demarrage du backend (fenetre "AEKD Backend (partage)", 1 a 2 minutes)...'
# Variables heritees par la fenetre du backend uniquement.
$env:APP_FRONTEND_DIR = $FrontendDist
$env:SERVER_FORWARD_HEADERS_STRATEGY = 'native'
# Secret JWT aleatoire a chaque partage : l'adresse etant publique, le secret de
# developpement (connu, versionne) ne doit pas servir a signer les jetons.
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:APP_JWT_SECRET = [Convert]::ToBase64String($bytes)

$backendDir = Join-Path $Root 'backend'
Start-Process cmd -WorkingDirectory $backendDir -ArgumentList '/k', "title AEKD Backend (partage) && java -Xmx512m -jar `"$($jar.FullName)`""
Wait-Port 8080 300 'backend'

# --- 5. Tunnel HTTPS ---------------------------------------------------------
Write-Host '[5/5] Ouverture du tunnel HTTPS (fenetre "AEKD Tunnel")...'
Remove-Item $TunnelLog, $UrlFile -ErrorAction SilentlyContinue
Start-Process cmd -WorkingDirectory $ToolsDir -ArgumentList '/k', "title AEKD Tunnel && `"$Cloudflared`" tunnel --no-autoupdate --url http://localhost:8080 --logfile `"$TunnelLog`""

$publicUrl = $null
for ($i = 0; $i -lt 40 -and -not $publicUrl; $i++) {
    Start-Sleep -Seconds 2
    if (Test-Path $TunnelLog) {
        $match = Select-String -Path $TunnelLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
        if ($match) { $publicUrl = $match.Matches[0].Value }
    }
}
if (-not $publicUrl) { Fail "Adresse du tunnel introuvable. Consultez la fenetre AEKD Tunnel et $TunnelLog." }

Set-Content -Path $UrlFile -Value $publicUrl -Encoding ascii
try { Set-Clipboard -Value $publicUrl } catch {}

Write-Host ''
Write-Host ' ===================================================================' -ForegroundColor Green
Write-Host "  Adresse a envoyer aux testeurs : $publicUrl" -ForegroundColor Green
Write-Host '  (copiee dans le presse-papiers, et dans tools\url-publique.txt)'
Write-Host ' ===================================================================' -ForegroundColor Green
Write-Host ''
Write-Host '  - L''adresse change a chaque lancement.'
Write-Host '  - Le PC doit rester allume et connecte pendant les tests.'
Write-Host '  - Les comptes de demonstration (admin, tresorier) sont accessibles'
Write-Host '    a toute personne qui connait l''adresse : ne la diffusez qu''aux testeurs.'
Write-Host '  - Pour arreter : fermez les fenetres "AEKD Backend (partage)" et "AEKD Tunnel".'
Write-Host ''
