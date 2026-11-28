package com.korur.oba.apps.otak.campaign.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 4. Internal
import com.korur.oba.apps.otak.support.AbstractOtakDocumentsIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Proves, through the real security chain and the migrated schema, the access of the campaign
 * endpoints: a GUEST reads and writes nothing, a MEMBER lists, reads, creates and updates but
 * cannot delete, an ADMIN deletes, and an organization other than the owner's answers 404
 * without data and changes nothing.
 */
class CampaignAccessIT extends AbstractOtakDocumentsIT {

    private static final String CAMPAIGNS_PATH = "/otak/campaigns";

    private Tenant tenant;
    private Tenant otherTenant;
    private Person member;
    private Person admin;
    private Person guest;
    private Person otherAdmin;
    private String campaignId;
    private String name;

    static String body(String name) {
        return "{\"name\":\"" + name + "\",\"description\":\"Night watch\"}";
    }

    @BeforeEach
    void setUp() throws Exception {
        tenant = newTenant();
        otherTenant = newTenant();
        member = signInAs(tenant, OrganizationMemberRole.MEMBER, "campmember");
        admin = signInAs(tenant, OrganizationMemberRole.ADMIN, "campadmin");
        guest = signInAs(tenant, OrganizationMemberRole.GUEST, "campguest");
        otherAdmin = signInAs(otherTenant, OrganizationMemberRole.ADMIN, "campother");
        name = "Campaign " + uniqueSuffix();
        campaignId = read(mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), member.jwt(), body(name)))
                .andExpect(status().isCreated()).andReturn(), "$.data.id");
    }

    private long campaignsOfTenant() {
        return count("SELECT count(*) FROM otak.campaigns WHERE organization_id = ?::uuid", tenant.id());
    }

    @Test
    void member_listDetailCreateAndUpdate_succeed() throws Exception {
        mockMvc.perform(in(tenant, get(CAMPAIGNS_PATH), member.jwt(), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(campaignId))
                .andExpect(jsonPath("$.data[0].contentCount").value(0));
        mockMvc.perform(in(tenant, get(CAMPAIGNS_PATH + "/" + campaignId), member.jwt(), null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.description").value("Night watch"));
        mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), member.jwt(), body("Second " + uniqueSuffix())))
                .andExpect(status().isCreated());
        mockMvc.perform(in(tenant, put(CAMPAIGNS_PATH + "/" + campaignId), member.jwt(),
                "{\"name\":\"Renamed\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-31\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.endDate").value("2026-12-31"));

        assertThat(count("SELECT count(*) FROM otak.campaigns WHERE id = ?::uuid AND name = 'Renamed' "
                + "AND description IS NULL AND start_date = DATE '2026-12-01'", campaignId)).isEqualTo(1L);
    }

    @Test
    void guest_everyEndpoint_answers403AndChangesNothing() throws Exception {
        mockMvc.perform(in(tenant, get(CAMPAIGNS_PATH), guest.jwt(), null)).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, get(CAMPAIGNS_PATH + "/" + campaignId), guest.jwt(), null))
                .andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), guest.jwt(), body("By guest")))
                .andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, put(CAMPAIGNS_PATH + "/" + campaignId), guest.jwt(),
                "{\"name\":\"Hijacked\"}")).andExpect(status().isForbidden());
        mockMvc.perform(in(tenant, delete(CAMPAIGNS_PATH + "/" + campaignId), guest.jwt(), null))
                .andExpect(status().isForbidden());

        assertThat(campaignsOfTenant()).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM otak.campaigns WHERE id = ?::uuid AND name = ?", campaignId, name))
                .isEqualTo(1L);
    }

    @Test
    void member_delete_answers403AndDeletesNothing() throws Exception {
        mockMvc.perform(in(tenant, delete(CAMPAIGNS_PATH + "/" + campaignId), member.jwt(), null))
                .andExpect(status().isForbidden());

        assertThat(campaignsOfTenant()).isEqualTo(1L);
    }

    @Test
    void admin_delete_answers200AndRemovesTheCampaign() throws Exception {
        mockMvc.perform(in(tenant, delete(CAMPAIGNS_PATH + "/" + campaignId), admin.jwt(), null))
                .andExpect(status().isOk());

        assertThat(campaignsOfTenant()).isZero();
        mockMvc.perform(in(tenant, get(CAMPAIGNS_PATH + "/" + campaignId), admin.jwt(), null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("OTK_NOT_012"));
    }

    @Test
    void otherOrganization_getPutDelete_answer404AndChangeNothing() throws Exception {
        mockMvc.perform(in(otherTenant, get(CAMPAIGNS_PATH + "/" + campaignId), otherAdmin.jwt(), null))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("OTK_NOT_012"));
        mockMvc.perform(in(otherTenant, put(CAMPAIGNS_PATH + "/" + campaignId), otherAdmin.jwt(),
                "{\"name\":\"Hijacked\"}")).andExpect(status().isNotFound());
        mockMvc.perform(in(otherTenant, delete(CAMPAIGNS_PATH + "/" + campaignId), otherAdmin.jwt(), null))
                .andExpect(status().isNotFound());

        assertThat(count("SELECT count(*) FROM otak.campaigns WHERE id = ?::uuid AND name = ?", campaignId, name))
                .isEqualTo(1L);
    }

    @Test
    void otherOrganization_list_showsNothingOfTheCampaign() throws Exception {
        mockMvc.perform(in(otherTenant, get(CAMPAIGNS_PATH), otherAdmin.jwt(), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void create_sameNameDifferentCase_answers409() throws Exception {
        mockMvc.perform(in(tenant, post(CAMPAIGNS_PATH), member.jwt(), body(name.toUpperCase())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("OTK_STA_015"));

        assertThat(campaignsOfTenant()).isEqualTo(1L);
    }
}
