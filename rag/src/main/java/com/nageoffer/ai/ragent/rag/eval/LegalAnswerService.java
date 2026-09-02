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

import cn.hutool.core.collection.CollUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.legal.model.LegalEvidence;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 第一版法规 RAG 问答：复用现有改写、意图、混合检索和 Rerank，只增加证据 DTO 与 grounded prompt。
 */
@Service
@RequiredArgsConstructor
public class LegalAnswerService {

    public static final String NO_EVIDENCE = "当前知识库未检索到明确依据";

    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;
    private final LLMService llmService;
    private final JdbcTemplate jdbcTemplate;

    public LegalAnswerResponse answer(String question) {
        RewriteResult rewrite = queryRewriteService.rewriteWithSplit(question, List.of());
        List<SubQuestionIntent> intents = intentResolver.resolve(rewrite);
        RetrievalContext context = retrievalEngine.retrieve(intents);
        List<RetrievedChunk> chunks = flatten(context);
        List<LegalEvidence> evidence = withEvidenceIds(resolveEvidence(chunks));
        if (evidence.isEmpty()) {
            return new LegalAnswerResponse(NO_EVIDENCE, List.of(), List.of());
        }

        String groundedPrompt = buildGroundedPrompt(question, evidence);
        String answer = llmService.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("你是施工安全法规问答助手。只能依据用户提供的 Evidence 回答，不得补充或猜测法规数字、标准编号或条款。每个关键结论后标注对应证据编号，如 [evidence-1]。禁止生成 Citation 的文档、标准号或条款号；这些字段由系统生成。如果证据不足，原样回答：" + NO_EVIDENCE),
                        ChatMessage.user(groundedPrompt)))
                .temperature(0D)
                .topP(1D)
                .thinking(false)
                .build());
        if (answer == null || answer.isBlank()) {
            answer = NO_EVIDENCE;
        }
        List<LegalAnswerResponse.Citation> citations = evidence.stream()
                .map(e -> new LegalAnswerResponse.Citation(e.evidenceId(), referenceText(e)))
                .toList();
        return new LegalAnswerResponse(answer, evidence, citations);
    }

    private List<RetrievedChunk> flatten(RetrievalContext context) {
        if (context == null || CollUtil.isEmpty(context.getIntentChunks())) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        return context.getIntentChunks().values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .filter(c -> c != null && c.getId() != null && seen.add(c.getId()))
                .toList();
    }

    private List<LegalEvidence> resolveEvidence(List<RetrievedChunk> chunks) {
        List<LegalEvidence> result = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            try {
                LegalEvidence evidence = jdbcTemplate.queryForObject("""
                        SELECT d.doc_title, d.standard_no, c.clause_no, c.hierarchy_path,
                               c.content_role, c.page_start, c.content
                        FROM t_knowledge_chunk c
                        JOIN t_knowledge_document d ON d.id = c.doc_id
                        WHERE c.id = ?
                        """, (rs, rowNum) -> new LegalEvidence(
                        "evidence-pending",
                        rs.getString("doc_title"),
                        rs.getString("standard_no"),
                        rs.getString("clause_no"),
                        rs.getString("hierarchy_path"),
                        rs.getString("content_role"),
                        rs.getString("content"),
                        chunk.getId(),
                        rs.getObject("page_start", Integer.class),
                        chunk.getScore(),
                        chunk.getRerankScore()), chunk.getId());
                if (evidence != null && evidence.content() != null && !evidence.content().isBlank()) {
                    result.add(evidence);
                }
            } catch (Exception ignored) {
                // 只跳过无法回溯的候选，避免单条脏数据使法规问答整体失败。
            }
        }
        return result;
    }

    private String buildGroundedPrompt(String question, List<LegalEvidence> evidence) {
        String sources = evidence.stream()
                .map(e -> {
                    return "[" + e.evidenceId() + "] 文档=" + safe(e.documentTitle())
                            + "；标准=" + safe(e.standardNo())
                            + "；条款=" + safe(e.clauseNo())
                            + "；证据=" + e.content();
                }).collect(Collectors.joining("\n"));
        return "用户问题：" + question + "\n\nEvidence（唯一事实来源）：\n" + sources
                + "\n\n请用中文简洁回答。只能陈述 Evidence 明确支持的内容；不能把证据外的常识当作法规要求。";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<LegalEvidence> withEvidenceIds(List<LegalEvidence> evidence) {
        return java.util.stream.IntStream.range(0, evidence.size())
                .mapToObj(i -> {
                    LegalEvidence e = evidence.get(i);
                    return new LegalEvidence("evidence-" + (i + 1), e.documentTitle(), e.standardNo(),
                            e.clauseNo(), e.hierarchyPath(), e.contentRole(), e.content(), e.chunkId(),
                            e.pageNo(), e.retrievalScore(), e.rerankScore());
                })
                .toList();
    }

    private String referenceText(LegalEvidence evidence) {
        return "《" + safe(evidence.documentTitle()) + "》 "
                + safe(evidence.standardNo()) + " " + safe(evidence.clauseNo());
    }
}
