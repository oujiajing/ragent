param(
    [string]$CacheDir = (Join-Path (Get-Location) '.output/legal-pdf-cache'),
    [string]$BackupFile = 'D:\1-project\ragent\backups\phase5-9-20260904T211049\ragent-pre-pdf-rebuild.dump',
    [string]$PlanPath = (Join-Path (Get-Location) 'PHASE5_9_PDF_DELETE_PLAN.json'),
    [switch]$ExecuteDelete
)
$ErrorActionPreference='Stop'
$cache=@(Get-ChildItem -Directory $CacheDir|Where-Object{(Test-Path -LiteralPath (Join-Path $_.FullName 'origin.pdf')) -and (Test-Path -LiteralPath (Join-Path $_.FullName 'result.zip')) -and (Test-Path -LiteralPath (Join-Path $_.FullName 'manifest.json'))})
if($cache.Count -ne 30){throw "Expected 30 complete cache directories, got $($cache.Count)."}
$backup=Get-Item -LiteralPath $BackupFile
if($backup.Length -le 0){throw 'Backup is empty.'}
$sql="SELECT d.id,d.doc_name,d.file_hash,(SELECT count(*) FROM t_legal_clause c WHERE c.document_id=d.id),(SELECT count(*) FROM t_knowledge_chunk c WHERE c.doc_id=d.id AND c.deleted=0),(SELECT count(*) FROM t_knowledge_vector v WHERE v.collection_name='legal_corpus_2b' AND v.metadata->>'doc_id'=d.id) FROM t_knowledge_document d WHERE d.kb_id='legal-corpus-2b' AND d.file_type='pdf' AND d.deleted=0 ORDER BY d.doc_name;"
$docs=@(docker exec ragent-postgres psql -U postgres -d ragent -A -t -F "`t" -P footer=off -c $sql|ForEach-Object{$p=$_ -split "`t",6;if($p.Count -eq 6){[pscustomobject]@{documentId=$p[0];filename=$p[1];fileHash=$p[2];clauseCount=[int]$p[3];chunkCount=[int]$p[4];vectorCount=[int]$p[5]}}})
if($docs.Count -ne 30){throw "Expected exactly 30 live Legal PDF documents, got $($docs.Count)."}
$txtBefore=[int](docker exec ragent-postgres psql -U postgres -d ragent -At -c "SELECT count(*) FROM t_knowledge_document WHERE kb_id='legal-corpus-2b' AND file_type='txt' AND deleted=0;")
$plan=[ordered]@{schemaVersion=1;phase='5.9';generatedAt=(Get-Date).ToUniversalTime().ToString('o');executeDelete=[bool]$ExecuteDelete;backup=@{path=$backup.FullName;bytes=$backup.Length;sha256=(Get-FileHash $backup.FullName -Algorithm SHA256).Hash.ToLowerInvariant()};cacheDocuments=$cache.Count;targetDocuments=$docs.Count;targetClauses=($docs|Measure-Object clauseCount -Sum).Sum;targetChunks=($docs|Measure-Object chunkCount -Sum).Sum;targetVectors=($docs|Measure-Object vectorCount -Sum).Sum;preservedTxtDocuments=$txtBefore;documents=$docs}
$plan|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $PlanPath -Encoding UTF8
if(-not $ExecuteDelete){Write-Host "Plan only: documents=$($docs.Count), chunks=$($plan.targetChunks), vectors=$($plan.targetVectors)";exit 0}
$ids=($docs|ForEach-Object{"'"+$_.documentId.Replace("'","''")+"'"}) -join ','
$deleteSql="BEGIN; DELETE FROM t_knowledge_vector WHERE collection_name='legal_corpus_2b' AND metadata->>'doc_id' IN ($ids); DELETE FROM t_knowledge_chunk WHERE doc_id IN ($ids); DELETE FROM t_legal_quality_report WHERE document_id IN ($ids); DELETE FROM t_legal_clause WHERE document_id IN ($ids); DELETE FROM t_legal_document_element WHERE document_id IN ($ids); DELETE FROM t_knowledge_document WHERE id IN ($ids); COMMIT;"
docker exec ragent-postgres psql -U postgres -d ragent -v ON_ERROR_STOP=1 -c $deleteSql|Out-Null
$pdfAfter=[int](docker exec ragent-postgres psql -U postgres -d ragent -At -c "SELECT count(*) FROM t_knowledge_document WHERE kb_id='legal-corpus-2b' AND file_type='pdf' AND deleted=0;")
$txtAfter=[int](docker exec ragent-postgres psql -U postgres -d ragent -At -c "SELECT count(*) FROM t_knowledge_document WHERE kb_id='legal-corpus-2b' AND file_type='txt' AND deleted=0;")
if($pdfAfter -ne 0 -or $txtAfter -ne $txtBefore){throw "Post-delete scope check failed: pdf=$pdfAfter, txt=$txtBefore->$txtAfter"}
Write-Host "Deleted all Legal PDFs: documents=$($docs.Count); preserved TXT=$txtAfter"
