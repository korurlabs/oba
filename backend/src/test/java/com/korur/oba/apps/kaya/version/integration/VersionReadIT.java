package com.korur.oba.apps.kaya.version.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The version reads over HTTP: the paged list newest first, filtered by owner and status; one version
 * with its rendered files by path and without content; one file as text, Base64 or a skill link; and
 * a 404 for a version of another organization.
 */
class VersionReadIT extends AbstractVersionIT {

    private static final String SKILL = "---\nname: tool\ndescription: Use for the tool\n---\n# Tool\n";
    private static final String SKILL_LINK = ".claude/skills/tool";

    @BeforeEach
    void setUp() {
        initTenant();
    }

    private UUID insertVersion(UUID packId, int number, String status, UUID baseVersionId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO kaya.versions (organization_id, pack_id, number, status, base_version_id, proposed_by, source, reason)
                VALUES (?, ?, ?, ?::version_version_status_enum, ?, ?, 'SEED'::version_version_source_enum, ?)
                RETURNING id
                """, UUID.class, tenant.organization().getId(), packId, number, status, baseVersionId, proposer,
                "Seed of version " + number);
    }

    /** Stores a skill and its .claude/skills link into the version in one parse, which replaces the version's files. */
    private void insertSkillWithLink(UUID versionId) {
        renderService.parse(tenant.organization().getId(), versionId, List.of(
                SourceFile.of(".agents/skills/tool/SKILL.md", FileMode.FILE, SKILL.getBytes(StandardCharsets.UTF_8)),
                new SourceFile(SKILL_LINK, FileMode.SYMLINK, "../../.agents/skills/tool", null, null)));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    // --- GET /versions ---

    @Test
    void getVersions_pagedNewestFirstAndFilteredByOwnerAndStatus() throws Exception {
        UUID pack = insertPack(tenant.organization(), "org");
        UUID first = insertVersion(pack, 1, "SUPERSEDED", null);
        UUID second = insertVersion(pack, 2, "APPROVED", first);
        insertVersion(pack, 3, "DRAFT", second);
        approvedVersion(insertPack(tenant.organization(), "other"), 1);

        mockMvc.perform(in(tenant, get("/kaya/versions").param("packId", pack.toString()).param("amount", "2"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].number").value(3))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[0].baseVersionId").value(second.toString()))
                .andExpect(jsonPath("$.data[1].number").value(2))
                .andExpect(jsonPath("$.data[1].packId").value(pack.toString()))
                .andExpect(jsonPath("$.data[1].projectId").doesNotExist())
                .andExpect(jsonPath("$.data[1].proposedBy").value(proposer.toString()))
                .andExpect(jsonPath("$.data[1].source").value("SEED"))
                .andExpect(jsonPath("$.pagination.totalElements").value(3));

        mockMvc.perform(in(tenant, get("/kaya/versions").param("packId", pack.toString()).param("status", "SUPERSEDED"),
                        member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(first.toString()))
                .andExpect(jsonPath("$.data[0].baseVersionId").doesNotExist());
    }

    @Test
    void getVersions_packOfAnotherOrganization_listsNothing() throws Exception {
        Tenant other = newTenant();
        UUID foreign = insertPack(other.organization(), "foreign");
        approvedVersion(foreign, 1);

        mockMvc.perform(in(tenant, get("/kaya/versions").param("packId", foreign.toString()), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- GET /versions/{id} ---

    @Test
    void getVersion_returnsTheOwnerAndFilesByPathWithoutContent() throws Exception {
        UUID pack = insertPack(tenant.organization(), "org");
        UUID version = approvedVersion(pack, 1);
        byte[] rule = "# Core rules\n".getBytes(StandardCharsets.UTF_8);
        insertSkillWithLink(version);
        insertFile(tenant.organization(), version, ".agents/rules/core.md", "# Core rules\n");
        insertFile(tenant.organization(), version, ".agents/hooks/dispatch.sh", FileMode.EXECUTABLE, "#!/bin/sh\n");

        mockMvc.perform(in(tenant, get("/kaya/versions/" + version), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(version.toString()))
                .andExpect(jsonPath("$.data.packId").value(pack.toString()))
                .andExpect(jsonPath("$.data.files.length()").value(4))
                .andExpect(jsonPath("$.data.files[0].path").value(".agents/hooks/dispatch.sh"))
                .andExpect(jsonPath("$.data.files[0].mode").value("EXECUTABLE"))
                .andExpect(jsonPath("$.data.files[1].path").value(".agents/rules/core.md"))
                .andExpect(jsonPath("$.data.files[1].size").value(rule.length))
                .andExpect(jsonPath("$.data.files[1].sha256").value(sha256(rule)))
                .andExpect(jsonPath("$.data.files[1].content").doesNotExist())
                .andExpect(jsonPath("$.data.files[2].path").value(".agents/skills/tool/SKILL.md"))
                .andExpect(jsonPath("$.data.files[3].path").value(SKILL_LINK))
                .andExpect(jsonPath("$.data.files[3].sha256").doesNotExist())
                .andExpect(jsonPath("$.data.files[3].symlinkTarget").value("../../.agents/skills/tool"));
    }

    @Test
    void getVersion_ofAnotherOrganization_answers404() throws Exception {
        Tenant other = newTenant();
        UUID foreign = approvedVersion(insertPack(other.organization(), "foreign"), 1);

        mockMvc.perform(in(tenant, get("/kaya/versions/" + foreign), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        mockMvc.perform(in(tenant, get("/kaya/versions/" + foreign + "/file?path=AGENTS.md"), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }

    // --- GET /versions/{id}/file ---

    @Test
    void getFile_textBinaryAndSkillLink() throws Exception {
        UUID version = approvedVersion(insertPack(tenant.organization(), "org"), 1);
        String text = "# Kern\nçözüm\n";
        insertSkillWithLink(version);
        insertFile(tenant.organization(), version, ".agents/rules/core.md", text);
        byte[] binary = {(byte) 0xff, (byte) 0xfe, 0x00, 0x41};
        insertBytes(tenant.organization(), version, ".agents/hooks/icon.bin", binary);

        mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/file").param("path", ".agents/rules/core.md"),
                        member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(version.toString()))
                .andExpect(jsonPath("$.data.mode").value("FILE"))
                .andExpect(jsonPath("$.data.encoding").value("UTF_8"))
                .andExpect(jsonPath("$.data.content").value(text));

        mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/file").param("path", ".agents/hooks/icon.bin"),
                        member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.encoding").value("BASE64"))
                .andExpect(jsonPath("$.data.content").value(Base64.getEncoder().encodeToString(binary)))
                .andExpect(jsonPath("$.data.size").value(4));

        mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/file").param("path", SKILL_LINK), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("SYMLINK"))
                .andExpect(jsonPath("$.data.symlinkTarget").value("../../.agents/skills/tool"))
                .andExpect(jsonPath("$.data.encoding").doesNotExist())
                .andExpect(jsonPath("$.data.content").doesNotExist());
    }

    @Test
    void getFile_unknownPath_answers404AndMissingPath_answers400() throws Exception {
        UUID version = approvedVersion(insertPack(tenant.organization(), "org"), 1);

        mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/file").param("path", "AGENTS.md"), member, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/file"), member, null))
                .andExpect(status().isBadRequest());
        mockMvc.perform(in(tenant, get("/kaya/versions/" + version + "/file").param("path", " "), member, null))
                .andExpect(status().isUnprocessableEntity());
    }
}
