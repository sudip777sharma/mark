# dev.ps1 - MARK Auto-Restart Runner for Nodemon
$ErrorActionPreference = "SilentlyContinue"

# Check and free port 8081 if occupied by a previous run
$conns = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
if ($conns) {
    foreach ($c in $conns) {
        $procId = $c.OwningProcess
        if ($procId -and $procId -ne $PID) {
            Write-Host "[NODEMON] Freeing port 8081 (PID: $procId)..." -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Milliseconds 400
}

Write-Host "[NODEMON] Launching MARK Spring Boot application..." -ForegroundColor Cyan
& .\mvnw.cmd spring-boot:run
