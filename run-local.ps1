<#
.SYNOPSIS
    Builds PayPilot and starts all four services on this machine. No Docker needed.

.DESCRIPTION
    Starts mock-psp (8083), ledger-service (8082), payment-service (8081) and api-gateway (8080),
    waits until each one is healthy, then opens the dashboard at http://localhost:8080.
    Logs go to .\logs, data (H2 databases) to .\data. Stop everything with .\stop-local.ps1

.EXAMPLE
    .\run-local.ps1                 # build, start, open the browser
.EXAMPLE
    .\run-local.ps1 -SkipBuild      # start again without rebuilding
.EXAMPLE
    .\run-local.ps1 -Fresh          # wipe .\data first (empty database)
#>
param(
    [switch]$SkipBuild,
    [switch]$Fresh,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

$services = @(
    @{ Name = "mock-psp";        Port = 8083 },
    @{ Name = "ledger-service";  Port = 8082 },
    @{ Name = "payment-service"; Port = 8081 },
    @{ Name = "api-gateway";     Port = 8080 }
)

function Write-Step($text) { Write-Host "==> $text" -ForegroundColor Cyan }

# --- 1. Prerequisites ----------------------------------------------------------------------
$java = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { "$env:JAVA_HOME\bin\java.exe" } else { "java" }
if (-not (Get-Command $java -ErrorAction SilentlyContinue)) {
    throw "Java not found. Install JDK 17 or newer (JDK 21 recommended) and make sure 'java' is on PATH."
}
# 'java -version' prints to stderr; Windows PowerShell 5.1 would treat that as an error under "Stop".
$ErrorActionPreference = "Continue"
$versionLine = (& $java -version 2>&1 | Select-Object -First 1 | ForEach-Object { "$_" })
$ErrorActionPreference = "Stop"
Write-Step "Using $versionLine"

# --- 2. Build ------------------------------------------------------------------------------
if (-not $SkipBuild) {
    Write-Step "Building (first build downloads dependencies and takes a few minutes)"
    # Trust the Windows certificate store. Fixes "PKIX path building failed" when antivirus
    # software or a proxy inspects HTTPS traffic.
    $env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT $env:MAVEN_OPTS"
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw "Maven not found. Install Maven 3.9+ and make sure 'mvn' is on PATH (or run with -SkipBuild if the jars are built)."
    }
    $ErrorActionPreference = "Continue"   # Maven may print warnings to stderr
    & mvn -B -q -DskipTests package
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($buildExit -ne 0) { throw "Maven build failed. Run 'mvn -DskipTests package' to see the full error." }
}

# --- 3. Start ------------------------------------------------------------------------------
& "$root\stop-local.ps1" -Quiet
Start-Sleep -Milliseconds 500
foreach ($s in $services) {
    $listener = Get-NetTCPConnection -LocalPort $s.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        $owner = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        throw "Port $($s.Port) (needed by $($s.Name)) is already used by '$($owner.ProcessName)' (pid $($listener.OwningProcess)). Close that program or change server.port in $($s.Name)\src\main\resources\application.yml."
    }
}
New-Item -ItemType Directory -Force "$root\logs" | Out-Null
if ($Fresh -and (Test-Path "$root\data")) {
    Write-Step "Deleting .\data (fresh databases)"
    Remove-Item -Recurse -Force "$root\data"
}
New-Item -ItemType Directory -Force "$root\data" | Out-Null
Remove-Item "$root\logs\pids.txt" -ErrorAction SilentlyContinue

foreach ($s in $services) {
    $jar = Get-ChildItem "$root\$($s.Name)\target\$($s.Name)-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
    if (-not $jar) { throw "No jar for $($s.Name). Run without -SkipBuild." }
    $process = Start-Process -FilePath $java `
        -ArgumentList @("-Xmx384m", "-jar", "`"$($jar.FullName)`"") `
        -WorkingDirectory $root `
        -RedirectStandardOutput "$root\logs\$($s.Name).log" `
        -RedirectStandardError "$root\logs\$($s.Name).err.log" `
        -WindowStyle Hidden -PassThru
    Add-Content "$root\logs\pids.txt" "$($process.Id) $($s.Name)"
    Write-Step "Started $($s.Name) (pid $($process.Id), port $($s.Port))"
}

# --- 4. Wait until healthy -----------------------------------------------------------------
foreach ($s in $services) {
    $deadline = (Get-Date).AddSeconds(120)
    $healthy = $false
    while ((Get-Date) -lt $deadline) {
        try {
            # Accept: application/json, otherwise Windows PowerShell 5.1 returns the body as bytes.
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 -Headers @{ Accept = "application/json" } "http://localhost:$($s.Port)/actuator/health"
            $body = if ($r.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($r.Content) } else { $r.Content }
            if ($body -match '"UP"') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 700
    }
    if (-not $healthy) {
        Write-Host "$($s.Name) did not become healthy. Last log lines:" -ForegroundColor Red
        Get-Content "$root\logs\$($s.Name).log" -Tail 30
        throw "$($s.Name) failed to start"
    }
    Write-Host "    $($s.Name) is up" -ForegroundColor Green
}

Write-Host ""
Write-Host "PayPilot is running" -ForegroundColor Green
Write-Host "  Dashboard        http://localhost:8080"
Write-Host "  Payment API docs http://localhost:8081/swagger-ui.html"
Write-Host "  Ledger API docs  http://localhost:8082/swagger-ui.html"
Write-Host "  Logs             .\logs      Stop with .\stop-local.ps1"
if (-not $NoBrowser) { Start-Process "http://localhost:8080" }
