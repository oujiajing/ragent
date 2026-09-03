param(
    [string]$CacheRoot = (Join-Path $PSScriptRoot '../.output/legal-pdf-cache'),
    [string]$Output = (Join-Path $PSScriptRoot '../.output/legal-pdf-audit'),
    [switch]$Strict
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$cachePath = [IO.Path]::GetFullPath($CacheRoot)
$outputPath = [IO.Path]::GetFullPath($Output)
if (-not (Test-Path -LiteralPath $cachePath)) { throw 'Cache missing; prepare it once before offline replay.' }
Push-Location $repo
try {
    & ./mvnw.cmd -o -pl rag '-Dtest=LegalPdfOfflineAuditTest' "-Dlegal.pdf.cache.dir=$cachePath" "-Dlegal.pdf.audit.out=$outputPath" "-Dlegal.pdf.audit.strict=$($Strict.IsPresent.ToString().ToLowerInvariant())" test
    $auditExit = $LASTEXITCODE
} finally { Pop-Location }
exit $auditExit
