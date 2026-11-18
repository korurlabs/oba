package com.korur.oba.apps.kaya.render.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /path-mappings/tools on PostgreSQL: every member reads one entry per AI tool, Claude Code reads
 * the rules through .claude/rules while a version holds the link and sees them as missing once a
 * version does not, and the endpoint refuses more than 20 ids, a caller without a JWT and a caller of
 * another organization.
 */
class ToolMappingControllerIT extends AbstractVersionIT {

    private static final String CLAUDE_RULES = "$.data[0].reads[?(@.readPath == '.claude/rules/<path>.md')]";

    private UUID linkedVersionId;
    private UUID unlinkedVersionId;

    @BeforeEach
    void setUp() {
        initTenant();
        linkedVersionId = approvedVersion(insertPack(tenant.organization(), "linked"), 1);
        renderService.parse(tenant.organization().getId(), linkedVersionId, linkedTree());
        unlinkedVersionId = approvedVersion(insertPack(tenant.organization(), "unlinked"), 1);
        renderService.parse(tenant.organization().getId(), unlinkedVersionId, RenderTrees.base());
    }

    @Test
    void getToolMappings_versionWithTheRulesLink_showsClaudeCodeRulesLinked() throws Exception {
        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings/tools?versionId=" + linkedVersionId), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].tool").value("CLAUDE_CODE"))
                .andExpect(jsonPath("$.data[0].name").value("Claude Code"))
                .andExpect(jsonPath("$.data[3].tool").value("GEMINI_CLI"))
                .andExpect(jsonPath("$.data[0].notDelivered[0].path").value("CLAUDE.md"))
                .andExpect(jsonPath("$.data[2].notDelivered[0].path").value(".cursor/rules/"))
                .andExpect(jsonPath(CLAUDE_RULES + ".loading").value("ALWAYS"))
                .andExpect(jsonPath(CLAUDE_RULES + ".linkPath").value(".claude/rules"))
                .andExpect(jsonPath(CLAUDE_RULES + ".linkTarget").value("../.agents/rules"))
                .andExpect(jsonPath(CLAUDE_RULES + ".sourcePattern").value(".agents/rules/<path>.md"))
                .andExpect(jsonPath(CLAUDE_RULES + ".kind").value("RULE"))
                .andExpect(jsonPath(CLAUDE_RULES + ".state").value("LINKED"))
                .andExpect(jsonPath(CLAUDE_RULES + ".count").value(2))
                .andExpect(jsonPath("$.data[1].reads[?(@.readPath == '.agents/rules/<path>.md')].linkPath", contains(nullValue())))
                .andExpect(jsonPath("$.data[1].reads[?(@.readPath == '.agents/rules/<path>.md')].state").value("DIRECT"));
    }

    @Test
    void getToolMappings_versionWithoutTheRulesLink_showsClaudeCodeRulesLinkMissing() throws Exception {
        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings/tools?versionId=" + unlinkedVersionId), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CLAUDE_RULES + ".state").value("LINK_MISSING"))
                .andExpect(jsonPath(CLAUDE_RULES + ".count").value(2))
                .andExpect(jsonPath("$.data[0].reads[0].readPath").value("AGENTS.md"))
                .andExpect(jsonPath("$.data[0].reads[0].state").value("DIRECT"));
    }

    @Test
    void getToolMappings_noVersionId_returnsEveryToolWithNullStates() throws Exception {
        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings/tools"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].reads[0].readPath").value("AGENTS.md"))
                .andExpect(jsonPath("$.data[0].reads[0].state").doesNotExist())
                .andExpect(jsonPath("$.data[0].reads[0].count").doesNotExist());
    }

    @Test
    void getToolMappings_unknownAndForeignVersions_areNotDelivered() throws Exception {
        // Arrange
        Tenant other = newTenant();
        UUID foreignVersion = approvedVersion(insertPack(other.organization(), "foreign"), 1);
        renderService.parse(other.organization().getId(), foreignVersion, linkedTree());

        // Act and assert
        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings/tools?versionId=" + foreignVersion
                        + "&versionId=" + UUID.randomUUID()), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CLAUDE_RULES + ".state").value("NOT_DELIVERED"))
                .andExpect(jsonPath(CLAUDE_RULES + ".count").value(0));
    }

    @Test
    void getToolMappings_moreThan20VersionIds_answers422() throws Exception {
        StringBuilder query = new StringBuilder("/kaya/path-mappings/tools?versionId=" + linkedVersionId);
        for (int index = 0; index < 20; index++) {
            query.append("&versionId=").append(UUID.randomUUID());
        }

        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get(query.toString()), member, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
    }

    @Test
    void getToolMappings_withoutJwt_answers401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/kaya/path-mappings/tools").header("X-Organization-ID", tenant.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getToolMappings_userOfAnotherOrganization_answers403() throws Exception {
        RequestPostProcessor outsider = jwtOf(newTenant().owner().getUser());

        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings/tools?versionId=" + linkedVersionId), outsider, null))
                .andExpect(status().isForbidden());
    }

    // The base tree with AGENTS.md as the link to .agents/AGENTS.md and the two .claude links.
    private static List<SourceFile> linkedTree() {
        List<SourceFile> tree = new ArrayList<>(RenderTrees.base());
        tree.removeIf(file -> file.path().equals("AGENTS.md"));
        tree.add(new SourceFile("AGENTS.md", FileMode.SYMLINK, ".agents/AGENTS.md", null, null));
        tree.add(new SourceFile(".claude/rules", FileMode.SYMLINK, "../.agents/rules", null, null));
        tree.add(new SourceFile(".claude/templates", FileMode.SYMLINK, "../.agents/templates", null, null));
        return tree;
    }
}
