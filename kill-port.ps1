# kill-port.ps1 - Safely terminates processes holding port 8081
$ErrorActionPreference = "SilentlyContinue"
$conns = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($conns) {
    foreach ($c in $conns) {
        $p = $c.OwningProcess
        if ($p -and $p -ne $PID) {
            Write-Host "Freeing port 8081 (PID: $p)..." -ForegroundColor Yellow
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
} else {
    Write-Host "Port 8081 is free." -ForegroundColor Green
}
