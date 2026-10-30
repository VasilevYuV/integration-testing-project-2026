$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Prepare-Environment.ps1')
Push-Location $root
try {
    & docker compose build
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    # API applies the shared migrations before Web starts to avoid migration races.
    & docker compose up -d database api
    if ($LASTEXITCODE -ne 0) { throw 'Database or API startup failed.' }
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $catalog = Invoke-RestMethod 'http://localhost:18302/api/catalog-items' -TimeoutSec 5
            if ($catalog.catalogItems.Count -gt 0) { $ready = $true; break }
        } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $ready) { throw 'API did not initialize the catalog.' }
    & docker compose up -d web
    if ($LASTEXITCODE -ne 0) { throw 'Web startup failed.' }
    & (Join-Path $PSScriptRoot 'Test-Environment.ps1')
} finally {
    Pop-Location
}
