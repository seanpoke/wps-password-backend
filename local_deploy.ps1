<#
 .SYNOPSIS
  Local one-click deploy with LIVE progress reporting.
  Builds backend jar + frontend SPA, then restarts backend.

  Every stage prints a numbered banner, a start/end timestamp and an
  elapsed-time heartbeat so you can always tell what it is doing.
  Full transcript: tmp\deploy.log  (tail it: Get-Content -Wait tmp\deploy.log)

  Usage:
    .\local_deploy.ps1                 # full rebuild + restart
    .\local_deploy.ps1 -SkipFrontend   # only rebuild backend jar + restart
    .\local_deploy.ps1 -SkipBackend    # only rebuild frontend SPA + restart
    .\local_deploy.ps1 -BuildOnly      # build both, do NOT restart backend
#>
[CmdletBinding()]
param(
    [switch]$SkipFrontend,
    [switch]$SkipBackend,
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'

function Write-Banner($text) { Write-Host ("`n===== " + $text + " =====") -ForegroundColor Cyan }
function Write-Ok($text)     { Write-Host ("  [ok] " + $text) -ForegroundColor Green }
function Write-Step($idx, $total, $name) {
    Write-Host ("`n>>> [$idx/$total] " + $name) -ForegroundColor Yellow
    Write-Progress -Activity 'Local Deploy' -Status $name -PercentComplete ([int]($idx / $total * 100))
}

$root    = $PSScriptRoot
$logDir  = Join-Path $root 'tmp'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir 'deploy.log'
$startAll = Get-Date
function Log($msg) { $ts = (Get-Date).ToString('HH:mm:ss'); Add-Content -Path $logFile -Value ("[{0}] {1}" -f $ts, $msg) }

function Invoke-BuildStep {
    param([string]$Name,[string]$FilePath,[string[]]$ArgumentList,[string]$WorkDir,[string]$StepLog)
    $stepStart = Get-Date
    Log ("START " + $Name)
    Write-Host ("    (live output -> $StepLog)") -ForegroundColor DarkGray
    Push-Location $WorkDir
    $prevEAP = $ErrorActionPreference
    # native tools (vite/maven) emit non-fatal warnings on stderr; judge success by
    # exit code only, otherwise PowerShell aborts the whole deploy on a warning.
    $ErrorActionPreference = 'SilentlyContinue'
    try {
        # run directly and stream to BOTH host and log file, so progress is visible live
        & $FilePath @ArgumentList 2>&1 | Tee-Object -FilePath $StepLog
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevEAP
        Pop-Location
    }
    $el = [int]((Get-Date) - $stepStart).TotalSeconds
    if ($code -ne 0) { Log ("FAIL " + $Name + " exit=" + $code); throw ($Name + " failed (exit code " + $code + ")") }
    Write-Ok ($Name + " finished in " + $el + "s")
    Log ("DONE " + $Name + " in " + $el + "s")
}

# ===========================================================================
$totalStages = 5
Write-Banner ("Local deploy started at " + $startAll.ToString('HH:mm:ss'))
Write-Host ("  log file: " + $logFile) -ForegroundColor DarkGray
Write-Host ("  options : SkipFrontend=$SkipFrontend  SkipBackend=$SkipBackend  BuildOnly=$BuildOnly") -ForegroundColor DarkGray

# 0. config
Write-Step 0 $totalStages 'load config'
if (Test-Path "env:SPRING_DATASOURCE_URL") { Remove-Item "env:SPRING_DATASOURCE_URL" -ErrorAction SilentlyContinue; Write-Host '  cleared stale SPRING_DATASOURCE_URL' -ForegroundColor DarkGray }
function Import-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path |
        Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') -and $_.Contains('=') } |
        ForEach-Object {
            $line = $_.Trim(); $idx = $line.IndexOf('=')
            $k = $line.Substring(0, $idx).Trim(); $v = $line.Substring($idx + 1).Trim()
            if (-not (Test-Path "env:$k")) { [Environment]::SetEnvironmentVariable($k, $v, 'Process') }
        }
    Write-Ok ("loaded env from " + $Path)
}
Import-EnvFile (Join-Path $root 'ci/conf/.env')
Import-EnvFile (Join-Path $root '.env')

if ($env:JAVA_EXE)              { $javaExe = $env:JAVA_EXE }
elseif ($env:JAVA_HOME)         { $javaExe = Join-Path $env:JAVA_HOME 'bin/java.exe' }
else                            { $javaExe = 'java' }

