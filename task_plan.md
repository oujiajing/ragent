# Phase 5.6C 30 PDF Production Reimport & Isolation

## Goal
Execute and verify a safe replacement of the 30 approved Legal PDF records using the existing ragent pipeline, isolating 5 REVIEW_REQUIRED documents and preserving all out-of-scope data.

## Phases

- [complete] 1. Inspect repositories, artifacts, scripts, schema, and runtime configuration.
- [complete] 2. Start/verify local dependencies and read the real pre-reindex database state.
- [complete] 3. Create and verify a recoverable backup and exact delete plan.
- [complete] 4. Execute safe deletion and cached production reimport/isolation.
- [partial] 5. Validate post-state, integrity, and embedding/index facts; retrieval regression blocked by unavailable embedding provider.
- [complete] 6. Produce final report, inspect scope, run relevant tests, and decide Phase 5.6C status.

## Constraints

- Do not modify parser, chunker, Agent, Retrieval, Citation, or Safe-team implementation.
- Do not alter TXT, hazard, trace, or unrelated knowledge-base data.
- No deletion before a verified non-empty backup and validated delete plan.
- Do not replay a write after Safe-team/API 409; Safe-team is out of scope.

## Errors Encountered

| Error | Attempt | Resolution |
|---|---:|---|

| PowerShell/SQL alias escaping caused a failed read-only query | 1 | Replaced with tab-separated query and verified all 30 manifest names and hashes match the 30 live PDF rows. |
| Inline PowerShell delete-plan generation had a parser error | 1 | Replaced with the checked-in `scripts/phase5-6b-generate-delete-plan.ps1`; plan generated and validated. |
| Initial cache-count command concatenated member access with a path suffix | 1 | Switched to `Join-Path` for each cache artifact path. |
| PowerShell wrapped PostgreSQL JSON array as one pipeline object | 1 | Explicitly enumerated the parsed JSON node into `$docs`. |
| Quality aggregate query used ambiguous `chunk_count` | 1 | Qualified all quality-report aggregate columns with alias `q`. |

## Phase 5.6C outcome

Cached result.zip replay completed for 30/30 PDFs after deleting the old PDF scope. New parserVersion is legal-pdf-mineru-adapter/2.0.0; 11 documents are PASS/eligible and 19 are REVIEW_REQUIRED/ineligible. Old parserVersion, duplicate hashes, orphan Clause/Chunk/Vector, and empty chunks are zero. Embedding was attempted for PASS documents but blocked because the configured TEI bge-m3 client was unavailable; target PDF vector count remains zero. Keyword is configured as none. Final status is PARTIAL PASS and Phase 6 is not allowed.

## Phase 5.9 — Latest 30 PDF Clean Rebuild and PDF-only Evaluation

Goal: remove all existing Legal PDF records, reimport exactly the latest 30 cached PDFs through the frozen parser/cleaner/chunker, index eligible PDFs, and evaluate using a new immutable dataset sourced only from those 30 PDFs.

- [complete] 1. Verify cache set, current PDF deletion scope, services, and backup prerequisites.
- [complete] 2. Create a fresh database backup and exact all-PDF delete plan.
- [complete] 3. Delete all Legal PDF records and dependent data transactionally.
- [complete] 4. Reimport the 30 source-folder PDFs and reapply the approved document Quality Gate.
- [complete] 5. Index PASS PDFs and verify parser/cleaning/chunk/vector integrity.
- [complete] 6. Generate a 30-PDF-derived Gold dataset and run PDF-only retrieval evaluation.
- [complete] 7. Produce the final report, run tests, inspect diff, and commit scoped changes.
