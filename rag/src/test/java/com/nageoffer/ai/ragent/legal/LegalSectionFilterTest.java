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

import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.legal.filter.LegalSectionFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalSectionFilterTest {

    private static final Provenance PROVENANCE = Provenance.ofFile("fixture.pdf");

    @Test
    void removesAllConfiguredNonBodySectionsAndKeepsBodyChapterAndClauses() {
        List<Block> blocks = List.of(
                heading("前言"), paragraph("本前言不应入库"),
                heading("目 录"), paragraph("1 总则 ........ 1"), paragraph("2 术语 ........ 2"),
                heading("第一章 总则"), paragraph("1.1 正文条款"),
                heading("CONTENTS"), paragraph("3 Scope ........ 3"),
                heading("引用标准名录"), paragraph("GB/T 1234"),
                heading("本规范用词说明"), paragraph("本说明不应入库"),
                heading("附录A"), paragraph("附录内容不应入库"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-1", ParsedDocument.of(blocks));

        List<String> retained = result.document().blocks().stream()
                .map(block -> block instanceof HeadingBlock h ? h.text() : ((ParagraphBlock) block).text())
                .toList();
        assertEquals(List.of("第一章 总则", "1.1 正文条款"), retained);
        assertEquals(6, result.logs().stream().map(LegalSectionFilter.FilterLog::sectionType).distinct().count());
        assertTrue(result.logs().stream().allMatch(log -> log.document().equals("doc-1")));
        assertTrue(result.logs().stream().allMatch(log -> !log.reason().isBlank()));
        assertTrue(result.document().metadata().containsKey("legalSectionFilterLogs"));
    }

    @Test
    void doesNotTreatOrdinaryBodyNumberAsTocEntry() {
        List<Block> blocks = List.of(
                heading("第一章 总则"), paragraph("1 总则"), paragraph("1.1 施工单位应当建立安全制度"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-2", ParsedDocument.of(blocks));

        assertEquals(3, result.document().blocks().size());
        assertTrue(result.logs().isEmpty());
    }

    @Test
    void resumesBodyAtNumberedChapterAfterChineseToc() {
        List<Block> blocks = List.of(
                heading("目录"), paragraph("1 总则 ........ 1"),
                heading("1 总则"), paragraph("1.1 正文条款"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-3", ParsedDocument.of(blocks));

        assertEquals(List.of("1 总则", "1.1 正文条款"), result.document().blocks().stream()
                .map(block -> block instanceof HeadingBlock h ? h.text() : ((ParagraphBlock) block).text())
                .toList());
    }

    @Test
    void resumesBodyAtCompactNumberedHeadingAfterPreface() {
        List<Block> blocks = List.of(
                heading("前 言"), paragraph("前言内容"),
                heading("1范围"), paragraph("1.1 正文条款"));

        LegalSectionFilter.FilterResult result = new LegalSectionFilter()
                .filter("doc-4", ParsedDocument.of(blocks));

        assertEquals(List.of("1范围", "1.1 正文条款"), result.document().blocks().stream()
                .map(block -> block instanceof HeadingBlock h ? h.text() : ((ParagraphBlock) block).text())
                .toList());
    }

    private Block heading(String text) {
        return new HeadingBlock(PROVENANCE, 1, text);
    }

    private Block paragraph(String text) {
        return new ParagraphBlock(PROVENANCE, text);
    }
}
