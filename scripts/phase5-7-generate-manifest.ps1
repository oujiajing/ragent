param(
    [string]$OutputPath = (Join-Path (Get-Location) 'PHASE5_7_FINAL_INDEX_MANIFEST.json'),
    [string]$QualityEvidence = (Join-Path (Get-Location) 'PHASE5_6_APPROVED_REINDEX_MANIFEST.json')
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$json = Get-Content -Raw -LiteralPath $QualityEvidence | ConvertFrom-Json
$approved = @{}
foreach ($item in @($json.documents)) { $approved[$item.document] = $item }

$sql = @"
SELECT d.id AS document_id, d.doc_name AS filename, d.file_hash, d.parser_version,
       d.quality_status AS persisted_status, d.chunk_count,
       (SELECT count(*) FROM t_legal_clause lc WHERE lc.document_id=d.id) AS clause_count,
       count(c.id) AS actual_chunk_count,
       count(c.id) FILTER (WHERE c.index_eligible) AS eligible_chunk_count,
       COALESCE(q.warnings::text, '[]') AS warnings
FROM t_knowledge_document d
LEFT JOIN t_knowledge_chunk c ON c.doc_id=d.id AND c.deleted=0
LEFT JOIN LATERAL (SELECT warnings FROM t_legal_quality_report WHERE document_id=d.id ORDER BY create_time DESC LIMIT 1) q ON TRUE
WHERE d.kb_id='legal-corpus-2b' AND d.file_type='pdf' AND d.deleted=0
GROUP BY d.id,d.doc_name,d.file_hash,d.parser_version,d.quality_status,d.chunk_count,q.warnings
ORDER BY d.doc_name;
"@
$rows = @(docker exec ragent-postgres psql -U postgres -d ragent -A -t -F "`t" -P footer=off -c $sql)
if ($rows.Count -lt 1) { throw 'No persisted Phase 5.6C PDF rows found.' }

$docs = foreach ($line in $rows) {
    $p = $line -split "`t", 10
    if ($p.Count -lt 10) { continue }
    $hash = $p[2]
    $e = $approved[$p[1]]
    if ($null -eq $e) { throw "PDF filename is absent from approved evidence: $($p[1])" }
    $warningText = $p[9]
    try { $warningTypes = @($warningText | ConvertFrom-Json) } catch { $warningTypes = @($warningText) }
    $isReview = $e.finalDisposition -eq 'REVIEW_REQUIRED'
    $blocking = if ($isReview) { @($e.dispositionReason) } else { @() }
    $final = if ($isReview) { 'REVIEW_REQUIRED' } else { 'PASS' }
    $eligible = $final -eq 'PASS'
    [pscustomobject][ordered]@{
        documentId = $p[0]
        filename = $p[1]
        fileHash = $hash
        parserVersion = $p[3]
        persistedStatus = $p[4]
        finalQualityStatus = $final
        indexEligible = $eligible
        clauseCount = [int]$p[6]
        chunkCount = [int]$p[5]
        actualChunkCount = [int]$p[7]
        # The final manifest is the authority; current 5.6C index_eligible values still reflect the coarse gate.
        eligibleChunkCount = if ($eligible) { [int]$p[7] } else { 0 }
        warningTypes = $warningTypes
        blockingReasons = $blocking
        dispositionReason = [string]$e.dispositionReason
        evidenceSource = @('PHASE5_6_APPROVED_REINDEX_MANIFEST.json','PHASE5_6C_30PDF_PRODUCTION_REIMPORT_REPORT.md','t_legal_quality_report','t_knowledge_chunk')
    }
}

if (@($docs).Count -ne 30) { throw "Expected 30 PDF documents, got $(@($docs).Count)." }
$pass = @($docs | Where-Object finalQualityStatus -eq 'PASS')
$review = @($docs | Where-Object finalQualityStatus -eq 'REVIEW_REQUIRED')
$manifest = [ordered]@{
    schemaVersion = 1
    phase = '5.7'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    source = 'Phase 5.6C persisted PDF rows plus Phase 5.6A reviewed dispositions'
    totalDocuments = @($docs).Count
    PASS = $pass.Count
    REVIEW_REQUIRED = $review.Count
    REJECTED = 0
    indexEligibleDocuments = $pass.Count
    indexEligibleChunks = (($pass | Measure-Object -Property eligibleChunkCount -Sum).Sum)
    documents = @($docs)
}
$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
# Apply only the document-level eligibility decision from the newly written manifest.
$eligibleIds = @($pass | ForEach-Object { "'" + $_.documentId.Replace("'", "''") + "'" }) -join ','
$allIds = @($docs | ForEach-Object { "'" + $_.documentId.Replace("'", "''") + "'" }) -join ','
$eligibilitySql = "BEGIN; UPDATE t_knowledge_document SET quality_status='REVIEW' WHERE id IN ($allIds); UPDATE t_knowledge_chunk SET index_eligible=FALSE WHERE doc_id IN ($allIds); UPDATE t_legal_clause SET index_eligible=FALSE WHERE document_id IN ($allIds); UPDATE t_knowledge_document SET quality_status='PASS' WHERE id IN ($eligibleIds); UPDATE t_knowledge_chunk SET index_eligible=TRUE WHERE doc_id IN ($eligibleIds) AND deleted=0; UPDATE t_legal_clause SET index_eligible=TRUE WHERE document_id IN ($eligibleIds); COMMIT;"
docker exec ragent-postgres psql -U postgres -d ragent -v ON_ERROR_STOP=1 -c $eligibilitySql | Out-Null
Write-Host ("Generated {0}: PASS={1}, REVIEW_REQUIRED={2}, eligibleChunks={3}" -f $OutputPath,$pass.Count,$review.Count,$manifest.indexEligibleChunks)
