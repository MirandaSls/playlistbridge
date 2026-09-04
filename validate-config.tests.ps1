$ErrorActionPreference = 'Stop'

$validator = Join-Path $PSScriptRoot 'validate-config.ps1'
if (-not (Test-Path -LiteralPath $validator)) {
    throw "Expected validation script at $validator"
}

& $validator
if ($LASTEXITCODE -ne 0) {
    throw "Configuration validation failed with exit code $LASTEXITCODE"
}

Write-Output 'Configuration validation passed.'
