package com.korur.oba.apps.kaya.guardrail.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The guardrail reads: the list of a version sorted by name with each guardrail's category, deny
 * rules and enforcement, and the detail with the file's text.
 */
class GuardrailControllerIT extends AbstractVersionIT {

    private static final String PROTECT_SECRETS = "---\ncategory: SECURITY\ndescription: Keep secrets out of sessions\n"
            + "deny:\n  - Read(./.env)\n---\nNever read secret files.\n";
    private static final String ENGLISH = "---\ncategory: LANGUAGE\n---\nAnswer in English.\n";

    private UUID versionId;

    @BeforeEach
    void setUp() {
        initTenant();
        versionId = approvedVersion(insertPack(tenant.organization(), "guardrails"), 1);
        renderService.parse(tenant.organization().getId(), versionId, List.of(
                SourceFile.of(".agents/rules/guardrails/protect-secrets.md", FileMode.FILE, utf8(PROTECT_SECRETS)),
                SourceFile.of(".agents/rules/guardrails/english.md", FileMode.FILE, utf8(ENGLISH))));
    }

    @Test
    void getGuardrails_versionWithTwoGuardrails_listsThemByNameWithTheirEnforcement() throws Exception {
        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/guardrails?versionId=" + versionId), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("english"))
                .andExpect(jsonPath("$.data[0].path").value("english.md"))
                .andExpect(jsonPath("$.data[0].category").value("LANGUAGE"))
                .andExpect(jsonPath("$.data[0].deny.length()").value(0))
                .andExpect(jsonPath("$.data[0].enforcement").value("INSTRUCT"))
                .andExpect(jsonPath("$.data[1].name").value("protect-secrets"))
                .andExpect(jsonPath("$.data[1].description").value("Keep secrets out of sessions"))
                .andExpect(jsonPath("$.data[1].deny[0]").value("Read(./.env)"))
                .andExpect(jsonPath("$.data[1].enforcement").value("BLOCK"))
                .andExpect(jsonPath("$.data[1].sizeBytes").value(PROTECT_SECRETS.length()));
    }

    @Test
    void getGuardrail_ownRow_returnsTheFileText() throws Exception {
        UUID id = jdbcTemplate.queryForObject("SELECT id FROM kaya.guardrails WHERE version_id = ? AND name = ?",
                UUID.class, versionId, "protect-secrets");

        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/guardrails/" + id), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.content").value(PROTECT_SECRETS))
                .andExpect(jsonPath("$.data.enforcement").value("BLOCK"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void getGuardrails_noVersionId_answers422() throws Exception {
        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/guardrails"), member, null))
                .andExpect(status().isUnprocessableEntity());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
