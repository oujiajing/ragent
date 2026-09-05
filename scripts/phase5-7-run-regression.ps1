param([string]$OutputPath = (Join-Path (Get-Location) 'PHASE5_7_RETRIEVAL_REGRESSION.json'))
$ErrorActionPreference='Stop'
$goldNode = ConvertFrom-Json -InputObject (Get-Content -Raw 'rag/src/main/resources/evaluation/phase2b_gold.json')
$gold = foreach ($item in $goldNode) { $item }
$rows=@()
$availableSql="SELECT DISTINCT d.doc_name FROM t_knowledge_vector v JOIN t_knowledge_document d ON d.id=v.metadata->>'doc_id' WHERE v.collection_name='legal_corpus_2b';"
$available=@(docker exec ragent-postgres psql -U postgres -d ragent -A -t -P footer=off -c $availableSql)
foreach($g in $gold){
  $body=(@{model='bge-m3';input=@($g.query)}|ConvertTo-Json -Compress)
  $x=Invoke-RestMethod -Uri 'http://127.0.0.1:18083/v1/embeddings' -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
  $a=@($x.data[0].embedding); while($a.Count -lt 1536){$a+=0}; if($a.Count -ne 1536){throw "Embedding dimension $($a.Count)"}
  $v='['+($a -join ',')+']'
  $sql="SELECT d.doc_name,d.quality_status FROM t_knowledge_vector v JOIN t_knowledge_document d ON d.id=v.metadata->>'doc_id' WHERE v.collection_name='legal_corpus_2b' ORDER BY v.embedding <=> '$v'::vector LIMIT 20;"
  $top=@(docker exec ragent-postgres psql -U postgres -d ragent -A -t -F "`t" -P footer=off -c $sql | ForEach-Object { $q=$_ -split "`t",2; if($q.Count -eq 2){[pscustomobject]@{name=$q[0];status=$q[1]}} })
  $rank=0; for($i=0;$i -lt $top.Count;$i++){foreach($doc in @($g.gold_documents)){if($top[$i].name -like "*$doc*"){$rank=$i+1;break}};if($rank -gt 0){break}}
  $hit=$rank -gt 0
  $reachable=$false; foreach($doc in @($g.gold_documents)){if($available | Where-Object {$_ -like "*$doc*"}){$reachable=$true;break}}
  $rows += [pscustomobject]@{query=$g.query;goldDocuments=@($g.gold_documents);hit=$hit;rank=$rank;reachable=$reachable;top20=@($top|Select-Object -ExpandProperty name)}
}
function Rate($items,$k){$n=@($items).Count;if($n -eq 0){return 0};return [math]::Round((@($items|Where-Object {$_.rank -gt 0 -and $_.rank -le $k}).Count/[double]$n),4)}
$all=@($rows);$reach=@($rows|Where-Object reachable);$isolated=@($rows|Where-Object {-not $_.reachable});$missReach=@($reach|Where-Object {-not $_.hit})
$metrics=[ordered]@{};foreach($k in @(1,3,5,10,20)){$metrics["Recall@$k"]=(Rate $all $k);$metrics["Recall@${k}_reachable"]=(Rate $reach $k)}
$mrr10=@($all|ForEach-Object {if($_.rank -gt 0 -and $_.rank -le 10){1.0/$_.rank}else{0}}|Measure-Object -Average).Average;$mrr20=@($all|ForEach-Object {if($_.rank -gt 0 -and $_.rank -le 20){1.0/$_.rank}else{0}}|Measure-Object -Average).Average
$ndcg10=@($all|ForEach-Object {if($_.rank -gt 0 -and $_.rank -le 10){1.0/[math]::Log($_.rank+1,2)}else{0}}|Measure-Object -Average).Average
$out=[ordered]@{schemaVersion=1;phase='5.7';goldCount=$all.Count;reachableGold=$reach.Count;isolatedGold=$isolated.Count;retrievalMissAmongReachable=$missReach.Count;metrics=$metrics;MRR10=[math]::Round($mrr10,4);MRR20=[math]::Round($mrr20,4);nDCG10=[math]::Round($ndcg10,4);missAt20All=(@($all|Where-Object {$_.rank -eq 0}).Count);missAt20Reachable=$missReach.Count;note='Vector-only top20 regression; BM25 is disabled.';cases=$all}
$out|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Host "Regression complete: gold=$($all.Count), reachable=$($reach.Count), isolated=$($isolated.Count), missReach=$($missReach.Count), recall20Reach=$(Rate $reach 20)"
