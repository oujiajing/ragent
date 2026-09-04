[CmdletBinding()]
param(
    [string]$ManifestPath = (Join-Path (Get-Location) 'PHASE5_6_APPROVED_REINDEX_MANIFEST.json'),
    [string]$OutputPath = (Join-Path (Get-Location) 'PHASE5_6B_DELETE_PLAN.json')
)

$ErrorActionPreference = 'Stop'
$manifest = Get-Content -Raw -LiteralPath $ManifestPath | ConvertFrom-Json
$sql = @'
SELECT row_to_json(x)::text FROM (
SELECT d.id AS document_id,d.doc_name AS filename,d.file_hash AS file_hash,d.parser_version AS parser_version,
COALESCE((SELECT json_agg(c.id ORDER BY c.id) FROM t_legal_clause c WHERE c.document_id=d.id),'[]'::json) AS clause_ids,
COALESCE((SELECT json_agg(c.id ORDER BY c.id) FROM t_knowledge_chunk c WHERE c.doc_id=d.id),'[]'::json) AS chunk_ids,
COALESCE((SELECT json_agg(v.id ORDER BY v.id) FROM t_knowledge_vector v WHERE v.collection_name='legal_corpus_2b' AND v.metadata->>'doc_id'=d.id),'[]'::json) AS vector_ids,
COALESCE((SELECT json_agg(e.id ORDER BY e.id) FROM t_legal_document_element e WHERE e.document_id=d.id),'[]'::json) AS element_ids,
COALESCE((SELECT json_agg(q.id ORDER BY q.id) FROM t_legal_quality_report q WHERE q.document_id=d.id),'[]'::json) AS quality_report_ids,
COALESCE((SELECT json_agg(t.id ORDER BY t.id) FROM t_legal_table t WHERE t.document_id=d.id),'[]'::json) AS table_ids
FROM t_knowledge_document d
WHERE d.kb_id='legal-corpus-2b' AND d.file_type='pdf' AND d.source_format='MINERU_PDF' AND d.deleted=0
ORDER BY d.doc_name) x
'@
$rows = docker exec ragent-postgres psql -U postgres -d ragent -Atc $sql
$db = @($rows | ForEach-Object { $_ | ConvertFrom-Json })
$manifestDocs = @($manifest.documents)
if ($db.Count -ne 30 -or $manifestDocs.Count -ne 30) {
    throw "Expected 30 live PDF rows and 30 manifest rows; live=$($db.Count), manifest=$($manifestDocs.Count)."
}

$items = @(
    foreach ($d in $db) {
        $m = @($manifestDocs | Where-Object { $_.document -eq $d.filename })
        if ($m.Count -ne 1) { throw "Manifest match failure for $($d.filename): $($m.Count) matches." }
        if ($m[0].fileHash -ne $d.file_hash) { throw "Hash mismatch for $($d.filename)." }
        [ordered]@{
            documentId = $d.document_id; filename = $d.filename; fileHash = $d.file_hash
            oldParserVersion = $d.parser_version; disposition = $m[0].finalDisposition
            clauseIds = @($d.clause_ids); clauseCount = @($d.clause_ids).Count
            chunkIds = @($d.chunk_ids); chunkCount = @($d.chunk_ids).Count
            vectorIds = @($d.vector_ids); vectorCount = @($d.vector_ids).Count
            elementIds = @($d.element_ids); elementCount = @($d.element_ids).Count
            qualityReportIds = @($d.quality_report_ids); qualityReportCount = @($d.quality_report_ids).Count
            tableIds = @($d.table_ids); tableCount = @($d.table_ids).Count
            keywordIndexRefs = @(); keywordIndexRefCount = 0
        }
    }
)

$plan = [ordered]@{
    schemaVersion = 1; phase = '5.6B'; planType = 'DRY_RUN'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    sourceManifest = [IO.Path]::GetFileName($ManifestPath)
    scopeCriteria = 'kb_id=legal-corpus-2b AND file_type=pdf AND source_format=MINERU_PDF AND deleted=0'
    scopeValidated = $true; expectedDocumentCount = 30; actualDocumentCount = $items.Count
    keywordBackend = 'none'
    deleteOrder = @('vector','keyword-index','chunk','quality-report','clause','document-element','document')
    documents = $items
    validation = [ordered]@{
        allHashesMatchManifest = $true; unmanifestedDocuments = 0; nonPdfDocumentsIncluded = 0
        txtDocumentsIncluded = 0; otherKnowledgeBaseRowsIncluded = 0
        vectorRefsOutsideScopeIncluded = 0; keywordRefsCount = 0; dryRunOnly = $true
    }
}
$plan | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Output "Generated $OutputPath with $($items.Count) documents."
