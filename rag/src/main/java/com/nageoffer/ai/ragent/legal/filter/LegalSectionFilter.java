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

package com.nageoffer.ai.ragent.legal.filter;

import com.nageoffer.ai.ragent.core.parser.model.Block;
import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Removes known non-body sections from MinerU output before legal parsing. */
@Component
public class LegalSectionFilter {

    private static final Pattern TOC_ENTRY = Pattern.compile(
            "^(?:[A-Za-z]?\\d+(?:[.．]\\d+){0,5}|第[一二三四五六七八九十百零〇两]+章)\\s+.+?(?:[.·…_\\-]{2,}|\\s{2,})\\s*\\d{1,4}$");
    private static final Pattern BODY_CHAPTER = Pattern.compile(
            "^(?:第[一二三四五六七八九十百零〇两]+章|附录\\s*[A-Za-z一二三四五六七八九十百零〇两]*)\\s*.*$");
    private static final Pattern NUMBERED_BODY_CHAPTER = Pattern.compile(
            "^\\d{1,2}\\s+[^.。！？；;:]{1,20}$");

    public FilterResult filter(String documentId, ParsedDocument parsed) {
        if (parsed == null || parsed.blocks() == null || parsed.blocks().isEmpty()) {
            return new FilterResult(ParsedDocument.of(List.of()), List.of());
        }
        List<Block> retained = new ArrayList<>();
        List<FilterLog> logs = new ArrayList<>();
        SectionState state = SectionState.BODY;
        for (Block block : parsed.blocks()) {
            String text = blockText(block);
            String normalized = text.strip();
            String section = headingSection(normalized);
            if (section != null) {
                state = SectionState.from(section);
                logs.add(log(documentId, section, block, reason(section, normalized)));
                continue;
            }
            if (state != SectionState.BODY) {
                // A real chapter starts the body again; ordinary numbered clauses do not.
                if (isBodyChapter(normalized) && state != SectionState.APPENDIX) {
                    state = SectionState.BODY;
                    retained.add(block);
                } else {
                    logs.add(log(documentId, state.sectionType, block,
                            "位于已识别的非正文区域").withReason(state.sectionType));
                }
                continue;
            }
            if (isTocEntry(normalized)) {
                logs.add(log(documentId, "CHINESE_TOC", block, "标题+页码连续结构").withReason("CHINESE_TOC"));
            } else {
                retained.add(block);
            }
        }
        Map<String, Object> metadata = new LinkedHashMap<>(parsed.metadata() == null ? Map.of() : parsed.metadata());
        metadata.put("legalSectionFilterLogs", List.copyOf(logs));
        metadata.put("legalSectionFilterRemovedBlocks", logs.size());
        return new FilterResult(ParsedDocument.of(retained, metadata), List.copyOf(logs));
    }

    private String headingSection(String text) {
        String compact = text.replaceAll("\\s+", " ").strip();
        if (compact.equals("前言")) return "PREFACE";
        if (compact.equals("目录") || compact.equals("目 录")) return "CHINESE_TOC";
        if (compact.equalsIgnoreCase("CONTENTS") || compact.equalsIgnoreCase("TABLE OF CONTENTS")) return "ENGLISH_TOC";
        if (compact.equals("引用标准名录")) return "REFERENCED_STANDARDS";
        if (compact.equals("本标准用词说明") || compact.equals("本规范用词说明")) return "TERMINOLOGY_NOTE";
        if (compact.matches("附录\\s*[A-Za-z一二三四五六七八九十百零〇两].*")) return "APPENDIX";
        if (compact.equalsIgnoreCase("APPENDIX") || compact.toUpperCase(Locale.ROOT).startsWith("APPENDIX ")) return "APPENDIX";
        return null;
    }

    private boolean isBodyChapter(String text) {
        return (BODY_CHAPTER.matcher(text).matches() || NUMBERED_BODY_CHAPTER.matcher(text).matches())
                && headingSection(text) == null;
    }

    private boolean isTocEntry(String text) {
        return TOC_ENTRY.matcher(text.replace('．', '.')).matches();
    }

    private FilterLog log(String documentId, String section, Block block, String reason) {
        String page = block.provenance() == null ? "unknown" : "unknown";
        return new FilterLog(documentId, section, page, reason);
    }

    private String reason(String section, String text) {
        return switch (section) {
            case "PREFACE" -> "关键词：前言";
            case "CHINESE_TOC" -> "关键词：目录/目 录";
            case "ENGLISH_TOC" -> "关键词：CONTENTS/Contents/TABLE OF CONTENTS";
            case "REFERENCED_STANDARDS" -> "关键词：引用标准名录";
            case "TERMINOLOGY_NOTE" -> "关键词：本标准/本规范用词说明";
            case "APPENDIX" -> "关键词：附录/Appendix";
            default -> text;
        };
    }

    private String blockText(Block block) {
        if (block instanceof HeadingBlock heading) return heading.text() == null ? "" : heading.text();
        if (block instanceof com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock paragraph) return paragraph.text();
        if (block instanceof com.nageoffer.ai.ragent.core.parser.model.CodeBlock code) return code.code();
        if (block instanceof com.nageoffer.ai.ragent.core.parser.model.ListBlock list) return String.join(" ", list.items());
        if (block instanceof com.nageoffer.ai.ragent.core.parser.model.HtmlTableBlock table) return table.html();
        if (block instanceof com.nageoffer.ai.ragent.core.parser.model.TableBlock table) return String.join(" ", table.headers());
        return "";
    }

    public record FilterResult(ParsedDocument document, List<FilterLog> logs) {
        public FilterResult {
            logs = logs == null ? List.of() : List.copyOf(logs);
        }
    }

    public record FilterLog(String document, String sectionType, String pageRange, String reason) {
        private FilterLog withReason(String value) { return new FilterLog(document, value, pageRange, reason); }
    }

    private enum SectionState {
        BODY("BODY"), PREFACE("PREFACE"), CHINESE_TOC("CHINESE_TOC"), ENGLISH_TOC("ENGLISH_TOC"),
        REFERENCED_STANDARDS("REFERENCED_STANDARDS"), TERMINOLOGY_NOTE("TERMINOLOGY_NOTE"), APPENDIX("APPENDIX");

        private final String sectionType;
        SectionState(String sectionType) { this.sectionType = sectionType; }
        static SectionState from(String value) { return valueOf(value); }
    }
}
