#Requires -Version 7.3
# Permanently deletes this Compose project's database volume, then installs fresh demo data.
[CmdletBinding()]
param([ValidateRange(30,1800)][int]$StartupTimeoutSeconds = 300)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$demoRoot = Split-Path -Parent $PSScriptRoot
$demoCompose = Join-Path $demoRoot 'docker-compose.yml'

function Invoke-DemoCompose {
    & docker compose --project-directory $demoRoot -f $demoCompose @args
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed: $($args -join ' ')" }
}

if (-not (Test-Path -LiteralPath (Join-Path $demoRoot '.env'))) {
    throw 'Create .env from .env.example and set your local database passwords before resetting.'
}

# Validate before deleting anything. These are the only persistent volumes declared here.
$demoVolumes = @(& docker compose --project-directory $demoRoot -f $demoCompose config --volumes)
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid; reset was not started.' }
if ($demoVolumes.Count -ne 1 -or $demoVolumes[0].Trim() -ne 'mysql-data') {
    throw 'Unexpected Compose volumes. Expected only mysql-data; reset was not started.'
}
$demoProfile = & docker compose --project-directory $demoRoot -f $demoCompose config --format json |
    ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Compose configuration.' }
$demoProfiles = @(([string]$demoProfile.services.backend.environment.SPRING_PROFILES_ACTIVE) -split ',' | ForEach-Object { $_.Trim() })
if ($demoProfiles -notcontains 'local' -or
    $demoProfiles -contains 'prod' -or $demoProfiles -contains 'production') {
    throw 'Fresh demo reset requires explicit SPRING_PROFILES_ACTIVE=local in .env.'
}
# Do not display resolved Compose configuration: it contains local credentials.
$demoProfile = $null

Write-Host 'Deleting this Compose project database volume and starting a fresh MySQL instance...'
Invoke-DemoCompose down --volumes --remove-orphans
Invoke-DemoCompose up -d --build mysql backend

Write-Host 'Waiting for Flyway migrations and automatic local demo seed through V39...'
$demoDeadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$demoReady = $false
while ((Get-Date) -lt $demoDeadline) {
    $demoResult = & docker compose --project-directory $demoRoot -f $demoCompose exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --user="$MYSQL_USER" --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE version=''39'' AND success=1;"' 2>$null
    if ($LASTEXITCODE -eq 0 -and ([string]($demoResult -join '')).Trim() -eq '1') {
        $demoReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $demoReady) {
    Invoke-DemoCompose logs --tail 100 backend
    throw 'Backend migrations/local demo seed did not finish. Frontend startup was not attempted.'
}

Write-Host 'Flyway V39 automatically seeded the local vendors and demo accounts.'
Invoke-DemoCompose up -d --build --no-deps frontend
$demoDeadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$demoReady = $false
while ((Get-Date) -lt $demoDeadline) {
    try {
        $demoVendors = @(Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/marketplace/vendors' -TimeoutSec 5)
        $demoSlugs = @($demoVendors | ForEach-Object { $_.slug })
        if ($demoSlugs -contains 'saumya-rooftops' -and $demoSlugs -contains 'helio-rural' -and $demoSlugs -contains 'lumina-lighting') {
            $demoReady = $true
            break
        }
    } catch { }
    Start-Sleep -Seconds 2
}
if (-not $demoReady) {
    Invoke-DemoCompose logs --tail 100 backend frontend
    throw 'Demo data loaded, but the marketplace did not become ready. Check the logs above.'
}
Write-Host 'Fresh demo is ready at http://localhost:8080'
Write-Host 'Credentials and vendor associations: docs/demo/multi-vendor-testing.md'
