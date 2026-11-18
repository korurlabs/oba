package com.korur.oba.apps.otak.mailsender.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.entity.MailSender;

/**
 * Repository interface for {@link MailSender} entity database operations.
 */
@Repository
public interface MailSenderRepository extends JpaRepository<MailSender, UUID>, JpaSpecificationExecutor<MailSender> {

    /**
     * Reads one mail sender within an organization, so a sender of another organization is not found.
     *
     * @param id             the mail sender identifier
     * @param organizationId the organization that owns the sender
     * @return the sender, or empty when the organization has no sender with the id
     */
    Optional<MailSender> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Lists every mail sender of the organization by mail address.
     *
     * @param organizationId the organization that owns the senders
     * @return the senders, ordered by mail address
     */
    List<MailSender> findAllByOrganizationIdOrderByMailAddressAscIdAsc(UUID organizationId);

    /**
     * Tells whether the organization already has a sender with the mail address, case ignored.
     *
     * @param organizationId the organization that owns the senders
     * @param mailAddress    the mail address to look for
     * @return true when a sender of the organization has the mail address
     */
    boolean existsByOrganizationIdAndMailAddressIgnoreCase(UUID organizationId, String mailAddress);
}
