$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$lock = Get-Content (Join-Path $root 'upstream.lock.json') -Raw | ConvertFrom-Json
$upstream = Join-Path $root '.upstream'

if (-not (Test-Path -LiteralPath $upstream)) {
    & git clone $lock.repository $upstream
    if ($LASTEXITCODE -ne 0) { throw 'Cannot clone eShopOnWeb.' }
}
$changes = & git -C $upstream status --porcelain
if ($LASTEXITCODE -ne 0 -or $changes) { throw 'Upstream checkout must be clean.' }
& git -C $upstream checkout --detach $lock.commit
if ($LASTEXITCODE -ne 0) { throw 'Cannot select the locked revision.' }
$actual = & git -C $upstream rev-parse HEAD
if ($actual -ne $lock.commit) { throw 'Upstream revision mismatch.' }

$envPath = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    $password = 'Kt4!' + [Guid]::NewGuid().ToString('N') + 'Aa9'
    @(
        "SQL_PASSWORD=$password"
        "SDK_IMAGE=$($lock.images.sdk)"
        "RUNTIME_IMAGE=$($lock.images.runtime)"
        "SQL_IMAGE=$($lock.images.sql)"
    ) | Set-Content -LiteralPath $envPath -Encoding utf8
}
Write-Host "Prepared eShopOnWeb $actual"
