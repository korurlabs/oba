package com.korur.oba.apps.kaya.release.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.validation.helper.CompositionCandidate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositionUnitTest {

    private static final UUID REPOSITORY = UUID.randomUUID();

    private final Map<String, byte[]> contents = new LinkedHashMap<>();
    private final List<ComposedFile> files = new ArrayList<>();

    private void file(String path, String content) {
        String sha256 = "sha-" + path;
        contents.put(sha256, content.getBytes(StandardCharsets.UTF_8));
        files.add(new ComposedFile(path, FileMode.FILE, sha256, content.length(), null, false, UUID.randomUUID(),
                UUID.randomUUID(), null, null, null));
    }

    private void symlink(String path, String target) {
        files.add(new ComposedFile(path, FileMode.SYMLINK, null, null, target, false, UUID.randomUUID(), UUID.randomUUID(),
                null, null, null));
    }

    private CompositionCandidate candidate() {
        return new Composition(REPOSITORY, files, List.of(), List.of(), null, List.of(), List.of(), List.of(), contents)
                .toCandidate();
    }

    @Test
    void toCandidate_files_mapsEveryComposedFileAndTheRepository() {
        // Arrange
        file(".agents/workflows/plan.md", "# Plan\n");
        file(".agents/rules/core.md", "# Core\n");
        symlink(".agents/workflows/ship.md", "../shared/ship.md");

        // Act
        CompositionCandidate candidate = candidate();

        // Assert
        assertEquals(3, candidate.files().size());
        assertEquals(REPOSITORY, candidate.repositoryId());
    }

    @Test
    void toCandidate_pipelineFile_isAnOrdinaryFileWithoutPipelineValidation() {
        // Arrange
        file(".agents/pipelines/feature.yaml", "name: Feature\nunknown: true\n");

        // Act
        CompositionCandidate candidate = candidate();

        // Assert
        assertEquals(1, candidate.files().size());
    }
}
