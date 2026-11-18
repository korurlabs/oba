package com.korur.oba.apps.kaya.session.entity;

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
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.apps.kaya.session.enums.SessionLinkKind;
import com.korur.oba.apps.kaya.session.enums.SessionLinkSource;

/**
 * A pull request or Kazan ticket linked to a session, found by the recorder or added by a member.
 * Kaya never calls Kazan: a ticket is its key plus the URL the repository's template gives.
 */
@Entity
@Table(
        name = "session_links",
        schema = "kaya",
        uniqueConstraints = @UniqueConstraint(
                name = "session_links_session_id_kind_reference_uk", columnNames = {"session_id", "kind", "reference"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SessionLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "kind", nullable = false, updatable = false)
    private SessionLinkKind kind;

    @Column(name = "reference", length = 500, nullable = false, updatable = false)
    private String reference;

    // Null for a ticket key when the repository has no ticket URL template.
    @Column(name = "url", length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "source", nullable = false, updatable = false)
    private SessionLinkSource source;

    // The member who added a MANUAL link; null for the recorder.
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
}
