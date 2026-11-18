package com.korur.oba.apps.kaya.version.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.support.AbstractVersionIT;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call the pack, version, kind, release and link endpoints and which
 * organization's ids each one answers. The reads need MEMBER, the writes OWNER; an id of another
 * organization is a 404, never a 403, so a caller cannot learn that the id exists, and a kind list
 * drops version ids of another organization.
 */
class VersionedResourceAccessIT extends AbstractVersionIT {

    private record Call(HttpMethod method, String path, String body) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private Repository repository;
    private UUID packId;
    private List<Call> reads;
    private List<Call> writes;

    private Tenant other;
    private Repository otherRepository;
    private UUID otherPackId;
    private UUID otherVersionId;

    @BeforeEach
    void setUp() throws Exception {
        initTenant();
        packId = packWith("org", "AGENTS.md", "# Agents\n", ".claude/settings.json", SETTINGS);
        UUID versionId = currentVersionOf(packId);
        repository = saveRepository(tenant.organization());
        mockMvc.perform(in(tenant, request(HttpMethod.PUT, packsPath(repository)), owner, packIds(packId)))
                .andExpect(status().isOk());

        String base = "/kaya/repositories/" + repository.getId();
        reads = List.of(
                new Call(HttpMethod.GET, "/kaya/packs", null),
                new Call(HttpMethod.GET, "/kaya/versions?packId=" + packId, null),
                new Call(HttpMethod.GET, "/kaya/versions/" + versionId, null),
                new Call(HttpMethod.GET, "/kaya/versions/" + versionId + "/file?path=AGENTS.md", null),
                new Call(HttpMethod.GET, "/kaya/agents-files?versionId=" + versionId, null),
                new Call(HttpMethod.GET, "/kaya/rules?versionId=" + versionId, null),
                new Call(HttpMethod.GET, "/kaya/skills?versionId=" + versionId, null),
                new Call(HttpMethod.GET, "/kaya/settings?versionId=" + versionId, null),
                new Call(HttpMethod.GET, "/kaya/signing-keys", null),
                new Call(HttpMethod.GET, "/kaya/links?repositoryId=" + repository.getId(), null),
                new Call(HttpMethod.GET, base + "/packs", null),
                new Call(HttpMethod.GET, base + "/releases", null),
                new Call(HttpMethod.GET, base + "/releases/1", null));
        writes = List.of(
                new Call(HttpMethod.POST, "/kaya/packs", "{\"name\":\"access-" + uniqueSuffix() + "\"}"),
                new Call(HttpMethod.POST, "/kaya/packs", "{\"name\":\"child-" + uniqueSuffix()
                        + "\",\"parentPackId\":\"" + packId + "\"}"),
                new Call(HttpMethod.PUT, base + "/packs", packIds(packId)));

        other = newTenant();
        otherPackId = insertPack(other.organization(), "foreign");
        otherVersionId = approvedVersion(otherPackId, 1);
        insertFile(other.organization(), otherVersionId, "AGENTS.md", "# Foreign\n");
        otherRepository = saveRepository(other.organization());
    }

    private MockHttpServletRequestBuilder build(Call call, Tenant inTenant, RequestPostProcessor jwt) {
        return in(inTenant, request(call.method(), call.path()), jwt, call.body());
    }

    private void assertStatus(int expected, Call call, Tenant inTenant, RequestPostProcessor jwt) throws Exception {
        int actual = mockMvc.perform(build(call, inTenant, jwt)).andReturn().getResponse().getStatus();
        assertEquals(expected, actual, call + " answered " + actual);
    }

    private List<Call> all() {
        List<Call> calls = new ArrayList<>(reads);
        calls.addAll(writes);
        return calls;
    }

    // --- Roles ---

    @Test
    void reads_memberAdminAndOwner_answer200() throws Exception {
        for (Call call : reads) {
            assertStatus(200, call, tenant, member);
            assertStatus(200, call, tenant, admin);
            assertStatus(200, call, tenant, owner);
        }
    }

