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

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SafeTeamDevTokenGuard {
    private final SafeTeamIntegrationProperties properties;
    private final Environment environment;

    public SafeTeamDevTokenGuard(SafeTeamIntegrationProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (properties.getDevToken() == null || properties.getDevToken().isBlank()) {
            return;
        }
        boolean nonProductionProfile = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("local") || profile.equals("dev") || profile.equals("test"));
        if (!nonProductionProfile) {
            throw new IllegalStateException("SAFE_TEAM_DEV_TOKEN 仅允许在 local/dev/test profile 使用");
        }
    }
}
