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

package com.nageoffer.ai.ragent.rag.eval;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalMetricsTest {

    @Test
    void calculatesHitRankMrrAndNdcgAtCutoff() {
        List<RetrievedChunk> ranked = List.of(
                chunk("noise"), chunk("gold"), chunk("other"));

        RetrievalMetrics.Result result = RetrievalMetrics.evaluate(ranked, "gold", 5);

        assertTrue(result.hit());
        assertEquals(2, result.rank());
        assertEquals(0.5D, result.reciprocalRank());
        assertEquals(1D / (Math.log(3D) / Math.log(2D)), result.ndcg());
    }

    @Test
    void missesGoldOutsideCutoff() {
        RetrievalMetrics.Result result = RetrievalMetrics.evaluate(
                List.of(chunk("noise"), chunk("gold")), "gold", 1);

        assertFalse(result.hit());
        assertEquals(0D, result.reciprocalRank());
    }

    private RetrievedChunk chunk(String id) {
        return RetrievedChunk.builder().id(id).text(id).build();
    }
}