    @Test
    void writes_memberAndAdmin_answer403AndChangeNothing() throws Exception {
        long packs = count("SELECT count(*) FROM kaya.packs WHERE organization_id = ?", tenant.organization().getId());
        long releases = count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId());
        for (Call call : writes) {
            assertStatus(403, call, tenant, member);
            assertStatus(403, call, tenant, admin);
        }
        assertEquals(packs, count("SELECT count(*) FROM kaya.packs WHERE organization_id = ?", tenant.organization().getId()));
        assertEquals(releases, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void writes_owner_succeeds() throws Exception {
        assertStatus(201, writes.get(0), tenant, owner);
        assertStatus(201, writes.get(1), tenant, owner);
        assertStatus(200, writes.get(2), tenant, owner);
    }

    @Test
    void everyEndpoint_withoutJwt_answers401() throws Exception {
        for (Call call : all()) {
            int actual = mockMvc.perform(request(call.method(), call.path())
                            .header("X-Organization-ID", tenant.id())
                            .contentType("application/json")
                            .content(call.body() == null ? "" : call.body()))
                    .andReturn().getResponse().getStatus();
            assertEquals(401, actual, call + " answered " + actual);
        }
    }

    @Test
    void everyEndpoint_userOfAnotherOrganization_answers403() throws Exception {
        RequestPostProcessor outsider = jwtOf(other.owner().getUser());
        for (Call call : all()) {
            assertStatus(403, call, tenant, outsider);
        }
    }

    @Test
    void everyEndpoint_withoutOrganizationHeader_answers400() throws Exception {
        for (Call call : all()) {
            MockHttpServletRequestBuilder builder = request(call.method(), call.path()).with(owner);
            if (call.body() != null) {
                builder.contentType("application/json").content(call.body());
            }
            int actual = mockMvc.perform(builder).andReturn().getResponse().getStatus();
            assertEquals(400, actual, call + " answered " + actual);
        }
    }

    // --- Tenant isolation ---

    @Test
    void reads_idsOfAnotherOrganization_answer404WithTheNotFoundCode() throws Exception {
        String foreignBase = "/kaya/repositories/" + otherRepository.getId();
        List<Call> foreign = List.of(
                new Call(HttpMethod.GET, "/kaya/versions/" + otherVersionId, null),
                new Call(HttpMethod.GET, "/kaya/versions/" + otherVersionId + "/file?path=AGENTS.md", null),
                new Call(HttpMethod.GET, "/kaya/versions/" + otherVersionId + "/diff", null),
                new Call(HttpMethod.GET, "/kaya/links?versionId=" + otherVersionId, null),
                new Call(HttpMethod.GET, "/kaya/links?repositoryId=" + otherRepository.getId(), null),
                new Call(HttpMethod.GET, foreignBase + "/packs", null),
                new Call(HttpMethod.GET, foreignBase + "/releases", null),
                new Call(HttpMethod.GET, foreignBase + "/releases/1", null));
        for (Call call : foreign) {
            mockMvc.perform(build(call, tenant, owner))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value(startsWith("KAY_NF_")));
        }
    }

    @Test
    void kindLists_versionIdOfAnotherOrganization_isIgnored() throws Exception {
        mockMvc.perform(build(new Call(HttpMethod.GET, "/kaya/agents-files?versionId=" + otherVersionId, null), tenant, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void reads_foreignIdAnswersExactlyLikeAnUnknownId() throws Exception {
        String foreign = mockMvc.perform(build(new Call(HttpMethod.GET, "/kaya/versions/" + otherVersionId, null), tenant, owner))
                .andReturn().getResponse().getContentAsString().replace(otherVersionId.toString(), "ID");
        String unknown = UUID.randomUUID().toString();
        String missing = mockMvc.perform(build(new Call(HttpMethod.GET, "/kaya/versions/" + unknown, null), tenant, owner))
                .andReturn().getResponse().getContentAsString().replace(unknown, "ID");
        assertEquals(missing.replaceAll("\"(timestamp|path)\":\"[^\"]*\",?", ""),
                foreign.replaceAll("\"(timestamp|path)\":\"[^\"]*\",?", ""));
    }

    @Test
    void replacePacks_foreignRepositoryOrForeignPack_answers404AndChangesNothing() throws Exception {
        mockMvc.perform(in(tenant, request(HttpMethod.PUT, packsPath(otherRepository)), owner, packIds(packId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(in(tenant, request(HttpMethod.PUT, packsPath(repository)), owner, packIds(packId, otherPackId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
        assertEquals(0, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", otherRepository.getId()));
        assertEquals(1, count("SELECT count(*) FROM kaya.repository_packs WHERE repository_id = ?", repository.getId()));
        assertEquals(1, count("SELECT count(*) FROM kaya.releases WHERE repository_id = ?", repository.getId()));
    }

    @Test
    void packs_listOfOneOrganizationNeverShowsAnother() throws Exception {
        mockMvc.perform(build(new Call(HttpMethod.GET, "/kaya/packs", null), tenant, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + otherPackId + "')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.id == '" + packId + "')]").isNotEmpty());
        mockMvc.perform(build(new Call(HttpMethod.GET, "/kaya/packs", null), other, jwtOf(other.member().getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + packId + "')]").isEmpty());
    }
}
