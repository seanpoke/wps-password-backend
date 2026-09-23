<#
 .SYNOPSIS
  Local one-click deploy: build backend jar + frontend SPA, then restart backend.
  Runtime config is read from environment variables, or a gitignored .env file
  (ci/conf/.env, falling back to .env at project root). NEVER hardcode secrets.
  Usage: .\local_deploy.ps1   (run at project root)
#>
$ErrorActionPreference = 'Stop'

# Clear any stale SPRING_DATASOURCE_URL left in the session env so it cannot
# override the JDBC URL composed from .env (MYSQL_*). Otherwise the app may
# connect to the wrong database (e.g. checkin) and fail to start.
if (Test-Path "env:SPRING_DATASOURCE_URL") {
    Remove-Item "env:SPRING_DATASOURCE_URL" -ErrorAction SilentlyContinue
    Write-Host "[deploy] cleared stale SPRING_DATASOURCE_URL from session"
}

$root    = $PSScriptRoot
$jarName = 'wps-password-backend-1.0.0.jar'

# --- load config: environment variables > .env file (gitignored) > safe defaults ---
function Import-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path |
        Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') -and $_.Contains('=') } |
        ForEach-Object {
            $line = $_.Trim()
            $idx  = $line.IndexOf('=')
            $k    = $line.Substring(0, $idx).Trim()
            $v    = $line.Substring($idx + 1).Trim()
            # env var already set in the shell wins over the file
            if (-not (Test-Path "env:$k")) {
                [Environment]::SetEnvironmentVariable($k, $v, 'Process')
            }
        }
    Write-Host ('[deploy] loaded env from ' + $Path)
}
# prefer ci/conf/.env (gitignored), then project-root .env
Import-EnvFile (Join-Path $root 'ci/conf/.env')
Import-EnvFile (Join-Path $root '.env')

# JDK executable: explicit JAVA_EXE > $env:JAVA_HOME/bin/java.exe > java on PATH
if ($env:JAVA_EXE) {
    $javaExe = $env:JAVA_EXE
} elseif ($env:JAVA_HOME) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin/java.exe'
} else {
    $javaExe = 'java'
}

# runtime DB / Redis config (no hardcoded secrets; fall back to safe defaults)
$mysqlHost     = if ($env:MYSQL_HOST)            { $env:MYSQL_HOST }            else { '127.0.0.1' }
$mysqlPort     = if ($env:MYSQL_PORT)            { $env:MYSQL_PORT }            else { '3306' }
$mysqlUser     = if ($env:MYSQL_USER)            { $env:MYSQL_USER }            else { 'root' }
$mysqlPassword = if ($null -ne $env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD }     else { '' }
$redisHost     = if ($env:REDIS_HOST)            { $env:REDIS_HOST }            else { '127.0.0.1' }
$redisPort     = if ($env:REDIS_PORT)            { $env:REDIS_PORT }            else { '6379' }
$redisPassword = if ($null -ne $env:REDIS_PASSWORD) { $env:REDIS_PASSWORD }     else { '' }
$springProfile = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }

# 1. stop running backend (match only this jar, leave other java procs alone)
$running = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
           Where-Object { $_.CommandLine -and $_.CommandLine.Contains($jarName) }
if ($running) {
    $running | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 3
    Write-Host '[deploy] stopped old backend'
} else {
    Write-Host '[deploy] no running backend found'
}

# 2. build backend
Set-Location $root
Write-Host '[deploy] building backend jar ...'
mvn -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'backend build failed' }

# 3. build frontend (output to ../webroot)
Set-Location "$root\frontend"
Write-Host '[deploy] building frontend SPA ...'
# Vite prints chunk-size warnings to stderr; under ErrorActionPreference='Stop' that
# would be misread as fatal and abort the deploy. Relax briefly, judge by exit code only.
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'SilentlyContinue'
npm run build 2>&1 | Out-String | Write-Host
$ErrorActionPreference = $prevEAP
if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }

# 4. start backend
Set-Location $root
$logDir = "$root\tmp"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$args = @(
    "-DMYSQL_HOST=$mysqlHost", "-DMYSQL_PORT=$mysqlPort",
    "-DMYSQL_USER=$mysqlUser", "-DMYSQL_PASSWORD=$mysqlPassword",
    "-DREDIS_HOST=$redisHost", "-DREDIS_PORT=$redisPort", "-DREDIS_PASSWORD=$redisPassword",
    "-Dspring.profiles.active=$springProfile",
    '-jar', "target\$jarName"
)
Start-Process -FilePath $javaExe -ArgumentList $args `
    -RedirectStandardOutput "$logDir\backend.log" `
    -RedirectStandardError "$logDir\backend.err" -NoNewWindow
Write-Host '[deploy] backend starting ...'
Start-Sleep -Seconds 25

# 5. smoke check
try {
    $r = Invoke-WebRequest -Uri http://localhost:8081/ -UseBasicParsing -TimeoutSec 5
    Write-Host ('[deploy] root HTTP=' + $r.StatusCode + ' (admin UI ready)')
} catch {
    Write-Host ('[deploy] root check error: ' + $_.Exception.Message)
}
Write-Host '[deploy] done.'
