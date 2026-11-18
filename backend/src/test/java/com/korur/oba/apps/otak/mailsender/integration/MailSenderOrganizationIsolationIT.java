package com.korur.oba.apps.otak.mailsender.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the migrated PostgreSQL schema and the real security chain, that the mail
 * senders of an organization are closed to every other organization: a user who sits in two
 * organizations reaches a sender only with the header of the organization that owns it; every
 * other header answers 404 without its data and changes nothing.
 */
class MailSenderOrganizationIsolationIT extends AbstractTenantIT {

    private static final String MAIL_SENDER_NOT_FOUND_CODE = "OTK_NOT_006";
    private static final String MAIL_SENDERS_PATH = "/otak/mail-senders";
    private static final String INSERT_MAIL_SENDER = "INSERT INTO otak.mail_senders "
            + "(mail_address, added_by, organization_id, type) VALUES (?, ?, ?, 'PERSONAL') RETURNING id";

    private Tenant tenantA;
    private Tenant tenantB;
    private Person user;
    private String mailAddress;
    private String mailSenderId;

    @BeforeEach
    void setUpTenantsAndMailSenderOfA() throws Exception {
        tenantA = newTenant();
        tenantB = newTenant();
        // signIn(tenant, ...) would add a MEMBER row in A; the user signs in without one and joins both as ADMIN.
        user = signIn("kc-" + uniqueSuffix(), "otaksender" + uniqueSuffix().substring(0, 6) + "@" + tenantA.domain());
        // ADMIN in both, so even the ADMIN-only delete reaches the lookup with the header of B.
        createMember(tenantA.organization(), user.user(), OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);
        createMember(tenantB.organization(), user.user(), OrganizationMemberRole.ADMIN, OrganizationMemberStatus.ACTIVE);

        mailAddress = "sender" + uniqueSuffix() + "@" + tenantA.domain();
        mailSenderId = jdbcTemplate.queryForObject(INSERT_MAIL_SENDER, UUID.class, mailAddress,
                user.user().getId(), tenantA.organization().getId()).toString();
    }

    @Test
    void deleteWithHeaderOfAnotherOrganization_answers404AndDeletesNothing() throws Exception {
        MvcResult result = mockMvc.perform(in(tenantB, delete(MAIL_SENDERS_PATH + "/" + mailSenderId), user.jwt(), null))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains(MAIL_SENDER_NOT_FOUND_CODE)
                .doesNotContain(mailAddress);
        assertThat(count("SELECT count(*) FROM otak.mail_senders WHERE id = ?::uuid", mailSenderId)).isEqualTo(1L);
    }

    @Test
    void listWithHeaderOfAnotherOrganization_showsNothingOfTheSenderOfA() throws Exception {
        MvcResult other = mockMvc.perform(in(tenantB, get(MAIL_SENDERS_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();
        MvcResult own = mockMvc.perform(in(tenantA, get(MAIL_SENDERS_PATH), user.jwt(), null))
                .andExpect(status().isOk()).andReturn();

        assertThat(other.getResponse().getContentAsString()).doesNotContain(mailSenderId).doesNotContain(mailAddress);
        assertThat(own.getResponse().getContentAsString()).contains(mailSenderId).contains(mailAddress);
    }

    @Test
    void deleteWithHeaderOfTheOwningOrganization_sameUser_answers200() throws Exception {
        mockMvc.perform(in(tenantA, delete(MAIL_SENDERS_PATH + "/" + mailSenderId), user.jwt(), null))
                .andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM otak.mail_senders WHERE id = ?::uuid", mailSenderId)).isZero();
    }
}
