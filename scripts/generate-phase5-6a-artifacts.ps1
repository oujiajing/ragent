[CmdletBinding()]
param(
    [string]$SourceManifest = (Join-Path (Get-Location) 'PHASE5_6_REINDEX_MANIFEST.json'),
    [string]$OutputManifest = (Join-Path (Get-Location) 'PHASE5_6_APPROVED_REINDEX_MANIFEST.json'),
    [string]$OutputReport = (Join-Path (Get-Location) 'PHASE5_6A_REVIEW_DISPOSITION_REPORT.md')
)

$ErrorActionPreference = 'Stop'
$source = Get-Content -LiteralPath $SourceManifest -Raw | ConvertFrom-Json
if (@($source.documents).Count -ne 30) { throw 'Source manifest must contain exactly 30 documents.' }

$reviewDecisions = @{
    '《安全帽》GB 2811-2019.pdf' = [ordered]@{
        triggerReasons = @('missingExpectedClauses: 3.1, 3.2, 5.2.1, 5.2.16, 5.2.17', 'OCR Review: 20', 'oversized chunks: 2')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $false; oversizedChunkOnly = $false; coverageWarningOnly = $false
        manualEvidence = 'block-decisions.json: all expected BODY blocks retained and covered; tablesNotFullyCovered=0; hierarchyMismatchCandidates=[]; however the structured audit still reports five missing expected clauses and OCR/numbering uncertainty.'
        finalDisposition = 'REVIEW_REQUIRED'
        finalReason = 'No confirmed body/table/hierarchy loss, but missing expected clauses cannot be proven to be OCR-only from the existing evidence.'
    }
    '《爆破安全规程》GB 6722-2014.pdf' = [ordered]@{
        triggerReasons = @('OCR Review: 110', 'oversized chunks: 3')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $true; oversizedChunkOnly = $true; coverageWarningOnly = $false
        manualEvidence = 'summary.json: bodyBlocksNotFullyCovered=0, tablesNotFullyCovered=0, hierarchyMismatchCandidates=[]; block-decisions.json contains no uncovered BODY block.'
        finalDisposition = 'PASS'
        finalReason = 'Review triggers are OCR and oversized-chunk diagnostics only; no confirmed body, table, or hierarchy loss.'
    }
    '《建筑与市政工程地下水控制技术规范》JGJ 111-2016.pdf' = [ordered]@{
        triggerReasons = @('coverage warning: 59 BODY blocks', 'OCR Review: 10', 'oversized chunks: 2')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $false; oversizedChunkOnly = $false; coverageWarningOnly = $false
        manualEvidence = 'block-decisions.json: affected BODY blocks are retained; 58/59 sampled warning prefixes are present in after.json; tablesNotFullyCovered=0 and hierarchyMismatchCandidates=[]. One coverage warning remains not conclusively explained.'
        finalDisposition = 'REVIEW_REQUIRED'
        finalReason = 'Coverage is likely a normalization/formula false positive, but one warning remains unresolved; retain isolation until confirmed.'
    }
    '《建筑基坑支护技术规程》JGJ 120-2012.pdf' = [ordered]@{
        triggerReasons = @('coverage warning: 99 BODY blocks', 'OCR Review: 5', 'oversized chunks: 1')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $false; oversizedChunkOnly = $false; coverageWarningOnly = $false
        manualEvidence = 'block-decisions.json: affected BODY blocks are retained; sampled after.json matching is incomplete; tablesNotFullyCovered=0 and hierarchyMismatchCandidates=[].'
        finalDisposition = 'REVIEW_REQUIRED'
        finalReason = 'Coverage warning cannot be conclusively classified as a false positive using the existing artifacts.'
    }
    '《职业健康监护技术规范》GBZ 188-2025.pdf' = [ordered]@{
        triggerReasons = @('coverage warning: 1057 BODY blocks', 'OCR Review: 49', 'oversized chunks: 1')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $false; oversizedChunkOnly = $false; coverageWarningOnly = $false
        manualEvidence = 'summary.json reports 1057 uncovered BODY blocks; tablesNotFullyCovered=0 and hierarchyMismatchCandidates=[]; current evidence does not establish complete coverage.'
        finalDisposition = 'REVIEW_REQUIRED'
        finalReason = 'Large unresolved coverage warning; cannot safely promote on the available evidence.'
    }
    '《建筑施工门式钢管脚手架安全技术标准》JGJ_T 128-2019.pdf' = [ordered]@{
        triggerReasons = @('oversized chunks: 2')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $false; oversizedChunkOnly = $true; coverageWarningOnly = $false
        manualEvidence = 'summary.json: bodyBlocksNotFullyCovered=0, tablesNotFullyCovered=0, hierarchyMismatchCandidates=[] and ocrReviewCount=0.'
        finalDisposition = 'PASS'
        finalReason = 'Oversized-chunk diagnostic only; no confirmed body, table, or hierarchy loss.'
    }
    '《建筑施工碗扣式钢管脚手架安全技术规范》JGJ 166-2016.pdf' = [ordered]@{
        triggerReasons = @('coverage warning: 4 BODY blocks', 'OCR Review: 7')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $false; oversizedChunkOnly = $false; coverageWarningOnly = $false
        manualEvidence = 'block-decisions.json: four affected BODY blocks are retained but not covered by the audit matcher; tablesNotFullyCovered=0 and hierarchyMismatchCandidates=[]; formula content prevents conclusive false-positive classification.'
        finalDisposition = 'REVIEW_REQUIRED'
        finalReason = 'Small but unresolved coverage warning around formula/table-adjacent content; retain isolation.'
    }
    '《建筑地基基础工程施工质量验收规范》GB 50202-2018.pdf' = [ordered]@{
        triggerReasons = @('oversized chunks: 35', 'OCR Review: 8')
        confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
        ocrReviewOnly = $false; oversizedChunkOnly = $true; coverageWarningOnly = $false
        manualEvidence = 'summary.json: bodyBlocksNotFullyCovered=0, tablesNotFullyCovered=0, hierarchyMismatchCandidates=[]; oversized chunks are the only structural review trigger.'
        finalDisposition = 'PASS'
        finalReason = 'Oversized-table/chunk diagnostic only; no confirmed body, table, or hierarchy loss.'
    }
}

