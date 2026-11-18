package com.korur.oba.apps.otak.outreach.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party
import jakarta.persistence.LockModeType;

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.entity.Outreach;

/**
 * Repository interface for {@link Outreach} entity database operations, the only place that writes the
 * otak.outreaches table. Other domains reach outreaches through
 * {@link com.korur.oba.apps.otak.outreach.service.OutreachQueryService}, never through this repository. The
 * one exception reads the table in SQL:
 * {@link com.korur.oba.apps.otak.mail.repository.MailRepository#findOutreachStatusForUpdate} locks the
 * outreach of a mail and reads its status, for the storage write check on the mail's attachments,
 * because the mail domain must not depend on the outreach domain.
 *
 * Organization scoping: every lookup by id also takes the organization id, so an outreach
 * of another organization is not found. The list is filtered through
 * {@link JpaSpecificationExecutor} with
 * {@link com.korur.oba.apps.otak.outreach.specification.OutreachSpecification#filterBy},
 * which always restricts it to one organization. The attachments belong to the outreach's
 * mail and are shrine attachments with the mail as parent.
 *
 * Sending: the status and the send columns of {@link Outreach} are not updatable through
 * the entity. Only {@link #claimForSending} and {@link #markSendFailed} change them, each
 * in one conditional UPDATE, so the send of
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} and the record as done of
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete}
 * are safe against a second send and against an edit that loaded the outreach earlier.
 * {@link #findByIdAndOrganizationIdForUpdate} takes the same row lock for edits and
 * deletes.
 *
 * Used by:
 * - {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl}
 * - {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl}
 * - {@link com.korur.oba.apps.otak.outreach.validator.OutreachValidator}
 */
@Repository
public interface OutreachRepository extends JpaRepository<Outreach, UUID>, JpaSpecificationExecutor<Outreach> {

    /**
     * Reads one outreach within an organization, so an outreach of another organization is not found.
     * Takes no lock: it serves reads and the checks a send makes before its claim.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.validator.OutreachValidator#ensureOutreachExists}.
     *
     * @param id             the outreach identifier
     * @param organizationId the organization that owns the outreach
     * @return the outreach, or empty when the organization has no outreach with the id
     */
    Optional<Outreach> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Reads one outreach within an organization with a row lock held until the transaction
     * ends. An edit or a delete reads the outreach this way, so its status check and its
     * write run either wholly before a send's claim or after it, and then see SENT.
     *
     * The lock is PESSIMISTIC_WRITE, a SELECT ... FOR UPDATE in PostgreSQL. It needs a
     * running transaction; the caller's transaction holds it until commit or rollback.
     *
     * Called by the private lockOutreach of
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl}, for
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#update},
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete} and
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#delete}.
     *
     * @param id             the outreach identifier
     * @param organizationId the organization that owns the outreach
     * @return the locked outreach, or empty when the organization has no outreach with the id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Outreach o WHERE o.id = :id AND o.organizationId = :organizationId")
    Optional<Outreach> findByIdAndOrganizationIdForUpdate(@Param("id") UUID id,
                                                          @Param("organizationId") UUID organizationId);

    /**
     * Claims an outreach for sending: moves it from DRAFT or FAILED to SENT with the sender,
     * the address and the moment, in one statement. Its row lock orders two sends of the
     * same outreach, so only one of them updates a row and only that one calls the mail
     * service; the other finds the outreach SENT and gets 0. A WHATSAPP or CALL outreach is
     * recorded as done through the same claim, with no mail sender and the phone as sentTo.
     *
     * The status condition sits in the WHERE clause rather than in a read before the
     * write, so the check and the change are one atomic step. An edit or delete holding the
     * lock of {@link #findByIdAndOrganizationIdForUpdate} makes the claim wait, and after it
     * the claim sees the edited outreach. The query runs outside the entity, so an
     * {@link Outreach} loaded before it still shows its old status.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} in its own
     * short transaction, before the mail is built and before
     * {@link com.korur.oba.connect.notification.client.MsGraphClient#sendEmail}; a 0 is
     * turned into a conflict by
     * {@link com.korur.oba.apps.otak.outreach.validator.OutreachValidator#ensureClaimed}. Also called by
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete} inside its own
     * transaction, under the row lock; the persistence context is cleared after the update, so
     * the read that answers the request sees the new status instead of the loaded entity.
     *
     * @param outreachId     the outreach identifier
     * @param organizationId the organization that owns the outreach
     * @param mailSenderId   the mail sender the mail goes out from; null for WHATSAPP and CALL
     * @param sentTo         the contact's address the mail goes to, or the contact's phone for
     *                       WHATSAPP and CALL
     * @param sentAt         the moment of the claim
     * @return 1 when the outreach was claimed, 0 when it is SENT already or not of the organization
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE otak.outreaches
            SET status = CAST('SENT' AS otak_outreach_status_enum),
                mail_sender_id = :mailSenderId,
                sent_to = :sentTo,
                sent_at = :sentAt,
                updated_at = :sentAt
            WHERE id = :outreachId
              AND organization_id = :organizationId
              AND status IN ('DRAFT', 'FAILED')
            """, nativeQuery = true)
    int claimForSending(@Param("outreachId") UUID outreachId,
                        @Param("organizationId") UUID organizationId,
                        @Param("mailSenderId") UUID mailSenderId,
                        @Param("sentTo") String sentTo,
                        @Param("sentAt") Instant sentAt);

    /**
     * Marks a claimed outreach FAILED after the mail service refused or failed it, and clears
     * the send moment, so the outreach can be sent again. The sender and the address of the
     * attempt stay.
     *
     * Only a SENT row is changed, so this never turns a DRAFT into FAILED. It runs in its
     * own transaction, after the claim's transaction committed and after the mail service
     * failed, so the failure is stored even though the send threw.
     *
     * Called by the private markFailed of
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl}, from
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} when building the mail
     * or {@link com.korur.oba.connect.notification.client.MsGraphClient#sendEmail} failed.
     *
     * @param outreachId     the outreach identifier
     * @param organizationId the organization that owns the outreach
     * @param failedAt       the moment of the failure
     * @return 1 when the outreach was marked, 0 when it is not SENT or not of the organization
     */
    @Modifying
    @Query(value = """
            UPDATE otak.outreaches
            SET status = CAST('FAILED' AS otak_outreach_status_enum),
                sent_at = NULL,
                updated_at = :failedAt
            WHERE id = :outreachId
              AND organization_id = :organizationId
              AND status = 'SENT'
            """, nativeQuery = true)
    int markSendFailed(@Param("outreachId") UUID outreachId,
                       @Param("organizationId") UUID organizationId,
                       @Param("failedAt") Instant failedAt);
}
