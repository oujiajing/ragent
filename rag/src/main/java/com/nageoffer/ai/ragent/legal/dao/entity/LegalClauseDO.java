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
@TableName(value = "t_legal_clause", autoResultMap = true)
public class LegalClauseDO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String documentId;
    private String contentRole;
    private String structureType;
    private String chapterNo;
    private String chapterTitle;
    private String sectionNo;
    private String sectionTitle;
    private String clauseNo;
    private String hierarchyPath;
    private String rawText;
    private String normalizedText;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String childrenJson;
    private String firstElementId;
    private String lastElementId;
    private Integer pageStart;
    private Integer pageEnd;
    private Date createTime;
}
