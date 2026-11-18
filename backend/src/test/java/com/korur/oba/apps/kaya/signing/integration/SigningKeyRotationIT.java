package com.korur.oba.apps.kaya.signing.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.service.ReleaseQueryService;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider;
import com.korur.oba.apps.kaya.signing.service.SigningService;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The backend runs with a configured signing key and the public key of the key it replaced. The
 * keys are the ones the shared integration context generates for the run, so no key material is
 * committed. A release signed by the
 * replaced key keeps verifying with the previous key the endpoint publishes, and no key verifies
 * the other's signature.
 */
class SigningKeyRotationIT extends AbstractVersionIT {

    private static final String CURRENT_ID = SIGNING_KEY_ID;
    private static final String PREVIOUS_ID = PREVIOUS_SIGNING_KEY_ID;
    private static final String PREVIOUS_VALID_TO = PREVIOUS_SIGNING_KEY_VALID_TO;
    private static final KeyPair CURRENT = SIGNING_KEY;
    private static final KeyPair PREVIOUS = PREVIOUS_SIGNING_KEY;

    @Autowired
    private ReleaseQueryService releaseQueryService;
    @Autowired
    private SigningService signingService;

    private Repository repository;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        UUID pack = packWith("org", "AGENTS.md", "# Agents\n");
        repository = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack))).andExpect(status().isOk());
    }

    private static String sign(KeyPair key, String canonical) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private MvcResult detail() throws Exception {
        return mockMvc.perform(in(tenant, get("/kaya/repositories/" + repository.getId() + "/releases/1"), member, null))
                .andExpect(status().isOk()).andReturn();
    }

    private String published(String keyId) throws Exception {
        MvcResult keys = mockMvc.perform(in(tenant, get("/kaya/signing-keys"), member, null)).andReturn();
        return list(keys, "$.data[?(@.keyId == '" + keyId + "')].publicKey").get(0);
    }

    @Test
    void signingKeys_listsTheCurrentKeyFirstAndThePreviousKeyWithItsEnd() throws Exception {
        mockMvc.perform(in(tenant, get("/kaya/signing-keys"), member, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].keyId").value(CURRENT_ID))
                .andExpect(jsonPath("$.data[0].publicKey").value(SigningKeyProvider.rawPublicKey(CURRENT.getPublic())))
                .andExpect(jsonPath("$.data[0].validTo").doesNotExist())
                .andExpect(jsonPath("$.data[1].keyId").value(PREVIOUS_ID))
                .andExpect(jsonPath("$.data[1].publicKey").value(SigningKeyProvider.rawPublicKey(PREVIOUS.getPublic())))
                .andExpect(jsonPath("$.data[1].validTo").value(PREVIOUS_VALID_TO));
    }

    @Test
    void newRelease_isSignedWithTheConfiguredKeyAndVerifiesWithItsPublishedKey() throws Exception {
        MvcResult detail = detail();
        String canonical = canonicalFromApi(tenant.id(), repository.getRemoteUrl(), detail);

        assertEquals(CURRENT_ID, read(detail, "$.data.keyId"));
        assertTrue(verifyWithRawKey(published(CURRENT_ID), canonical, read(detail, "$.data.signature")));
        assertFalse(verifyWithRawKey(published(PREVIOUS_ID), canonical, read(detail, "$.data.signature")),
                "the previous key did not sign it");
    }

    @Test
    void releaseSignedByThePreviousKey_stillVerifiesWithThePublishedPreviousKey() throws Exception {
        Release stored = releaseQueryService.getEntityByNumber(tenant.organization().getId(), repository.getId(), 1);
        String canonical = releaseQueryService.toCanonicalJson(tenant.organization().getId(), stored);
        String oldSignature = sign(PREVIOUS, canonical);
        jdbcTemplate.update("UPDATE kaya.releases SET signature = ?, key_id = ? WHERE id = ?",
                oldSignature, PREVIOUS_ID, stored.getId());

        MvcResult detail = detail();

        assertEquals(PREVIOUS_ID, read(detail, "$.data.keyId"));
        assertTrue(verifyWithRawKey(published(PREVIOUS_ID), canonicalFromApi(tenant.id(), repository.getRemoteUrl(), detail),
                read(detail, "$.data.signature")));
        assertTrue(signingService.verify(canonical, oldSignature, PREVIOUS_ID));
        assertFalse(signingService.verify(canonical, oldSignature, CURRENT_ID), "the current key did not sign it");
    }

    @Test
    void releaseNamingTheWrongKey_failsVerification() throws Exception {
        Release stored = releaseQueryService.getEntityByNumber(tenant.organization().getId(), repository.getId(), 1);
        String canonical = releaseQueryService.toCanonicalJson(tenant.organization().getId(), stored);

        assertTrue(signingService.verify(canonical, stored.getSignature(), CURRENT_ID));
        assertFalse(signingService.verify(canonical, stored.getSignature(), PREVIOUS_ID));
        assertFalse(signingService.verify(canonical, sign(PREVIOUS, canonical), CURRENT_ID));
        assertFalse(signingService.verify(canonical, sign(PREVIOUS, canonical), "retired-key"));
    }

    @Test
    void releaseMadeAfterRotation_getsTheNextNumberUnderTheCurrentKey() throws Exception {
        UUID second = packWith("second", ".agents/rules/second.md", "---\npaths: \"src/**\"\n---\nSecond\n");
        UUID first = jdbcTemplate.queryForObject("SELECT pack_id FROM kaya.repository_packs WHERE repository_id = ?", UUID.class,
                repository.getId());
        Release before = releaseQueryService.getEntityByNumber(tenant.organization().getId(), repository.getId(), 1);
        jdbcTemplate.update("UPDATE kaya.releases SET signature = ?, key_id = ? WHERE id = ?",
                sign(PREVIOUS, releaseQueryService.toCanonicalJson(tenant.organization().getId(), before)),
                PREVIOUS_ID, before.getId());

        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(first, second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.release.number").value(2))
                .andExpect(jsonPath("$.data.release.keyId").value(CURRENT_ID));

        mockMvc.perform(in(tenant, get("/kaya/repositories/" + repository.getId() + "/releases"), member, null))
                .andExpect(jsonPath("$.data[0].keyId").value(CURRENT_ID))
                .andExpect(jsonPath("$.data[1].keyId").value(PREVIOUS_ID));
    }
}
