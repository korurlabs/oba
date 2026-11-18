package com.korur.oba.apps.kaya.link.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a file of the editor's scope links to and what uses it: Markdown links found and broken,
 * a rule and the conventions it cites, and the parts of a
 * skill, across the versions of the scope and never across organizations.
 */
class DependencyIT extends AbstractVersionIT {

    private static final String RULE = ".agents/rules/10-backend.md";
    private static final String NAMING = "docs/private/conventions/backend/naming.md";
    private static final String IMPLEMENT = ".agents/workflows/implement.md";
    private static final String SKILL = ".agents/skills/kazan/SKILL.md";
    private static final String SKILL_WORKFLOW = ".agents/skills/kazan/workflows/close.md";
    private static final String LINKER = ".agents/roles/linker.md";

    private UUID packVersion;
    private UUID otherPackVersion;

    @BeforeEach
    void setUp() {
        initTenant();
        packVersion = approvedVersion(insertPack(tenant.organization(), "deps"), 1);
        List<SourceFile> tree = new ArrayList<>(RenderTrees.base());
        renderService.parse(tenant.organization().getId(), packVersion, tree);
        otherPackVersion = approvedVersion(insertPack(tenant.organization(), "linker"), 1);
        insertFiles(tenant.organization(), otherPackVersion,
                LINKER, "# Linker\n\nSee [core](../../AGENTS.md) and [gone](../templates/GONE.md).\n");
    }

    private MvcResult dependencies(String path, UUID... versionIds) throws Exception {
        MockHttpServletRequestBuilder request = get("/kaya/links/dependencies")
                .param("path", path);
        for (UUID versionId : versionIds) {
            request.param("versionId", versionId.toString());
        }
        return mockMvc.perform(in(tenant, request, member, null))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void rule_linksToTheConventionItCites_andTheConventionIsUsedByTheRule() throws Exception {
        MvcResult rule = dependencies(RULE, packVersion);
        MvcResult convention = dependencies(NAMING, packVersion);

        assertEquals("RULE", read(rule, "$.data.kind"));
        assertEquals(List.of("MARKDOWN_LINK", "RULE_CONVENTION"),
                list(rule, "$.data.linksTo[?(@.path == '" + NAMING + "')].relation"));
        assertEquals(List.of(packVersion.toString(), packVersion.toString()),
                list(rule, "$.data.linksTo[?(@.path == '" + NAMING + "')].versionId"));
        assertEquals(List.of("MARKDOWN_LINK", "RULE_CONVENTION"),
                list(convention, "$.data.usedBy[?(@.path == '" + RULE + "')].relation"));
    }

    @Test
    void skill_linksToItsParts_andAPartIsUsedByTheSkill() throws Exception {
        MvcResult skill = dependencies(SKILL, packVersion);
        MvcResult part = dependencies(SKILL_WORKFLOW, packVersion);

        assertEquals(List.of("SKILL_PART"), list(skill, "$.data.linksTo[?(@.path == '" + SKILL_WORKFLOW + "')].relation"));
        assertEquals(List.of("SKILL"), list(part, "$.data.usedBy[?(@.path == '" + SKILL + "')].kind"));
    }

    @Test
    void markdownLinks_acrossTheScope_areFoundOrBroken() throws Exception {
        MvcResult linker = dependencies(LINKER, packVersion, otherPackVersion);
        MvcResult agents = dependencies("AGENTS.md", packVersion, otherPackVersion);

        assertEquals(List.of("FOUND"), list(linker, "$.data.linksTo[?(@.path == 'AGENTS.md')].status"));
        assertEquals(List.of(packVersion.toString()), list(linker, "$.data.linksTo[?(@.path == 'AGENTS.md')].versionId"));
        assertEquals(List.of("BROKEN"), list(linker, "$.data.linksTo[?(@.path == '.agents/templates/GONE.md')].status"));
        assertEquals(List.of(otherPackVersion.toString()), list(agents, "$.data.usedBy[?(@.path == '" + LINKER + "')].versionId"));
    }

    @Test
    void pathNoVersionDelivers_answers404() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/links/dependencies").param("versionId", packVersion.toString())
                        .param("path", ".agents/rules/none.md"), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void versionOfAnotherOrganization_isLeftOutOfTheScope() throws Exception {
        Tenant other = newTenant();

        mockMvc.perform(in(other, get("/kaya/links/dependencies").param("versionId", packVersion.toString())
                        .param("path", RULE), jwtOf(other.member().getUser()), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void missingVersionId_answers422() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/links/dependencies").param("path", RULE), member, null))
                .andExpect(status().isUnprocessableEntity());
    }
}
