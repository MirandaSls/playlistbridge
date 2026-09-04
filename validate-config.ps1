$ErrorActionPreference = 'Stop'

function Assert-Contains {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Description
    )

    if ($Text -notmatch $Pattern) {
        throw "Missing $Description"
    }
}

$composePath = Join-Path $PSScriptRoot 'docker-compose.yml'
$envPath = Join-Path $PSScriptRoot '.env.example'

if (-not (Test-Path -LiteralPath $composePath)) {
    throw "Missing docker-compose.yml"
}
if (-not (Test-Path -LiteralPath $envPath)) {
    throw "Missing .env.example"
}

$compose = Get-Content -Raw -LiteralPath $composePath
$envExample = Get-Content -Raw -LiteralPath $envPath
$backendDockerfilePath = Join-Path $PSScriptRoot 'backend/Dockerfile'
$frontendDockerfilePath = Join-Path $PSScriptRoot 'frontend/Dockerfile'
$nextConfigPath = Join-Path $PSScriptRoot 'frontend/next.config.mjs'

Assert-Contains $compose '(?m)^services:\s*$' 'services section'
Assert-Contains $compose '(?m)^\s{1,4}backend:\s*$' 'backend service'
Assert-Contains $compose '(?m)^\s{1,4}frontend:\s*$' 'frontend service'
Assert-Contains $compose ([regex]::Escape('- "8080:8080"')) 'backend port mapping'
Assert-Contains $compose ([regex]::Escape('- "3000:3000"')) 'frontend port mapping'
Assert-Contains $compose 'NEXT_PUBLIC_API_BASE_URL' 'frontend API base URL'
foreach ($path in @($backendDockerfilePath, $frontendDockerfilePath, $nextConfigPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing deployment file $path"
    }
}

$backendDockerfile = Get-Content -Raw -LiteralPath $backendDockerfilePath
$frontendDockerfile = Get-Content -Raw -LiteralPath $frontendDockerfilePath
$nextConfig = Get-Content -Raw -LiteralPath $nextConfigPath

Assert-Contains $backendDockerfile '(?im)^FROM .+ AS build' 'backend builder stage'
Assert-Contains $backendDockerfile '(?im)^FROM .+ AS runtime' 'backend runtime stage'
Assert-Contains $backendDockerfile 'mvn -B -DskipTests package' 'backend Maven packaging'
Assert-Contains $backendDockerfile '(?im)^USER spring\s*$' 'backend non-root runtime user'
Assert-Contains $backendDockerfile '(?im)^HEALTHCHECK ' 'backend image healthcheck'
Assert-Contains $backendDockerfile '/api/health' 'backend health endpoint probe'

Assert-Contains $frontendDockerfile '(?im)^FROM .+ AS deps' 'frontend dependencies stage'
Assert-Contains $frontendDockerfile '(?im)^FROM .+ AS builder' 'frontend builder stage'
Assert-Contains $frontendDockerfile '(?im)^FROM .+ AS runner' 'frontend runtime stage'
Assert-Contains $frontendDockerfile 'npm ci' 'reproducible frontend install'
Assert-Contains $frontendDockerfile 'npm run build' 'frontend production build'
Assert-Contains $frontendDockerfile 'ARG NEXT_PUBLIC_API_BASE_URL' 'frontend API build argument'
Assert-Contains $frontendDockerfile '(?im)^USER nextjs\s*$' 'frontend non-root runtime user'
Assert-Contains $frontendDockerfile '(?im)^HEALTHCHECK ' 'frontend image healthcheck'

Assert-Contains $nextConfig 'output:\s*["'']standalone["'']' 'Next standalone output'
Assert-Contains $compose 'condition:\s*service_healthy' 'backend readiness dependency'
Assert-Contains $compose '(?ms)frontend:.*build:.*args:.*NEXT_PUBLIC_API_BASE_URL' 'frontend API build wiring'
Assert-Contains $compose '(?ms)backend:.*healthcheck:' 'backend Compose healthcheck'
Assert-Contains $compose '(?ms)frontend:.*healthcheck:' 'frontend Compose healthcheck'

$requiredVariables = @(
    'BACKEND_PUBLIC_URL',
    'CORS_ALLOWED_ORIGINS',
    'FRONTEND_PUBLIC_URL',
    'GOOGLE_CLIENT_ID',
    'GOOGLE_CLIENT_SECRET',
    'GOOGLE_REDIRECT_URI',
    'NEXT_PUBLIC_API_BASE_URL',
    'SPOTIFY_CLIENT_ID',
    'SPOTIFY_CLIENT_SECRET',
    'SPOTIFY_REDIRECT_URI',
    'SPRING_PROFILES_ACTIVE'
)

foreach ($variable in $requiredVariables) {
    Assert-Contains $envExample ("(?m)^$([regex]::Escape($variable))=") ".env.example variable $variable"
    Assert-Contains $compose ("\$\{${variable}:-") "compose variable $variable"
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $docker) {
    Write-Warning 'Docker CLI unavailable; skipped docker compose config parsing.'
    exit 0
}

& $docker.Source compose version *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'Docker Compose unavailable; skipped docker compose config parsing.'
    exit 0
}

& $docker.Source compose --env-file $envPath -f $composePath config --quiet
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose config rejected docker-compose.yml'
}

Write-Output 'docker-compose.yml and .env.example are valid.'
