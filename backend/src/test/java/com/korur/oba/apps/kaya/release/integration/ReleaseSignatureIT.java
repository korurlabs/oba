package com.korur.oba.apps.kaya.release.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.render.dto.embedded.SourceFile;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.signing.service.SigningService;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A built release is checked the way the CLI checks it: the signed document is rebuilt from the
 * API's release detail and the repository's remote URL, and the signature is verified with the raw
 * public key of GET /signing-keys, using the JDK alone.
 */
class ReleaseSignatureIT extends AbstractVersionIT {

    @Autowired
    private ReleaseQueryService releaseQueryService;
    @Autowired
    private SigningService signingService;

    private Repository repository;
    private UUID pack;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        pack = insertPack(tenant.organization(), "org");
        renderService.parse(tenant.organization().getId(), approvedVersion(pack, 1), List.of(
                SourceFile.of("AGENTS.md", FileMode.FILE, bytes("# Agents\n")),
                SourceFile.of(".agents/rules/style.md", FileMode.FILE, bytes("---\npaths: \"src/**\"\n---\nStyle\n")),
                SourceFile.of(".agents/hooks/dispatch.sh", FileMode.EXECUTABLE, bytes("#!/bin/sh\n")),
                SourceFile.of(".claude/settings.json", FileMode.FILE, bytes(SETTINGS)),
                SourceFile.of(".agents/skills/kazan/SKILL.md", FileMode.FILE,
                        bytes("---\nname: kazan\ndescription: Tickets\n---\n# Kazan\n")),
                new SourceFile(".claude/skills/kazan", FileMode.SYMLINK, "../../.agents/skills/kazan", null, null)));
        repository = saveRepository(tenant.organization());
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private MvcResult releaseDetail(int number) throws Exception {
        return mockMvc.perform(in(tenant, get("/kaya/repositories/" + repository.getId() + "/releases/" + number), member, null))
                .andExpect(status().isOk()).andReturn();
    }

    private String publishedKey(String keyId) throws Exception {
        MvcResult keys = mockMvc.perform(in(tenant, get("/kaya/signing-keys"), member, null)).andExpect(status().isOk()).andReturn();
        return list(keys, "$.data[?(@.keyId == '" + keyId + "')].publicKey").get(0);
    }

    @Test
    void release_rebuiltFromTheApiVerifiesWithThePublishedKey() throws Exception {
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack))).andExpect(status().isOk());
        MvcResult detail = releaseDetail(1);
        String signature = read(detail, "$.data.signature");
        String keyId = read(detail, "$.data.keyId");

        String canonical = canonicalFromApi(tenant.id(), repository.getRemoteUrl(), detail);

        assertTrue(verifyWithRawKey(publishedKey(keyId), canonical, signature), canonical);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))),
                read(detail, "$.data.manifestSha256"));
        Release stored = releaseQueryService.getEntityByNumber(tenant.organization().getId(), repository.getId(), 1);
        assertEquals(canonical, releaseQueryService.toCanonicalJson(tenant.organization().getId(), stored));
    }

    @Test
    void release_anyChangeToTheDocument_failsVerification() throws Exception {
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack))).andExpect(status().isOk());
        MvcResult detail = releaseDetail(1);
        String signature = read(detail, "$.data.signature");
        String key = publishedKey(read(detail, "$.data.keyId"));
        String canonical = canonicalFromApi(tenant.id(), repository.getRemoteUrl(), detail);

        assertFalse(verifyWithRawKey(key, canonical.replace("\"number\":1", "\"number\":2"), signature), "replayed as another number");
        assertFalse(verifyWithRawKey(key, canonical.replace(repository.getRemoteUrl(), "github.com/other/repo"), signature),
                "replayed on another remote");
        assertFalse(verifyWithRawKey(key, canonical.replace(tenant.id(), UUID.randomUUID().toString()), signature),
                "replayed in another organization");
        assertFalse(verifyWithRawKey(key, canonical.replace("\"alwaysOn\":true", "\"alwaysOn\":false"), signature), "always-on flag");
        assertFalse(verifyWithRawKey(key, canonical.replace("\"mode\":\"EXECUTABLE\"", "\"mode\":\"FILE\""), signature), "mode");
        assertFalse(verifyWithRawKey(key, canonical + " ", signature), "trailing byte");
        assertFalse(verifyWithRawKey(key, canonical, "A".repeat(86) + "=="), "forged signature");
        assertFalse(signingService.verify(canonical, signature, "no-such-key"), "unknown key id");
    }

    @Test
    void release_ofAnotherNumber_doesNotVerifyWithTheSignatureOfThisOne() throws Exception {
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack))).andExpect(status().isOk());
        UUID extra = packWith("extra", ".agents/rules/extra.md", "---\npaths: \"src/**\"\n---\nExtra\n");
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack, extra))).andExpect(status().isOk());
        MvcResult first = releaseDetail(1);
        MvcResult second = releaseDetail(2);
        String key = publishedKey(read(first, "$.data.keyId"));

        String canonicalOne = canonicalFromApi(tenant.id(), repository.getRemoteUrl(), first);
        String canonicalTwo = canonicalFromApi(tenant.id(), repository.getRemoteUrl(), second);

        assertTrue(verifyWithRawKey(key, canonicalOne, read(first, "$.data.signature")));
        assertTrue(verifyWithRawKey(key, canonicalTwo, read(second, "$.data.signature")));
        assertFalse(verifyWithRawKey(key, canonicalOne, read(second, "$.data.signature")));
        assertFalse(verifyWithRawKey(key, canonicalTwo, read(first, "$.data.signature")));
    }

    @Test
    void release_afterTheRemoteChanges_nolongerVerifiesAgainstTheNewRemote() throws Exception {
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack))).andExpect(status().isOk());
        MvcResult detail = releaseDetail(1);
        String key = publishedKey(read(detail, "$.data.keyId"));
        String signature = read(detail, "$.data.signature");
        String oldRemote = repository.getRemoteUrl();

        jdbcTemplate.update("UPDATE kaya.repositories SET remote_url = ? WHERE id = ?",
                "github.com/moved" + uniqueSuffix() + "/repo", repository.getId());
        String newRemote = jdbcTemplate.queryForObject("SELECT remote_url FROM kaya.repositories WHERE id = ?", String.class,
                repository.getId());

        assertTrue(verifyWithRawKey(key, canonicalFromApi(tenant.id(), oldRemote, detail), signature));
        assertFalse(verifyWithRawKey(key, canonicalFromApi(tenant.id(), newRemote, detail), signature));
        Release stored = releaseQueryService.getEntityByNumber(tenant.organization().getId(), repository.getId(), 1);
        assertFalse(signingService.verify(
                releaseQueryService.toCanonicalJson(tenant.organization().getId(), stored), signature, read(detail, "$.data.keyId")));
    }
}
