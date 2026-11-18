package com.korur.oba.apps.kaya.pack.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.pack.service.PackQueryService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The packs of an organization over HTTP: the list by name with each pack's current version, one
 * pack with the repositories that compose it, and creation by an OWNER with its name rules, unique
 * within one organization only, and an optional description, as a root pack or as the child of a
 * pack of the same organization; the update of a pack's description at any time and of its name
 * only while nothing uses it; and the chain and descendant reads of the pack tree.
 */
class PackIT extends AbstractVersionIT {

    @Autowired
    private PackQueryService packQueryService;

    @BeforeEach
    void setUp() {
        initTenant();
    }

    private static String nameBody(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private static String nameAndDescriptionBody(String name, String description) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}";
    }

    private String descriptionOf(UUID packId) {
        return jdbcTemplate.queryForObject("SELECT description FROM kaya.packs WHERE id = ?", String.class, packId);
    }

    private long packCount() {
        return count("SELECT count(*) FROM kaya.packs WHERE organization_id = ?", tenant.organization().getId());
    }

    // --- GET /packs ---

    @Test
    void getPacks_listsTheOrganizationsPacksByNameWithTheirCurrentVersion() throws Exception {
        UUID village = insertPack(tenant.organization(), "village");
        UUID org = insertPack(tenant.organization(), "org");
        UUID current = approvedVersion(org, 1);
        insertPack(newTenant().organization(), "foreign");

        mockMvc.perform(in(tenant, get("/kaya/packs"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(org.toString()))
                .andExpect(jsonPath("$.data[0].name").value("org"))
                .andExpect(jsonPath("$.data[0].currentVersionId").value(current.toString()))
                .andExpect(jsonPath("$.data[1].id").value(village.toString()))
                .andExpect(jsonPath("$.data[1].currentVersionId").doesNotExist());
    }

    @Test
    void getPacks_withoutJwt_answers401() throws Exception {
        mockMvc.perform(get("/kaya/packs").header("X-Organization-ID", tenant.id()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPacks_userOfAnotherOrganization_answers403() throws Exception {
        Tenant other = newTenant();
        mockMvc.perform(in(tenant, get("/kaya/packs"), jwtOf(other.member().getUser()), null))
                .andExpect(status().isForbidden());
    }

    // --- GET /packs/{id} ---

    private Repository repositoryNamed(String name) {
        Repository repository = saveRepository(tenant.organization());
        jdbcTemplate.update("UPDATE kaya.repositories SET name = ? WHERE id = ?", name, repository.getId());
        return repository;
    }

    @Test
    void getPack_composedByRepositories_returnsThemByNameWithFolderAndLatestRelease() throws Exception {
        UUID org = packWith("org", "AGENTS.md", "# Org\n");
        UUID kagi = insertChildPack(tenant.organization(), "kagi", org);
        insertFiles(tenant.organization(), approvedVersion(kagi, 1), "AGENTS.md", "# Kagi\n");
        Repository released = repositoryNamed("b-released");
        Repository unreleased = repositoryNamed("a-unreleased");
        mockMvc.perform(in(tenant, put(packsPath(released)), owner, mountBody(org, kagi, "projects/kagi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.release.number").value(1));
        composePacks(unreleased, org);

        mockMvc.perform(in(tenant, get("/kaya/packs/" + org), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(org.toString()))
                .andExpect(jsonPath("$.data.name").value("org"))
                .andExpect(jsonPath("$.data.currentVersionId").value(currentVersionOf(org).toString()))
                .andExpect(jsonPath("$.data.mountPaths.length()").value(0))
                .andExpect(jsonPath("$.data.repositories.length()").value(2))
                .andExpect(jsonPath("$.data.repositories[0].id").value(unreleased.getId().toString()))
                .andExpect(jsonPath("$.data.repositories[0].name").value("a-unreleased"))
                .andExpect(jsonPath("$.data.repositories[0].remoteUrl").value(unreleased.getRemoteUrl()))
                .andExpect(jsonPath("$.data.repositories[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.repositories[0].rootPath").doesNotExist())
                .andExpect(jsonPath("$.data.repositories[0].latestReleaseNumber").doesNotExist())
                .andExpect(jsonPath("$.data.repositories[1].id").value(released.getId().toString()))
                .andExpect(jsonPath("$.data.repositories[1].rootPath").doesNotExist())
                .andExpect(jsonPath("$.data.repositories[1].latestReleaseNumber").value(1));
        mockMvc.perform(in(tenant, get("/kaya/packs/" + kagi), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentPackId").value(org.toString()))
                .andExpect(jsonPath("$.data.mountPaths[0]").value("projects/kagi"))
                .andExpect(jsonPath("$.data.repositories.length()").value(1))
                .andExpect(jsonPath("$.data.repositories[0].id").value(released.getId().toString()))
                .andExpect(jsonPath("$.data.repositories[0].rootPath").value("projects/kagi"))
                .andExpect(jsonPath("$.data.repositories[0].latestReleaseNumber").value(1));
    }

    @Test
    void getPack_withoutRepositories_returnsThePackWithAnEmptyList() throws Exception {
        UUID pack = insertPack(tenant.organization(), "unused");
        saveRepository(tenant.organization());

        mockMvc.perform(in(tenant, get("/kaya/packs/" + pack), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(pack.toString()))
                .andExpect(jsonPath("$.data.currentVersionId").doesNotExist())
                .andExpect(jsonPath("$.data.required").value(false))
                .andExpect(jsonPath("$.data.repositories.length()").value(0));
    }

    @Test
    void getPack_requiredPack_returnsEveryActiveRepositoryOfTheOrganization() throws Exception {
        UUID pack = insertPack(tenant.organization(), "required");
        jdbcTemplate.update("UPDATE kaya.packs SET required = true WHERE id = ?", pack);
        Repository repository = saveRepository(tenant.organization());
        saveRepository(newTenant().organization());

        mockMvc.perform(in(tenant, get("/kaya/packs/" + pack), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.required").value(true))
                .andExpect(jsonPath("$.data.repositories.length()").value(1))
                .andExpect(jsonPath("$.data.repositories[0].id").value(repository.getId().toString()));
    }

    @Test
    void getPack_packOfAnotherOrganizationOrUnknownId_answers404() throws Exception {
        UUID foreign = insertPack(newTenant().organization(), "foreign");

        for (UUID id : List.of(foreign, UUID.randomUUID())) {
            mockMvc.perform(in(tenant, get("/kaya/packs/" + id), member, null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }
    }

    // --- PATCH /packs/{id} ---

    private String nameOf(UUID packId) {
        return jdbcTemplate.queryForObject("SELECT name FROM kaya.packs WHERE id = ?", String.class, packId);
    }

    private void expectInUse(UUID packId, String condition) throws Exception {
        mockMvc.perform(in(tenant, patch("/kaya/packs/" + packId), owner, nameBody("renamed")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_035"))
                .andExpect(jsonPath("$.error.errorValues.packId").value(packId.toString()))
                .andExpect(jsonPath("$.error.errorValues.condition").value(condition));
    }

    @Test
    void updatePack_freshPackWithADraftOutsideItsNamespace_renamesIt() throws Exception {
        UUID pack = insertPack(tenant.organization(), "fresh");
        insertFiles(tenant.organization(), insertDraft(pack, 1, proposer), "AGENTS.md", "# Fresh\n");

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, nameBody("family-java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(pack.toString()))
                .andExpect(jsonPath("$.data.name").value("family-java"))
                .andExpect(jsonPath("$.data.currentVersionId").doesNotExist())
                .andExpect(jsonPath("$.data.required").value(false))
                .andExpect(jsonPath("$.data.mountPaths.length()").value(0));
        assertEquals("family-java", nameOf(pack));
    }

    @Test
    void updatePack_nameTaken_answers409AndKeepsTheName() throws Exception {
        UUID pack = insertPack(tenant.organization(), "fresh");
        insertPack(tenant.organization(), "taken");

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, nameBody("taken")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_008"));
        assertEquals("fresh", nameOf(pack));
    }

    @Test
    void updatePack_unchangedNameOfAPackInUse_answers200WithoutWriting() throws Exception {
        UUID pack = packWith("used", "AGENTS.md", "# Used\n");
        composePacks(saveRepository(tenant.organization()), pack);
        Object updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM kaya.packs WHERE id = ?", Object.class, pack);

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, nameBody("used")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("used"))
                .andExpect(jsonPath("$.data.currentVersionId").value(currentVersionOf(pack).toString()));
        assertEquals(updatedAt, jdbcTemplate.queryForObject("SELECT updated_at FROM kaya.packs WHERE id = ?", Object.class, pack));
    }

    @Test
    void updatePack_descriptionOnlyOfARequiredComposedPack_writesItAndKeepsTheName() throws Exception {
        UUID pack = packWith("used", "AGENTS.md", "# Used\n");
        jdbcTemplate.update("UPDATE kaya.packs SET required = true WHERE id = ?", pack);
        composePacks(saveRepository(tenant.organization()), pack);

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, nameAndDescriptionBody("used", "Shared Java rules")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("used"))
                .andExpect(jsonPath("$.data.description").value("Shared Java rules"))
                .andExpect(jsonPath("$.data.required").value(true));
        assertEquals("Shared Java rules", descriptionOf(pack));
        mockMvc.perform(in(tenant, get("/kaya/packs/" + pack), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Shared Java rules"));
    }

    @Test
    void updatePack_nullDescription_removesIt() throws Exception {
        UUID pack = insertPack(tenant.organization(), "fresh");
        jdbcTemplate.update("UPDATE kaya.packs SET description = 'Old text' WHERE id = ?", pack);

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, "{\"name\":\"fresh\",\"description\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").doesNotExist());
        assertNull(descriptionOf(pack));
    }

    @Test
    void updatePack_newNameAndDescriptionOfAPackInUse_answers409AndWritesNeither() throws Exception {
        UUID pack = packWith("used", "AGENTS.md", "# Used\n");

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, nameAndDescriptionBody("renamed", "New text")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_035"))
                .andExpect(jsonPath("$.error.errorValues.condition").value("VERSIONED"));
        assertEquals("used", nameOf(pack));
        assertNull(descriptionOf(pack));
    }

    @Test
    void updatePack_blankOrTooLongDescription_answers422() throws Exception {
        UUID pack = insertPack(tenant.organization(), "fresh");

        for (String description : List.of("", "   ", "a".repeat(1001))) {
            mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, nameAndDescriptionBody("fresh", description)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }
        assertNull(descriptionOf(pack));
    }

    @Test
    void updatePack_composedByARepositoryItselfOrThroughAChild_answers409Composed() throws Exception {
        UUID parent = insertPack(tenant.organization(), "parent");
        UUID child = insertChildPack(tenant.organization(), "child", parent);
        composePacks(saveRepository(tenant.organization()), child);

        expectInUse(child, "COMPOSED");
        expectInUse(parent, "COMPOSED");
        assertEquals("parent", nameOf(parent));
        assertEquals("child", nameOf(child));
    }

    @Test
    void updatePack_requiredPack_answers409Required() throws Exception {
        UUID pack = insertPack(tenant.organization(), "required");
        jdbcTemplate.update("UPDATE kaya.packs SET required = true WHERE id = ?", pack);

        expectInUse(pack, "REQUIRED");
    }

    @Test
    void updatePack_approvedOrInReviewVersion_answers409Versioned() throws Exception {
        UUID approved = packWith("approved", "AGENTS.md", "# Approved\n");
        UUID inReview = insertPack(tenant.organization(), "in-review");
        UUID draft = insertDraft(inReview, 1, proposer);
        jdbcTemplate.update("UPDATE kaya.versions SET status = 'IN_REVIEW'::version_version_status_enum WHERE id = ?", draft);

        expectInUse(approved, "VERSIONED");
        expectInUse(inReview, "VERSIONED");
        assertEquals("approved", nameOf(approved));
    }

    @Test
    void updatePack_draftHoldsAFileInTheNamesNamespace_answers409DraftNamespaceFiles() throws Exception {
        UUID pack = insertPack(tenant.organization(), "kagi");
        insertFiles(tenant.organization(), insertDraft(pack, 1, proposer),
                ".agents/rules/kagi/api.md", "---\npaths: [\"projects/kagi/**\"]\n---\n# Api\n");

        expectInUse(pack, "DRAFT_NAMESPACE_FILES");
        assertEquals("kagi", nameOf(pack));
    }

    @Test
    void updatePack_packOfAnotherOrganizationOrUnknownId_answers404() throws Exception {
        UUID foreign = insertPack(newTenant().organization(), "foreign");

        for (UUID id : List.of(foreign, UUID.randomUUID())) {
            mockMvc.perform(in(tenant, patch("/kaya/packs/" + id), owner, nameBody("renamed")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }
        assertEquals("foreign", nameOf(foreign));
    }

    @Test
    void updatePack_memberOrAdmin_answers403AndKeepsTheName() throws Exception {
        UUID pack = insertPack(tenant.organization(), "fresh");

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), member, nameBody("renamed"))).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), admin, nameBody("renamed"))).andExpect(status().isForbidden());
        assertEquals("fresh", nameOf(pack));
    }

    @Test
    void updatePack_invalidName_answers422() throws Exception {
        UUID pack = insertPack(tenant.organization(), "fresh");

        mockMvc.perform(in(tenant, patch("/kaya/packs/" + pack), owner, nameBody("Not Valid")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        assertEquals("fresh", nameOf(pack));
    }

    // --- POST /packs ---

    @Test
    void createPack_owner_creates201ThenRefusesTheSameName() throws Exception {
        mockMvc.perform(in(tenant, post("/kaya/packs"), owner, nameBody("family-java")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("family-java"))
                .andExpect(jsonPath("$.data.description").doesNotExist())
                .andExpect(jsonPath("$.data.currentVersionId").doesNotExist());
        mockMvc.perform(in(tenant, post("/kaya/packs"), owner, nameBody("family-java")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_008"));
        assertEquals(1, count("SELECT count(*) FROM kaya.packs WHERE organization_id = ? AND name = 'family-java'",
                tenant.organization().getId()));
    }

    @Test
    void createPack_memberOrAdmin_answers403AndCreatesNothing() throws Exception {
        mockMvc.perform(in(tenant, post("/kaya/packs"), member, nameBody("x"))).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, post("/kaya/packs"), admin, nameBody("x"))).andExpect(status().isForbidden());
        assertEquals(0, packCount());
    }

    @Test
    void createPack_badBodies_answer422OrMalformed400() throws Exception {
        String[] unprocessable = {
                "{}",
                nameBody(""),
                nameBody("a".repeat(61)),
                nameBody("has_underscore"),
                nameBody("UPPER"),
                nameBody("Not Valid"),
                nameAndDescriptionBody("family-java", ""),
                nameAndDescriptionBody("family-java", "a".repeat(1001))
        };
        for (String body : unprocessable) {
            mockMvc.perform(in(tenant, post("/kaya/packs"), owner, body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }
        mockMvc.perform(in(tenant, post("/kaya/packs"), owner, "{\"name\":"))
                .andExpect(status().isBadRequest());
        assertEquals(0, packCount());
    }

    @Test
    void createPack_withDescription_returnsAndStoresIt() throws Exception {
        String description = "d".repeat(1000);

        mockMvc.perform(in(tenant, post("/kaya/packs"), owner, nameAndDescriptionBody("family-java", description)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description").value(description));
        mockMvc.perform(in(tenant, get("/kaya/packs"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").value(description));
    }

    @Test
    void createPack_nameOfSixtyCharacters_isAccepted() throws Exception {
        mockMvc.perform(in(tenant, post("/kaya/packs"), owner, nameBody("a".repeat(60))))
                .andExpect(status().isCreated());
    }

    @Test
    void createPack_sameNameInAnotherOrganization_isAllowed() throws Exception {
        Tenant other = newTenant();
        mockMvc.perform(in(tenant, post("/kaya/packs"), owner, nameBody("shared-name"))).andExpect(status().isCreated());
        mockMvc.perform(in(other, post("/kaya/packs"), jwtOf(other.owner().getUser()), nameBody("shared-name")))
                .andExpect(status().isCreated());
    }

    @Test
    void createPack_childOfAPackOfTheOrganization_creates201WithTheParent() throws Exception {
        UUID org = insertPack(tenant.organization(), "org");

        mockMvc.perform(in(tenant, post("/kaya/packs"), owner, "{\"name\":\"family-java\",\"parentPackId\":\"" + org + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("family-java"))
                .andExpect(jsonPath("$.data.parentPackId").value(org.toString()));
        mockMvc.perform(in(tenant, get("/kaya/packs"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("family-java"))
                .andExpect(jsonPath("$.data[0].parentPackId").value(org.toString()))
                .andExpect(jsonPath("$.data[1].parentPackId").doesNotExist());
    }

    @Test
    void createPack_unknownOrForeignParent_answers404AndCreatesNothing() throws Exception {
        UUID foreign = insertPack(newTenant().organization(), "foreign");

        for (UUID parent : List.of(UUID.randomUUID(), foreign)) {
            mockMvc.perform(in(tenant, post("/kaya/packs"), owner, "{\"name\":\"child\",\"parentPackId\":\"" + parent + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }
        assertEquals(0, packCount());
    }

    // --- Chain and descendants ---

    @Test
    void getChain_grandchild_returnsTheRootFirst() {
        UUID org = insertPack(tenant.organization(), "org");
        UUID family = insertChildPack(tenant.organization(), "family", org);
        UUID repository = insertChildPack(tenant.organization(), "repo", family);

        List<UUID> chain = packQueryService.getChain(tenant.organization().getId(), repository).stream()
                .map(Pack::getId)
                .toList();

        assertEquals(List.of(org, family, repository), chain);
    }

    @Test
    void getDescendantIds_tree_returnsNearestLevelFirstWithoutTheRoot() {
        UUID org = insertPack(tenant.organization(), "org");
        UUID family = insertChildPack(tenant.organization(), "family", org);
        UUID other = insertChildPack(tenant.organization(), "other", org);
        UUID repository = insertChildPack(tenant.organization(), "repo", family);
        insertPack(tenant.organization(), "unrelated");

        List<UUID> descendants = packQueryService.getDescendantIds(tenant.organization().getId(), org);

        assertEquals(3, descendants.size());
        assertEquals(Set.of(family, other), Set.copyOf(descendants.subList(0, 2)));
        assertEquals(repository, descendants.get(2));
        assertEquals(List.of(), packQueryService.getDescendantIds(tenant.organization().getId(), repository));
    }
}
