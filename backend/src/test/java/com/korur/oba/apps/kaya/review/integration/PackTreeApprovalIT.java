package com.korur.oba.apps.kaya.review.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
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
 * What approving a change in a pack tree does: an approval of the org pack releases every
 * repository that composes one of its descendants and writes no version into any descendant,
 * and an approval re-checks the mount rule scope against the folders the pack is mounted in
 * now, not the ones it was mounted in when the change was submitted.
 */
class PackTreeApprovalIT extends AbstractVersionIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RELEASES = "SELECT count(*) FROM kaya.releases WHERE repository_id = ?";
    private static final String VERSIONS = "SELECT count(*) FROM kaya.versions WHERE pack_id = ?";
    private static final String UNSCOPED_RULE = "---\npaths: [\"**/*.java\"]\n---\nWide\n";

    @BeforeEach
    void setUp() {
        initTenant();
    }

    private UUID childPackWith(String name, UUID parent, String... pathsAndContents) {
        UUID pack = insertChildPack(tenant.organization(), name, parent);
        insertFiles(tenant.organization(), approvedVersion(pack, 1), pathsAndContents);
        return pack;
    }

    private UUID inReview(UUID pack, String path, String content) throws Exception {
        MvcResult created = mockMvc.perform(in(tenant, post("/kaya/drafts"), owner, draftBody(pack)))
                .andExpect(status().isCreated()).andReturn();
        UUID draft = UUID.fromString(read(created, "$.data.id"));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner,
                        JSON.writeValueAsString(Map.of("path", path, "content", content))))
                .andExpect(status().isOk());
        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), owner, "{\"reason\":\"Change the pack\"}"))
                .andExpect(status().isOk());
        return draft;
    }

    private String statusOf(UUID version) {
        return jdbcTemplate.queryForObject("SELECT status::text FROM kaya.versions WHERE id = ?", String.class, version);
    }

    @Test
    void approve_orgPackChange_releasesVillageAndFabrikamRepositoriesAndGivesNoPackAVersion() throws Exception {
        UUID org = packWith("org", "AGENTS.md", "# Org\n");
        UUID village = childPackWith("village", org, ".agents/roles/dev.md", "# Dev\n");
        UUID fabrikam = childPackWith("fabrikam", org, ".agents/roles/fep.md", "# Fep\n");
        Repository villageRepository = saveRepository(tenant.organization());
        Repository fabrikamRepository = saveRepository(tenant.organization());
        Repository unrelated = saveRepository(tenant.organization());
        UUID other = packWith("other", "AGENTS.md", "# Other\n");
        mockMvc.perform(in(tenant, put(packsPath(villageRepository)), owner, packIds(village))).andExpect(status().isOk());
        mockMvc.perform(in(tenant, put(packsPath(fabrikamRepository)), owner, packIds(fabrikam))).andExpect(status().isOk());
        mockMvc.perform(in(tenant, put(packsPath(unrelated)), owner, packIds(other))).andExpect(status().isOk());
        UUID villageVersion = currentVersionOf(village);
        UUID fabrikamVersion = currentVersionOf(fabrikam);
        UUID draft = inReview(org, "AGENTS.md", "# Org v2\n");

        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/reviews"), admin, "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releases.length()").value(2));

        assertEquals(2, count(RELEASES, villageRepository.getId()));
        assertEquals(2, count(RELEASES, fabrikamRepository.getId()));
        assertEquals(1, count(RELEASES, unrelated.getId()));
        assertEquals(draft, currentVersionOf(org));
        assertEquals(1, count(VERSIONS, village));
        assertEquals(1, count(VERSIONS, fabrikam));
        assertEquals(villageVersion, currentVersionOf(village));
        assertEquals(fabrikamVersion, currentVersionOf(fabrikam));
        assertEquals("# Org v2\n", contentOf(draft, "AGENTS.md"));
    }

    @Test
    void approve_unscopedRuleOfAPackMountedAfterTheSubmit_answers422KayCfg034AndKeepsTheChangeInReview() throws Exception {
        UUID org = packWith("org", "AGENTS.md", "# Org\n");
        UUID kagi = childPackWith("kagi", org, "AGENTS.md", "# Kagi\n");
        UUID draft = inReview(kagi, ".agents/rules/kagi/wide.md", UNSCOPED_RULE);
        Repository repository = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, mountBody(org, kagi, "projects/kagi")))
                .andExpect(status().isOk());
        long releasesBefore = count(RELEASES, repository.getId());

        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/reviews"), admin, "{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_034"))
                .andExpect(jsonPath("$.error.errorValues.rule").value(".agents/rules/kagi/wide.md"))
                .andExpect(jsonPath("$.error.errorValues.folder").value("projects/kagi"));

        assertEquals("IN_REVIEW", statusOf(draft));
        assertEquals(releasesBefore, count(RELEASES, repository.getId()));
    }
}
