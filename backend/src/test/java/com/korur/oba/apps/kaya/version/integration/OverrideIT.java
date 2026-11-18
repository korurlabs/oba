package com.korur.oba.apps.kaya.version.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Overrides of a child pack over HTTP: a draft save of an inherited item records the override,
 * keeping it moves its base, an update of the parent shows on the effective view, deleting the
 * item or clearing the record inherits the parent's item again, and the records travel with a
 * new draft and a restore.
 */
class OverrideIT extends AbstractVersionIT {

    private static final String CORE = ".agents/rules/core.md";

    private UUID org;
    private UUID kagi;

    @BeforeEach
    void setUp() {
        initTenant();
        org = packWith("org", CORE, "# Core\n", ".agents/rules/org.md", "# Org\n");
        kagi = insertChildPack(tenant.organization(), "kagi", org);
        insertFiles(tenant.organization(), approvedVersion(kagi, 1), ".agents/rules/kagi.md", "# Kagi\n");
    }

    private static String fileBody(String path, String content) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("path", path, "content", content));
    }

    private static String overrideBody(String path, UUID baseVersionId) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("path", path, "baseVersionId", baseVersionId.toString()));
    }

    private void save(UUID draft, String path, String content) throws Exception {
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/files"), owner, fileBody(path, content)))
                .andExpect(status().isOk());
    }

    private long overrides(UUID versionId) {
        return count("SELECT count(*) FROM kaya.item_overrides WHERE version_id = ?", versionId);
    }

    private UUID baseOf(UUID versionId, String path) {
        return jdbcTemplate.queryForObject("SELECT base_version_id FROM kaya.item_overrides WHERE version_id = ? "
                + "AND path = ?", UUID.class, versionId, path);
    }

    private void insertOverride(UUID versionId, String path, UUID basePackId, UUID baseVersionId) {
        jdbcTemplate.update("INSERT INTO kaya.item_overrides (organization_id, version_id, path, base_pack_id, "
                + "base_version_id) VALUES (?, ?, ?, ?, ?)", tenant.organization().getId(), versionId, path, basePackId,
                baseVersionId);
    }

    /** A second approved version of the org pack, with the core rule changed or left out. */
    private UUID updateOrg(String coreContent) {
        UUID version = approvedVersion(org, 2);
        if (coreContent == null) {
            insertFiles(tenant.organization(), version, ".agents/rules/org.md", "# Org\n");
        } else {
            insertFiles(tenant.organization(), version, CORE, coreContent, ".agents/rules/org.md", "# Org\n");
        }
        return version;
    }

    private String effectivePath(UUID versionId) {
        return "/kaya/packs/" + kagi + "/effective?versionId=" + versionId;
    }

    @Test
    void saveFile_inheritedItem_recordsTheOverrideOnTheParentsCurrentVersion() throws Exception {
        UUID draft = startDraft(kagi, owner);

        save(draft, CORE, "# Kagi core\n");
        save(draft, ".agents/rules/own.md", "# Own\n");

        assertEquals(1, overrides(draft));
        assertEquals(currentVersionOf(org), baseOf(draft, CORE));
        mockMvc.perform(in(tenant, get(effectivePath(draft)), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value(CORE))
                .andExpect(jsonPath("$.data[0].own").value(true))
                .andExpect(jsonPath("$.data[0].override.status").value("CURRENT"))
                .andExpect(jsonPath("$.data[0].override.basePackId").value(org.toString()))
                .andExpect(jsonPath("$.data[0].override.baseItemVersion").value(1))
                .andExpect(jsonPath("$.data[1].path").value(".agents/rules/kagi.md"))
                .andExpect(jsonPath("$.data[1].override").doesNotExist())
                .andExpect(jsonPath("$.data[3]").doesNotExist());
    }

    @Test
    void parentUpdated_keepWithTheNewBase_isCurrentAndAStaleBaseAnswers409() throws Exception {
        UUID draft = startDraft(kagi, owner);
        save(draft, CORE, "# Kagi core\n");
        UUID first = currentVersionOf(org);
        UUID second = updateOrg("# Core changed\n");

        mockMvc.perform(in(tenant, get(effectivePath(draft)), member, null))
                .andExpect(jsonPath("$.data[0].override.status").value("PARENT_UPDATED"))
                .andExpect(jsonPath("$.data[0].override.baseItemVersion").value(1))
                .andExpect(jsonPath("$.data[0].override.parentItemVersion").value(2))
                .andExpect(jsonPath("$.data[0].override.parentVersionId").value(second.toString()));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/overrides"), owner, overrideBody(CORE, first)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_032"))
                .andExpect(jsonPath("$.error.errorValues.parentVersionId").value(second.toString()));
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/overrides"), owner, overrideBody(CORE, second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseVersionId").value(second.toString()));

        assertEquals(second, baseOf(draft, CORE));
        mockMvc.perform(in(tenant, get(effectivePath(draft)), member, null))
                .andExpect(jsonPath("$.data[0].override.status").value("CURRENT"));
    }

    @Test
    void parentUpdatedWithAnUnchangedItem_staysCurrent() throws Exception {
        UUID draft = startDraft(kagi, owner);
        save(draft, CORE, "# Kagi core\n");
        updateOrg("# Core\n");

        mockMvc.perform(in(tenant, get(effectivePath(draft)), member, null))
                .andExpect(jsonPath("$.data[0].override.status").value("CURRENT"))
                .andExpect(jsonPath("$.data[0].override.parentItemVersion").value(1));
    }

    @Test
    void drop_deletingTheDraftsItem_removesTheRecordAndInheritsAgain() throws Exception {
        UUID draft = startDraft(kagi, owner);
        save(draft, CORE, "# Kagi core\n");

        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/files").param("path", CORE), owner, null))
                .andExpect(status().isOk());

        assertEquals(0, overrides(draft));
        mockMvc.perform(in(tenant, get(effectivePath(draft) + "&inherited=true"), member, null))
                .andExpect(jsonPath("$.data[0].path").value(CORE))
                .andExpect(jsonPath("$.data[0].own").value(false))
                .andExpect(jsonPath("$.data[0].sourcePackId").value(org.toString()));
    }

    @Test
    void clearOverride_parentRemovedTheItem_dropsTheRecordAndKeepsTheItemAsOwn() throws Exception {
        UUID draft = startDraft(kagi, owner);
        save(draft, CORE, "# Kagi core\n");

        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/overrides").param("path", CORE), owner, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        updateOrg(null);
        mockMvc.perform(in(tenant, get(effectivePath(draft)), member, null))
                .andExpect(jsonPath("$.data[0].override.status").value("PARENT_REMOVED"));
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/overrides").param("path", CORE), owner, null))
                .andExpect(status().isOk());

        assertEquals(0, overrides(draft));
        mockMvc.perform(in(tenant, get(effectivePath(draft)), member, null))
                .andExpect(jsonPath("$.data[0].path").value(CORE))
                .andExpect(jsonPath("$.data[0].override").doesNotExist());
        mockMvc.perform(in(tenant, delete("/kaya/drafts/" + draft + "/overrides").param("path", CORE), owner, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void setOverrideBase_itemTheDraftDoesNotHold_answers404() throws Exception {
        UUID draft = startDraft(kagi, owner);

        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/overrides"), owner, overrideBody(CORE, currentVersionOf(org))))
                .andExpect(status().isNotFound());
    }

    @Test
    void setOverrideBase_itemOfARequiredAncestor_answers422KayCfg031() throws Exception {
        jdbcTemplate.update("UPDATE kaya.packs SET required = true WHERE id = ?", org);
        UUID draft = startDraft(kagi, owner);
        // Stored past the save check, as a draft written before the parent became required holds it.
        insertFile(tenant.organization(), draft, CORE, "# Kagi core\n");

        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/overrides"), owner, overrideBody(CORE, currentVersionOf(org))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("KAY_CFG_031"));
    }

    @Test
    void createDraft_copiesTheCurrentVersionsRecords() throws Exception {
        UUID current = currentVersionOf(kagi);
        insertFiles(tenant.organization(), current, CORE, "# Kagi core\n");
        insertOverride(current, CORE, org, currentVersionOf(org));

        UUID draft = startDraft(kagi, owner);

        assertEquals(1, overrides(draft));
        assertEquals(currentVersionOf(org), baseOf(draft, CORE));
    }

    @Test
    void restore_copiesTheRestoredVersionsRecords() throws Exception {
        UUID first = currentVersionOf(kagi);
        insertFiles(tenant.organization(), first, CORE, "# Kagi core\n");
        insertOverride(first, CORE, org, currentVersionOf(org));
        insertFiles(tenant.organization(), approvedVersion(kagi, 2), ".agents/rules/kagi.md", "# Kagi 2\n");

        String draft = read(mockMvc.perform(in(tenant, post("/kaya/versions/" + first + "/restore"), owner, null))
                .andExpect(status().isCreated())
                .andReturn(), "$.data.id");

        assertEquals(1, overrides(UUID.fromString(draft)));
        assertEquals(currentVersionOf(org), baseOf(UUID.fromString(draft), CORE));
    }

    @Test
    void submit_draftThatOnlyMovesAnOverrideBase_isNotUnchanged() throws Exception {
        UUID current = currentVersionOf(kagi);
        insertFiles(tenant.organization(), current, CORE, "# Kagi core\n");
        insertOverride(current, CORE, org, currentVersionOf(org));
        UUID second = updateOrg("# Core changed\n");
        UUID draft = startDraft(kagi, owner);
        mockMvc.perform(in(tenant, put("/kaya/drafts/" + draft + "/overrides"), owner, overrideBody(CORE, second)))
                .andExpect(status().isOk());

        mockMvc.perform(in(tenant, post("/kaya/changes/" + draft + "/submit"), owner, "{\"reason\":\"Keep the kagi core\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void effective_inheritedRequested_listsTheAncestorsItemsWithTheirSource() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/packs/" + kagi + "/effective"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value(".agents/rules/kagi.md"))
                .andExpect(jsonPath("$.data[1]").doesNotExist());
        mockMvc.perform(in(tenant, get("/kaya/packs/" + kagi + "/effective").param("inherited", "true"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].path").value(CORE))
                .andExpect(jsonPath("$.data[0].own").value(false))
                .andExpect(jsonPath("$.data[0].kind").value("RULE"))
                .andExpect(jsonPath("$.data[0].sourcePackId").value(org.toString()))
                .andExpect(jsonPath("$.data[0].sourceVersionId").value(currentVersionOf(org).toString()))
                .andExpect(jsonPath("$.data[1].path").value(".agents/rules/kagi.md"))
                .andExpect(jsonPath("$.data[1].own").value(true))
                .andExpect(jsonPath("$.data[2].path").value(".agents/rules/org.md"));
    }

    @Test
    void effective_anotherOrganizationsPackOrAVersionOfAnotherPack_answers404() throws Exception {
        Tenant other = newTenant();

        mockMvc.perform(in(other, get("/kaya/packs/" + kagi + "/effective"), jwtOf(other.owner().getUser()), null))
                .andExpect(status().isNotFound());
        mockMvc.perform(in(tenant, get(effectivePath(currentVersionOf(org))), member, null))
                .andExpect(status().isNotFound());
    }
}
