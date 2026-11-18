package com.korur.oba.shrine.access.helper.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.constant.service.AppAccessServiceLogConstant;
import com.korur.oba.shrine.access.constant.service.OrganizationGroupServiceLogConstant;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.helper.AccessHelper;
import com.korur.oba.shrine.identity.entity.User;

class AccessHelperUnitTest {

    private final AccessHelper accessHelper = new AccessHelper();

    private static User user() {
        User user = User.builder().email("ada@example.com").build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static OrganizationGroup group() {
        OrganizationGroup group = OrganizationGroup.builder().name("Engineering").build();
        group.setId(UUID.randomUUID());
        return group;
    }

    @Nested
    class AppsOf {

        @Test
        void grantsInAnyOrderWithDuplicates_returnsEachAppOnceInAppOrder() {
            // Arrange
            List<AppGrant> grants = List.of(
                    AppGrant.builder().app(App.KAYA).build(),
                    AppGrant.builder().app(App.KAZAN).build(),
                    AppGrant.builder().app(App.KAYA).build());

            // Act
            List<App> result = accessHelper.appsOf(grants);

            // Assert
            assertEquals(List.of(App.KAZAN, App.KAYA), result);
        }

        @Test
        void noGrants_returnsEmptyList() {
            // Act
            List<App> result = accessHelper.appsOf(List.of());

            // Assert
            assertEquals(List.of(), result);
        }
    }

    @Nested
    class UserIdsOf {

        @Test
        void memberRows_returnsTheirUserIdsInRowOrder() {
            // Arrange
            User first = user();
            User second = user();
            List<OrganizationGroupMember> rows = List.of(
                    OrganizationGroupMember.builder().user(first).build(),
                    OrganizationGroupMember.builder().user(second).build());

            // Act
            Set<UUID> result = accessHelper.userIdsOf(rows);

            // Assert
            assertEquals(List.of(first.getId(), second.getId()), List.copyOf(result));
        }
    }

    @Nested
    class DescribeGroup {

        @Test
        void groupWithoutDescription_returnsNameAndNullDescription() {
            // Arrange
            OrganizationGroup group = group();

            // Act
            Map<String, Object> result = accessHelper.describeGroup(group);

            // Assert
            assertEquals("Engineering", result.get(OrganizationGroupServiceLogConstant.AUDIT_FIELD_NAME));
            assertTrue(result.containsKey(OrganizationGroupServiceLogConstant.AUDIT_FIELD_DESCRIPTION));
            assertNull(result.get(OrganizationGroupServiceLogConstant.AUDIT_FIELD_DESCRIPTION));
        }
    }

    @Nested
    class DescribeGrant {

        @Test
        void userGrant_returnsAppAndUserId() {
            // Arrange
            User target = user();
            AppGrant grant = AppGrant.builder().app(App.KAYA).user(target).build();

            // Act
            Map<String, Object> result = accessHelper.describeGrant(grant);

            // Assert
            assertEquals(Map.of(
                    AppAccessServiceLogConstant.AUDIT_FIELD_APP, "KAYA",
                    AppAccessServiceLogConstant.AUDIT_FIELD_USER_ID, target.getId()), result);
        }

        @Test
        void groupGrant_returnsAppAndGroupId() {
            // Arrange
            OrganizationGroup group = group();
            AppGrant grant = AppGrant.builder().app(App.KAZAN).group(group).build();

            // Act
            Map<String, Object> result = accessHelper.describeGrant(grant);

            // Assert
            assertEquals(Map.of(
                    AppAccessServiceLogConstant.AUDIT_FIELD_APP, "KAZAN",
                    AppAccessServiceLogConstant.AUDIT_FIELD_GROUP_ID, group.getId()), result);
        }
    }
}
