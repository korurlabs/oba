package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.apps.otak.relation.dto.projection.RelationMainContactProjection;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema, that the relations list filters by event and
 * lists every type when no type is given, that the main contact name of a row is read per
 * relation and per organization, that a contact update with the main flag switches the main
 * contact without a unique-index error, and that deleting an event keeps its relations.
 */
class RelationEventAndMainContactIT extends AbstractTenantIT {

    private static final String RELATIONS_PATH = "/otak/relations";
    private static final String EVENTS_PATH = "/otak/events";

    @Autowired
    private RelationRepository relationRepository;

    private Tenant tenant;
    private Person user;
    private String suffix;
    private String eventId;
    private String leadOfEventId;
    private String customerOfEventId;
    private String leadWithoutEventId;

    @BeforeEach
    void setUpRelationsOfAnEvent() throws Exception {
        tenant = newTenant();
        user = signIn(tenant, "otakevrel");
        suffix = uniqueSuffix();
        eventId = read(mockMvc.perform(in(tenant, post(EVENTS_PATH), user.jwt(),
                "{\"name\":\"Expo " + suffix + "\",\"type\":\"EXPO\",\"startDate\":\"2026-11-19\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
        leadOfEventId = createRelation("Lead of event", "LEAD", eventId);
        customerOfEventId = createRelation("Customer of event", "CUSTOMER", eventId);
        leadWithoutEventId = createRelation("Lead without event", "LEAD", null);
    }

    private String createRelation(String label, String type, String linkedEventId) throws Exception {
        String event = linkedEventId == null ? "" : ",\"eventId\":\"" + linkedEventId + "\"";
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH), user.jwt(),
                        "{\"name\":\"" + label + " " + suffix + "\",\"type\":\"" + type + "\""
                                + event + "}"))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private String createContact(String relationId, String name, Boolean main) throws Exception {
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH + "/" + relationId + "/contacts"), user.jwt(),
                        contactJson(name, "PERSON", Boolean.TRUE.equals(main), false)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    /** A contact body with every field the request requires, the flags as given. */
    private static String contactJson(String name, String kind, boolean mainPerson, boolean mainBusiness) {
        return "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"decisionMaker\":false,"
                + "\"mainPersonContact\":" + mainPerson + ",\"mainBusinessContact\":" + mainBusiness + "}";
    }

    private String createContactOfKind(String relationId, String name, String kind, String email, boolean main)
            throws Exception {
        return read(mockMvc.perform(in(tenant, post(RELATIONS_PATH + "/" + relationId + "/contacts"), user.jwt(),
                        contactJson(name, kind, "PERSON".equals(kind) && main, "BUSINESS".equals(kind) && main)
                                .replaceFirst("}$", ",\"email\":\"" + email + "\"}")))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private String list(String query) throws Exception {
        return mockMvc.perform(in(tenant, get(RELATIONS_PATH + "?search=" + suffix + query), user.jwt(), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void listByEventWithoutType_returnsEveryTypeOfThatEventOnly() throws Exception {
        String body = list("&eventId=" + eventId);

        assertThat(body).contains(leadOfEventId).contains(customerOfEventId).doesNotContain(leadWithoutEventId);
    }

    @Test
    void listWithoutTypeAndWithoutEvent_returnsEveryType() throws Exception {
        assertThat(list("")).contains(leadOfEventId).contains(customerOfEventId).contains(leadWithoutEventId);
    }

    @Test
    void listByUnknownEvent_returnsAnEmptyPage() throws Exception {
        assertThat(list("&eventId=" + UUID.randomUUID()))
                .doesNotContain(leadOfEventId).doesNotContain(customerOfEventId);
    }

    @Test
    void deleteEvent_keepsTheRelationAndClearsItsLink() throws Exception {
        Person admin = signIn("kc-" + uniqueSuffix(), "evadmin" + uniqueSuffix().substring(0, 6) + "@" + tenant.domain());
        createMember(tenant.organization(), admin.user(), OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

        mockMvc.perform(in(tenant, delete(EVENTS_PATH + "/" + eventId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid AND event_id IS NULL",
                leadOfEventId)).isEqualTo(1L);
    }

    @Test
    void findMainContacts_returnsTheMainPersonNamePerRelationWithAMain() throws Exception {
        createContact(leadOfEventId, "Anna Main", null);
        createContact(leadOfEventId, "Bob Other", false);
        createContact(customerOfEventId, "Cem Main", null);

        List<RelationMainContactProjection> result = relationRepository.findMainContacts(
                tenant.organization().getId(),
                List.of(UUID.fromString(leadOfEventId), UUID.fromString(customerOfEventId),
                        UUID.fromString(leadWithoutEventId)));

        assertThat(result).extracting(row -> row.getRelationId().toString() + ":" + row.getName())
                .containsExactlyInAnyOrder(leadOfEventId + ":Anna Main", customerOfEventId + ":Cem Main");
    }

    @Test
    void findMainContacts_ofAnotherOrganization_returnsNothing() throws Exception {
        createContact(leadOfEventId, "Anna Main", null);
        Tenant other = newTenant();

        List<RelationMainContactProjection> result = relationRepository.findMainContacts(
                other.organization().getId(), List.of(UUID.fromString(leadOfEventId)));

        assertThat(result).isEmpty();
    }

    @Test
    void findMainContacts_returnsNameAndEmailOfTheMainPersonAndTheEmailOfTheMainBusiness() throws Exception {
        createContactOfKind(leadOfEventId, "Jan de Vries", "PERSON", "jan@fabrikam.example", false);
        createContactOfKind(leadOfEventId, "Anna Other", "PERSON", "anna@fabrikam.example", false);
        createContactOfKind(leadOfEventId, "Info", "BUSINESS", "info@fabrikam.example", false);
        createContactOfKind(leadOfEventId, "Sales", "BUSINESS", "sales@fabrikam.example", false);

        List<RelationMainContactProjection> result = relationRepository.findMainContacts(
                tenant.organization().getId(), List.of(UUID.fromString(leadOfEventId)));

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(RelationMainContactProjection::getMainPersonContact)
                .extracting(RelationMainContactProjection::getName, RelationMainContactProjection::getEmail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Jan de Vries", "jan@fabrikam.example"));
        assertThat(result).filteredOn(row -> !row.getMainPersonContact())
                .extracting(RelationMainContactProjection::getEmail).containsExactly("info@fabrikam.example");
    }

    @Test
    void findMainContacts_relationWithoutAMainOfAKind_hasNoRowForThatKind() throws Exception {
        createContactOfKind(leadOfEventId, "Info", "BUSINESS", "info@fabrikam.example", false);

        List<RelationMainContactProjection> result = relationRepository.findMainContacts(
                tenant.organization().getId(), List.of(UUID.fromString(leadOfEventId)));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getMainPersonContact()).isFalse();
    }

    @Test
    void findMainContacts_mainContactsOfAnotherOrganization_areExcluded() throws Exception {
        createContactOfKind(leadOfEventId, "Info", "BUSINESS", "info@fabrikam.example", false);
        Tenant other = newTenant();

        List<RelationMainContactProjection> result = relationRepository.findMainContacts(
                other.organization().getId(), List.of(UUID.fromString(leadOfEventId)));

        assertThat(result).isEmpty();
    }

    @Test
    void relationsList_showsMainPersonNameAndEmailAndMainBusinessEmail() throws Exception {
        createContactOfKind(leadOfEventId, "Jan de Vries", "PERSON", "jan@fabrikam.example", false);
        createContactOfKind(leadOfEventId, "Info", "BUSINESS", "info@fabrikam.example", false);
        createContactOfKind(customerOfEventId, "Info", "BUSINESS", "info@customer.nl", false);

        String body = list("&eventId=" + eventId);

        String lead = "$.data[?(@.id=='" + leadOfEventId + "')]";
        String customer = "$.data[?(@.id=='" + customerOfEventId + "')]";
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(body, lead + ".mainPersonContactName"))
                .containsExactly("Jan de Vries");
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(body, lead + ".mainPersonContactEmail"))
                .containsExactly("jan@fabrikam.example");
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(body, lead + ".mainBusinessContactEmail"))
                .containsExactly("info@fabrikam.example");
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(body, customer + ".mainPersonContactName"))
                .containsExactly((String) null);
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(body, customer + ".mainBusinessContactEmail"))
                .containsExactly("info@customer.nl");
    }

    @Test
    void contactPutChangingTheKindOfTheMainPerson_promotesTheOldestPersonWithoutAConstraintError() throws Exception {
        String main = createContactOfKind(leadOfEventId, "Jan", "PERSON", "jan@fabrikam.example", false);
        String oldest = createContactOfKind(leadOfEventId, "Anna", "PERSON", "anna@fabrikam.example", false);

        mockMvc.perform(in(tenant, put(RELATIONS_PATH + "/" + leadOfEventId + "/contacts/" + main), user.jwt(),
                        contactJson("Jan", "BUSINESS", false, false)))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND kind = 'BUSINESS' "
                + "AND main_business_contact AND NOT main_person_contact", main)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND main_person_contact", oldest))
                .isEqualTo(1L);
    }

    @Test
    void contactPutWithTheFlagOfTheOtherKind_answers422MainFlagNotForKindAndChangesNothing() throws Exception {
        String person = createContactOfKind(leadOfEventId, "Jan", "PERSON", "jan@fabrikam.example", false);

        MvcResult result = mockMvc.perform(in(tenant, put(RELATIONS_PATH + "/" + leadOfEventId + "/contacts/"
                        + person), user.jwt(), contactJson("Jan", "PERSON", false, true)))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_VAL_013");
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND main_person_contact "
                + "AND NOT main_business_contact", person)).isEqualTo(1L);
    }

