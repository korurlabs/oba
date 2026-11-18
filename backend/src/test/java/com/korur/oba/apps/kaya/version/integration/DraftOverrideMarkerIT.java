package com.korur.oba.apps.kaya.version.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A new draft is the render of its base parsed into a new version; a rule's override marker must
 * survive that copy, or every draft of a pack that overrides an inherited rule would silently drop
 * its overrides.
 */
class DraftOverrideMarkerIT extends AbstractVersionIT {

    private static final String MOUNTED_RULE = "---\npaths: [\"projects/kagi/**\"]\n---\nKagi rule\n";
    private static final String RULE_PATH = ".agents/rules/kagi/backend.md";

    private UUID packId;

    @BeforeEach
    void setUp() {
        initTenant();
        packId = insertPack(tenant.organization(), "kagi-" + uniqueSuffix().substring(0, 8));
        UUID baseVersionId = approvedVersion(packId, 1);
        renderService.parse(tenant.organization().getId(), baseVersionId, List.of(
                new SourceFile(RULE_PATH, FileMode.FILE, null, MOUNTED_RULE.getBytes(StandardCharsets.UTF_8),
                        ".agents/rules/backend.md"),
                SourceFile.of("AGENTS.md", FileMode.FILE, "# Kagi\n".getBytes(StandardCharsets.UTF_8))));
    }

    private String overridesPathOf(UUID versionId) {
        return jdbcTemplate.queryForObject("SELECT overrides_path FROM kaya.rules WHERE version_id = ? AND relative_path = ?",
                String.class, versionId, "kagi/backend.md");
    }

    @Test
    void createDraft_draftOfAVersionWithAMarker_keepsTheOverrideMarker() throws Exception {
        UUID draftId = startDraft(packId, owner);

        assertEquals(1, count("SELECT count(*) FROM kaya.rules WHERE version_id = ?", draftId));
        assertEquals(1, count("SELECT count(*) FROM kaya.agents_files WHERE version_id = ?", draftId));
        assertEquals(".agents/rules/backend.md", overridesPathOf(draftId));
    }

    @Test
    void createDraft_ownerWithoutOverride_storesNoMarker() throws Exception {
        UUID other = insertPack(tenant.organization(), "kaizen-" + uniqueSuffix().substring(0, 8));
        insertFile(tenant.organization(), approvedVersion(other, 1), RULE_PATH, MOUNTED_RULE);

        UUID draftId = startDraft(other, owner);

        assertNull(overridesPathOf(draftId));
    }
}
