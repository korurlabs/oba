package com.korur.oba.apps.kaya.render.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.PathMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.render.helper.DeliveryPaths;
import com.korur.oba.apps.kaya.render.mock_data.PathMappingRows;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /path-mappings on PostgreSQL: every member reads the rows, the counts of seeded packs equal
 * the rendered paths that classify to each row, versions of another organization count
 * 0, and the endpoint refuses more than 20 ids, a caller without a JWT and a caller of another
 * organization.
 */
class PathMappingControllerIT extends AbstractVersionIT {

    private UUID packVersionId;
    private UUID repositoryVersionId;

    @BeforeEach
    void setUp() {
        initTenant();
        packVersionId = approvedVersion(insertPack(tenant.organization(), "mapping"), 1);
        renderService.parse(tenant.organization().getId(), packVersionId, everyKind());
        Repository repository = saveRepository(tenant.organization());
        repositoryVersionId = currentVersionOf(repositoryPackWith(repository, "AGENTS.md", "# Agents\n"));
    }

    @Test
    void getPathMappings_seededPacks_countEqualsTheRenderedPathsOfEachRow() throws Exception {
        // Arrange
        List<Integer> expected = renderedCounts(List.of(packVersionId, repositoryVersionId));

        // Act
        MvcResult result = mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings?versionId=" + packVersionId
                        + "&versionId=" + repositoryVersionId), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(22))
                .andExpect(jsonPath("$.data[2].placement").value("MOUNT_ROOT"))
                .andExpect(jsonPath("$.data[2].count").value(0))
                .andExpect(jsonPath("$.data[?(@.kind == 'PIPELINE')]").isEmpty())
                .andReturn();

        // Assert
        List<Integer> counts = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].count");
        assertEquals(expected, counts);
    }

    @Test
    void getPathMappings_noVersionId_returnsEveryRowWithNullCounts() throws Exception {
        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(22))
                .andExpect(jsonPath("$.data[0].kind").value("AGENTS_FILE"))
                .andExpect(jsonPath("$.data[0].placement").value("ROOT"))
                .andExpect(jsonPath("$.data[0].pathPattern").value("AGENTS.md"))
                .andExpect(jsonPath("$.data[14].part").value("SCRIPT"))
                .andExpect(jsonPath("$.data[14].executable").value(true))
                .andExpect(jsonPath("$.data[0].link").doesNotExist())
                .andExpect(jsonPath("$.data[20].kind").value("TOOL_LINK"))
                .andExpect(jsonPath("$.data[20].link").value("CLAUDE_RULES"))
                .andExpect(jsonPath("$.data[20].pathPattern").value(".claude/rules"))
                .andExpect(jsonPath("$.data[20].formats[0]").value("SYMLINK"))
                .andExpect(jsonPath("$.data[0].count").doesNotExist());
    }

    @Test
    void getPathMappings_unknownAndForeignVersions_countZero() throws Exception {
        // Arrange
        Tenant other = newTenant();
        UUID foreignVersion = approvedVersion(insertPack(other.organization(), "foreign"), 1);
        renderService.parse(other.organization().getId(), foreignVersion, everyKind());

        // Act
        MvcResult result = mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings?versionId=" + foreignVersion
                        + "&versionId=" + UUID.randomUUID()), member, null))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        List<Integer> counts = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].count");
        assertEquals(22, counts.size());
        counts.forEach(count -> assertEquals(0, count));
    }

    @Test
    void getPathMappings_moreThan20VersionIds_answers422() throws Exception {
        StringBuilder query = new StringBuilder("/kaya/path-mappings?versionId=" + packVersionId);
        for (int index = 0; index < 20; index++) {
            query.append("&versionId=").append(UUID.randomUUID());
        }

        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get(query.toString()), member, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
    }

    @Test
    void getPathMappings_withoutJwt_answers401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/kaya/path-mappings").header("X-Organization-ID", tenant.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPathMappings_userOfAnotherOrganization_answers403() throws Exception {
        RequestPostProcessor outsider = jwtOf(newTenant().owner().getUser());

        mockMvc.perform(in(tenant, MockMvcRequestBuilders.get("/kaya/path-mappings?versionId=" + packVersionId), outsider, null))
                .andExpect(status().isForbidden());
    }

    // The rendered files of each version, counted per row through the path rules.
    private List<Integer> renderedCounts(List<UUID> versionIds) {
        List<PathMapping> mappings = DeliveryPaths.mappings();
        List<Integer> counts = new ArrayList<>(mappings.stream().map(mapping -> 0).toList());
        for (UUID versionId : versionIds) {
            for (RenderedFile file : renderQueryService.render(tenant.organization().getId(), versionId)) {
                int row = mappings.indexOf(PathMappingRows.rowOf(file.path(), file.mode(), null).orElseThrow());
                counts.set(row, counts.get(row) + 1);
            }
        }
        return counts;
    }

    private static List<SourceFile> everyKind() {
        List<SourceFile> tree = new ArrayList<>(RenderTrees.base());
        tree.add(SourceFile.of(".agents/workflows/ship.md", FileMode.FILE, utf8("# Ship\n")));
        tree.add(SourceFile.of(".agents/rules/guardrails/protect-secrets.md", FileMode.FILE, utf8("---\ncategory: SECURITY\n"
                + "description: Keep secrets out of sessions\ndeny:\n  - Read(./.env)\n---\nNever read secret files.\n")));
        return tree;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
