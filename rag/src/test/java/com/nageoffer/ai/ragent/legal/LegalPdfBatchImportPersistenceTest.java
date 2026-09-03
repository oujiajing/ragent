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

package com.nageoffer.ai.ragent.legal;

import com.nageoffer.ai.ragent.TestRagentApplication;
import com.nageoffer.ai.ragent.legal.batch.LegalPdfBatchImportJob;
import com.nageoffer.ai.ragent.legal.batch.LegalPdfBatchImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit opt-in destructive integration test: persists and indexes the configured PDF corpus. */
@SpringBootTest(classes = TestRagentApplication.class, webEnvironment = WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "legal.pdf.persist", matches = "true")
class LegalPdfBatchImportPersistenceTest {

    @Autowired
    private LegalPdfBatchImportJob batchImportJob;

    @Test
    void importsAndIndexesTheConfiguredPdfCorpus() throws Exception {
        LegalPdfBatchImportResult result = batchImportJob.run(
                Path.of(System.getProperty("legal.pdf.dir")), true,
                Boolean.parseBoolean(System.getProperty("legal.pdf.index", "false")));
        result.tasks().forEach(task -> System.out.println("PDF_IMPORT_TASK " + task.fileName()
                + " status=" + task.status() + " error=" + task.errorMessage()));
        assertEquals(result.totalFiles(), result.successCount());
    }
}
