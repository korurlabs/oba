package com.korur.oba.apps.kaya.identity.mock_data;

// 1. Framework-level
import org.springframework.security.oauth2.jwt.Jwt;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.identity.enums.UserStatus;

/**
 * Builders for identity entities and JWTs used by the unit tests. Every entity gets a random id,
 * because the services compare and log ids.
 */
public final class IdentityMocks {

    private IdentityMocks() {
    }

    public static User user(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .keycloakId("kc-" + UUID.randomUUID())
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static Organization organization(OrganizationStatus status) {
        return Organization.builder()
                .id(UUID.randomUUID())
                .name("Korur")
                .status(status)
                .build();
    }

    public static OrganizationMember member(
            Organization organization, User user, OrganizationMemberRole role, OrganizationMemberStatus status) {
        return OrganizationMember.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .user(user)
                .role(role)
                .status(status)
                .build();
    }

    public static Jwt jwt(String subject, String email, String name, String preferredUsername) {
        Jwt.Builder builder = Jwt.withTokenValue("unit-test-token").header("alg", "none");
        if (subject != null) {
            builder.subject(subject);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        if (name != null) {
            builder.claim("name", name);
        }
        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }
        builder.claim("email_verified", true);
        builder.claim("typ", "Bearer");
        return builder.build();
    }
}
