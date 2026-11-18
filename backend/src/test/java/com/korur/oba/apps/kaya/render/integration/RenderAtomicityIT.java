package com.korur.oba.apps.kaya.render.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.settings.service.SettingsService;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;
import com.korur.oba.apps.kaya.support.KindIndexSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * One render call writes the files of every kind in one transaction. When the last kind fails after
 * all the others were written, PostgreSQL rolls back the blobs, the stored files and every index row
 * of the version, so the version reads exactly as it did before the call. The settings service is
 * the spy every integration test shares, so failing it here starts no context of its own.
 */
class RenderAtomicityIT extends AbstractVersionIT {

    @Autowired
    private SettingsService settingsService;

    private UUID version;

    @BeforeEach
    void setUp() {
        initTenant();
        UUID pack = insertPack(tenant.organization(), "atomic");
        version = approvedVersion(pack, 1);
        renderService.parse(tenant.organization().getId(), version, RenderTrees.base());
    }

    private long blobCount() {
        return count("SELECT count(*) FROM kaya.blobs WHERE organization_id = ?", tenant.organization().getId());
    }

    private List<String> files() {
        return filesOf(version).stream().map(file -> file.path() + ":" + file.mode() + ":" + file.symlinkTarget() + ":" + file.sha256()).toList();
    }

    private void failOnTheLastKind() {
        doThrow(new IllegalStateException("settings write failed")).when(settingsService).replaceVersion(any(), any(), any());
    }

    @Test
    void parse_lastKindFails_rollsBackEveryKindAndLeavesFilesAndIndexUnchanged() {
        List<String> filesBefore = files();
        List<String> indexBefore = KindIndexSnapshot.exact(jdbcTemplate, version);
        long blobsBefore = blobCount();
        failOnTheLastKind();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> renderService.parse(
                tenant.organization().getId(), version, RenderTrees.edited()));

        assertEquals("settings write failed", failure.getMessage());
        assertFalse(indexBefore.isEmpty());
        assertEquals(filesBefore, files());
        assertEquals(indexBefore, KindIndexSnapshot.exact(jdbcTemplate, version));
        assertEquals(blobsBefore, blobCount());
    }

    @Test
    void parseOne_settingsWriteFails_keepsTheStoredSettingsAndIndex() {
        List<String> filesBefore = files();
        List<String> indexBefore = KindIndexSnapshot.exact(jdbcTemplate, version);
        long blobsBefore = blobCount();
        failOnTheLastKind();
        SourceFile changed = RenderTrees.edited().stream()
                .filter(file -> file.path().equals(RenderTrees.SETTINGS_PATH))
                .findFirst().orElseThrow();

        assertThrows(IllegalStateException.class,
                () -> renderService.parseOne(tenant.organization().getId(), version, changed));

        assertEquals(filesBefore, files());
        assertEquals(indexBefore, KindIndexSnapshot.exact(jdbcTemplate, version));
        assertEquals(blobsBefore, blobCount());
    }

    @Test
    void parse_afterARolledBackCall_theSameCallSucceedsOnceTheFailureIsGone() {
        failOnTheLastKind();
        assertThrows(IllegalStateException.class, () -> renderService.parse(
                tenant.organization().getId(), version, RenderTrees.edited()));
        reset(settingsService);

        renderService.parse(tenant.organization().getId(), version, RenderTrees.edited());

        assertEquals("# Plan\n", contentOf(version, ".agents/templates/PLAN.md"));
        assertNull(contentOf(version, ".agents/roles/tester.md"));
    }
}
