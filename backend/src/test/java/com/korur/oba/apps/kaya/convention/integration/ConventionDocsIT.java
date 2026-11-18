package com.korur.oba.apps.kaya.convention.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The convention docs of a pack: the links between delivered Markdown files as GET /links serves
 * them for a version, a repository's latest release and a repository with a draft in place.
 */
class ConventionDocsIT extends AbstractVersionIT {

    private static final String SOFT_DELETE = "docs/private/conventions/backend/32-soft-delete.md";
    private static final String BACKEND_RULE = ".agents/rules/11-backend-rules.md";
    private static final String BACKEND_DIRECTORY = "docs/private/conventions/backend/";
    private static final String GONE = "docs/private/conventions/backend/99-gone.md";
    private static final String INDEX = "docs/private/conventions/_index.md";
    private static final String LOCAL_RULE = ".agents/rules/local.md";

    private static final String RULE_CONTENT = "See [soft delete](/" + SOFT_DELETE + ").\n"
            + "Gone: [missing](/" + GONE + ")\n"
            + "Folder: `" + BACKEND_DIRECTORY + "`\n";
    private static final String SOFT_DELETE_CONTENT = "Back to [the rule](/" + BACKEND_RULE + ").\n";
    private static final String INDEX_CONTENT = "[soft delete](./backend/32-soft-delete.md)\n";

    private Repository repository;
    private UUID rootPack;

    @BeforeEach
    void setUp() {
        initTenant();
        repository = saveRepository(tenant.organization());
    }

    /** The organization pack: a rule that cites docs, the docs themselves and a doc index. */
    private UUID orgPack() {
        return packWith("org", "AGENTS.md", "# Agents\n", BACKEND_RULE, RULE_CONTENT, SOFT_DELETE, SOFT_DELETE_CONTENT,
                INDEX, INDEX_CONTENT);
    }

    /** The repository's own pack with one plain rule and no links. */
    private UUID rootPack() {
        rootPack = repositoryPackWith(repository, LOCAL_RULE, "---\npaths: \"src/**\"\n---\nLocal rule\n");
        return rootPack;
    }