$approved = foreach ($doc in $source.documents) {
    $decision = $reviewDecisions[$doc.document]
    if ($null -eq $decision) {
        $decision = [ordered]@{
            triggerReasons = @('original Phase 5.5 status PASS_SAMPLED_CHECKS')
            confirmedBodyLoss = $false; confirmedTableLoss = $false; confirmedHierarchyError = $false
            ocrReviewOnly = $false; oversizedChunkOnly = $false; coverageWarningOnly = $false
            manualEvidence = 'PHASE5_6_REINDEX_MANIFEST.json and PHASE5_5_FULL_CORPUS_QUALITY_REPORT.md: original status PASS_SAMPLED_CHECKS; no review disposition trigger.'
            finalDisposition = 'PASS'
            finalReason = 'Original Phase 5.5 sampled checks passed; no contrary evidence in the reviewed artifacts.'
        }
    }
    [ordered]@{
        document = $doc.document
        fileHash = $doc.fileHash
        currentStatus = $doc.qualityStatus
        triggerReasons = @($decision.triggerReasons)
        confirmedBodyLoss = $decision.confirmedBodyLoss
        confirmedTableLoss = $decision.confirmedTableLoss
        confirmedHierarchyError = $decision.confirmedHierarchyError
        ocrReviewOnly = $decision.ocrReviewOnly
        oversizedChunkOnly = $decision.oversizedChunkOnly
        coverageWarningOnly = $decision.coverageWarningOnly
        manualEvidence = $decision.manualEvidence
        finalDisposition = $decision.finalDisposition
        dispositionReason = $decision.finalReason
        evidenceSource = @('PHASE5_5_FULL_CORPUS_QUALITY_REPORT.md', 'PHASE5_6_REINDEX_MANIFEST.json', (Join-Path (Join-Path '.output/phase5-5-quality' $doc.fileHash) 'summary.json'), (Join-Path (Join-Path '.output/phase5-5-quality' $doc.fileHash) 'block-decisions.json'))
    }
}

$approved = @($approved)
$approvedObject = [ordered]@{
    schemaVersion = 1
    phase = '5.6A'
    sourceManifest = 'PHASE5_6_REINDEX_MANIFEST.json'
    reviewDate = '2026-09-04'
    basis = 'automated Phase 5.5 quality detection plus evidence/manual disposition review; no MinerU call or PDF reparse'
    originalPassCount = @($approved | Where-Object currentStatus -eq 'PASS').Count
    originalReviewRequiredCount = @($approved | Where-Object currentStatus -eq 'REVIEW_REQUIRED').Count
    originalRejectedCount = 0
    finalPassCount = @($approved | Where-Object finalDisposition -eq 'PASS').Count
    finalReviewRequiredCount = @($approved | Where-Object finalDisposition -eq 'REVIEW_REQUIRED').Count
    finalRejectedCount = @($approved | Where-Object finalDisposition -eq 'REJECTED').Count
    realSafeReindexAllowed = $false
    documents = $approved
}
$approvedObject | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutputManifest -Encoding UTF8

