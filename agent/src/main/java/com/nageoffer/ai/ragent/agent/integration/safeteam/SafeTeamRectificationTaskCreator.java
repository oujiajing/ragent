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

package com.nageoffer.ai.ragent.agent.integration.safeteam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.agent.config.ConditionalOnAgentEngine;
import com.nageoffer.ai.ragent.rag.eval.HazardAssessment;
import com.nageoffer.ai.ragent.rag.eval.RectificationTaskCreator;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEngine
public class SafeTeamRectificationTaskCreator implements RectificationTaskCreator {
    private final SafeTeamToolExecutor executor;
    private final ObjectMapper mapper;
    public SafeTeamRectificationTaskCreator(
            @Qualifier("createRectificationOrder") SafeTeamToolExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        this.mapper = mapper;
    }
    @Override public TaskCreationResult create(HazardAssessment a) {
        Map<String,Object> params = Map.of("businessDate", LocalDate.now(), "items", List.of(Map.of(
                "riskType", a.category(), "checkItem", a.riskSummary(), "hazardDescription", a.hazardDescription(),
                "defaultFollowUpPlan", String.join("；", a.rectificationSuggestions()))));
        CallToolResult result = executor.executeForDevelopment(params);
        String text = result.content().stream().filter(TextContent.class::isInstance).map(x -> ((TextContent)x).text()).findFirst().orElse("");
        if (result.isError()) return new TaskCreationResult(false, null, null, text);
        try { JsonNode root = mapper.readTree(text); return new TaskCreationResult(true, root.path("id").asText(null), root.path("status").asText("CREATED"), null); }
        catch (Exception e) { return new TaskCreationResult(false, null, null, "Safe-team 返回结果无法解析"); }
    }
}