    /** Releases the repository with the given packs, followed by its own pack when it has one. */
    private void composeRelease(UUID... packs) throws Exception {
        List<UUID> composed = new ArrayList<>(List.of(packs));
        if (rootPack != null) {
            composed.add(rootPack);
        }
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(composed.toArray(new UUID[0]))))
                .andExpect(status().isOk());
    }

    private static String linksOf(Repository scope) {
        return "/kaya/links?repositoryId=" + scope.getId();
    }

    // --- GET /links?repositoryId= ---

    @Test
    void getLinks_latestRelease_listsFoundAndBrokenLinksSorted() throws Exception {
        rootPack();
        composeRelease(orgPack());

        mockMvc.perform(in(tenant, get(linksOf(repository)), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].sourcePath").value(BACKEND_RULE))
                .andExpect(jsonPath("$.data[0].targetPath").value(SOFT_DELETE))
                .andExpect(jsonPath("$.data[0].line").value(1))
                .andExpect(jsonPath("$.data[0].status").value("FOUND"))
                .andExpect(jsonPath("$.data[1].targetPath").value(GONE))
                .andExpect(jsonPath("$.data[1].line").value(2))
                .andExpect(jsonPath("$.data[1].status").value("BROKEN"))
                .andExpect(jsonPath("$.data[2].targetPath").value(BACKEND_DIRECTORY))
                .andExpect(jsonPath("$.data[2].line").value(3))
                .andExpect(jsonPath("$.data[2].status").value("FOUND"))
                .andExpect(jsonPath("$.data[3].sourcePath").value(INDEX))
                .andExpect(jsonPath("$.data[3].targetPath").value(SOFT_DELETE))
                .andExpect(jsonPath("$.data[3].status").value("FOUND"))
                .andExpect(jsonPath("$.data[4].sourcePath").value(SOFT_DELETE))
                .andExpect(jsonPath("$.data[4].targetPath").value(BACKEND_RULE))
                .andExpect(jsonPath("$.data[4].status").value("FOUND"));
    }

    @Test
    void getLinks_aDocLinkedFromTwoFiles_showsBothAsBacklinks() throws Exception {
        rootPack();
        composeRelease(orgPack());

        mockMvc.perform(in(tenant, get(linksOf(repository)), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.targetPath=='" + SOFT_DELETE + "')].sourcePath")
                        .value(containsInAnyOrder(BACKEND_RULE, INDEX)));
    }

    @Test
    void getLinks_newerReleaseWithoutThePack_listsNothing() throws Exception {
        rootPack();
        composeRelease(orgPack());
        composeRelease();

        mockMvc.perform(in(tenant, get(linksOf(repository)), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getLinks_repositoryWithoutRelease_returnsEmptyList() throws Exception {
        mockMvc.perform(in(tenant, get(linksOf(repository)), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- GET /links?repositoryId=&versionId= ---

    @Test
    void getLinks_draftOfTheRootPack_reflectsTheSavedDraftFiles() throws Exception {
        UUID root = rootPack();
        composeRelease(orgPack());
        UUID draft = insertDraft(root, 2, proposer);
        insertFiles(tenant.organization(), draft, LOCAL_RULE,
                "[soft](/" + SOFT_DELETE + ") and [new](/docs/private/conventions/new.md)\n");

        mockMvc.perform(in(tenant, get(linksOf(repository) + "&versionId=" + draft), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[?(@.sourcePath=='" + LOCAL_RULE + "' && @.status=='FOUND')].targetPath")
                        .value(contains(SOFT_DELETE)))
                .andExpect(jsonPath("$.data[?(@.sourcePath=='" + LOCAL_RULE + "' && @.status=='BROKEN')].targetPath")
                        .value(contains("docs/private/conventions/new.md")));
        mockMvc.perform(in(tenant, get(linksOf(repository)), member, null))
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void getLinks_draftOfAComposedPack_replacesThePackFiles() throws Exception {
        rootPack();
        UUID pack = orgPack();
        composeRelease(pack);
        UUID draft = insertDraft(pack, 2, proposer);
        insertFiles(tenant.organization(), draft, BACKEND_RULE, "Nothing to link.\n", SOFT_DELETE, "Nothing either.\n");

        mockMvc.perform(in(tenant, get(linksOf(repository) + "&versionId=" + draft), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getLinks_repositoryOfAnotherOrganization_answers404() throws Exception {
        Organization other = createOrganization(OrganizationStatus.ACTIVE);
        Repository foreign = saveRepository(other);

        mockMvc.perform(in(tenant, get(linksOf(foreign)), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        mockMvc.perform(in(tenant, get(linksOf(foreign) + "&versionId=" + UUID.randomUUID()), member, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLinks_unknownVersionOrVersionOfAnotherOrganization_answers404() throws Exception {
        Organization other = createOrganization(OrganizationStatus.ACTIVE);
        UUID foreignDraft = insertDraft(insertPack(other, "foreign"), 1, proposer);

        mockMvc.perform(in(tenant, get(linksOf(repository) + "&versionId=" + UUID.randomUUID()), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        mockMvc.perform(in(tenant, get(linksOf(repository) + "&versionId=" + foreignDraft), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    @Test
    void getLinks_neitherIdGiven_answers422() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/links"), member, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"))
                .andExpect(jsonPath("$.error.errorValues.versionId").value("Give a versionId, a repositoryId, or both"));
    }

    @Test
    void getLinks_approvedVersionInPlace_answers422OnVersionId() throws Exception {
        UUID root = rootPack();
        UUID approved = currentVersionOf(root);

        mockMvc.perform(in(tenant, get(linksOf(repository) + "&versionId=" + approved), member, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"))
                .andExpect(jsonPath("$.error.errorValues.versionId")
                        .value("The version is APPROVED; only a DRAFT or IN_REVIEW version is composed in place"));
    }

    @Test
    void getLinks_draftOfAPackTheRepositoryDoesNotCompose_answers422OnVersionId() throws Exception {
        rootPack();
        composeRelease(orgPack());
        UUID draft = insertDraft(insertPack(tenant.organization(), "stranger"), 1, proposer);

        mockMvc.perform(in(tenant, get(linksOf(repository) + "&versionId=" + draft), member, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"))
                .andExpect(jsonPath("$.error.errorValues.versionId")
                        .value("The version is of a pack this repository does not compose"));
    }

    @Test
    void getLinks_withoutJwt_answers401() throws Exception {
        mockMvc.perform(get(linksOf(repository)).header("X-Organization-ID", tenant.id()))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /links?versionId= ---

    @Test
    void getLinks_versionOfAPack_listsFoundAndBrokenLinks() throws Exception {
        UUID version = currentVersionOf(orgPack());

        mockMvc.perform(in(tenant, get("/kaya/links?versionId=" + version), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].sourcePath").value(BACKEND_RULE))
                .andExpect(jsonPath("$.data[0].status").value("FOUND"))
                .andExpect(jsonPath("$.data[1].targetPath").value(GONE))
                .andExpect(jsonPath("$.data[1].status").value("BROKEN"))
                .andExpect(jsonPath("$.data[2].targetPath").value(BACKEND_DIRECTORY))
                .andExpect(jsonPath("$.data[2].status").value("FOUND"));
    }

    @Test
    void getLinks_targetHeldOnlyByAnotherPack_isBroken() throws Exception {
        orgPack();
        UUID other = packWith("other", ".agents/rules/other.md", "[soft](/" + SOFT_DELETE + ")\n");

        mockMvc.perform(in(tenant, get("/kaya/links?versionId=" + currentVersionOf(other)), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].targetPath").value(SOFT_DELETE))
                .andExpect(jsonPath("$.data[0].status").value("BROKEN"));
    }

    @Test
    void getLinks_versionOfAnotherOrganization_answers404() throws Exception {
        Organization other = createOrganization(OrganizationStatus.ACTIVE);
        UUID foreign = approvedVersion(insertPack(other, "foreign"), 1);

        mockMvc.perform(in(tenant, get("/kaya/links?versionId=" + foreign), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    // --- GET /conventions ---

    @Test
    void getConventions_versionWithDocs_listsTheirPathsWithTheirSizes() throws Exception {
        UUID version = currentVersionOf(orgPack());

        mockMvc.perform(in(tenant, get("/kaya/conventions?versionId=" + version), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].path").value(containsInAnyOrder("_index.md", "backend/32-soft-delete.md")))
                .andExpect(jsonPath("$.data[?(@.path=='_index.md')].sizeBytes").value(contains(INDEX_CONTENT.length())))
                .andExpect(jsonPath("$.data[?(@.path=='backend/32-soft-delete.md')].sizeBytes")
                        .value(contains(SOFT_DELETE_CONTENT.length())));
    }
}
