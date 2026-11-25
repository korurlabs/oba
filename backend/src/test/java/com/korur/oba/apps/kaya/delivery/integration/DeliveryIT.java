package com.korur.oba.apps.kaya.delivery.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.mock_data.RepositoryMocks;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The delivery endpoints as the CLI calls them, with a machine token: the current release and its
 * ETag, signed releases by id and number, and the content a release names. Every read stays in
 * the token's organization, and nothing a release does not name is served.
 */
class DeliveryIT extends AbstractVersionIT {

    private static final String AGENTS = "# Agents\n";
    private static final String DRAFT_ONLY = "content only a draft holds\n";

    private Repository repository;
    private String remote;
    private UUID pack;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        pack = packWith("org", "AGENTS.md", AGENTS, ".agents/rules/style.md", "---\npaths: \"src/**\"\n---\nStyle\n");
        repository = saveRepository(tenant.organization());
        // The CLI sends the checkout's remote as git prints it; Kaya normalises it.
        remote = "git@" + repository.getRemoteUrl().replaceFirst("/", ":") + ".git";
        token = machineToken(tenant);
    }

    private String machineToken(Tenant of) throws Exception {
        Person developer = signIn(of, "dev");
        MvcResult created = mockMvc.perform(in(of, post("/kaya/machines"), developer.jwt(), "{\"machineName\":\"m-" + uniqueSuffix() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return read(created, "$.data.token");
    }

    private void release() throws Exception {
        mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack))).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String machineToken) {
        return builder.header("Authorization", "Bearer " + machineToken);
    }

    private ResultActions current(String machineToken, String remoteUrl) throws Exception {
        return mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remoteUrl), machineToken));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Nested
    class Repositories {

        private static final String REMOTES = "$.data.repositories[*].remoteUrl";

        private Repository connect(String remoteUrl, String name, RepositoryStatus status) {
            Repository connected = RepositoryMocks.repository(tenant.organization().getId(), remoteUrl, status);
            connected.setId(null);
            connected.setName(name);
            return repositoryRepository.saveAndFlush(connected);
        }

        private ResultActions repositories(String machineToken) throws Exception {
            return mockMvc.perform(withToken(get("/kaya/delivery/repositories"), machineToken));
        }

        @Test
        void everyActiveRepositoryOfTheOrganization_isListedSortedByRemote() throws Exception {
            String suffix = uniqueSuffix();
            // The names sort the other way round, so the order can only come from the remote.
            Repository last = connect("github.com/z" + suffix + "/r", "a-last", RepositoryStatus.ACTIVE);
            Repository first = connect("github.com/a" + suffix + "/r", "z-first", RepositoryStatus.ACTIVE);

            MvcResult result = repositories(token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.organizationId").value(tenant.id()))
                    .andReturn();

            assertEquals(List.of(first.getRemoteUrl(), repository.getRemoteUrl(), last.getRemoteUrl()), list(result, REMOTES));
        }

        @Test
        void archivedRepository_isLeftOut() throws Exception {
            connect("github.com/b" + uniqueSuffix() + "/r", "archived", RepositoryStatus.ARCHIVED);

            MvcResult result = repositories(token).andExpect(status().isOk()).andReturn();

            assertEquals(List.of(repository.getRemoteUrl()), list(result, REMOTES));
        }

        @Test
        void repositoryOfAnotherOrganization_isNotListed() throws Exception {
            Tenant other = newTenant();
            Repository foreign = saveRepository(other.organization());
            String otherToken = machineToken(other);

            MvcResult own = repositories(token).andExpect(status().isOk()).andReturn();
            MvcResult theirs = repositories(otherToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.organizationId").value(other.id()))
                    .andReturn();

            assertEquals(List.of(repository.getRemoteUrl()), list(own, REMOTES));
            assertEquals(List.of(foreign.getRemoteUrl()), list(theirs, REMOTES));
        }

        @Test
        void organizationWithoutRepositories_answersAnEmptyList() throws Exception {
            Tenant empty = newTenant();

            repositories(machineToken(empty))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.organizationId").value(empty.id()))
                    .andExpect(jsonPath("$.data.repositories").isArray())
                    .andExpect(jsonPath("$.data.repositories").isEmpty());
        }

        @Test
        void withoutTokenOrWithARevokedToken_answers401() throws Exception {
            mockMvc.perform(get("/kaya/delivery/repositories"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
            jdbcTemplate.update("""
                    UPDATE dojo.api_tokens SET revoked_at = now()
                    WHERE subject_id IN (SELECT id FROM kaya.machines WHERE organization_id = ?)
                    """, tenant.organization().getId());

            repositories(token)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
        }
    }

    @Nested
    class Current {

        @Test
        void withRelease_returnsTheNewestReleaseAndAnETag() throws Exception {
            release();
            UUID second = packWith("extra", ".agents/rules/extra.md", "---\npaths: \"docs/**\"\n---\nExtra\n");
            mockMvc.perform(in(tenant, put(packsPath(repository)), owner, packIds(pack, second))).andExpect(status().isOk());

            MvcResult result = current(token, remote)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.repositoryId").value(repository.getId().toString()))
                    .andExpect(jsonPath("$.data.number").value(2))
                    .andExpect(jsonPath("$.data.releaseId").isNotEmpty())
                    .andExpect(jsonPath("$.data.manifestSha256").isNotEmpty())
                    .andExpect(jsonPath("$.data.revocations").doesNotExist())
                    .andReturn();

            assertNotNull(result.getResponse().getHeader("ETag"));
        }

        @Test
        void ifNoneMatchWithTheCurrentETag_answers304WithoutBody() throws Exception {
            release();
            String etag = current(token, remote).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");

            mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remote), token).header("If-None-Match", etag))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", etag))
                    .andExpect(content().string(""));
        }

        @Test
        void aNewRelease_changesTheETag() throws Exception {
            String before = current(token, remote)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.releaseId").isEmpty())
                    .andExpect(jsonPath("$.data.number").isEmpty())
                    .andReturn().getResponse().getHeader("ETag");
            release();

            String after = mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remote), token)
                            .header("If-None-Match", before))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.number").value(1))
                    .andReturn().getResponse().getHeader("ETag");
            assertNotEquals(before, after);
        }

        @Test
        void remoteOfAnotherOrganization_answers404() throws Exception {
            release();
            String otherToken = machineToken(newTenant());

            current(otherToken, remote)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }

        @Test
        void unknownRemote_answers404AndConnectsNothing() throws Exception {
            String unknown = "https://github.com/o" + uniqueSuffix() + "/unknown.git";

            current(token, unknown)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
            assertEquals(0L, count("SELECT count(*) FROM kaya.repositories WHERE organization_id = ? AND remote_url LIKE ?",
                    tenant.organization().getId(), "%/unknown"));
        }

        @Test
        void remoteKayaDoesNotAccept_answers400() throws Exception {
            current(token, "not a remote")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("GEN_API_001"));
        }

        @Test
        void withoutRemoteUrl_answers422() throws Exception {
            mockMvc.perform(withToken(get("/kaya/delivery/current"), token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }

        @Test
        void archivedRepository_answers422() throws Exception {
            jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED'::repository_repository_status_enum WHERE id = ?",
                    repository.getId());

            current(token, remote)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("KAY_VAL_002"));
        }

        @Test
        void withoutTokenOrWithAJwt_answers401() throws Exception {
            mockMvc.perform(get("/kaya/delivery/current").param("remoteUrl", remote))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
            // A user's access token is not a machine token, so the machine chain refuses it.
            mockMvc.perform(withToken(get("/kaya/delivery/current").param("remoteUrl", remote),
                            "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.c2lnbmF0dXJl"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("KAY_SEC_003"));
        }
    }

    @Nested
    class Releases {

        @Test
        void byId_returnsTheSignedManifestThatVerifiesWithThePublishedKey() throws Exception {
            release();
            String releaseId = read(current(token, remote).andReturn(), "$.data.releaseId");

            MvcResult result = mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.releaseId").value(releaseId))
                    .andExpect(jsonPath("$.data.repositoryId").value(repository.getId().toString()))
                    .andExpect(jsonPath("$.data.number").value(1))
                    .andReturn();
            String manifest = read(result, "$.data.manifest");
            String keyId = read(result, "$.data.keyId");

            assertEquals(sha256(manifest.getBytes(StandardCharsets.UTF_8)), read(result, "$.data.manifestSha256"));
            assertTrue(manifest.contains("\"remoteUrl\":\"" + repository.getRemoteUrl() + "\""), manifest);
            assertTrue(manifest.contains("\"organization\":\"" + tenant.id() + "\""), manifest);
            MvcResult keys = mockMvc.perform(in(tenant, get("/kaya/signing-keys"), member, null))
                    .andExpect(status().isOk()).andReturn();
            String publicKey = list(keys, "$.data[?(@.keyId == '" + keyId + "')].publicKey").get(0);
            assertTrue(verifyWithRawKey(publicKey, manifest, read(result, "$.data.signature")));
        }

        @Test
        void byIdOfAnotherOrganization_answers404() throws Exception {
            release();
            String releaseId = read(current(token, remote).andReturn(), "$.data.releaseId");
            String otherToken = machineToken(newTenant());

            mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), otherToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
            mockMvc.perform(withToken(get("/kaya/delivery/releases/" + UUID.randomUUID()), token))
                    .andExpect(status().isNotFound());
        }

        @Test
        void byNumber_returnsTheSameSignedRelease() throws Exception {
            release();
            String releaseId = read(current(token, remote).andReturn(), "$.data.releaseId");
            String byId = read(mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), token)).andReturn(),
                    "$.data.manifest");

            MvcResult result = mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", remote)
                            .param("number", "1"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.releaseId").value(releaseId))
                    .andReturn();
            assertEquals(byId, read(result, "$.data.manifest"));

            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", remote).param("number", "9"), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
            mockMvc.perform(withToken(get("/kaya/delivery/releases").param("remoteUrl", remote).param("number", "0"), token))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void afterTheRemoteChanged_answers409() throws Exception {
            release();
            String releaseId = read(current(token, remote).andReturn(), "$.data.releaseId");
            jdbcTemplate.update("UPDATE kaya.repositories SET remote_url = ? WHERE id = ?",
                    "github.com/moved" + uniqueSuffix() + "/r", repository.getId());

            mockMvc.perform(withToken(get("/kaya/delivery/releases/" + releaseId), token))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("KAY_CFG_012"));
        }
    }

    @Nested
    class Blobs {

        @Test
        void contentAReleaseNames_isServedAsRawBytes() throws Exception {
            release();
            byte[] bytes = AGENTS.getBytes(StandardCharsets.UTF_8);
            String sha = sha256(bytes);

            MvcResult result = mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha), token))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    .andExpect(header().string("ETag", "\"" + sha + "\""))
                    .andReturn();
            assertArrayEquals(bytes, result.getResponse().getContentAsByteArray());
        }

        @Test
        void contentOnlyADraftHolds_answers404() throws Exception {
            release();
            UUID draft = insertDraft(pack, 2, proposer);
            insertFile(tenant.organization(), draft, ".agents/rules/draft.md", DRAFT_ONLY);

            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha256(DRAFT_ONLY.getBytes(StandardCharsets.UTF_8))), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        }

        @Test
        void contentOfAnotherOrganization_answers404() throws Exception {
            release();
            String otherToken = machineToken(newTenant());

            mockMvc.perform(withToken(get("/kaya/delivery/blobs/" + sha256(AGENTS.getBytes(StandardCharsets.UTF_8))), otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void malformedHash_answers422() throws Exception {
            mockMvc.perform(withToken(get("/kaya/delivery/blobs/ABC"), token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"));
        }
    }

    // Kaya no longer serves slash commands or revocations; their old routes must be refused as
    // unknown, never served and never fail on the server.
    @Nested
    class RemovedRoutes {

        @Test
        void revocationAck_withMachineToken_answers4xx() throws Exception {
            mockMvc.perform(withToken(post("/kaya/delivery/revocations/" + UUID.randomUUID() + "/acks")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"sessionIds\":[\"s\"]}"), token))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        void commandsAndRevocations_asOwner_answer4xx() throws Exception {
            mockMvc.perform(in(tenant, get("/kaya/commands"), owner, null)).andExpect(status().is4xxClientError());
            mockMvc.perform(in(tenant, get("/kaya/revocations"), owner, null)).andExpect(status().is4xxClientError());
            mockMvc.perform(in(tenant, post("/kaya/revocations"), owner, "{\"rules\":[\"Read(./.env)\"]}"))
                    .andExpect(status().is4xxClientError());
        }
    }
}
