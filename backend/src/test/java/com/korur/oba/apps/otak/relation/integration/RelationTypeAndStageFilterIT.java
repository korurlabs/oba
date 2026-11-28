package com.korur.oba.apps.otak.relation.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 2. Third-party
import com.jayway.jsonpath.JsonPath;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.apps.otak.relation.entity.Relation;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.apps.otak.relation.repository.RelationRepository;

/**
 * Proves, against the migrated PostgreSQL schema, that the relations list is one list of every
 * type: the repeated type parameter narrows it to several types, the stage filter applies
 * without a type and never matches a supplier, and with no stage every relation shows unless the
 * caller asks to hide the leads at LOST and PARKED, which then leave whatever the type filter.
 */
class RelationTypeAndStageFilterIT extends AbstractTenantIT {

    private static final String RELATIONS_PATH = "/otak/relations";

    @Autowired
    private RelationRepository relationRepository;

    private Tenant tenant;
    private Person user;
    private String suffix;
    private String leadNew;
    private String leadLost;
    private String leadParked;
    private String customerClosed;
    private String customerDirect;
    private String supplier;

    @BeforeEach
    void setUpOneRelationOfEachKind() throws Exception {
        tenant = newTenant();
        user = signIn(tenant, "otak");
        suffix = uniqueSuffix();
        leadNew = save(tenant, RelationType.LEAD, PipelineStage.NEW);
        leadLost = save(tenant, RelationType.LEAD, PipelineStage.LOST);
        leadParked = save(tenant, RelationType.LEAD, PipelineStage.PARKED);
        customerClosed = save(tenant, RelationType.CUSTOMER, PipelineStage.CLOSED);
        customerDirect = save(tenant, RelationType.CUSTOMER, PipelineStage.DIRECT);
        supplier = save(tenant, RelationType.SUPPLIER, null);
    }

    private String save(Tenant owner, RelationType type, PipelineStage stage) {
        Relation relation = relationRepository.saveAndFlush(Relation.builder()
                .name("Filter " + suffix + " " + type + " " + stage)
                .type(type)
                .stage(stage)
                .organizationId(owner.organization().getId())
                .build());
        return relation.getId().toString();
    }

    private List<String> list(String filter) throws Exception {
        String body = mockMvc.perform(in(tenant, get(RELATIONS_PATH + "?search=" + suffix + filter), user.jwt(), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[*].id");
    }

    @Test
    void listWithoutFilters_showsEveryRelation() throws Exception {
        assertThat(list("")).containsExactlyInAnyOrder(
                leadNew, leadLost, leadParked, customerClosed, customerDirect, supplier);
    }

    @Test
    void listHidingInactiveLeads_showsEveryTypeButTheInactiveLeads() throws Exception {
        assertThat(list("&hideInactiveLeads=true"))
                .containsExactlyInAnyOrder(leadNew, customerClosed, customerDirect, supplier);
    }

    @Test
    void listWithLeadTypeHidingInactiveLeads_showsOnlyTheActiveLead() throws Exception {
        assertThat(list("&type=LEAD&hideInactiveLeads=true")).containsExactly(leadNew);
    }

    @Test
    void listWithStagesHidingInactiveLeads_ignoresTheFlag() throws Exception {
        assertThat(list("&stage=LOST&hideInactiveLeads=true")).containsExactly(leadLost);
    }

    @Test
    void listWithRepeatedType_showsThoseTypes() throws Exception {
        assertThat(list("&type=LEAD&type=SUPPLIER"))
                .containsExactlyInAnyOrder(leadNew, leadLost, leadParked, supplier);
    }

    @Test
    void listWithCustomerType_showsEveryCustomer() throws Exception {
        assertThat(list("&type=CUSTOMER")).containsExactlyInAnyOrder(customerClosed, customerDirect);
    }

    @Test
    void listWithStagesWithoutType_showsExactlyThoseStages() throws Exception {
        assertThat(list("&stage=LOST&stage=DIRECT")).containsExactlyInAnyOrder(leadLost, customerDirect);
    }

    @Test
    void listWithInactiveStagesAndLeadType_showsLostAndParkedLeads() throws Exception {
        assertThat(list("&type=LEAD&stage=LOST&stage=PARKED")).containsExactlyInAnyOrder(leadLost, leadParked);
    }

    @Test
    void listWithStageOfAnotherType_showsNothing() throws Exception {
        assertThat(list("&type=CUSTOMER&stage=NEW")).isEmpty();
    }

    @Test
    void listWithSupplierTypeAndStage_showsNothing() throws Exception {
        assertThat(list("&type=SUPPLIER&stage=NEW")).isEmpty();
    }

    @Test
    void listWithUnknownType_answers422() throws Exception {
        mockMvc.perform(in(tenant, get(RELATIONS_PATH + "?type=PARTNER"), user.jwt(), null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listOfAnotherOrganization_doesNotShowTheseRelations() throws Exception {
        Tenant other = newTenant();
        Person otherUser = signIn(other, "otak");

        String body = mockMvc.perform(in(other, get(RELATIONS_PATH + "?search=" + suffix), otherUser.jwt(), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.data[*].id");
        assertThat(ids).isEmpty();
    }
}
