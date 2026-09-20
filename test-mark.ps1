param(
    [Parameter(Mandatory = $true)]
    [string]$Goal
)

$baseUrl = "http://localhost:8081"

Write-Host "`nSending goal:"
Write-Host $Goal
Write-Host ""

# 1. Create task
$body = @{
    goal = $Goal
    provider = "gemini"
} | ConvertTo-Json

$response = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/tasks" `
    -ContentType "application/json" `
    -Body $body

$taskId = $response.taskId

Write-Host "Task ID: $taskId"
Write-Host "Waiting for task..."

# 2. Poll task until it finishes
while ($true) {

    Start-Sleep -Seconds 1

    $task = Invoke-RestMethod `
        -Method Get `
        -Uri "$baseUrl/api/tasks/$taskId"

    Write-Host "Status: $($task.status)"

    if ($task.status -in @("COMPLETED", "FAILED", "NEEDS_INPUT")) {
        break
    }
}

# 3. Print complete task response
Write-Host "`n========================================"
Write-Host "TASK RESULT"
Write-Host "========================================"

$task | ConvertTo-Json -Depth 10

