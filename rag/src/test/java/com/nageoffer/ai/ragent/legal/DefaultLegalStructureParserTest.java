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

import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultLegalStructureParserTest {
    @Test
    void shouldClassifyRoleHeadingsDeterministically() {
        var result = LegalTestFixtures.importer().importText("doc-role", "《规范》JGJ 1-2020.txt",
                ("1 总则\n1.0.1 正文。\n本规范用词说明\n1 表示必须。\n条文说明\n1.0.1 解释。\n").getBytes(StandardCharsets.UTF_8),
                CleanedTextImportMode.DRY_RUN);
        assertEquals(LegalContentRole.COMMENTARY, result.document().clauses().get(1).contentRole());
    }
}
