package com.korur.oba.apps.kazan.dailyupdate.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.identity.entity.User;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The missing daily updates report and its nudge follow the Kazan grant: a sender without Kazan is
 * refused, a recipient without Kazan is reported as not found, and a member without Kazan is neither
 * listed nor counted as expected.
 */
class DailyUpdateKazanAccessIT extends AbstractTenantIT {

    private static final String APP_ACCESS_DENIED = "APP_SEC_001";
    private static final String NUDGE_RECIPIENT_NOT_FOUND = "DLY_NOT_001";

    private Tenant tenant;
    private User owner;
    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        tenant = newTenant();
        owner = tenant.owner().getUser();
        admin = tenant.admin().getUser();
        member = tenant.member().getUser();
    }

    private void revokeKazanFromEveryone() {
        jdbcTemplate.update("UPDATE shrine.organization_app_grants SET deleted = TRUE "
                + "WHERE organization_id = ? AND app = 'KAZAN'::shrine_app_grant_app_enum", tenant.organization().getId());
    }

    private void grantKazanDirectly(User user) {
        jdbcTemplate.update("INSERT INTO shrine.organization_app_grants (organization_id, app, user_id) "
                + "VALUES (?, 'KAZAN'::shrine_app_grant_app_enum, ?)", tenant.organization().getId(), user.getId());
    }

    /** Memberships created now would join after the deadline of a past day, so they are moved into the past. */
    private String lastWorkdayWithMembersJoinedBefore() {
        jdbcTemplate.update("UPDATE shrine.organization_members SET created_at = now() - interval '60 days' "
                + "WHERE organization_id = ?", tenant.organization().getId());
        LocalDate day = LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.minusDays(1);
        }
        return day.toString();
    }

    @Test
    void nudge_senderAndRecipientWithKazan_returns201() throws Exception {
        mockMvc.perform(in(tenant, post("/kazan/updates/missing/" + member.getId() + "/nudge"), jwtOf(admin), null))
                .andExpect(status().isCreated());
    }

    @Test
    void nudge_senderWithoutKazan_returns403AppAccessDenied() throws Exception {
        revokeKazanFromEveryone();

        mockMvc.perform(in(tenant, post("/kazan/updates/missing/" + member.getId() + "/nudge"), jwtOf(admin), null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(APP_ACCESS_DENIED));
    }

    @Test
    void nudge_recipientWithoutKazan_returns404NudgeRecipientNotFound() throws Exception {
        revokeKazanFromEveryone();
        grantKazanDirectly(admin);

        mockMvc.perform(in(tenant, post("/kazan/updates/missing/" + member.getId() + "/nudge"), jwtOf(admin), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(NUDGE_RECIPIENT_NOT_FOUND));
    }

    @Test
    void missingReport_everyMemberHasKazan_listsAndCountsAllExpectedMembers() throws Exception {
        String day = lastWorkdayWithMembersJoinedBefore();

        mockMvc.perform(in(tenant, get("/kazan/updates/missing").param("organizationId", tenant.id())
                        .param("date", day), jwtOf(owner), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedCount").value(3))
                .andExpect(jsonPath("$.data.members", hasSize(3)));
    }

    @Test
    void missingReport_memberWithoutKazan_isLeftOutOfListAndExpectedCount() throws Exception {
        revokeKazanFromEveryone();
        grantKazanDirectly(admin);
        String day = lastWorkdayWithMembersJoinedBefore();

        mockMvc.perform(in(tenant, get("/kazan/updates/missing").param("organizationId", tenant.id())
                        .param("date", day), jwtOf(owner), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedCount").value(2))
                .andExpect(jsonPath("$.data.members", hasSize(2)))
                .andExpect(jsonPath("$.data.members[*].email",
                        containsInAnyOrder(owner.getEmail(), admin.getEmail())));
    }
}