$mysqlHost     = if ($env:MYSQL_HOST)            { $env:MYSQL_HOST }            else { '127.0.0.1' }
$mysqlPort     = if ($env:MYSQL_PORT)            { $env:MYSQL_PORT }            else { '3306' }
$mysqlUser     = if ($env:MYSQL_USER)            { $env:MYSQL_USER }            else { 'root' }
$mysqlPassword = if ($null -ne $env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD }     else { '' }
$redisHost     = if ($env:REDIS_HOST)            { $env:REDIS_HOST }            else { '127.0.0.1' }
$redisPort     = if ($env:REDIS_PORT)            { $env:REDIS_PORT }            else { '6379' }
$redisPassword = if ($null -ne $env:REDIS_PASSWORD) { $env:REDIS_PASSWORD }     else { '' }
$springProfile = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
Write-Ok ("DB=${mysqlHost}:${mysqlPort} user=$mysqlUser  Redis=${redisHost}:${redisPort}  profile=$springProfile")

$jarName = 'wps-password-backend-1.0.0.jar'

# 1. stop old backend
Write-Step 1 $totalStages 'stop old backend'
$running = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
           Where-Object { $_.CommandLine -and $_.CommandLine.Contains($jarName) }
if ($running) {
    $running | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 3
    Write-Ok ("stopped old backend (pid " + ($running.ProcessId -join ',') + ")")
} else { Write-Ok 'no running backend found' }

# 2. build backend
if (-not $SkipBackend) {
    Write-Step 2 $totalStages 'build backend jar (mvn package)'
    Invoke-BuildStep -Name 'backend build' -FilePath 'mvn' `
        -ArgumentList @('-q', 'package', '-DskipTests') -WorkDir $root `
        -StepLog (Join-Path $logDir 'mvn.log')
} else {
    Write-Step 2 $totalStages 'build backend jar (SKIPPED via -SkipBackend)'
    Write-Host '  skipped' -ForegroundColor DarkGray
}

# 3. build frontend
if (-not $SkipFrontend) {
    Write-Step 3 $totalStages 'build frontend SPA (npm run build)'
    Invoke-BuildStep -Name 'frontend build' -FilePath 'npm' `
        -ArgumentList @('run', 'build') -WorkDir (Join-Path $root 'frontend') `
        -StepLog (Join-Path $logDir 'npm.log')
} else {
    Write-Step 3 $totalStages 'build frontend SPA (SKIPPED via -SkipFrontend)'
    Write-Host '  skipped' -ForegroundColor DarkGray
}

# 4. start backend + poll health
if ($BuildOnly) {
    Write-Step 4 $totalStages 'start backend (SKIPPED via -BuildOnly)'
    Write-Host '  build only, not starting backend' -ForegroundColor DarkGray
} else {
    Write-Step 4 $totalStages 'start backend + wait for health'
    $args = @(
        "-DMYSQL_HOST=$mysqlHost", "-DMYSQL_PORT=$mysqlPort",
        "-DMYSQL_USER=$mysqlUser", "-DMYSQL_PASSWORD=$mysqlPassword",
        "-DREDIS_HOST=$redisHost", "-DREDIS_PORT=$redisPort", "-DREDIS_PASSWORD=$redisPassword",
        "-Dspring.profiles.active=$springProfile",
        '-jar', "target\$jarName"
    )
    Start-Process -FilePath $javaExe -ArgumentList $args `
        -RedirectStandardOutput (Join-Path $logDir 'backend.log') `
        -RedirectStandardError  (Join-Path $logDir 'backend.err') -NoNewWindow
    Write-Host '  backend process launched, polling http://localhost:8081/ ...' -ForegroundColor DarkGray
    $ready = $false
    for ($i = 1; $i -le 60; $i++) {
        Start-Sleep -Seconds 1
        try {
            $r = Invoke-WebRequest -Uri 'http://localhost:8081/' -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) {
                Write-Ok ("backend ready (HTTP 200) after {0}s" -f $i)
                Log ("backend ready after {0}s" -f $i)
                $ready = $true
                break
            }
        } catch { }
        if ($i % 5 -eq 0) { Write-Host ("    ... waiting ({0}s) HTTP not ready yet" -f $i) -ForegroundColor DarkGray }
    }
    if (-not $ready) { Write-Host '  [warn] backend not ready within 60s, check tmp\backend.err' -ForegroundColor Red }
}

# 5. smoke check
Write-Step 5 $totalStages 'smoke check'
try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8081/config/version/check?platform=win&current=1.0.0' -UseBasicParsing -TimeoutSec 5
    Write-Ok ("version/check HTTP=" + $r.StatusCode)
} catch { Write-Host ('  [warn] version/check error: ' + $_.Exception.Message) -ForegroundColor Red }

$elAll = [int]((Get-Date) - $startAll).TotalSeconds
Write-Banner ("Deploy finished in " + $elAll + "s")
Write-Progress -Activity 'Local Deploy' -Completed
Write-Host ("  view full log: Get-Content -Wait $logFile") -ForegroundColor DarkGray
