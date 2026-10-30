param(
    [Parameter(Mandatory)][string]$Project,
    [Parameter(Mandatory)][string]$ResultsDirectory
)
$ErrorActionPreference = 'Stop'
# Only the disposable test project created by Run-BigBangTests may be interrupted.
if ($Project -notmatch '^kt4-check-[a-f0-9]{8}$') { throw 'Expected a temporary KT4 test project.' }
$compose = @('compose', '-f', 'compose.yaml', '-f', 'compose.tests.yaml', '-p', $Project)

function Read-Probe([string]$url, [string]$file) {
    $status = & docker @compose run --rm --no-deps tests curl --silent --show-error `
        --max-time 20 --output "/results/$file" --write-out '%{http_code}' $url
    if ($LASTEXITCODE -ne 0) { throw "Probe failed: $url" }
    return [int](($status -join '').Trim())
}

try {
    & docker @compose stop api
    if ($LASTEXITCODE -ne 0) { throw 'Cannot stop the test API.' }
    $healthStatus = Read-Probe 'http://web:18301/health' 'health-api-down.json'
    # Brand 5 is a different cache key from ordinary catalog tests.
    $catalogStatus = Read-Probe 'http://web:18301/?CatalogModel.BrandFilterApplied=5' 'catalog-api-down.html'
    $health = Get-Content (Join-Path $ResultsDirectory 'health-api-down.json') -Raw | ConvertFrom-Json
    if ($healthStatus -ne 503 -or $health.status -ne 'Unhealthy' -or $catalogStatus -ne 200) {
        throw 'Unexpected application behavior while the API is stopped.'
    }
} finally {
    & docker @compose start api
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restore the test API.' }
    & docker @compose run --rm --no-deps tests curl --fail --silent --show-error --output /dev/null `
        --retry 30 --retry-delay 2 --retry-all-errors --retry-max-time 90 --max-time 10 'http://web:18301/health'
    if ($LASTEXITCODE -ne 0) { throw 'Application health did not recover.' }
}
$recoveredStatus = Read-Probe 'http://web:18301/health' 'health-recovered.json'
[ordered]@{
    experiment = 'Controlled API outage; not an application defect'
    healthWhileStopped = $healthStatus
    detailsWhileStopped = $health
    catalogWhileStopped = $catalogStatus
    healthAfterRecovery = $recoveredStatus
} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $ResultsDirectory 'api-outage.json') -Encoding utf8