$rows = foreach ($d in $approved | Where-Object currentStatus -eq 'REVIEW_REQUIRED' | Sort-Object document) {
    '| {0} | {1} | {2} | {3} | {4} | {5} |' -f $d.document, $d.currentStatus, ($d.triggerReasons -join '<br>'), $d.confirmedBodyLoss, $d.confirmedTableLoss, $d.confirmedHierarchyError
}
$report = @"
# Phase 5.6A Review Disposition Report

状态：**完成文档放行决策；未执行 Reindex**

## 结论

Phase 5.5 的历史报告保持不变，仍为 `Reindex allowed: NO`。本阶段仅基于现有自动质量产物和人工/证据复核生成最终文档级放行结果，没有调用 MinerU、重新解析 PDF、启动 Reindex、删除数据库或重建任何索引。

原始分布为 PASS 22 / REVIEW_REQUIRED 8 / REJECTED 0。复核后放行 PASS 25、继续 REVIEW_REQUIRED 5、REJECTED 0。

## 8 份 REVIEW_REQUIRED 逐份复核

| document | currentStatus | triggerReasons | confirmedBodyLoss | confirmedTableLoss | confirmedHierarchyError |
|---|---|---|---:|---:|---:|
$($rows -join "`n")

详细字段（包括 OCR-only、oversized-only、coverage-only、manualEvidence、finalDisposition 和 dispositionReason）已写入 `PHASE5_6_APPROVED_REINDEX_MANIFEST.json`。

## 分类结论

### 可放行的 3 份

- 《爆破安全规程》：仅 OCR Review 110、超长 Chunk 3；正文覆盖、表格覆盖和层级候选均无异常。
- 《建筑施工门式钢管脚手架安全技术标准》：仅超长 Chunk 2；无 OCR、正文覆盖、表格覆盖和层级异常。
- 《建筑地基基础工程施工质量验收规范》：超长 Chunk 35 和 OCR Review 8；正文覆盖、表格覆盖和层级候选均无异常。

这些告警不等同于正文丢失，符合 PASS 放行原则。

### 继续隔离的 5 份

- 《安全帽》：正文块和表格未显示确认性丢失，但存在 5 个 missing expected clauses，无法仅凭现有证据证明是 OCR/编号识别问题。
- 《建筑与市政工程地下水控制技术规范》：59 个 coverage warning；虽有 58 个采样前缀可在 after.json 找到，仍有 1 个未完全解释。
- 《建筑基坑支护技术规程》：99 个 coverage warning，现有采样匹配不足以证明全部为假阳性。
- 《职业健康监护技术规范》：1057 个 coverage warning，无法安全确认完整覆盖。
- 《建筑施工碗扣式钢管脚手架安全技术规范》：4 个公式/表格邻接内容 coverage warning，尚无充分证据完成假阳性确认。

这些文档没有被判定为 confirmed body loss、confirmed table loss 或 confirmed hierarchy error；继续 REVIEW_REQUIRED 的原因是证据不足，而不是伪造为 REJECTED。

## 最终索引放行

- 最终允许进入后续真实 Reindex 的文档：25 份。
- 继续隔离：5 份。
- REJECTED：0 份。
- 真实 Safe Reindex：**当前不允许执行**。仍需恢复 PostgreSQL/Docker，并在下一阶段先完成删除前备份和数据库范围核验。

## 证据来源

- `PHASE5_5_FULL_CORPUS_QUALITY_REPORT.md`（历史报告，未修改）
- `PHASE5_6_REINDEX_MANIFEST.json`
- `.output/phase5-5-quality/<fileHash>/summary.json`
- `.output/phase5-5-quality/<fileHash>/block-decisions.json`
- `.output/phase5-5-quality/<fileHash>/before.json` / `after.json`

## 停止条件

本阶段已完成并停止。未启动 Reindex，未执行数据库删除、Embedding、PgVector、Elasticsearch 或 Keyword Index 操作。
"@
$report | Set-Content -LiteralPath $OutputReport -Encoding UTF8
Write-Output "Generated $OutputManifest and $OutputReport."
