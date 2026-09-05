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

package com.nageoffer.ai.ragent.infra.embedding;

import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TeiEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public TeiEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.TEI.getId();
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    protected int maxBatchSize() {
        return 16;
    }

    @Override
    protected void customizeRequestBody(JsonObject body, com.nageoffer.ai.ragent.infra.model.ModelTarget target) {
        body.remove("dimensions");
        body.remove("encoding_format");
    }

    @Override
    protected List<List<Float>> doEmbed(List<String> texts,
                                        com.nageoffer.ai.ragent.infra.model.ModelTarget target) {
        List<List<Float>> embeddings = super.doEmbed(texts, target);
        int dimension = target.candidate().getDimension();
        return embeddings.stream().map(vector -> {
            if (vector.size() == dimension) return vector;
            if (vector.size() > dimension) {
                throw new IllegalStateException("TEI 向量维度超过目标维度: " + vector.size() + ">" + dimension);
            }
            List<Float> padded = new ArrayList<>(vector);
            while (padded.size() < dimension) padded.add(0F);
            return List.copyOf(padded);
        }).toList();
    }
}