    @Test
    void contactPostWithTheFlagOfTheOtherKind_answers422MainFlagNotForKindAndStoresNothing() throws Exception {
        MvcResult result = mockMvc.perform(in(tenant, post(RELATIONS_PATH + "/" + leadOfEventId + "/contacts"),
                        user.jwt(), contactJson("Info", "BUSINESS", true, false)))
                .andExpect(status().isUnprocessableEntity()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("OTK_VAL_013");
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE relation_id = ?::uuid", leadOfEventId))
                .isZero();
    }

    @Test
    void contactDelete_ofTheMainBusiness_promotesTheOldestBusinessAndKeepsTheMainPerson() throws Exception {
        String person = createContactOfKind(leadOfEventId, "Jan", "PERSON", "jan@fabrikam.example", false);
        String firstBusiness = createContactOfKind(leadOfEventId, "Info", "BUSINESS", "info@fabrikam.example", false);
        String secondBusiness = createContactOfKind(leadOfEventId, "Sales", "BUSINESS", "sales@fabrikam.example",
                true);

        mockMvc.perform(in(tenant, delete(RELATIONS_PATH + "/" + leadOfEventId + "/contacts/" + secondBusiness),
                user.jwt(), null)).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND main_business_contact",
                firstBusiness)).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND main_person_contact",
                person)).isEqualTo(1L);
    }

    @Test
    void relationsList_showsTheMainContactNameAndNullWithoutContacts() throws Exception {
        createContact(leadOfEventId, "Anna Main", null);

        String body = list("&eventId=" + eventId + "&type=LEAD");

        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(body, "$.data[*].mainPersonContactName"))
                .containsExactly("Anna Main");
        String other = list("&type=LEAD");
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(other,
                "$.data[?(@.id=='" + leadWithoutEventId + "')].mainPersonContactName")).containsExactly((String) null);
    }

    @Test
    void contactPutWithMainTrue_switchesTheMainWithoutAUniqueIndexError() throws Exception {
        String first = createContact(leadOfEventId, "Anna First", null);
        String second = createContact(leadOfEventId, "Bob Second", null);

        mockMvc.perform(in(tenant, put(RELATIONS_PATH + "/" + leadOfEventId + "/contacts/" + second), user.jwt(),
                        contactJson("Bob Second", "PERSON", true, false)))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND main_person_contact", second))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND NOT main_person_contact", first))
                .isEqualTo(1L);
    }

    @Test
    void contactDelete_ofTheMain_promotesTheOldestRemaining() throws Exception {
        String first = createContact(leadOfEventId, "Anna First", null);
        String second = createContact(leadOfEventId, "Bob Second", true);

        mockMvc.perform(in(tenant, delete(RELATIONS_PATH + "/" + leadOfEventId + "/contacts/" + second),
                user.jwt(), null)).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.contacts WHERE id = ?::uuid AND main_person_contact", first))
                .isEqualTo(1L);
    }
}
