package com.korur.oba.apps.otak.mail.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;

/**
 * Repository of the {@link Mail} rows in otak.mails, the only place that writes the table. Other
 * domains reach mails through {@link com.korur.oba.apps.otak.mail.service.MailService} and
 * {@link com.korur.oba.apps.otak.mail.service.MailQueryService}.
 *
 * Organization scoping: the lookup by id also takes the organization id, so a mail of another
 * organization is not found. The org-free lookup of the access check and the outreach status read
 * take only a mail id; their callers compare the organization themselves.
 *
 * Used by {@link com.korur.oba.apps.otak.mail.service.impl.MailServiceImpl},
 * {@link com.korur.oba.apps.otak.mail.service.impl.MailQueryServiceImpl} and
 * {@link com.korur.oba.apps.otak.mail.validator.MailValidator}.
 */
@Repository
public interface MailRepository extends JpaRepository<Mail, UUID> {

    /**
     * Reads one mail within an organization, so a mail of another organization is not found.
     *
     * Called by {@link com.korur.oba.apps.otak.mail.validator.MailValidator#ensureMailExists(UUID, UUID)}.
     *
     * @param id             the mail identifier
     * @param organizationId the organization that owns the mail
     * @return the mail, or empty when the organization has no mail with the id
     */
    Optional<Mail> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Reads the ids of the organization's mails whose relation is the given one.
     *
     * Called by {@link com.korur.oba.apps.otak.mail.service.impl.MailQueryServiceImpl#getIdsByRelation}.
     *
     * @param organizationId the organization that owns the mails
     * @param relationId     the relation of the mails
     * @return the mail ids, in no particular order; empty when the relation has no mail
     */
    @Query("SELECT m.id FROM Mail m WHERE m.organizationId = :organizationId AND m.relationId = :relationId")
    List<UUID> findIdsByOrganizationIdAndRelationId(@Param("organizationId") UUID organizationId,
                                                    @Param("relationId") UUID relationId);

    /**
     * Reads the status of the outreach that sends a mail, with a row lock on that outreach held until the
     * transaction ends. A change of the mail's attachments reads the status this way, so the change runs
     * either wholly before a send's claim, which then waits for the lock, or after it and sees SENT.
     *
     * Native, because the mail domain reads only this one column of the outreach and the outreach domain
     * depends on this one. The status is cast to text so it maps onto
     * {@link com.korur.oba.apps.otak.outreach.enums.OutreachStatus} by name; outreaches_mail_id_uk serves the
     * lookup. It needs a read-write transaction, as FOR UPDATE does.
     *
     * Called by {@link com.korur.oba.apps.otak.mail.validator.MailValidator#ensureMailUnsent}.
     *
     * @param mailId the mail identifier
     * @return the outreach status; empty when no outreach sends the mail yet
     */
    @Query(value = """
            SELECT CAST(o.status AS TEXT) FROM otak.outreaches o
            WHERE o.mail_id = :mailId
            FOR UPDATE
            """, nativeQuery = true)
    Optional<OutreachStatus> findOutreachStatusForUpdate(@Param("mailId") UUID mailId);
}
