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

package com.nageoffer.ai.ragent.legal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nageoffer.ai.ragent.framework.database.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_legal_quality_report", autoResultMap = true)
public class LegalQualityReportDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String documentId;
    private Integer pageCount;
    private Integer tableCount;
    private Integer parsedTextLength;
    private Integer chapterCount;
    private Integer sectionCount;
    private Integer clauseCount;
    private Integer normativeClauseCount;
    private Integer commentaryClauseCount;
    private Integer supplementaryCount;
    private Integer appendixCount;
    private Integer unknownRoleCount;
    private Integer unstructuredParagraphCount;
    private Integer duplicateClauseCount;
    private Integer chunkCount;
    private Integer oversizedChunkCount;
    private Integer emptyChunkCount;
    private String qualityStatus;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String warnings;
    private Date createTime;
}
