<#
.SYNOPSIS
    Stops the services started by run-local.ps1.
    Finds them by the pid file and, as a fallback, by any java process running a jar from this folder.
#>
param([switch]$Quiet)

$root = $PSScriptRoot
$stopped = @{}

$pidFile = Join-Path $root "logs\pids.txt"
if (Test-Path $pidFile) {
    foreach ($line in Get-Content $pidFile) {
        $parts = $line -split " ", 2
        $process = Get-Process -Id ([int]$parts[0]) -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -like "java*") {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            $stopped[$process.Id] = $parts[1]
        }
    }
    Remove-Item $pidFile -ErrorAction SilentlyContinue
}

# Fallback: java processes whose command line points at a jar inside this project.
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains($root) -and -not $stopped.ContainsKey([int]$_.ProcessId) } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        $stopped[[int]$_.ProcessId] = "java"
    }

if (-not $Quiet) {
    if ($stopped.Count -eq 0) { Write-Host "Nothing was running." }
    else { $stopped.GetEnumerator() | ForEach-Object { Write-Host "Stopped $($_.Value) (pid $($_.Key))" } }
}
