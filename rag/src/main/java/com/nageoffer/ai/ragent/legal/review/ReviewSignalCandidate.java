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

package com.nageoffer.ai.ragent.legal.review;

import java.util.List;
import java.util.Map;

/** Immutable detector output. It contains evidence only; persistence assigns review state. */
public record ReviewSignalCandidate(
        String stableKey,
        String documentId,
        ReviewSignalScope scope,
        ReviewSignalType signalType,
        String targetId,
        List<String> relatedClauseIds,
        List<String> relatedChunkIds,
        String message,
        Map<String, Object> evidence
) {
    public ReviewSignalCandidate {
        relatedClauseIds = relatedClauseIds == null ? List.of() : List.copyOf(relatedClauseIds);
        relatedChunkIds = relatedChunkIds == null ? List.of() : List.copyOf(relatedChunkIds);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
