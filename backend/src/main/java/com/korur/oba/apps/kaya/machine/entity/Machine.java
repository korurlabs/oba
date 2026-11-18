package com.korur.oba.apps.kaya.machine.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.account.entity.Account;
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;
import com.korur.oba.shrine.identity.entity.User;

/**
 * One Claude Code installation on one computer, owned by a user. The machine name is the
 * hostname the recorder reports and is unique per user, so a batch can be checked against the
 * machine of its token.
 */
@Entity
@Table(
        name = "machines",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "machines_organization_id_user_id_machine_name_uk",
                columnNames = {"organization_id", "user_id", "machine_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Machine extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // Fetch: EAGER -- Hibernate 6 refuses a LAZY to-one whose target uses @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "machine_name", length = 255, nullable = false, updatable = false)
    private String machineName;

    // Null until the recorder reports a version.
    @Column(name = "claude_code_version", length = 40)
    private String claudeCodeVersion;

    // The account last seen on the machine; cleared by the database when the account is deleted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "last_seen_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lastSeenAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private MachineStatus status = MachineStatus.ACTIVE;
}
