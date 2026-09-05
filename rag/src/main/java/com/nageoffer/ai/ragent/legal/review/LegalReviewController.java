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

package com.nageoffer.ai.ragent.legal.review;

import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/knowledge-base/docs")
public class LegalReviewController {

    private final LegalReviewService service;

    @GetMapping("/{doc-id}/legal-review/overview")
    public Result<LegalReviewOverviewVO> overview(@PathVariable("doc-id") String docId) {
        return Results.success(service.overview(docId));
    }

    @GetMapping("/{doc-id}/legal-review/signals")
    public Result<List<LegalReviewSignalVO>> list(@PathVariable("doc-id") String docId,
                                                  @RequestParam(required = false) String signalType,
                                                  @RequestParam(required = false) String reviewStatus) {
        return Results.success(service.list(docId, signalType, reviewStatus));
    }

    @PostMapping("/legal-review/{signal-id}/review")
    public Result<Void> review(@PathVariable("signal-id") String signalId,
                               @RequestBody @Validated ReviewRequest request) {
        service.review(signalId, request.signalStatus, request.reason, request.expectedVersion);
        return Results.success();
    }

    public record ReviewRequest(
            @NotNull LegalReviewStatus signalStatus,
            @NotBlank @Size(max = 1000) String reason,
            @NotNull Integer expectedVersion
    ) {
    }
}
