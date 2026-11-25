package com.korur.oba.shrine.identity.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

class UserFullNameMapperUnitTest {

    @Nested
    class ToUserFullName {

        @Test
        void userWithFirstAndLastName_joinsStrippedNames() {
            // Arrange
            User user = User.builder().email("ada@example.com").build();
            user.getProfile().setFirstName(" Ada ");
            user.getProfile().setLastName("Lovelace");

            // Act
            String result = UserFullNameMapper.toUserFullName(user);

            // Assert
            assertEquals("Ada Lovelace", result);
        }

        @Test
        void userWithoutProfile_returnsEmail() {
            // Arrange
            User user = User.builder().email("ada@example.com").build();
            user.setProfile(null);

            // Act
            String result = UserFullNameMapper.toUserFullName(user);

            // Assert
            assertEquals("ada@example.com", result);
        }

        @Test
        void nullUser_returnsNull() {
            // Act
            String result = UserFullNameMapper.toUserFullName(null);

            // Assert
            assertNull(result);
        }
    }

    @Nested
    class ToMemberFullName {

        @Test
        void memberWithOnlyLastName_returnsLastName() {
            // Arrange
            OrganizationMemberResponse member = new OrganizationMemberResponse(UUID.randomUUID(), UUID.randomUUID(),
                    "ada@example.com", " ", "Lovelace", OrganizationMemberRole.MEMBER,
                    OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);

            // Act
            String result = UserFullNameMapper.toMemberFullName(member);

            // Assert
            assertEquals("Lovelace", result);
        }

        @Test
        void memberWithBlankNames_returnsEmail() {
            // Arrange
            OrganizationMemberResponse member = new OrganizationMemberResponse(UUID.randomUUID(), UUID.randomUUID(),
                    "ada@example.com", " ", null, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);

            // Act
            String result = UserFullNameMapper.toMemberFullName(member);

            // Assert
            assertEquals("ada@example.com", result);
        }

        @Test
        void nullMember_returnsNull() {
            // Act
            String result = UserFullNameMapper.toMemberFullName(null);

            // Assert
            assertNull(result);
        }
    }
}
