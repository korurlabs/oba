package com.korur.oba.apps.otak.mailsender.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailsender.enums.EmailType;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing a mail sender: a mailbox of the Microsoft 365 tenant that an
 * organization may send its outreach mail from. The mailbox is checked to exist in the
 * tenant when the sender is added. The mail address is the sender's only label.
 *
 * Every sender belongs to one organization and is only read and written within it.
 * Its mail address is unique per organization with case ignored, which a unique index enforces.
 * The database index behind it compares the mail address in lower case, so the uniqueness ignores case.
 * Rows are never changed; a wrong address is deleted outright and added again.
 */
@Entity
@Table(
        name = "mail_senders",
        schema = "otak",
        uniqueConstraints = @UniqueConstraint(
                name = "mail_senders_organization_id_lower_mail_address_uk",
                columnNames = {"organization_id", "mail_address"}))
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MailSender extends BaseEntity {

    // -------------Atts-----------//

    @Column(name = "mail_address", length = 254, nullable = false)
    private String mailAddress;

    /**
     * User who added the sender. Kept as a bare id, so the row outlives a removed user.
     */
    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    /**
     * Organization that owns the sender, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // -------------Enums-----------//

    /**
     * Kind of mailbox, chosen when the sender is added. It decided how the address was
     * checked in the tenant, so it is never changed.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, updatable = false)
    private EmailType type;
}
