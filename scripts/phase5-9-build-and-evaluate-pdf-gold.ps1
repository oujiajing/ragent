param(
    [string]$SourcePdfDirectory = 'C:\Users\ojj\Desktop\法律法规数据集',
    [string]$DatasetPath = (Join-Path (Get-Location) 'PHASE5_9_30PDF_GOLD.json'),
    [string]$ResultPath = (Join-Path (Get-Location) 'PHASE5_9_30PDF_RETRIEVAL_EVAL.json')
)
$ErrorActionPreference='Stop'
if(@(Get-ChildItem -LiteralPath $SourcePdfDirectory -File -Filter '*.pdf').Count -ne 30){throw 'Source PDF directory must contain exactly 30 PDFs.'}
$sql=@"
SELECT json_agg(row_to_json(x) ORDER BY x.filename) FROM (
 SELECT d.id AS document_id,d.doc_name AS filename,d.file_hash,d.doc_title,d.standard_no,d.quality_status,
        c.id AS chunk_id,c.clause_no,c.hierarchy_path,c.content
 FROM t_knowledge_document d
 CROSS JOIN LATERAL (
   SELECT id,clause_no,hierarchy_path,content FROM t_knowledge_chunk
   WHERE doc_id=d.id AND deleted=0 AND trim(content)<>''
   ORDER BY CASE WHEN clause_no IS NOT NULL AND clause_no<>'' THEN 0 ELSE 1 END,
            abs(chunk_index-(d.chunk_count/2)),chunk_index LIMIT 1
 ) c
 WHERE d.kb_id='legal-corpus-2b' AND d.file_type='pdf'
   AND d.parser_version='legal-pdf-mineru-adapter/2.0.0' AND d.deleted=0
) x;
"@
$json=docker exec ragent-postgres psql -U postgres -d ragent -A -t -P footer=off -c $sql
$docNode=ConvertFrom-Json -InputObject ($json -join "`n")
$docs=foreach($document in $docNode){$document}
if($docs.Count -ne 30){throw "Expected 30 persisted PDFs, got $($docs.Count)."}
$cases=@()
foreach($d in $docs){
  $title=if([string]::IsNullOrWhiteSpace($d.doc_title)){[IO.Path]::GetFileNameWithoutExtension($d.filename)}else{$d.doc_title}
  $standard=if([string]::IsNullOrWhiteSpace($d.standard_no)){''}else{"（$($d.standard_no)）"}
  $clause=if([string]::IsNullOrWhiteSpace($d.clause_no)){'代表性条款'}else{$d.clause_no}
  $plain=($d.content -replace '\s+',' ').Trim();$probe=$plain.Substring(0,[Math]::Min(90,$plain.Length))
  $eligible=$d.quality_status -eq 'PASS'
  $base=[ordered]@{filename=$d.filename;fileHash=$d.file_hash;documentId=$d.document_id;qualityStatus=$d.quality_status;indexEligible=$eligible;goldChunkId=$d.chunk_id;goldClauseNo=$d.clause_no;hierarchyPath=$d.hierarchy_path;evidencePreview=$probe}
  $cases += [pscustomobject]($base + @{caseId="$($d.document_id)-structure";queryType='STRUCTURE_LOOKUP';query="根据《$title》$standard，第$clause 条主要规定了什么？"})
  $cases += [pscustomobject]($base + @{caseId="$($d.document_id)-content";queryType='CONTENT_PROBE';query="哪一条施工安全规定涉及以下内容：$probe"})
}
$dataset=[ordered]@{schemaVersion=1;phase='5.9';generatedAt=(Get-Date).ToUniversalTime().ToString('o');sourceDirectory=$SourcePdfDirectory;sourceDocumentCount=30;casesPerDocument=2;totalCases=$cases.Count;eligibleCases=@($cases|Where-Object indexEligible).Count;isolatedReviewCases=@($cases|Where-Object {-not $_.indexEligible}).Count;generationRule='One deterministic structure lookup and one content probe from a representative persisted chunk per PDF. No historical TXT source is used.';cases=$cases}
$dataset|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $DatasetPath -Encoding UTF8
$evaluated=@()
foreach($case in $cases){
  if(-not $case.indexEligible){$evaluated += [pscustomobject]@{caseId=$case.caseId;queryType=$case.queryType;filename=$case.filename;qualityStatus=$case.qualityStatus;rank=0;disposition='ISOLATED_SOURCE';top20=@()};continue}
  $body=(@{model='bge-m3';input=@($case.query)}|ConvertTo-Json -Compress)
  $response=Invoke-RestMethod -Uri 'http://127.0.0.1:18080/v1/embeddings' -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
  $vector=@($response.data[0].embedding);while($vector.Count -lt 1536){$vector+=0};if($vector.Count -ne 1536){throw "Unexpected embedding dimension $($vector.Count)"}
  $literal='['+($vector -join ',')+']'
  $search="SELECT v.id,d.id FROM t_knowledge_vector v JOIN t_knowledge_document d ON d.id=v.metadata->>'doc_id' WHERE v.collection_name='legal_corpus_2b' AND d.file_type='pdf' AND d.parser_version='legal-pdf-mineru-adapter/2.0.0' AND d.quality_status='PASS' ORDER BY v.embedding <=> '$literal'::vector LIMIT 20;"
  $topRows=@(docker exec ragent-postgres psql -U postgres -d ragent -A -t -F "`t" -P footer=off -c $search|ForEach-Object{$p=$_ -split "`t",2;if($p.Count -eq 2){[pscustomobject]@{chunkId=$p[0];documentId=$p[1]}}})
  $rank=0;$documentRank=0;for($i=0;$i -lt $topRows.Count;$i++){if($rank -eq 0 -and $topRows[$i].chunkId -eq $case.goldChunkId){$rank=$i+1};if($documentRank -eq 0 -and $topRows[$i].documentId -eq $case.documentId){$documentRank=$i+1}}
  $evaluated += [pscustomobject]@{caseId=$case.caseId;queryType=$case.queryType;filename=$case.filename;qualityStatus=$case.qualityStatus;rank=$rank;documentRank=$documentRank;disposition=if($rank -gt 0){'HIT'}elseif($documentRank -gt 0){'DOCUMENT_HIT'}else{'RETRIEVAL_MISS'};top20=@($topRows|Select-Object -ExpandProperty chunkId)}
}
function Metrics($rows,$field){$r=@($rows|Where-Object disposition -ne 'ISOLATED_SOURCE');$n=$r.Count;$o=[ordered]@{};foreach($k in @(1,3,5,10,20)){$o["RecallAt$k"]=[Math]::Round(@($r|Where-Object{$value=$_.$field;$value -gt 0 -and $value -le $k}).Count/[double]$n,4)};$o.MRR10=[Math]::Round((($r|ForEach-Object{$value=$_.$field;if($value -gt 0 -and $value -le 10){1.0/$value}else{0}}|Measure-Object -Average).Average),4);$o.MRR20=[Math]::Round((($r|ForEach-Object{$value=$_.$field;if($value -gt 0 -and $value -le 20){1.0/$value}else{0}}|Measure-Object -Average).Average),4);$o.nDCG10=[Math]::Round((($r|ForEach-Object{$value=$_.$field;if($value -gt 0 -and $value -le 10){1.0/[Math]::Log($value+1,2)}else{0}}|Measure-Object -Average).Average),4);$o.MissAt20=@($r|Where-Object{$_.$field -eq 0}).Count;return $o}
$eligible=@($evaluated|Where-Object disposition -ne 'ISOLATED_SOURCE');$structure=@($eligible|Where-Object queryType -eq 'STRUCTURE_LOOKUP');$content=@($eligible|Where-Object queryType -eq 'CONTENT_PROBE')
$result=[ordered]@{schemaVersion=1;phase='5.9';dataset=$DatasetPath;retrievalScope='Only the latest 30 parserVersion 2.0.0 PDF documents; historical TXT vectors are excluded by SQL filter.';totalCases=$evaluated.Count;evaluatedEligibleCases=$eligible.Count;isolatedReviewCases=@($evaluated|Where-Object disposition -eq 'ISOLATED_SOURCE').Count;exactChunkMetrics=(Metrics $eligible 'rank');documentMetrics=(Metrics $eligible 'documentRank');structureExactChunkMetrics=(Metrics $structure 'rank');contentExactChunkMetrics=(Metrics $content 'rank');cases=$evaluated}
$result|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $ResultPath -Encoding UTF8
Write-Host "PDF-only evaluation complete: total=$($evaluated.Count), eligible=$($eligible.Count), isolated=$($result.isolatedReviewCases), chunkRecall20=$($result.exactChunkMetrics.RecallAt20), documentRecall20=$($result.documentMetrics.RecallAt20)"
