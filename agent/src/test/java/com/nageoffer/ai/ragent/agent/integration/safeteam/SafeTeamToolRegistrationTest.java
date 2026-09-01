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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.agent.memory.AgentMemoryPipeline;
import com.nageoffer.ai.ragent.agent.memory.AgentMemoryProperties;
import com.nageoffer.ai.ragent.agent.service.AgentConversationService;
import com.nageoffer.ai.ragent.agent.tool.AgentToolCatalog;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNodeRegistry;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolRegistry;
import com.nageoffer.ai.ragent.rag.core.prompt.AgentPromptResolver;
import com.nageoffer.ai.ragent.rag.core.prompt.AgentPromptSlot;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.service.KnowledgeSearchFacade;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeTeamToolRegistrationTest {
    @Test
    void fourToolsAreVisibleOnlyWhenRegistryAndIntentBindingsBothExist() {
        SafeTeamApiClient client = mock(SafeTeamApiClient.class);
        ObjectMapper mapper = new ObjectMapper();
        List<SafeTeamToolExecutor> executors = List.of(
                SafeTeamToolExecutor.search(client, mapper),
                SafeTeamToolExecutor.detail(client, mapper),
                SafeTeamToolExecutor.create(client, mapper),
                SafeTeamToolExecutor.issue(client, mapper));

        IntentNodeRegistry intents = mock(IntentNodeRegistry.class);
        when(intents.listMcpToolNodes()).thenReturn(executors.stream()
                .map(executor -> IntentNode.builder().id(executor.getToolId())
                        .name(executor.getToolId()).description(executor.getToolId())
                        .kind(IntentKind.MCP).mcpToolId(executor.getToolId()).build()).toList());
        McpToolRegistry registry = mock(McpToolRegistry.class);
        when(registry.listAllExecutors()).thenReturn(List.copyOf(executors));
        AgentPromptResolver prompts = mock(AgentPromptResolver.class);
        when(prompts.resolve(AgentPromptSlot.KNOWLEDGE_TOOL_DESCRIPTION)).thenReturn("知识库工具");
        AgentMemoryProperties memory = new AgentMemoryProperties();
        memory.setLongTermEnabled(false);

        AgentToolCatalog catalog = new AgentToolCatalog(
                mock(KnowledgeSearchFacade.class), mock(AgentConversationService.class), intents,
                registry, prompts, memory, mock(AgentMemoryPipeline.class));
        Toolkit toolkit = catalog.buildToolkit(catalog.resolve());

        assertThat(toolkit.getToolNames()).containsExactlyInAnyOrder(
                "search_knowledge", "search_rectification_orders", "get_rectification_order",
                "create_rectification_order", "issue_rectification");
        assertThat(catalog.mcpToolCount()).isEqualTo(4);
    }

    @Test
    void factoryDeclaresAllFourIntentToolIds() {
        List<String> toolIds = com.nageoffer.ai.ragent.rag.core.intent.IntentTreeFactory.buildIntentTree()
                .stream().flatMap(root -> flatten(root).stream())
                .map(IntentNode::getMcpToolId).filter(java.util.Objects::nonNull).toList();
        assertThat(toolIds).containsExactlyInAnyOrder(
                "sales_query", "search_rectification_orders", "get_rectification_order",
                "create_rectification_order", "issue_rectification");
    }

    private static List<IntentNode> flatten(IntentNode root) {
        java.util.ArrayList<IntentNode> all = new java.util.ArrayList<>();
        all.add(root);
        if (root.getChildren() != null) root.getChildren().forEach(child -> all.addAll(flatten(child)));
        return all;
    }
}
