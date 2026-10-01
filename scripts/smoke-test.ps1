<#
.SYNOPSIS
    End-to-end check of a running PayPilot stack, through the API gateway.
    Start the stack first with .\run-local.ps1, then run .\scripts\smoke-test.ps1

    Every step prints PASS or FAIL; the script exits with code 1 if anything failed.
#>
param([string]$Gateway = "http://localhost:8080")

$MerchantKey = "pk_test_demo_merchant"
$AdminKey = "sk_admin_demo_only"
$script:Failures = 0

function Invoke-Api {
    param([string]$Method, [string]$Path, [string]$Key, $Body = $null, [string]$IdempotencyKey = $null)
    $headers = @{}
    if ($Key) { $headers["X-Api-Key"] = $Key }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }
    $params = @{ Method = $Method; Uri = "$Gateway$Path"; Headers = $headers; UseBasicParsing = $true; ContentType = "application/json" }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 6) }
    try {
        $r = Invoke-WebRequest @params
        $json = if ($r.Content) { $r.Content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Headers = $r.Headers; Body = $json }
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        $json = $null
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            try { $json = $_.ErrorDetails.Message | ConvertFrom-Json } catch { $json = $_.ErrorDetails.Message }
        }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Headers = $response.Headers; Body = $json }
    }
}

function Check([string]$Name, [bool]$Condition, [string]$Detail = "") {
    if ($Condition) { Write-Host "  PASS  $Name" -ForegroundColor Green }
    else { Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red; $script:Failures++ }
}

function New-Payment([long]$Amount, [string]$Payer, [string]$Key = [guid]::NewGuid().ToString()) {
    Invoke-Api -Method POST -Path "/api/v1/payments" -Key $MerchantKey -IdempotencyKey $Key -Body @{
        amount = $Amount; currency = "INR"; method = "UPI"; payer = $Payer; description = "smoke test"
    }
}

Write-Host "PayPilot smoke test against $Gateway" -ForegroundColor Cyan
try {
    $null = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "$Gateway/actuator/health"
} catch {
    Write-Host "The gateway is not reachable at $Gateway. Start the stack first with .\run-local.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "`n1. Security"
Check "no API key -> 401" ((Invoke-Api GET "/api/v1/payments" $null).Status -eq 401)
Check "merchant key cannot read the ledger -> 403" ((Invoke-Api GET "/api/v1/ledger/accounts" $MerchantKey).Status -eq 403)

Write-Host "`n2. Payment and idempotency"
$null = Invoke-Api POST "/api/v1/ops/chaos/reset" $AdminKey
$key ="smoke-" + [guid]::NewGuid()
$first = New-Payment 49900 "anishka@okaxis" $key
Check "create -> 201 SUCCEEDED" ($first.Status -eq 201 -and $first.Body.status -eq "SUCCEEDED") "(got $($first.Status) $($first.Body.status))"
$replay = New-Payment 49900 "anishka@okaxis" $key
Check "same Idempotency-Key -> 200, same payment, no second charge" ($replay.Status -eq 200 -and $replay.Body.id -eq $first.Body.id)
$reuse = New-Payment 99900 "anishka@okaxis" $key
Check "same key, different amount -> 422" ($reuse.Status -eq 422)

Write-Host "`n3. Declines"
$declined = New-Payment 1500 "decline@upi"
Check "payer 'decline' -> FAILED INSUFFICIENT_FUNDS" ($declined.Body.status -eq "FAILED" -and $declined.Body.failureCode -eq "INSUFFICIENT_FUNDS")

Write-Host "`n4. PSP outage and failover"
$null = Invoke-Api PUT "/api/v1/ops/chaos/alpha" $AdminKey @{ down = $true; declineRate = 0; errorRate = 0; timeoutRate = 0; latencyMs = 50 }
$failover = New-Payment 25000 "failover@upi"
Check "alpha down -> payment still SUCCEEDED via beta" ($failover.Body.status -eq "SUCCEEDED" -and $failover.Body.psp -eq "beta") "(psp $($failover.Body.psp))"
$attempts = Invoke-Api GET "/api/v1/payments/$($failover.Body.id)/attempts" $MerchantKey
Check "attempts show alpha NOT_PROCESSED then beta SUCCEEDED" ($attempts.Body[0].outcome -eq "NOT_PROCESSED" -and $attempts.Body[1].outcome -eq "SUCCEEDED")
$null = Invoke-Api POST "/api/v1/ops/chaos/reset" $AdminKey

Write-Host "`n5. Timeout with unknown outcome (resolved by the sweeper)"
$slow = New-Payment 30000 "timeout@upi"
Check "PSP timeout -> 202 PROCESSING (no failover)" ($slow.Status -eq 202 -and $slow.Body.status -eq "PROCESSING")
$resolved = $null
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    $resolved = Invoke-Api GET "/api/v1/payments/$($slow.Body.id)" $MerchantKey
    if ($resolved.Body.status -ne "PROCESSING") { break }
}
Check "sweeper asked the PSP and resolved it -> SUCCEEDED" ($resolved.Body.status -eq "SUCCEEDED") "(status $($resolved.Body.status))"

Write-Host "`n6. Refund"
$refund = Invoke-Api POST "/api/v1/payments/$($first.Body.id)/refund" $MerchantKey
Check "refund -> REFUNDED" ($refund.Body.status -eq "REFUNDED")

Write-Host "`n7. Ledger"
Start-Sleep -Seconds 2
$integrity = Invoke-Api GET "/api/v1/ledger/integrity" $AdminKey
Check "books balance (debits == credits)" ($integrity.Body.balanced -eq $true) "($($integrity.Body.totalDebits) vs $($integrity.Body.totalCredits))"
$entries = Invoke-Api GET "/api/v1/ledger/entries?paymentId=$($first.Body.id)" $AdminKey
Check "refunded payment has a capture and a refund entry" (@($entries.Body).Count -eq 2)

Write-Host "`n8. Ops"
$stats = Invoke-Api GET "/api/v1/ops/stats" $AdminKey
Check "ops stats available" ($stats.Status -eq 200 -and $stats.Body.totals.payments -gt 0)
Write-Host ("    payments={0} successRate={1}% transport={2}" -f $stats.Body.totals.payments, $stats.Body.totals.successRate, $stats.Body.eventTransport)

Write-Host ""
if ($script:Failures -eq 0) { Write-Host "All checks passed" -ForegroundColor Green; exit 0 }
Write-Host "$script:Failures check(s) failed" -ForegroundColor Red
exit 1
