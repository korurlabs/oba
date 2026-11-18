package com.korur.oba.shrine.identity.entity;

// 1. Framework-level
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// 2. Third-party
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

// 3. Java SDK
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.dojo.entity.SoftDeleteBaseEntity;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;

@Table(name = "users", schema = "shrine")
@Entity
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class User extends SoftDeleteBaseEntity {

    // -------------Atts-----------//

    @Column(name = "keycloak_id", length = 255, nullable = true)
    private String keycloakId;

    /*
     * Email acts as username in our system. Convention: 254
     */
    @Column(name = "email", length = 254, nullable = false)
    private String email;

    // -------------Enums-----------//

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.PENDING_ONBOARDING;

    // -------------Relations-----------//

    // Fetch: EAGER -- required by Hibernate 6 when both source and target entities
    // use @SoftDelete.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_organization_id")
    private Organization defaultOrganization;

    // Fetch: LAZY -- Hibernate 6 requires EAGER only for to-one associations whose
    // target uses @SoftDelete; a collection of soft-deleted rows may load lazily.
    // Cascade: REMOVE -- soft-deletes all memberships when user is soft-deleted.
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE)
    private List<OrganizationMember> organizationMemberships = new ArrayList<>();

    // -------------Embedded-----------//

    @Builder.Default
    @Embedded
    private UserProfile profile = new UserProfile();

    // -------------JSONB-----------//
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_preferences", columnDefinition = "jsonb DEFAULT '{\"theme\": \"system\"}'::jsonb")
    @Builder.Default
    private Map<String, Object> userPreferences = new LinkedHashMap<>(Map.of("theme", "system"));

}
