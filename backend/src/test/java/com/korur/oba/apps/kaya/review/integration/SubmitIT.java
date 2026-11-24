package com.korur.oba.apps.kaya.review.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Submitting a draft for review over HTTP: the reason, one change in review per pack,
 * the unchanged and executable drafts, and the composition rules of every repository that
 * composes the owner.
 */
class SubmitIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AGENTS = "# Agents\n";
    private static final String REASON = "Tighten the agent instructions";
    private static final String STATUS_OF = "SELECT status::text FROM kaya.versions WHERE id = ?";

    private Repository repository;
    private UUID packId;
    private UUID v1;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        repository = saveRepository(tenant.organization());
        packId = insertRepositoryPack(tenant.organization(), repository.getId(), "ws-" + uniqueSuffix().substring(0, 8));
        v1 = approvedVersion(packId, 1);
        insertFile(tenant.organization(), v1, "AGENTS.md", AGENTS);
        insertFile(tenant.organization(), v1, ".agents/rules/local.md", "---\npaths: \"src/**\"\n---\nLocal\n");
    }

    // --- Helpers ---

    private static String body(Object... keysAndValues) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            map.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return JSON.writeValueAsString(map);
    }

    private ResultActions save(UUID draft, String path, String content) throws Exception {
        return mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                body("path", path, "content", content)));
    }

    private ResultActions submit(UUID draft, String reason) throws Exception {
        return mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), owner, body("reason", reason)));
    }

    // --- Submit ---

    @Test
    void submit_validDraft_movesItToReviewOncePerPack() throws Exception {
        UUID first = startDraft(packId, owner);
        UUID second = startDraft(packId, owner);
        save(first, "AGENTS.md", "# First\n").andExpect(status().isOk());
        save(second, "AGENTS.md", "# Second\n").andExpect(status().isOk());

        submit(first, "Too short")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        submit(first, REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.data.reason").value(REASON))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.executableChange").value(false));

        submit(second, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_015"))
                .andExpect(jsonPath("$.error.errorValues.inReviewVersionId").value(first.toString()));
        save(first, "AGENTS.md", "# Again\n")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_014"));
        assertEquals(1, count("SELECT count(*) FROM kaya.versions WHERE pack_id = ? AND status = 'IN_REVIEW'", packId));
    }

    @Test
    void submit_unchangedDraft_answers409() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", AGENTS).andExpect(status().isOk());

        submit(draft, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_016"));
    }

    @Test
    void submit_executableHook_flagsTheExecutableChange() throws Exception {
        UUID draft = startDraft(packId, owner);
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        body("path", ".agents/hooks/check.sh", "executable", true, "content", "#!/bin/sh\nexit 0\n")))
                .andExpect(status().isOk());

        submit(draft, REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executableChange").value(true));
    }

    @Test
    void submit_afterTheOwnerMovedOn_answers409AndLeavesTheDraft() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "# Mine\n").andExpect(status().isOk());
        UUID v3 = approvedVersion(packId, 3);
        insertFile(tenant.organization(), v3, "AGENTS.md", AGENTS);
        insertFile(tenant.organization(), v3, ".agents/templates/x.md", "X\n");

        submit(draft, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_002"))
                .andExpect(jsonPath("$.error.errorValues.currentVersionId").value(v3.toString()));
        assertEquals("DRAFT", jdbcTemplate.queryForObject(STATUS_OF, String.class, draft));
    }

    // --- Composition rules of the repositories that compose the owner ---

    @Test
    void submit_alwaysOnFilesOverFortyThousandBytes_movesItToReview() throws Exception {
        UUID draft = startDraft(packId, owner);
        save(draft, "AGENTS.md", "a".repeat(30000)).andExpect(status().isOk());
        save(draft, ".agents/rules/always.md", "b".repeat(20000)).andExpect(status().isOk());

        submit(draft, REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));
    }

    @Test
    void submit_packDraft_isCheckedAgainstEveryRepositoryThatComposesTheBuild() throws Exception {
        UUID pack = packWith("org", ".agents/rules/style.md", "---\npaths: \"src/**\"\n---\nStyle\n");
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack))).andExpect(status().isOk());
        UUID draft = startDraft(pack, owner);
        // The guardrail denies a read, which only a settings file the repository lacks can enforce.
        save(draft, ".agents/rules/guardrails/no-secrets.md", "---\ncategory: SECURITY\ndescription: Keep secrets out of sessions\ndeny:\n  - Read(./.env)\n---\n"
                + "Never read secret files.\n").andExpect(status().isOk());

        submit(draft, REASON)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_001"))
                .andExpect(jsonPath("$.error.errorValues.violations[?(@.rule == 'GUARDRAIL_SETTINGS_MISSING')]").exists());
        assertEquals("DRAFT", jdbcTemplate.queryForObject(STATUS_OF, String.class, draft));
    }

    // --- A pipeline file is no longer a delivered kind ---

    @Test
    void save_pipelineFile_isRefusedAndKeepsTheDraft() throws Exception {
        UUID draft = startDraft(packId, owner);

        save(draft, ".agents/pipelines/feature.yaml", "name: Feature\nsteps:\n  - workflow: ship\n")
                .andExpect(status().is4xxClientError());
        assertEquals("DRAFT", jdbcTemplate.queryForObject(STATUS_OF, String.class, draft));
    }
}
