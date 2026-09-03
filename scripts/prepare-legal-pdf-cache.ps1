param(
    [Parameter(Mandatory=$true)][string]$Pdf,
    [string]$ResultZip,
    [string]$BatchId,
    [switch]$DirectDownload,
    [string]$CacheRoot = (Join-Path $PSScriptRoot '../.output/legal-pdf-cache')
)
$ErrorActionPreference = 'Stop'
$sourcePdf = (Resolve-Path -LiteralPath $Pdf).Path
$pdfHash = (Get-FileHash -LiteralPath $sourcePdf -Algorithm SHA256).Hash.ToLowerInvariant()
$sampleDir = Join-Path $CacheRoot $pdfHash
$manifestPath = Join-Path $sampleDir 'manifest.json'
if (Test-Path -LiteralPath $manifestPath) {
    $existing = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    $existingZip = Join-Path $sampleDir 'result.zip'
    if ((Test-Path -LiteralPath $existingZip) -and
        (Get-FileHash -LiteralPath $existingZip).Hash.ToLowerInvariant() -eq $existing.zipSha256) {
        Write-Output "CACHE_HIT $pdfHash (no network)"
        return
    }
    throw 'Existing cache failed hash validation; preserve it for diagnosis.'
}
if ([bool]$ResultZip -eq [bool]$BatchId) { throw 'Supply exactly one of ResultZip or BatchId.' }
New-Item -ItemType Directory -Path $sampleDir -Force | Out-Null
$zipPath = Join-Path $sampleDir 'result.zip'
$provenance = 'local ZIP supplied by operator; verify source pairing against PDF'
if ($ResultZip) {
    Copy-Item -LiteralPath (Resolve-Path -LiteralPath $ResultZip).Path -Destination $zipPath
} else {
    if (-not $env:MINERU_API_KEY) { throw 'MINERU_API_KEY required only for recovering a completed batch.' }
    # Read existing task only. Never submit/upload/reparse a PDF from this script.
    $headers = @{ Authorization = ('Bearer ' + $env:MINERU_API_KEY) }
    $response = Invoke-RestMethod -Uri ('https://mineru.net/api/v4/extract-results/batch/' + [uri]::EscapeDataString($BatchId)) -Headers $headers
    if ($response.code -ne 0) { throw ('MinerU query failed: code=' + $response.code) }
    $items = @($response.data.extract_result)
    $item = @($items | Where-Object { $_.file_name -eq [IO.Path]::GetFileName($sourcePdf) })
    if ($item.Count -ne 1 -or $item[0].state -ne 'done') { throw 'No unique completed result with matching filename.' }
    $downloadArguments = @('--fail', '--silent', '--show-error', '--location', '--max-time', '120', '--retry', '2', '--output', $zipPath)
    if ($DirectDownload) { $downloadArguments += @('--noproxy', '*') }
    & curl.exe @downloadArguments $item[0].full_zip_url
    if ($LASTEXITCODE -ne 0) { throw 'Completed batch ZIP download failed; no new parse was submitted.' }
    $provenance = 'recovered completed batch; filename matched; historical upload hash unavailable'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    foreach ($spec in @(@('*.md','raw.md'), @('*_content_list.json','content-list.json'), @('*_origin.pdf','origin.pdf'))) {
        $matches = @($archive.Entries | Where-Object { $_.Name -like $spec[0] })
        if ($matches.Count -ne 1) { throw ('Expected exactly one ZIP entry for ' + $spec[0]) }
        # Fixed destinations, never extract archive-supplied paths.
        [IO.Compression.ZipFileExtensions]::ExtractToFile($matches[0], (Join-Path $sampleDir $spec[1]), $true)
    }
} finally { $archive.Dispose() }
$manifest = [ordered]@{
    schemaVersion = 1; sourceFile = [IO.Path]::GetFileName($sourcePdf); pdfPath = $sourcePdf
    pdfSha256 = $pdfHash; zipSha256 = (Get-FileHash -LiteralPath $zipPath).Hash.ToLowerInvariant()
    originPdfSha256 = (Get-FileHash -LiteralPath (Join-Path $sampleDir 'origin.pdf')).Hash.ToLowerInvariant()
    batchId = $BatchId; provenance = $provenance; cachedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8
Write-Output "CACHED $pdfHash"
