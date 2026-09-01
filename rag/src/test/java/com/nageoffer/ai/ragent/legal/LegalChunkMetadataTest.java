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

import com.nageoffer.ai.ragent.legal.enums.LegalChunkType;
import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.model.LegalChunkMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegalChunkMetadataTest {
    @Test
    void shouldSerializeCitationFactsToStableMap() {
        var metadata = new LegalChunkMetadata("d", "标题", "JGJ 1-2020", "1", "总则", null, null,
                "1.0.1", "1 总则 / 1.0.1", "c", null, LegalContentRole.NORMATIVE,
                LegalChunkType.CLAUSE, null, null);
        assertEquals("1.0.1", metadata.toMap().get("clause_no"));
        assertEquals("c", metadata.toMap().get("parent_clause_id"));
    }
}
