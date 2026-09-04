# Progress

## 2026-09-04

- Read the Phase 5.6B request from the attached text.
- Confirmed `ragent` exists on branch `feat/safeguard-agent`, ahead of origin by 35 commits.
- Found pre-existing modified legal import/indexing and embedding files plus runtime logs/backups; these must be preserved and reviewed before any overlapping change.
- Phase 1 in progress.
- Phase 1 completed: inspected the existing import/indexing path and confirmed no execution entrypoint exists outside the opt-in Spring test runner.
- Phase 2 runtime completed: local PostgreSQL, Redis, RustFS, RocketMQ, and ragent backend are ready; Ollama warning is non-blocking.
- Phase 2 database scope completed: the approved manifest matches exactly 30 live active PDF rows by filename and SHA-256. Pre-counts are 30 documents / 8,130 clauses / 8,439 chunks / 0 vectors; all old chunks are currently eligible.
- Phase 2 remains in progress until PRE_REINDEX_STATE.json is generated.
- Generated and verified non-empty `PRE_REINDEX_STATE.json` (16,841 bytes).
- Created verified custom-format PostgreSQL dump and `PHASE5_6B_BACKUP_MANIFEST.json`; dump size is 212,372,808 bytes and SHA-256 is recorded.
- FK inspection found no declared foreign keys among the legal/vector tables; deletion order will therefore be explicit and transactionally controlled.
- Phase 3 in progress: exact delete plan still to be generated and validated.
- Phase 3 completed: `PHASE5_6B_DELETE_PLAN.json` covers exactly the 30 manifest PDFs; 8,130 clauses, 8,439 chunks, 0 vectors, 0 keyword refs; all scope guard checks pass.
- Phase 5.6C added cached MinerU replay, PDF parserVersion 2.0.0, scoped deletion, document-level eligibility isolation, and PASS-only indexing scope.
- Backup SHA-256 was revalidated before mutation. Old 30-PDF scope was deleted; 30/30 cached PDFs were processed and persisted. Final result: PASS=11, REVIEW_REQUIRED=19, FAILED=0; old parserVersion=0.
- Target PDF integrity checks passed: duplicate/orphan/empty counts are zero; Review chunks are ineligible. Keyword configuration is none.
- PASS-only embedding/index attempt failed safely because TEI bge-m3 provider was unavailable; target PDF vectors remain 0, so retrieval regression is blocked and final status is PARTIAL PASS.

## Phase 5.9

- User explicitly authorized deletion of all previous Legal PDF documents.
- Evaluation must be based only on the latest 30 PDFs; historical TXT Gold is out of scope for this validation.
- Verified the separate source folder has exactly 30 PDFs and no TXT.
- Created and verified a 238,924,390-byte PostgreSQL custom backup before deletion.
- Deleted exactly the 30 Legal PDF records and dependent rows twice: the first replay exposed cache-origin/source hash divergence; the second replay correctly used source-folder originals. Each delete preserved all 93 TXT documents.
- Reimported source-folder originals with cached MinerU results: 30/30 structured, 0 failed; source hashes match DB 30/30.
- Applied final gate (25 PASS/5 REVIEW), indexed 5,256 PASS chunks, and verified duplicate/orphan/empty/missing/leakage counts are zero.
- Generated 60-case PDF-only Gold/evaluation artifacts. Exact Chunk Recall@20=0.20; document Recall@20=0.82.
- Tests: cached live import 1/1 PASS, cached live index 1/1 PASS, related unit tests 8/8 PASS.
