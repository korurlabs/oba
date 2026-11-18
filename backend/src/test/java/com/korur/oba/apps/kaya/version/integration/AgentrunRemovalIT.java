package com.korur.oba.apps.kaya.version.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the in-app agent is gone from the migrated database and from the HTTP surface: no agentrun
 * schema, no agent run column on versions, no daily agent run quota on organizations, no route that
 * lists agent runs, and a result callback that the JWT chain refuses without a token.
 */
class AgentrunRemovalIT extends AbstractVersionIT {

    @BeforeEach
    void setUp() {
        initTenant();
    }

    @Test
    void schema_afterMigrations_hasNoAgentrunObjects() {
        Integer schemas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'agentrun'", Integer.class);
        Integer versionColumns = jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns"
                + " WHERE table_schema = 'version' AND table_name = 'versions' AND column_name = 'agent_run_id'",
                Integer.class);
        Integer quotaColumns = jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.columns"
                + " WHERE table_schema = 'identity' AND table_name = 'organizations'"
                + " AND column_name = 'agent_runs_per_day'", Integer.class);

        assertEquals(0, schemas, "the agentrun schema is dropped");
        assertEquals(0, versionColumns, "kaya.versions has no agent_run_id");
        assertEquals(0, quotaColumns, "shrine.organizations has no agent_runs_per_day");
    }

    @Test
    void getAgentRuns_authenticatedMember_returns404() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/agent-runs"), member, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void postAgentRunnerResult_withoutToken_isRefusedWithA4xx() throws Exception {
        int httpStatus = mockMvc.perform(post("/kaya/agent-runner/runs/" + UUID.randomUUID() + "/result")
                        .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus();

        assertTrue(httpStatus >= 400 && httpStatus < 500, "expected a 4xx but got " + httpStatus);
    }
}
