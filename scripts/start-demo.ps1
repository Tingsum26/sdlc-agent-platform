[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stateRoot = Join-Path $repoRoot '.demo'
$stateFile = Join-Path $stateRoot 'processes.json'
$logRoot = Join-Path $stateRoot 'logs'

if (Test-Path -LiteralPath $stateFile) {
    throw 'Demo state already exists. Run scripts/stop-demo.ps1 first.'
}

New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$service = Start-Process -FilePath (Join-Path $repoRoot 'mvnw.cmd') `
    -ArgumentList @('-q', '-pl', 'apps/workflow-service', 'spring-boot:run', '-Dspring-boot.run.profiles=fake') `
    -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $logRoot 'workflow-service.out.log') `
    -RedirectStandardError (Join-Path $logRoot 'workflow-service.err.log')
$web = Start-Process -FilePath 'pnpm.cmd' `
    -ArgumentList @('--filter', '@sdlc/web-ui', 'dev', '--host', '127.0.0.1') `
    -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $logRoot 'web-ui.out.log') `
    -RedirectStandardError (Join-Path $logRoot 'web-ui.err.log')

@{
    repoRoot = $repoRoot
    processes = @(
        @{ name = 'workflow-service'; pid = $service.Id; startedAt = $service.StartTime.ToUniversalTime().ToString('o') },
        @{ name = 'web-ui'; pid = $web.Id; startedAt = $web.StartTime.ToUniversalTime().ToString('o') }
    )
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile -Encoding utf8

$deadline = (Get-Date).AddSeconds(90)
do {
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2
        $webStatus = Invoke-WebRequest -Uri 'http://127.0.0.1:4173' -UseBasicParsing -TimeoutSec 2
        if ($health.status -eq 'UP' -and $webStatus.StatusCode -eq 200) {
            Write-Output 'Public demo ready: http://127.0.0.1:4173'
            Write-Output "Logs: $logRoot"
            exit 0
        }
    } catch {
        Start-Sleep -Milliseconds 750
    }
} while ((Get-Date) -lt $deadline)

throw "Demo did not become healthy. Inspect $logRoot and run scripts/stop-demo.ps1."
