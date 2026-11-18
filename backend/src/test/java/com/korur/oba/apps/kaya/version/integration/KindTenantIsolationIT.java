package com.korur.oba.apps.kaya.version.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The list and detail reads of every kind stay inside the caller's organization: a version id or a
 * row id of another organization lists nothing and answers 404, while the same read in the owning
 * organization finds the rows.
 */
class KindTenantIsolationIT extends AbstractVersionIT {

    // A kind's endpoint, its table, and whether the endpoint lists by version.
    private record Kind(String path, String table, boolean listed) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("/kaya/agents-files", "kaya.agents_files", true),
            new Kind("/kaya/rules", "kaya.rules", true),
            new Kind("/kaya/guardrails", "kaya.guardrails", true),
            new Kind("/kaya/conventions", "kaya.conventions", true),
            new Kind("/kaya/roles", "kaya.roles", true),
            new Kind("/kaya/workflows", "kaya.workflows", true),
            new Kind("/kaya/templates", "kaya.templates", true),
            new Kind("/kaya/hooks", "kaya.hooks", true),
            new Kind("/kaya/subagents", "kaya.subagents", true),
            new Kind("/kaya/settings", "kaya.settings", true),
            new Kind("/kaya/skills", "kaya.skills", true),
            new Kind("/kaya/scripts", "kaya.scripts", false),
            new Kind("/kaya/references", "kaya.reference_files", false),
            new Kind("/kaya/tool-parameters", "kaya.tool_parameters", false));

    private static final String GUARDRAIL_TEXT = "---\ncategory: SECURITY\ndeny:\n  - Read(./.env)\n---\nNever read secrets.\n";

    private UUID ownVersion;
    private UUID foreignVersion;
    private Tenant other;

    @BeforeEach
    void setUp() {
        initTenant();
        ownVersion = approvedVersion(insertPack(tenant.organization(), "own"), 1);
        renderService.parse(tenant.organization().getId(), ownVersion, withGuardrail());
        other = newTenant();
        foreignVersion = approvedVersion(insertPack(other.organization(), "foreign"), 1);
        renderService.parse(other.organization().getId(), foreignVersion, withGuardrail());
    }

    // The base tree holds no guardrail file, and each kind needs a row in each version.
    private static List<SourceFile> withGuardrail() {
        List<SourceFile> tree = new ArrayList<>(RenderTrees.base());
        tree.add(SourceFile.of(".agents/rules/guardrails/protect-secrets.md", FileMode.FILE,
                GUARDRAIL_TEXT.getBytes(StandardCharsets.UTF_8)));
        return tree;
    }

    private UUID firstRow(Kind kind, UUID versionId) {
        return jdbcTemplate.queryForObject("SELECT id FROM " + kind.table() + " WHERE version_id = ? ORDER BY id LIMIT 1",
                UUID.class, versionId);
    }

    @Test
    void list_versionOfAnotherOrganization_listsNothingForEveryKind() throws Exception {
        for (Kind kind : KINDS) {
            if (!kind.listed()) {
                continue;
            }
            mockMvc.perform(in(tenant, MockMvcRequestBuilders.get(kind.path() + "?versionId=" + foreignVersion), member, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Test
    void list_versionOfTheOwnOrganization_findsRowsForEveryKind() throws Exception {
        for (Kind kind : KINDS) {
            if (!kind.listed()) {
                continue;
            }
            long expected = count("SELECT count(*) FROM " + kind.table() + " WHERE version_id = ?", ownVersion);
            String body = mockMvc.perform(in(tenant, MockMvcRequestBuilders.get(kind.path() + "?versionId=" + ownVersion), member, null))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(expected, ((Number) JsonPath.read(body, "$.data.length()")).longValue(), kind.path());
            assertTrue(expected > 0, kind.path());
        }
    }

    @Test
    void detail_rowOfAnotherOrganization_answers404ForEveryKind() throws Exception {
        for (Kind kind : KINDS) {
            UUID foreignRow = firstRow(kind, foreignVersion);
            mockMvc.perform(in(tenant, MockMvcRequestBuilders.get(kind.path() + "/" + foreignRow), member, null))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void detail_rowOfTheOwnOrganization_answers200ForEveryKind() throws Exception {
        for (Kind kind : KINDS) {
            UUID ownRow = firstRow(kind, ownVersion);
            mockMvc.perform(in(tenant, MockMvcRequestBuilders.get(kind.path() + "/" + ownRow), member, null))
                    .andExpect(status().isOk());
        }
    }
}
