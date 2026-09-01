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

package com.nageoffer.ai.ragent.legal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag.legal")
public class LegalIngestionProperties {

    private Chunk chunk = new Chunk();
    private Quality quality = new Quality();

    @Data
    public static class Chunk {
        private int maxTokens = 450;
        private int hardLimitTokens = 600;

        public void validate() {
            if (maxTokens <= 0) throw new IllegalArgumentException("rag.legal.chunk.max-tokens 必须 > 0");
            if (hardLimitTokens < maxTokens) {
                throw new IllegalArgumentException("rag.legal.chunk.hard-limit-tokens 不得小于 max-tokens");
            }
        }
    }

    @Data
    public static class Quality {
        private double maxUnstructuredRatio = 0.10;
        private double maxUnknownRoleRatio = 0.05;
    }
}
