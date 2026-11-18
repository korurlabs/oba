package com.korur.oba.apps.otak.mailsender.integration;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.support.AbstractTenantIT;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.enums.EmailType;
import com.korur.oba.apps.otak.mailsender.repository.MailSenderRepository;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * The mail sender schema refuses rows the application must never write: a missing adder, and
 * a duplicate mail address inside one organization with case ignored; it keeps no display
 * name; and deleting an organization removes its mail senders.
 */
class MailSenderMigrationIT extends AbstractTenantIT {

    private static final String INSERT_MAIL_SENDER = "INSERT INTO otak.mail_senders "
            + "(mail_address, added_by, organization_id, type) VALUES (?, ?, ?, 'PERSONAL')";

    @Autowired
    private MailSenderRepository mailSenderRepository;

    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUpTenants() {
        tenantA = newTenant();
        tenantB = newTenant();
    }

    private static String mailAddress(Tenant tenant) {
        return "sender" + uniqueSuffix() + "@" + tenant.domain();
    }

    private static MailSender mailSender(Tenant tenant, String mailAddress) {
        return MailSender.builder()
                .mailAddress(mailAddress)
                .type(EmailType.PERSONAL)
                .addedBy(UUID.randomUUID())
                .organizationId(tenant.organization().getId())
                .build();
    }

    @Test
    void mailSendersTable_hasNoDisplayNameColumn() {
        assertEquals(0L, count("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = 'otak' AND table_name = 'mail_senders' AND column_name = 'display_name'"));
    }

    @Test
    void insertMailSender_nullAddedBy_isRejectedByTheNotNullConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(INSERT_MAIL_SENDER,
                mailAddress(tenantA), null, tenantA.organization().getId()));
    }

    @Test
    void typeColumn_isNotNullWithoutDefault() {
        assertEquals(1L, count("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'otak' "
                + "AND table_name = 'mail_senders' AND column_name = 'type' AND is_nullable = 'NO' "
                + "AND column_default IS NULL"));
    }

    @Test
    void insertMailSender_missingType_isRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.mail_senders (mail_address, added_by, organization_id) VALUES (?, ?, ?)",
                mailAddress(tenantA), UUID.randomUUID(), tenantA.organization().getId()));
    }

    @ParameterizedTest
    @EnumSource(EmailType.class)
    void insertMailSender_eachType_isAcceptedAndReadBack(EmailType type) {
        jdbcTemplate.update("INSERT INTO otak.mail_senders (mail_address, added_by, organization_id, type) "
                        + "VALUES (?, ?, ?, ?::otak_email_type_enum)",
                mailAddress(tenantA), UUID.randomUUID(), tenantA.organization().getId(), type.name());

        assertEquals(1L, count("SELECT count(*) FROM otak.mail_senders WHERE organization_id = ? AND type::text = ?",
                tenantA.organization().getId(), type.name()));
    }

    @Test
    void insertMailSender_unknownType_isRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO otak.mail_senders (mail_address, added_by, organization_id, type) "
                        + "VALUES (?, ?, ?, 'OTHER')",
                mailAddress(tenantA), UUID.randomUUID(), tenantA.organization().getId()));
    }

    @Test
    void insertMailSender_everyFieldGiven_isAccepted() {
        jdbcTemplate.update(INSERT_MAIL_SENDER, mailAddress(tenantA), UUID.randomUUID(),
                tenantA.organization().getId());

        assertEquals(1L, count("SELECT count(*) FROM otak.mail_senders WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void saveMailSender_sameAddressDifferentCaseInOneOrganization_isRejected() {
        String mailAddress = mailAddress(tenantA);
        mailSenderRepository.saveAndFlush(mailSender(tenantA, mailAddress));

        assertThrows(DataIntegrityViolationException.class,
                () -> mailSenderRepository.saveAndFlush(mailSender(tenantA, mailAddress.toUpperCase())));
        assertEquals(1L, count("SELECT count(*) FROM otak.mail_senders WHERE organization_id = ?",
                tenantA.organization().getId()));
    }

    @Test
    void saveMailSender_sameAddressInTwoOrganizations_isAccepted() {
        String mailAddress = mailAddress(tenantA);

        mailSenderRepository.saveAndFlush(mailSender(tenantA, mailAddress));
        mailSenderRepository.saveAndFlush(mailSender(tenantB, mailAddress));

        assertEquals(1L, count("SELECT count(*) FROM otak.mail_senders WHERE organization_id = ?",
                tenantB.organization().getId()));
    }

    @Test
    void deleteOrganization_removesItsMailSendersAndKeepsThoseOfOthers() {
        Organization doomed = createOrganization(OrganizationStatus.ACTIVE);
        UUID doomedId = doomed.getId();
        jdbcTemplate.update(INSERT_MAIL_SENDER, mailAddress(tenantA), UUID.randomUUID(), doomedId);
        mailSenderRepository.saveAndFlush(mailSender(tenantA, mailAddress(tenantA)));

        jdbcTemplate.update("DELETE FROM shrine.organizations WHERE id = ?", doomedId);

        assertEquals(0L, count("SELECT count(*) FROM otak.mail_senders WHERE organization_id = ?", doomedId));
        assertEquals(1L, count("SELECT count(*) FROM otak.mail_senders WHERE organization_id = ?",
                tenantA.organization().getId()));
    }
}
