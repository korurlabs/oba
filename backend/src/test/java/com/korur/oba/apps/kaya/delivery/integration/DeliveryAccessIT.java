package com.korur.oba.apps.kaya.delivery.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The machine-token chain of the delivery endpoints and what a delivered release guarantees: every
 * endpoint refuses anything but a live machine token, a token never reads another organization, the
 * remote is matched however git prints it, a blob is served only while some release names it, and
 * the manifest is handed out byte for byte as it was signed.
 */
class DeliveryAccessIT extends AbstractVersionIT {

    private static final String AGENTS = "# Agents\n";
    private static final String RULE = "---\npaths: \"src/**\"\n---\nStyle\n";

    private Repository repository;
    private String remote;
    private UUID pack;
    private String token;
    private String machineId;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        pack = packWith("org", "AGENTS.md", AGENTS, ".agents/rules/style.md", RULE);
        repository = saveRepository(tenant.organization());
        remote = "https://" + repository.getRemoteUrl() + ".git";
        MvcResult created = register(tenant);
        token = read(created, "$.data.token");
        machineId = read(created, "$.data.machine.id");
    }

    private MvcResult register(Tenant of) throws Exception {
        Person developer = signIn(of, "dev");
        return mockMvc.perform(in(of, post("/kaya/machines"), developer.jwt(), "{\"machineName\":\"m-" + uniqueSuffix() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void release(UUID... packs) throws Exception {
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(packs))).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String machineToken) {
        return builder.header("Authorization", "Bearer " + machineToken);
    }

    private MvcResult current() throws Exception {
        return mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remote), token))
                .andExpect(status().isOk()).andReturn();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class Endpoints {

        private Endpoints() {
        }

        static List<MockHttpServletRequestBuilder> all(UUID releaseId, String remoteUrl) {
            return List.of(
                    get("/kaya/delivery/current").param("remoteUrl", remoteUrl),
                    get("/kaya/delivery/releases/" + releaseId),
                    get("/kaya/delivery/releases").param("remoteUrl", remoteUrl).param("number", "1"),
                    get("/kaya/delivery/blobs/" + "a".repeat(64)));
        }
    }

    @Nested
    class MachineTokenChain {

        private void assertAllRefused(java.util.function.UnaryOperator<MockHttpServletRequestBuilder> credentials)
                throws Exception {
            for (MockHttpServletRequestBuilder request : Endpoints.all(UUID.randomUUID(), remote)) {
                mockMvc.perform(credentials.apply(request))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
            }
        }

        @Test
        void noCredentials_everyEndpointAnswers401() throws Exception {
            assertAllRefused(request -> request);
        }

        @Test
        void userJwtInsteadOfMachineToken_everyEndpointAnswers401() throws Exception {
            assertAllRefused(request -> request.with(owner).header("X-Organization-ID", tenant.id()));
        }

        @Test
        void unknownToken_everyEndpointAnswers401() throws Exception {
            assertAllRefused(request -> withToken(request, "kya_" + "0".repeat(40)));
        }

        @Test
        void revokedToken_everyEndpointAnswers401() throws Exception {
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET revoked_at = now() WHERE machine_id = ?::uuid", machineId);

            assertAllRefused(request -> withToken(request, token));
        }

        @Test
        void expiredToken_everyEndpointAnswers401() throws Exception {
            jdbcTemplate.update("UPDATE kaya.machine_tokens SET expires_at = now() - interval '1 second' WHERE machine_id = ?::uuid",
                    machineId);

            assertAllRefused(request -> withToken(request, token));
        }

        @Test
        void retiredMachine_everyEndpointAnswers401() throws Exception {
            jdbcTemplate.update("UPDATE kaya.machines SET status = 'RETIRED' WHERE id = ?::uuid", machineId);

            assertAllRefused(request -> withToken(request, token));
        }

        @Test
        void organizationHeader_isNotNeededAndNotTrusted() throws Exception {
            release(pack);
            String other = tenantlessOrganizationId();

            mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remote), token)
                            .header("X-Organization-ID", other))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.repositoryId").value(repository.getId().toString()));
        }

        private String tenantlessOrganizationId() {
            return newTenant().id();
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void aTokenOfAnotherOrganization_readsNothingOfThisOne() throws Exception {
            release(pack);
            String releaseId = read(current(), "$.data.releaseId");
            String sha = sha256(AGENTS.getBytes(StandardCharsets.UTF_8));
            String foreign = read(register(newTenant()), "$.data.token");

            mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remote), foreign))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
            mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), foreign))
                    .andExpect(status().isNotFound());
            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", remote).param("number", "1"), foreign))
                    .andExpect(status().isNotFound());
            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), foreign))
                    .andExpect(status().isNotFound());
        }

        @Test
        void sameContentInTwoOrganizations_isServedToEachFromItsOwnRelease() throws Exception {
            release(pack);
            Tenant foreignTenant = newTenant();
            String foreign = read(register(foreignTenant), "$.data.token");
            String sha = sha256(AGENTS.getBytes(StandardCharsets.UTF_8));

            // The foreign organization stores the same bytes as a draft only: no release names them there.
            UUID foreignPack = insertPack(foreignTenant.organization(), "org");
            UUID draft = insertDraft(foreignPack, 1, foreignTenant.owner().getUser().getId());
            insertFile(foreignTenant.organization(), draft, "AGENTS.md", AGENTS);

            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), foreign)).andExpect(status().isNotFound());
            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), token)).andExpect(status().isOk());
        }
    }

    @Nested
    class Remote {

        @Test
        void everyFormGitPrints_resolvesToTheSameRepository() throws Exception {
            release(pack);
            String path = repository.getRemoteUrl();
            String host = path.substring(0, path.indexOf('/'));
            String rest = path.substring(path.indexOf('/') + 1);

            for (String form : List.of("https://" + path, "https://" + path + ".git", "https://" + path + "/",
                    "git@" + host + ":" + rest + ".git", "ssh://git@" + host + "/" + rest + ".git",
                    "HTTPS://" + host.toUpperCase() + "/" + rest)) {
                mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", form), token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.repositoryId").value(repository.getId().toString()));
            }
        }

        @Test
        void remoteLongerThan500_answers422() throws Exception {
            mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", "https://h.io/" + "a".repeat(500)), token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void byNumber_withoutRemoteOrNumber_answers422() throws Exception {
            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("number", "1"), token))
                    .andExpect(status().isUnprocessableEntity());
            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", remote), token))
                    .andExpect(status().isUnprocessableEntity());
            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", remote).param("number", "-1"), token))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void byNumber_unconnectedRemote_answers404() throws Exception {
            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", "https://github.com/none/" + uniqueSuffix())
                            .param("number", "1"), token))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Manifest {

        @Test
        void twoReads_returnTheSameBytesThatHashToTheAnnouncedSha() throws Exception {
            release(pack);
            MvcResult delivered = current();
            String releaseId = read(delivered, "$.data.releaseId");

            String first = read(mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), token)).andReturn(),
                    "$.data.manifest");
            String second = read(mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), token)).andReturn(),
                    "$.data.manifest");

            assertEquals(first, second);
            assertEquals(read(delivered, "$.data.manifestSha256"), sha256(first.getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        void aChangedByte_failsVerificationWithThePublishedKey() throws Exception {
            release(pack);
            String releaseId = read(current(), "$.data.releaseId");
            MvcResult result = mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), token)).andReturn();
            String manifest = read(result, "$.data.manifest");
            String signature = read(result, "$.data.signature");
            MvcResult keys = mockMvc.perform(in(tenant, get("/kaya/signing-keys"), member, null))
                    .andExpect(status().isOk()).andReturn();
            String publicKey = list(keys, "$.data[?(@.keyId == '" + read(result, "$.data.keyId") + "')].publicKey").get(0);

            assertTrue(verifyWithRawKey(publicKey, manifest, signature));
            assertFalse(verifyWithRawKey(publicKey, manifest.replace("AGENTS.md", "AGENTS.mx"), signature));
            assertFalse(verifyWithRawKey(publicKey, manifest + " ", signature));
        }

        @Test
        void manifestNamesEveryDeliveredFileWithTheHashOfItsBlob() throws Exception {
            release(pack);
            String releaseId = read(current(), "$.data.releaseId");
            String manifest = read(mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), token)).andReturn(),
                    "$.data.manifest");

            assertTrue(manifest.contains("\"sha256\":\"" + sha256(AGENTS.getBytes(StandardCharsets.UTF_8)) + "\""), manifest);
            assertTrue(manifest.contains("\"sha256\":\"" + sha256(RULE.getBytes(StandardCharsets.UTF_8)) + "\""), manifest);
            assertTrue(manifest.contains("\"path\":\".agents/rules/style.md\""), manifest);
        }

        @Test
        void anOlderRelease_staysReadableByNumberAfterANewerOne() throws Exception {
            release(pack);
            String firstId = read(current(), "$.data.releaseId");
            UUID extra = packWith("extra", ".agents/rules/extra.md", "---\npaths: \"docs/**\"\n---\nExtra\n");
            release(pack, extra);

            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", remote).param("number", "1"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.releaseId").value(firstId));
            mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remote), token))
                    .andExpect(jsonPath("$.data.number").value(2));
        }
    }

    @Nested
    class Blobs {

        @Test
        void aBlobWithNonTextBytes_isServedByteExactWithImmutableCaching() throws Exception {
            byte[] bytes = new byte[] {0, 1, (byte) 0xff, (byte) 0xfe, 10, 13, (byte) 0x80};
            String sha = sha256(bytes);
            UUID binary = packWith("bin");
            UUID version = currentVersionOf(binary);
            insertBytes(tenant.organization(), version, ".agents/hooks/blob.bin", bytes);
            release(pack, binary);

            MvcResult served = mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "\"" + sha + "\""))
                    .andExpect(header().string("Cache-Control", "private, max-age=31536000, immutable"))
                    .andReturn();
            assertArrayEquals(bytes, served.getResponse().getContentAsByteArray());
        }

        @Test
        void aBlobNoReleaseNamesYet_answers404UntilTheReleaseIsBuilt() throws Exception {
            String sha = sha256(AGENTS.getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), token)).andExpect(status().isNotFound());
            release(pack);
            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), token)).andExpect(status().isOk());
        }

        @Test
        void aBlobOfAnOlderRelease_isStillServedAfterANewReleaseDropsIt() throws Exception {
            release(pack);
            UUID replacement = packWith("replacement", "AGENTS.md", "# Other\n");
            release(replacement);
            String sha = sha256(AGENTS.getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), token)).andExpect(status().isOk());
        }

        @Test
        void aHashThatNoBlobHas_answers404() throws Exception {
            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + "f".repeat(64)), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }

        @Test
        void hashesOfTheWrongShape_answer422() throws Exception {
            for (String bad : List.of("a".repeat(63), "a".repeat(65), "g".repeat(64), "A".repeat(64))) {
                ResultActions result = mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + bad), token));
                result.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
            }
        }
    }
}
