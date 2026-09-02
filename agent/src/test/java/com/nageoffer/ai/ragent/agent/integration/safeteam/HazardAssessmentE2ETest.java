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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.legal.model.LegalEvidence;
import com.nageoffer.ai.ragent.rag.eval.HazardAssessment;
import com.nageoffer.ai.ragent.rag.eval.HazardAssessmentResult;
import com.nageoffer.ai.ragent.rag.eval.HazardAssessmentService;
import com.nageoffer.ai.ragent.rag.eval.InMemoryHazardAssessmentRepository;
import com.nageoffer.ai.ragent.rag.eval.LegalAnswerResponse;
import com.nageoffer.ai.ragent.rag.eval.LegalAnswerService;
import com.nageoffer.ai.ragent.rag.eval.RectificationTaskCreator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;

class HazardAssessmentE2ETest {
    @Test void assessmentConfirmAndSafeTeamCreateFormCompleteLocalFlow() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            SafeTeamIntegrationProperties properties = new SafeTeamIntegrationProperties(); properties.setBaseUrl(server.url("/").toString()); properties.setDevToken("test-token");
            SafeTeamToolExecutor tool = SafeTeamToolExecutor.create(new SafeTeamApiClient(properties, mapper), mapper);
            LegalAnswerService legal = mock(LegalAnswerService.class); LLMService llm = mock(LLMService.class);
            when(legal.answer(any())).thenReturn(new LegalAnswerResponse("栏杆依据[e1]", List.of(new LegalEvidence("e1", "规范", "GB-1", "1", null, "CLAUSE", "应设置栏杆", "c", 1, .9F, .9F)), List.of()));
            when(llm.chat(any())).thenReturn("{\"riskExplanation\":\"有坠落风险\",\"suggestion\":[\"设置栏杆\"],\"acceptanceCriteria\":[\"牢固连续\"]}");
            server.enqueue(new MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body("{\"code\":0,\"data\":{\"id\":\"T-E2E\",\"status\":\"CREATED\"},\"message\":\"ok\"}").build());
            RectificationTaskCreator creator = a -> { var result = tool.executeForDevelopment(Map.of("businessDate", LocalDate.now(), "items", List.of(Map.of("hazardDescription", a.hazardDescription())))); return new RectificationTaskCreator.TaskCreationResult(!result.isError(), "T-E2E", "CREATED", null); };
            HazardAssessmentService service = new HazardAssessmentService(legal, llm, mapper, new InMemoryHazardAssessmentRepository(), creator);
            HazardAssessmentResult assessment = service.assess("地下室临边没有设置防护栏杆");
            assertThat(assessment.assessmentId()).isNotBlank(); assertThat(service.confirm(assessment.assessmentId()).status()).isEqualTo("TASK_CREATED");
            assertThat(service.get(assessment.assessmentId()).trace()).extracting(HazardAssessment.TraceStep::type).contains("TOOL_CALL", "TASK_RESULT");
            assertThat(server.takeRequest(1, TimeUnit.SECONDS).getMethod()).isEqualTo("POST");
        }
    }
}
