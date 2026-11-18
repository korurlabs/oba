package com.korur.oba.apps.kaya.signing;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.signing.dto.embedded.Signature;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifest;
import com.korur.oba.apps.kaya.signing.dto.embedded.SignedManifestFile;
import com.korur.oba.apps.kaya.signing.helper.SignedManifestWriter;
import com.korur.oba.apps.kaya.signing.helper.SigningKeyProvider;
import com.korur.oba.apps.kaya.signing.service.SigningService;
import com.korur.oba.apps.kaya.signing.service.impl.SigningServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SigningUnitTest {

    private final LoggingService loggingService = mock(LoggingService.class);

    private static KeyPair newKeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static SigningProperties configured(KeyPair keyPair, String keyId, KeyPair previous) {
        String previousKey = previous == null ? null : SigningKeyProvider.rawPublicKey(previous.getPublic());
        return new SigningProperties(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()), keyId,
                null, previousKey, previous == null ? null : "kaya-old", previous == null ? null : "2026-12-01T00:00:00Z");
    }

    private static SignedManifest manifest() {
        return new SignedManifest("8b1f7c1e-3d0a-4c56-9a51-2f1b0e9c7d42", "github.com/senseylabs/village", 3,
                SignedManifestWriter.formatCreatedAt(Instant.parse("2026-10-23T09:15:02.123456Z")),
                List.of(new SignedManifestFile(".claude/skills/kaizen", null, "SYMLINK", "../../.agents/skills/kaizen", false, null),
                        new SignedManifestFile("AGENTS.md", "a".repeat(64), "FILE", null, true, null)));
    }

    @Test
    void canonicalJson_isCompactOrderedAndOmitsAbsentValues() {
        assertEquals("{\"organization\":\"8b1f7c1e-3d0a-4c56-9a51-2f1b0e9c7d42\",\"remoteUrl\":\"github.com/senseylabs/village\","
                        + "\"number\":3,\"createdAt\":\"2026-10-23T09:15:02.123Z\",\"files\":["
                        + "{\"path\":\".claude/skills/kaizen\",\"mode\":\"SYMLINK\",\"target\":\"../../.agents/skills/kaizen\",\"alwaysOn\":false},"
                        + "{\"path\":\"AGENTS.md\",\"sha256\":\"" + "a".repeat(64) + "\",\"mode\":\"FILE\",\"alwaysOn\":true}]}",
                SignedManifestWriter.toCanonicalJson(manifest()));
    }

    @Test
    void canonicalJson_guardrailWithDenyRules_writesDenyAfterAlwaysOn() {
        SignedManifest manifest = new SignedManifest("8b1f7c1e-3d0a-4c56-9a51-2f1b0e9c7d42", "github.com/senseylabs/village", 3,
                "2026-10-23T09:15:02.123Z", List.of(new SignedManifestFile(".agents/rules/guardrails/no-secrets.md", "b".repeat(64),
                "FILE", null, true, List.of("Read(./.env)", "Bash(cat .env:*)"))));

        assertEquals("{\"organization\":\"8b1f7c1e-3d0a-4c56-9a51-2f1b0e9c7d42\",\"remoteUrl\":\"github.com/senseylabs/village\","
                        + "\"number\":3,\"createdAt\":\"2026-10-23T09:15:02.123Z\",\"files\":["
                        + "{\"path\":\".agents/rules/guardrails/no-secrets.md\",\"sha256\":\"" + "b".repeat(64) + "\",\"mode\":\"FILE\","
                        + "\"alwaysOn\":true,\"deny\":[\"Read(./.env)\",\"Bash(cat .env:*)\"]}]}",
                SignedManifestWriter.toCanonicalJson(manifest));
    }

    @Test
    void configuredKey_signsWhatItsPublicKeyVerifies() throws Exception {
        KeyPair keyPair = newKeyPair();
        SigningKeyProvider provider = new SigningKeyProvider(configured(keyPair, "kaya-2026-10", null),
                new MockEnvironment(), loggingService);
        SigningService service = new SigningServiceImpl(provider, loggingService);

        Signature signature = service.sign(manifest());

        assertEquals("kaya-2026-10", signature.keyId());
        assertEquals(88, signature.signature().length());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(signature.canonicalJson().getBytes(StandardCharsets.UTF_8))), signature.manifestSha256());
        assertEquals(SigningKeyProvider.rawPublicKey(keyPair.getPublic()),
                SigningKeyProvider.rawPublicKey(provider.getTrustedKeys().getFirst().publicKey()));
        java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(signature.canonicalJson().getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(signature.signature())));
        assertTrue(service.verify(signature.canonicalJson(), signature.signature(), "kaya-2026-10"));
        assertFalse(service.verify(signature.canonicalJson() + " ", signature.signature(), "kaya-2026-10"));
        assertFalse(service.verify(signature.canonicalJson(), signature.signature(), "unknown"));
        assertFalse(service.verify(signature.canonicalJson(), "not-base64!", "kaya-2026-10"));
    }

    @Test
    void previousKey_staysTrustedDuringARotation() throws Exception {
        KeyPair previous = newKeyPair();
        SigningKeyProvider provider = new SigningKeyProvider(configured(newKeyPair(), "kaya-new", previous),
                new MockEnvironment(), loggingService);
        SigningService service = new SigningServiceImpl(provider, loggingService);
        String text = SignedManifestWriter.toCanonicalJson(manifest());
        java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
        signer.initSign(previous.getPrivate());
        signer.update(text.getBytes(StandardCharsets.UTF_8));

        assertEquals(2, provider.getTrustedKeys().size());
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), provider.getTrustedKeys().get(1).validTo());
        assertTrue(service.verify(text, Base64.getEncoder().encodeToString(signer.sign()), "kaya-old"));
    }

    @Test
    void noKey_underTest_usesAnEphemeralKey() {
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        SigningKeyProvider provider = new SigningKeyProvider(
                new SigningProperties(null, null, null, null, null, null), test, loggingService);
        assertTrue(provider.getKeyId().startsWith("test-"));
        SigningService service = new SigningServiceImpl(provider, loggingService);
        Signature signature = service.sign(manifest());
        assertTrue(service.verify(signature.canonicalJson(), signature.signature(), provider.getKeyId()));
    }

    @Test
    void noKey_underProd_stopsTheStartup() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new SigningKeyProvider(
                new SigningProperties("", "", null, null, null, null), prod, loggingService));
    }

    @Test
    void noKey_withoutProfile_stopsTheStartup() {
        assertThrows(IllegalStateException.class, () -> new SigningKeyProvider(
                new SigningProperties(null, null, null, null, null, null), new MockEnvironment(), loggingService));
    }

    @Test
    void invalidKeyOrKeyId_stopsTheStartup() throws Exception {
        KeyPair keyPair = newKeyPair();
        MockEnvironment environment = new MockEnvironment();
        assertThrows(IllegalStateException.class, () -> new SigningKeyProvider(
                new SigningProperties("bm90IGEga2V5", "kaya", null, null, null, null), environment, loggingService));
        assertThrows(IllegalStateException.class, () -> new SigningKeyProvider(
                configured(keyPair, "", null), environment, loggingService));
        assertThrows(IllegalStateException.class, () -> new SigningKeyProvider(
                configured(keyPair, "k".repeat(41), null), environment, loggingService));
    }
}
