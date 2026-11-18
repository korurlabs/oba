package com.korur.oba.apps.kaya.render.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.mock_data.RenderTrees;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;
import com.korur.oba.apps.kaya.support.KindIndexSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A draft saved file by file indexes exactly what a whole tree parsed at once indexes for the same
 * files, and a tree rendered from the stored bytes parses back into the same index. The index
 * columns, the rule-to-convention links and the skill-to-part links are compared on PostgreSQL.
 */
class RenderIndexParityIT extends AbstractVersionIT {

    private UUID pack;
    private UUID whole;
    private UUID byFile;

    @BeforeEach
    void setUp() {
        initTenant();
        pack = insertPack(tenant.organization(), "parity");
        whole = approvedVersion(pack, 1);
        byFile = insertDraft(pack, 2, proposer);
    }

    private UUID organizationId() {
        return tenant.organization().getId();
    }

    private void saveOneByOne(UUID versionId, List<SourceFile> tree) {
        for (SourceFile file : tree) {
            renderService.parseOne(organizationId(), versionId, file);
        }
    }

    private static boolean sameBytes(SourceFile file, SourceFile other) {
        return other != null && file.mode() == other.mode() && Arrays.equals(file.content(), other.content())
                && Objects.equals(file.symlinkTarget(), other.symlinkTarget());
    }

    private Map<String, String> contents(UUID versionId) {
        Map<String, String> contents = new HashMap<>();
        for (RenderedFile file : filesOf(versionId)) {
            contents.put(file.path(), file.mode() + ":" + file.symlinkTarget() + ":" + file.sha256());
        }
        return contents;
    }

    @Test
    void parseOne_everyFileOfATree_indexesWhatAWholeParseIndexes() {
        List<SourceFile> tree = RenderTrees.base();

        renderService.parse(organizationId(), whole, tree);
        saveOneByOne(byFile, tree);

        List<String> expected = KindIndexSnapshot.content(jdbcTemplate, whole);
        assertFalse(expected.isEmpty());
        assertEquals(expected, KindIndexSnapshot.content(jdbcTemplate, byFile));
        assertEquals(contents(whole), contents(byFile));
    }

    @Test
    void parseOne_changedAddedAndRemovedFiles_indexesWhatAWholeParseOfTheNewTreeIndexes() {
        List<SourceFile> base = RenderTrees.base();
        List<SourceFile> edited = RenderTrees.edited();
        Map<String, SourceFile> before = base.stream().collect(Collectors.toMap(SourceFile::path, file -> file));
        Map<String, SourceFile> after = edited.stream().collect(Collectors.toMap(SourceFile::path, file -> file));
        renderService.parse(organizationId(), whole, edited);
        saveOneByOne(byFile, base);

        for (SourceFile file : edited) {
            if (!sameBytes(file, before.get(file.path()))) {
                renderService.parseOne(organizationId(), byFile, file);
            }
        }
        for (SourceFile file : base) {
            if (!after.containsKey(file.path())) {
                renderService.parseOne(organizationId(), byFile, SourceFile.removal(file.path()));
            }
        }

        assertEquals(KindIndexSnapshot.content(jdbcTemplate, whole), KindIndexSnapshot.content(jdbcTemplate, byFile));
        assertEquals(contents(whole), contents(byFile));
    }

    @Test
    void parse_treeRenderedFromTheStoredBytes_indexesTheSameColumns() {
        renderService.parse(organizationId(), whole, RenderTrees.base());
        List<SourceFile> rendered = filesOf(whole).stream().map(RenderedFile::toSource).toList();

        renderService.parse(organizationId(), byFile, rendered);

        assertEquals(KindIndexSnapshot.content(jdbcTemplate, whole), KindIndexSnapshot.content(jdbcTemplate, byFile));
        assertEquals(contents(whole), contents(byFile));
    }
}
