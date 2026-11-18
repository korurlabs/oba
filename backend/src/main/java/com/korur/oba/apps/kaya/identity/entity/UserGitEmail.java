package com.korur.oba.apps.kaya.identity.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.identity.enums.UserGitEmailSource;

/**
 * A git email of a user in an organization. An email belongs to at most one user per organization
 * and is stored in lower case, so the recorder's git author email matches it exactly and a batch
 * is attributed to exactly one person.
 */
@Entity
@Table(
        name = "user_git_emails",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "user_git_emails_organization_id_email_uk",
                columnNames = {"organization_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserGitEmail extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // Fetch: EAGER -- Hibernate 6 refuses a LAZY to-one whose target (User) uses @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "email", length = 320, nullable = false, updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "source", nullable = false, updatable = false)
    private UserGitEmailSource source;
}
