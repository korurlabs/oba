package com.korur.oba.apps.kalkan.mock_data;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kalkan.page.entity.KalkanPage;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

/** Builders of the entities the Kalkan unit tests share. */
public final class KalkanMocks {

    private KalkanMocks() {
    }

    public static User user() {
        User user = User.builder().email("user-" + UUID.randomUUID() + "@example.com").status(UserStatus.ACTIVE).build();
        user.setId(UUID.randomUUID());
        return user;
    }

    public static OrganizationMember member(User user, OrganizationMemberRole role) {
        return member(user, role, OrganizationMemberStatus.ACTIVE);
    }

    public static OrganizationMember member(User user, OrganizationMemberRole role, OrganizationMemberStatus status) {
        return OrganizationMember.builder().user(user).role(role).status(status).build();
    }

    public static KalkanPage root(UUID organizationId, String slug) {
        KalkanPage page = KalkanPage.builder().organizationId(organizationId).slug(slug).createdById(UUID.randomUUID())
                .updatedById(UUID.randomUUID()).build();
        page.setId(UUID.randomUUID());
        return page;
    }

    public static KalkanPage child(KalkanPage parent, String slug) {
        KalkanPage page = KalkanPage.builder().organizationId(parent.getOrganizationId()).parent(parent)
                .rootId(parent.getAccessRootId()).slug(slug).createdById(UUID.randomUUID())
                .updatedById(UUID.randomUUID()).build();
        page.setId(UUID.randomUUID());
        return page;
    }
}
