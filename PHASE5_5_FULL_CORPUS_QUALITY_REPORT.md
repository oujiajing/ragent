# Phase 5.5 Full Corpus Offline Quality Replay

Run status: **BLOCKED — full 30-PDF offline replay was not possible without violating the requested constraints.**

The source directory contains 30 PDFs, but the current workspace contains only 3 complete MinerU result caches. The remaining 27 PDFs have no local MinerU ZIP/Markdown/Content-List cache. Re-running MinerU for them would be an online parse, and no such call was made in this phase. No database, knowledge base, vector index, keyword index, or source PDF was modified.

## Corpus availability

| item | count | evidence |
|---|---:|---|
| source PDFs | 30 | `C:\Users\ojj\Desktop\法律法规数据集` |
| complete offline MinerU caches | 3 | `.output/legal-pdf-cache/<pdf-sha256>` |
| PDFs available for latest-pipeline replay | 3 | supplied manifest + ZIP SHA256 validation |
| PDFs without offline cache | 27 | no local result ZIP found |
| new MinerU parse submissions | 0 | offline constraint |
| database/index writes | 0 | phase scope |

## Replay results for the 3 available caches

The following results were produced by the latest Phase 5.4 pipeline, using the same cached MinerU ZIP for before/after comparison:

`MinerU result → LegalSectionFilter → Block-preserving cleaning → PDF-aware LegalStructureParser → LegalChunker`

| sample | Clause before→after | Chunk before→after | body blocks deleted | non-body blocks retained | hierarchy conflicts | body table coverage | body coverage gaps |
|---|---:|---:|---:|---:|---:|---:|---:|
| 供用电规范 GB 50194-2014 | 206→203 | 210→205 | 0 | 0 | 0 | 8/8 | 0 |
| 安全帽 GB 2811-2019 | 24→23 | 31→30 | 0 | 0 | 0 | 3/3 | 0 |
| 建设工程安全生产管理条例 | 71→71 | 71→71 | 0 | 0 | 0 | 无表格 | 0 |

For the 3 replayable samples:

- 正文误删：0
- 非正文残留：0
- 已识别数字条号的章/节层级冲突：0
- 正文表格覆盖：11/11
- 正文 Block 覆盖缺口：0
- 目录边界合成用例：`目次`、`目 次`、`TABLE OF CONTENTS`、`Contents` 均通过
- `Clause_no` 和 `Hierarchy` 字段非空比例：100%

The latest pipeline keeps uncertain OCR content under explicit technical identifiers such as `UNNUMBERED@<elementIndex>` and `TABLE@<elementIndex>`. These are retained evidence containers, not reconstructed legal clause numbers. The `安全帽` cache still contains upstream OCR errors and two table chunks above the current hard token limit; its quality status remains REVIEW.

## Full-corpus gate

The required full-corpus gate is intentionally **not passed**:

| required check | result |
|---|---|
| 30/30 PDFs replayed through latest pipeline | NOT VERIFIED; 3/30 cached |
| 30-PDF body deletion count | NOT VERIFIED |
| 30-PDF non-body residue count | NOT VERIFIED |
| 30-PDF Clause hierarchy conflict count | NOT VERIFIED |
| 30-PDF table coverage | NOT VERIFIED |
| 30-PDF Chunk statistics | NOT VERIFIED |
| safe to enter Reindex | **NO** |

The previous Phase 5.3 30-PDF run is historical evidence for that earlier pipeline version and is not substituted for this phase's full-corpus result. Its aggregate numbers must not be reported as Phase 5.5 latest-pipeline metrics.

## Reproduction

The replay is deterministic and offline:

```powershell
Set-Location 'D:\1-project\ragent'
.\mvnw.cmd -o -pl rag `
  '-Dtest=LegalPdfHardeningTest,LegalPdfOfflineAuditTest' `
  '-Dlegal.pdf.cache.dir=D:\1-project\ragent\.output\legal-pdf-cache' `
  '-Dlegal.pdf.audit.out=D:\1-project\ragent\.output\phase5-4-quality' `
  '-Dlegal.pdf.audit.hardening=true' test
```

Result: 61 tests, 56 passed, 5 skipped, 0 failed. The 3 cached PDFs replayed without network calls in about one second. The 27 missing caches require a separate cache acquisition step before a valid Phase 5.5 full-corpus replay; that step is outside this run because it would require online MinerU calls.

Detailed evidence is available in:

- [latest-pipeline machine report](D:/1-project/ragent/.output/phase5-4-quality/REPORT.md)
- [latest-pipeline aggregate JSON](D:/1-project/ragent/.output/phase5-4-quality/summary.json)
- [Phase 5.4 hardening report](D:/1-project/ragent/PHASE5_4_QUALITY_HARDENING_REPORT.md)

Reindex is intentionally not started. This report is the stopping point for Phase 5.5 until all 30 MinerU result caches are present.
