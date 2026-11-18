package com.korur.oba.apps.otak.event.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the events of
 * an organization are closed to every other organization: a user who sits in two organizations
 * reaches an event only with the header of the organization that owns it; every other header
 * answers 404 without its data and changes nothing.
 */
class EventOrganizationIsolationIT extends AbstractTenantIT {

    private static final String EVENT_NOT_FOUND_CODE = "OTK_NOT_009";
    private static final String EVENTS_PATH = "/otak/events";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String eventName;
    private String eventId;

    @BeforeEach
    void setUpTenantsAndEventOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        user = signIn(tenantA, "otakevent");
        // ADMIN in B, so even the ADMIN-only delete reaches the lookup with the header of B.
        createMember(tenantB.organization(), user.user(), OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

        eventName = "Event of A " + uniqueSuffix();
        eventId = read(mockMvc.perform(in(tenantA, post(EVENTS_PATH), user.jwt(), eventBody(eventName)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private static String eventBody(String name) {
        return "{\"name\":\"" + name + "\",\"type\":\"EXPO\",\"startDate\":\"2026-11-19\"}";
    }

    private void assertAnswersNotFound(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains(EVENT_NOT_FOUND_CODE)
                .doesNotContain(eventName);
    }

    @Test
    void getWithHeaderOfAnotherOrganization_answers404WithoutData() throws Exception {
        assertAnswersNotFound(in(tenantB, get(EVENTS_PATH + "/" + eventId), user.jwt(), null));
    }

    @Test
    void putWithHeaderOfAnotherOrganization_answers404AndChangesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, put(EVENTS_PATH + "/" + eventId), user.jwt(), eventBody("Hijacked")));

        assertThat(count("SELECT count(*) FROM otak.events WHERE id = ?::uuid AND name = ?", eventId, eventName))
                .isEqualTo(1L);
    }

    @Test
    void deleteWithHeaderOfAnotherOrganization_answers404AndDeletesNothing() throws Exception {
        assertAnswersNotFound(in(tenantB, delete(EVENTS_PATH + "/" + eventId), user.jwt(), null));

        assertThat(count("SELECT count(*) FROM otak.events WHERE id = ?::uuid", eventId)).isEqualTo(1L);
    }

    @Test
    void listWithHeaderOfAnotherOrganization_showsNothingOfTheEventOfA() throws Exception {
        MvcResult other = mockMvc.perform(in(tenantB, get(EVENTS_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult own = mockMvc.perform(in(tenantA, get(EVENTS_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(other.getResponse().getContentAsString()).doesNotContain(eventId).doesNotContain(eventName);
        assertThat(own.getResponse().getContentAsString()).contains(eventId).contains(eventName);
    }

    @Test
    void relationCreateWithEventOfAnotherOrganization_answers404AndStoresNothing() throws Exception {
        long before = count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId());

        assertAnswersNotFound(in(tenantB, post("/otak/relations"), user.jwt(),
                "{\"name\":\"Linked\",\"type\":\"LEAD\",\"eventId\":\"" + eventId + "\"}"));

        assertThat(count("SELECT count(*) FROM otak.relations WHERE organization_id = ?",
                tenantB.organization().getId())).isEqualTo(before);
    }

    @Test
    void relationUpdateWithEventOfAnotherOrganization_answers404AndKeepsTheRelation() throws Exception {
        String relationId = read(mockMvc.perform(in(tenantB, post("/otak/relations"), user.jwt(),
                "{\"name\":\"Plain\",\"type\":\"LEAD\"}")).andExpect(status().isCreated()).andReturn(), "$.data.id");

        assertAnswersNotFound(in(tenantB, put("/otak/relations/" + relationId), user.jwt(),
                "{\"name\":\"Plain\",\"eventId\":\"" + eventId + "\"}"));

        assertThat(count("SELECT count(*) FROM otak.relations WHERE id = ?::uuid AND event_id IS NULL", relationId))
                .isEqualTo(1L);
    }

    @Test
    void getWithHeaderOfTheOwningOrganization_sameUser_answers200WithData() throws Exception {
        MvcResult result = mockMvc.perform(in(tenantA, get(EVENTS_PATH + "/" + eventId), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(read(result, "$.data.name")).isEqualTo(eventName);
    }
}
