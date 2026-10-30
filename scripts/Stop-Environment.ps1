$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & docker compose down
    if ($LASTEXITCODE -ne 0) { throw 'Environment stop failed.' }
} finally {
    Pop-Location
}
