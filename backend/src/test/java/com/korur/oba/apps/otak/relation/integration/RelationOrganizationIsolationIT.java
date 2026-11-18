package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the CRM of
 * an organization is closed to every other organization: a user who sits in two organizations
 * reaches a relation and its contacts only with the header of the organization that owns
 * them; every other header answers as if they did not exist, shows none of their
 * data and changes nothing.
 */
class RelationOrganizationIsolationIT extends AbstractTenantIT {

    private static final String RELATION_NOT_FOUND_CODE = "OTK_NOT_001";
    private static final String CONTACT_NOT_FOUND_CODE = "OTK_NOT_002";
    private static final String OWNER_NOT_MEMBER_CODE = "OTK_STA_001";

    private static final String RELATIONS_PATH = "/otak/relations";
    private static final String BATTLEFIELD_PATH = "/otak/battlefield";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String nameKey;
    private String relationName;
    private String contactName;
    private String relationId;
    private String contactId;
    private String relationOfBId;
    private String customerOfAId;

    /**
     * The user is a MEMBER of A, which owns a lead with one contact, and an ADMIN of B,
     * so even the ADMIN-only delete reaches the lookup with the header of B.
     */
    @BeforeEach
    void setUpTenantsAndRelationOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = signIn(tenantA, "otak");
        createMember(tenantB.organization(), user.user(), OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

        String suffix = uniqueSuffix();
        nameKey = suffix;
        relationName = "Relation of A " + suffix;
        contactName = "Contact of A " + suffix;

        relationId = read(mockMvc.perform(in(tenantA, post(RELATIONS_PATH), user.jwt(),
                        relationBody(relationName, "LEAD")))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        contactId = read(mockMvc.perform(in(tenantA, post(relationPath(relationId, "/contacts")), user.jwt(),
                        contactBody(contactName)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        customerOfAId = read(mockMvc.perform(in(tenantA, post(RELATIONS_PATH), user.jwt(),
                        relationBody("Customer of A " + suffix, "CUSTOMER")))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        relationOfBId = read(mockMvc.perform(in(tenantB, post(RELATIONS_PATH), user.jwt(),
                        relationBody("Relation of B " + suffix, "LEAD")))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private static String relationPath(String id, String suffix) {
        return RELATIONS_PATH + "/" + id + suffix;
    }

    private static String relationBody(String name, String type) {
        return "{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}";
    }

    private static String contactBody(String name) {
        return "{\"name\":\"" + name + "\",\"kind\":\"PERSON\",\"decisionMaker\":false,\"mainPersonContact\":false,\"mainBusinessContact\":false,\"email\":\"contact@example.com\"}";
    }

    private List<String> readPathsOfRelationOfA() {
        return List.of(
                relationPath(relationId, ""),
                relationPath(relationId, "/contacts"));
    }

    /** Asserts the not-found answer with the given code and that no data of A is in the body. */
    private void assertAnswersNotFound(MockHttpServletRequestBuilder request, String code) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains(code)
                .doesNotContain(relationName)
                .doesNotContain(contactName)
                .doesNotContain(tenantA.id());
    }

    @Test
    void readsWithHeaderOfAnotherOrganization_relationAndContacts_answer404WithoutData() throws Exception {
        for (String path : readPathsOfRelationOfA()) {
            assertAnswersNotFound(in(tenantB, get(path), user.jwt(), null), RELATION_NOT_FOUND_CODE);
        }
    }

    @Test
    void readsWithHeaderOfTheOwningOrganization_sameUser_answer200WithData() throws Exception {
        MvcResult relation = mockMvc.perform(in(tenantA, get(relationPath(relationId, "")), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult contacts = mockMvc.perform(in(tenantA, get(relationPath(relationId, "/contacts")), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(relation, "$.data.name")).isEqualTo(relationName);
        assertThat(contacts.getResponse().getContentAsString()).contains(contactId).contains(contactName);
    }

    @Test
    void listsAndBattlefieldWithHeaderOfAnotherOrganization_showNothingOfTheRelationOfA() throws Exception {
        String search = "&search=" + nameKey;
        for (String path : List.of(
                RELATIONS_PATH + "?type=LEAD",
                RELATIONS_PATH + "?type=LEAD" + search,
                RELATIONS_PATH + "?type=LEAD&stage=NEW&stage=QUALIFIED&stage=CONTACTED&stage=WARM&stage=PROPOSAL"
                        + "&stage=LOST&stage=PARKED",
                RELATIONS_PATH + "?type=CUSTOMER",
                RELATIONS_PATH + "?type=SUPPLIER",
                BATTLEFIELD_PATH)) {
            MvcResult result = mockMvc.perform(in(tenantB, get(path), user.jwt(), null))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentAsString()).as(path)
                    .doesNotContain(relationId)
                    .doesNotContain(customerOfAId)
                    .doesNotContain(relationName);
        }

        MvcResult ownList = mockMvc.perform(in(tenantA, get(RELATIONS_PATH + "?type=LEAD" + search), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult ownBattlefield = mockMvc.perform(in(tenantA, get(BATTLEFIELD_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        assertThat(ownList.getResponse().getContentAsString()).contains(relationId);
        assertThat(ownBattlefield.getResponse().getContentAsString()).contains(relationId).contains(customerOfAId)
                .doesNotContain(relationOfBId);
    }

    @Test
    void writesWithHeaderOfAnotherOrganization_answer404AndChangeNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(relationPath(relationId, "")), user.jwt(),
                relationBody("Hijacked", "LEAD")), RELATION_NOT_FOUND_CODE);
        assertAnswersNotFound(in(tenantB, patch(relationPath(relationId, "/stage")), user.jwt(),
                "{\"stage\":\"LOST\"}"), RELATION_NOT_FOUND_CODE);
        assertAnswersNotFound(in(tenantB, patch(relationPath(relationId, "/type")), user.jwt(),
                "{\"type\":\"CUSTOMER\"}"), RELATION_NOT_FOUND_CODE);
        assertAnswersNotFound(in(tenantB, post(relationPath(relationId, "/contacts")), user.jwt(),
                contactBody("Intruder")), RELATION_NOT_FOUND_CODE);
        assertAnswersNotFound(in(tenantB, put(relationPath(relationId, "/contacts/" + contactId)), user.jwt(),
                contactBody("Hijacked")), RELATION_NOT_FOUND_CODE);
        assertAnswersNotFound(in(tenantB, delete(relationPath(relationId, "/contacts/" + contactId)), user.jwt(),
                null), RELATION_NOT_FOUND_CODE);
        assertAnswersNotFound(in(tenantB, delete(relationPath(relationId, "")), user.jwt(), null),
                RELATION_NOT_FOUND_CODE);

        assertRelationOfAUnchanged();
    }

    @Test
    void childOfAnotherOrganization_underOwnRelation_answers404AndChangesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(relationPath(relationOfBId, "/contacts/" + contactId)), user.jwt(),
                contactBody("Hijacked")), CONTACT_NOT_FOUND_CODE);
        assertAnswersNotFound(in(tenantB, delete(relationPath(relationOfBId, "/contacts/" + contactId)), user.jwt(),
                null), CONTACT_NOT_FOUND_CODE);

        assertRelationOfAUnchanged();
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?::uuid", relationOfBId)).isZero();
    }

    @Test
    void createInOrganizationB_withOwnerOnlyInOrganizationA_answers409AndStoresNothing() throws Exception {
        Person onlyInA = signIn(tenantA, "onlya");
        long relationsOfBBefore = count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId());

        MvcResult result = mockMvc.perform(in(tenantB, post(RELATIONS_PATH), user.jwt(),
                        "{\"name\":\"Owned from A\",\"type\":\"LEAD\",\"ownerUserId\":\"" + onlyInA.userId() + "\"}"))
                .andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains(OWNER_NOT_MEMBER_CODE);
        assertThat(count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId())).isEqualTo(relationsOfBBefore);
    }

    @Test
    void deleteWithHeaderOfTheOwningOrganization_byAnAdmin_removesRelationWithContacts() throws Exception {
        mockMvc.perform(in(tenantA, delete(relationPath(relationId, "")), jwtOf(tenantA.admin().getUser()), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid", relationId)).isZero();
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?::uuid", relationId)).isZero();
        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid", relationOfBId)).isEqualTo(1L);
    }

    /** The relation of A and its one contact are exactly as the set-up wrote them. */
    private void assertRelationOfAUnchanged() {
        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid AND organization_id = ? AND name = ? "
                        + "AND type = 'LEAD'::otak_relation_type_enum AND stage = 'NEW'::otak_pipeline_stage_enum",
                relationId, tenantA.organization().getId(), relationName)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?::uuid", relationId)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND name = ?", contactId, contactName))
                .isEqualTo(1L);
    }
}
