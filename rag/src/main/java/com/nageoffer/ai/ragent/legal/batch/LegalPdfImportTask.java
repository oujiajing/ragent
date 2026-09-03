/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.legal.batch;

import java.time.Instant;

/** In-memory task snapshot for one batch item; database persistence remains the corpus source of truth. */
public final class LegalPdfImportTask {
    private final String id;
    private final String fileName;
    private final String fileHash;
    private final String sourceType = "PDF";
    private final String parserType = "MINERU";
    private LegalPdfImportTaskStatus status = LegalPdfImportTaskStatus.PENDING;
    private int clauseCount;
    private int chunkCount;
    private String errorMessage;
    private int retryCount;
    private final Instant createdTime = Instant.now();
    private Instant finishedTime;

    public LegalPdfImportTask(String id, String fileName, String fileHash) {
        this.id = id;
        this.fileName = fileName;
        this.fileHash = fileHash;
    }

    public void parsing() { status = LegalPdfImportTaskStatus.PARSING; }

    public void structured(int clauses, int chunks) {
        status = LegalPdfImportTaskStatus.STRUCTURED;
        clauseCount = clauses;
        chunkCount = chunks;
    }

    public void indexed() {
        status = LegalPdfImportTaskStatus.INDEXED;
        finishedTime = Instant.now();
    }

    public void failed(Exception error) {
        status = LegalPdfImportTaskStatus.FAILED;
        errorMessage = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        finishedTime = Instant.now();
    }

    public void retrying() { retryCount++; }

    public String id() { return id; }
    public String fileName() { return fileName; }
    public String fileHash() { return fileHash; }
    public String sourceType() { return sourceType; }
    public String parserType() { return parserType; }
    public LegalPdfImportTaskStatus status() { return status; }
    public int clauseCount() { return clauseCount; }
    public int chunkCount() { return chunkCount; }
    public String errorMessage() { return errorMessage; }
    public int retryCount() { return retryCount; }
    public Instant createdTime() { return createdTime; }
    public Instant finishedTime() { return finishedTime; }
}
