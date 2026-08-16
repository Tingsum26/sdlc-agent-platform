$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$skillRoot = Join-Path $repoRoot 'skills\importing-pod-members'
$skillFile = Join-Path $skillRoot 'SKILL.md'
$uiFile = Join-Path $skillRoot 'agents\openai.yaml'
$templateFile = Join-Path $skillRoot 'assets\pod-members-template.csv'
$contractFile = Join-Path $skillRoot 'references\import-contract.md'

$failures = [System.Collections.Generic.List[string]]::new()

function Require-Path([string]$Path, [string]$Label) {
  if (-not (Test-Path -LiteralPath $Path)) { $failures.Add("Missing ${Label}: $Path") }
}

Require-Path $skillFile 'SKILL.md'
Require-Path $uiFile 'agents/openai.yaml'
Require-Path $templateFile 'CSV template'
Require-Path $contractFile 'import contract reference'

if (Test-Path -LiteralPath $skillFile) {
  $skill = Get-Content -LiteralPath $skillFile -Raw -Encoding utf8
  $required = @(
    'name: importing-pod-members',
    'description: Use when',
    'DRY_RUN',
    'APPLY',
    'explicit confirmation',
    'Workflow MCP',
    'UNKNOWN_POD',
    'ASSIGNEE_NOT_ONBOARDED',
    'Import Report'
  )
  foreach ($value in $required) {
    if (-not $skill.Contains($value)) { $failures.Add("SKILL.md missing required contract text: $value") }
  }
  if ($skill -match '(?i)direct(ly)?\s+(write|connect).{0,30}mongo') {
    $failures.Add('SKILL.md permits direct Mongo access')
  }
}

if (Test-Path -LiteralPath $templateFile) {
  $header = Get-Content -LiteralPath $templateFile -Encoding utf8 | Select-Object -First 1
  $expected = 'employeeId,displayName,email,podId,role,capabilities,githubLogin,status'
  if ($header -ne $expected) { $failures.Add("Unexpected CSV header: $header") }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}

Write-Output 'PASS: importing-pod-members skill contract is complete.'
