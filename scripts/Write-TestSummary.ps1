param([Parameter(Mandatory)][string]$ResultsDirectory)
$ErrorActionPreference = 'Stop'
[xml]$trx = Get-Content (Join-Path $ResultsDirectory 'big-bang.trx') -Raw
$namespace = [System.Xml.XmlNamespaceManager]::new($trx.NameTable)
$namespace.AddNamespace('t', 'http://microsoft.com/schemas/VisualStudio/TeamTest/2010')
$tests = @($trx.SelectNodes('//t:UnitTestResult', $namespace) | ForEach-Object {
    [ordered]@{
        name = $_.testName
        outcome = $_.outcome
        duration = $_.duration
        message = $_.SelectSingleNode('t:Output/t:ErrorInfo/t:Message', $namespace).InnerText
        trace = $_.SelectSingleNode('t:Output/t:StdOut', $namespace).InnerText
    }
} | Sort-Object { $_.name })
[ordered]@{
    total = $tests.Count
    passed = @($tests | Where-Object outcome -eq 'Passed').Count
    failed = @($tests | Where-Object outcome -eq 'Failed').Count
    tests = $tests
} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $ResultsDirectory 'summary.json') -Encoding utf8
