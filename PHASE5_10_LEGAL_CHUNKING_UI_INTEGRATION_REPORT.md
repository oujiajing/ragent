# Phase 5.10 - Legal Chunking Strategy UI Integration

Date: 2026-09-04

Conclusion: **PARTIAL PASS / SMOKE BLOCKED**

## 1. Original flow

The existing document lifecycle remains two-stage:

```text
POST upload -> persist file and PENDING Document
POST startChunk -> RocketMQ -> executeChunk
```

GENERAL documents keep using `IngestionKernel`, `ParserRegistry`, and generic chunking unchanged.

## 2. Strategy contract

`t_knowledge_document.processing_strategy` was added with default `GENERAL` and a
`GENERAL | LEGAL` constraint. Upload accepts the optional multipart field
`processingStrategy`; old clients continue to get GENERAL.

The field is deliberately separate from `processMode`, `ingestionSpec`, and
`parserVersion`. The API rejects unknown strategies rather than falling back.

## 3. LEGAL route

`executeChunk` branches on the persisted strategy. LEGAL documents call
`LegalDocumentProcessingService`, which reuses the existing MinerU, section filter,
adapter, cleaning, structure parser, chunker, and Quality Gate.

The product route uses the uploaded document's existing `documentId`, `kbId`, and
`VectorTarget`; it does not call the fixed Phase 2B batch import entry point and does
not write new documents into `legal-corpus-2b`.

## 4. Validation and isolation

LEGAL accepts only local files whose stored MIME is `application/pdf`. It rejects URL
uploads, non-PDF MIME, unknown strategy values, and KBs not configured for
`bge-m3 / 1536`.

Quality is persisted as `PASS`, `REVIEW`, or `FAILED`. Only PASS chunks remain
index-eligible. The single-document index path deletes prior vectors before rebuilding;
REVIEW and FAILED have no eligible chunks and therefore leave no vectors. Reprocessing
clears prior Legal element, clause, quality, and chunk rows. Legal delete clears those
same Legal artifacts before the normal document/vector cleanup runs.

## 5. UI and compatibility

The upload dialog now presents General and Legal processing strategies. LEGAL limits
the source selector to local files, rejects non-PDF extension feedback before upload,
and hides generic chunk/pipeline configuration. The document list displays strategy and
Legal quality status. `KnowledgeDocumentVO` returns both fields.

Historical documents without a strategy return LEGAL when their parser version is
`legal-pdf-mineru-adapter/2.0.0`; no historical PDF was re-imported.

## 6. Verification

- `npm run lint`: PASS
- `npm run build`: PASS (existing bundle-size warning only)
- `mvnw.cmd -pl rag -am -Dtest=ProcessingStrategyTest,LegalPdfImportServiceTest,LegalQualityServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`: PASS
  - 7 tests, 0 failures, 0 errors, 0 skipped
- Scoped `git diff --check`: PASS

## 7. Smoke status

The runtime was started with `D:\1-project\safeguard-agent\scripts\start-all-dev.ps1`.
Safe-team, ragent backend (9090), ragent frontend (5173), TEI, PostgreSQL, Redis,
RocketMQ NameServer and Broker were available. Because the existing TEI service owns
host port 18080, the local smoke Compose copy mapped the Broker HTTP proxy to 18081;
RocketMQ remoting port 10911 remained available to ragent.

An isolated KB `phase510_smoke_20260904` was created successfully. A real API upload
with `processingStrategy=LEGAL` created Document `2095897086401806336` with
`LEGAL/PENDING`, and `startChunk` sent a transaction message successfully. The MQ
consumer received the same document ID and entered `LegalDocumentProcessingService`.

The pipeline then stopped at the external MinerU boundary because the running backend
had no `MINERU_API_KEY`:

```text
ServiceException: MinerU api-key 未配置,请设置环境变量 MINERU_API_KEY
```

The isolated Document ended as `LEGAL/failed`, with zero Legal Chunk rows and zero
Vectors. No production Legal PDF was changed. Therefore the end-to-end smoke result is
`BLOCKED_EXTERNAL_DEPENDENCY`, not PASS. A complete smoke still requires a configured
MinerU credential (or a formally supported cache-replay test mode), followed by proof
of PASS-only indexing and REVIEW vector isolation.

## 8. Remaining limitation

The previously measured PDF-only document Recall@20 remains 0.82, while exact chunk
Recall@20 remains 0.20. This implementation does not alter retrieval, Gold data,
reranking, or Phase 6 readiness. Phase 6 remains not approved.
