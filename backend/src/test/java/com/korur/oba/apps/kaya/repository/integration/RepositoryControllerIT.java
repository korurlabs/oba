package com.korur.oba.apps.kaya.repository.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.mock_data.RepositoryMocks;
import com.korur.oba.apps.kaya.repository.repository.RepositoryRepository;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RepositoryControllerIT extends ObaIntegrationTest {

    private static final String ORGANIZATION_HEADER = "X-Organization-ID";

    @Autowired
    private RepositoryRepository repositoryRepository;

    private Organization organization;
    private OrganizationMember admin;
    private OrganizationMember member;

    @BeforeEach
    void setUp() {
        String domain = uniqueDomain();
        organization = createOrganization(OrganizationStatus.ACTIVE);
        grantEveryAppToEveryone(organization);
        admin = createActiveMember(organization, domain, OrganizationMemberRole.ADMIN);
        member = createActiveMember(organization, domain, OrganizationMemberRole.MEMBER);
    }

    private RequestPostProcessor as(OrganizationMember membership) {
        UUID userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM shrine.organization_members WHERE id = ?", UUID.class, membership.getId());
        return jwtOf(userRepository.findById(userId).orElseThrow());
    }

    private Repository saveRepository(Organization owner, String name, RepositoryStatus status) {
        Repository repository = RepositoryMocks.repository(owner.getId(),
                "github.com/o" + uniqueSuffix() + "/r", status);
        repository.setId(null);
        repository.setName(name);
        return repositoryRepository.saveAndFlush(repository);
    }

    private String patchRepository(Repository repository, OrganizationMember caller, String body, int expectedStatus)
            throws Exception {
        return mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(caller))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void getRepositories_asMember_listsOwnOrganizationOnlyOrderedByName() throws Exception {
        saveRepository(organization, "Zeta", RepositoryStatus.ACTIVE);
        saveRepository(organization, "Alpha", RepositoryStatus.ARCHIVED);
        Organization other = createOrganization(OrganizationStatus.ACTIVE);
        saveRepository(other, "Foreign", RepositoryStatus.ACTIVE);

        mockMvc.perform(get("/kaya/repositories").with(as(member)).header(ORGANIZATION_HEADER, organization.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Alpha"))
                .andExpect(jsonPath("$.data[0].status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data[1].name").value("Zeta"));
    }

    @Test
    void getRepositories_noRepositories_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/kaya/repositories").with(as(member)).header(ORGANIZATION_HEADER, organization.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getRepositories_missingHeader_answers400() throws Exception {
        mockMvc.perform(get("/kaya/repositories").with(as(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GEN_API_007"));
    }

    @Test
    void getRepositories_headerNotUuid_answers400() throws Exception {
        mockMvc.perform(get("/kaya/repositories").with(as(member)).header(ORGANIZATION_HEADER, "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORG_API_001"));
    }

    @Test
    void getRepositories_withoutJwt_answers401() throws Exception {
        mockMvc.perform(get("/kaya/repositories").header(ORGANIZATION_HEADER, organization.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRepositories_userOutsideTheOrganization_answers403() throws Exception {
        Organization other = createOrganization(OrganizationStatus.ACTIVE);

        mockMvc.perform(get("/kaya/repositories").with(as(member)).header(ORGANIZATION_HEADER, other.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchRepository_asMember_answers403IncorrectRoleAndKeepsName() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(member))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORG_SEC_003"))
                .andExpect(jsonPath("$.error.errorValues.OrganizationId").value(organization.getId().toString()));

        assertEquals("Village", repositoryRepository.findById(repository.getId()).orElseThrow().getName());
    }

    @Test
    void patchRepository_renameTrimsAndPersists() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Kaya  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Kaya"))
                .andExpect(jsonPath("$.data.remoteUrl").value(repository.getRemoteUrl()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertEquals("Kaya", repositoryRepository.findById(repository.getId()).orElseThrow().getName());
    }

    @Test
    void patchRepository_setThenRemoveTemplate() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketUrlTemplate\":\"https://kazan.example/tickets/{key}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketUrlTemplate").value("https://kazan.example/tickets/{key}"));

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketUrlTemplate\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketUrlTemplate").doesNotExist());

        assertNull(repositoryRepository.findById(repository.getId()).orElseThrow().getTicketUrlTemplate());
    }

    @Test
    void patchRepository_nullTemplate_keepsExisting() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);
        patchRepository(repository, admin, "{\"ticketUrlTemplate\":\"https://k.example/{key}\"}", 200);

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketUrlTemplate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketUrlTemplate").value("https://k.example/{key}"));
    }

    @Test
    void patchRepository_archiveAndUnarchive_updatesStatus() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        assertEquals("ARCHIVED", jdbcTemplate.queryForObject(
                "SELECT status::text FROM kaya.repositories WHERE id = ?", String.class, repository.getId()));

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void patchRepository_emptyBody_answers200AndChangesNothing() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Village"));
    }

    @Test
    void patchRepository_invalidFields_answers422WithFieldPaths() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"ticketUrlTemplate\":\"http://k.example/{key}\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GEN_VAL_001"))
                .andExpect(jsonPath("$.error.errorValues.name").exists())
                .andExpect(jsonPath("$.error.errorValues.ticketUrlTemplate").exists());
    }

    @Test
    void patchRepository_templateWithoutKeyOrNonHttps_answers422() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        for (String template : new String[] {"https://k.example/t", "javascript:{key}", "https://k.example/ {key}"}) {
            mockMvc.perform(patch("/kaya/repositories/{id}", repository.getId()).with(as(admin))
                            .header(ORGANIZATION_HEADER, organization.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ticketUrlTemplate\":\"" + template + "\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void patchRepository_nameOver100Characters_answers422() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        patchRepository(repository, admin, "{\"name\":\"" + "n".repeat(101) + "\"}", 422);
    }

    @Test
    void patchRepository_unknownStatus_answers400() throws Exception {
        Repository repository = saveRepository(organization, "Village", RepositoryStatus.ACTIVE);

        patchRepository(repository, admin, "{\"status\":\"DELETED\"}", 400);
    }

    @Test
    void patchRepository_repositoryOfAnotherOrganization_answers404AndKeepsName() throws Exception {
        Organization other = createOrganization(OrganizationStatus.ACTIVE);
        Repository foreign = saveRepository(other, "Foreign", RepositoryStatus.ACTIVE);

        mockMvc.perform(patch("/kaya/repositories/{id}", foreign.getId()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Taken\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"))
                .andExpect(jsonPath("$.error.errorValues.repositoryId").value(foreign.getId().toString()));

        assertEquals("Foreign", repositoryRepository.findById(foreign.getId()).orElseThrow().getName());
    }

    @Test
    void patchRepository_unknownId_answers404() throws Exception {
        mockMvc.perform(patch("/kaya/repositories/{id}", UUID.randomUUID()).with(as(admin))
                        .header(ORGANIZATION_HEADER, organization.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("KAY_NF_001"));
    }
}
