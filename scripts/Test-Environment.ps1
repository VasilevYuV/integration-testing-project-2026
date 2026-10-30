$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$ready = $false
for ($i = 0; $i -lt 45; $i++) {
    try {
        $health = Invoke-RestMethod 'http://localhost:18301/health' -TimeoutSec 8
        if ($health.status -eq 'Healthy') { $ready = $true; break }
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $ready) { throw 'Web /health did not become Healthy.' }
$catalogPage = Invoke-WebRequest 'http://localhost:18301/' -TimeoutSec 10
if ($catalogPage.Content -notmatch '\.NET Bot Black Sweatshirt') { throw 'Catalog is absent from Web.' }
$catalog = Invoke-RestMethod 'http://localhost:18302/api/catalog-items' -TimeoutSec 10
if ($catalog.catalogItems.Count -lt 1) { throw 'API catalog is empty.' }
$swagger = Invoke-RestMethod 'http://localhost:18302/swagger/v1/swagger.json' -TimeoutSec 10
if (-not $swagger.paths.'/api/catalog-items') { throw 'Swagger route missing.' }
$blazor = Invoke-RestMethod 'http://localhost:18301/appsettings.Docker.json' -TimeoutSec 10
if ($blazor.baseUrls.apiBase -ne 'http://localhost:18302/api/') { throw 'Browser API address is wrong.' }
$evidence = [ordered]@{
    timestamp = [DateTime]::UtcNow.ToString('o')
    revision = (Get-Content (Join-Path $root 'upstream.lock.json') -Raw | ConvertFrom-Json).commit
    webStatus = [int]$catalogPage.StatusCode
    health = $health
    catalogItemsOnFirstPage = $catalog.catalogItems.Count
    swagger = 'available'
    browserApi = $blazor.baseUrls.apiBase
    scope = 'Environment smoke checks; not the complete KT4 scenario suite'
}
$artifacts = Join-Path $root 'artifacts'
New-Item -ItemType Directory -Force $artifacts | Out-Null
$evidence | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $artifacts 'environment-check.json') -Encoding utf8
Write-Host 'Environment checks passed: Web, SQL-backed catalog, API, Swagger and browser configuration.'
