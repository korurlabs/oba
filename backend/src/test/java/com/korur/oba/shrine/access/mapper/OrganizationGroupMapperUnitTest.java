package com.korur.oba.shrine.access.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.dto.request.OrganizationGroupCreateRequest;
import com.korur.oba.shrine.access.dto.request.OrganizationGroupUpdateRequest;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupMemberResponse;
import com.korur.oba.shrine.access.dto.response.OrganizationGroupResponse;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.entity.OrganizationGroupMember;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

class OrganizationGroupMapperUnitTest {

    private OrganizationGroupMapper organizationGroupMapper;

    @BeforeEach
    void setUp() {
        organizationGroupMapper = new OrganizationGroupMapperImpl();
    }

    @Nested
    class ToEntity {

        @Test
        void surroundingWhitespace_storesTrimmedNameAndDescription() {
            // Arrange
            OrganizationGroupCreateRequest request =
                    new OrganizationGroupCreateRequest("  Engineering ", "\tDevelopers and testers  ");

            // Act
            OrganizationGroup group = organizationGroupMapper.toEntity(request);

            // Assert
            assertEquals("Engineering", group.getName());
            assertEquals("Developers and testers", group.getDescription());
        }

        @Test
        void nullDescription_keepsDescriptionNull() {
            // Arrange
            OrganizationGroupCreateRequest request = new OrganizationGroupCreateRequest("Engineering", null);

            // Act
            OrganizationGroup group = organizationGroupMapper.toEntity(request);

            // Assert
            assertEquals("Engineering", group.getName());
            assertNull(group.getDescription());
        }
    }

    @Nested
    class UpdateEntity {

        @Test
        void surroundingWhitespace_storesTrimmedNameAndDescription() {
            // Arrange
            OrganizationGroup group = OrganizationGroup.builder()
                    .name("Engineering")
                    .description("Developers")
                    .build();
            OrganizationGroupUpdateRequest request =
                    new OrganizationGroupUpdateRequest(" Platform  ", " Platform team ");

            // Act
            organizationGroupMapper.updateEntity(group, request);

            // Assert
            assertEquals("Platform", group.getName());
            assertEquals("Platform team", group.getDescription());
        }

        @Test
        void nullDescription_clearsDescription() {
            // Arrange
            OrganizationGroup group = OrganizationGroup.builder()
                    .name("Engineering")
                    .description("Developers")
                    .build();
            OrganizationGroupUpdateRequest request = new OrganizationGroupUpdateRequest("Engineering ", null);

            // Act
            organizationGroupMapper.updateEntity(group, request);

            // Assert
            assertEquals("Engineering", group.getName());
            assertNull(group.getDescription());
        }
    }

    @Nested
    class Responses {

        @Test
        void toResponse_group_copiesCountAndApps() {
            OrganizationGroup group = OrganizationGroup.builder().name("Dev").description("d")
                    .type(OrganizationGroupType.CUSTOM).build();
            group.setId(UUID.randomUUID());

            OrganizationGroupResponse response =
                    organizationGroupMapper.toResponse(group, 4L, List.of(App.KAYA));

            assertEquals(group.getId(), response.id());
            assertEquals("Dev", response.name());
            assertEquals(OrganizationGroupType.CUSTOM, response.type());
            assertEquals(4L, response.memberCount());
            assertEquals(List.of(App.KAYA), response.apps());
        }

        @Test
        void toMemberResponse_memberWithName_joinsFirstAndLastName() {
            UUID userId = UUID.randomUUID();
            OrganizationMemberResponse member = new OrganizationMemberResponse(UUID.randomUUID(), userId,
                    "ada@example.com", " Ada ", "Lovelace", OrganizationMemberRole.ADMIN,
                    OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);

            OrganizationGroupMemberResponse response = organizationGroupMapper.toMemberResponse(member);

            assertEquals("Ada Lovelace", response.fullName());
            assertEquals(userId, response.userId());
            assertEquals(OrganizationMemberRole.ADMIN, response.role());
        }

        @Test
        void toMemberResponse_memberWithoutName_fallsBackToEmail() {
            OrganizationMemberResponse member = new OrganizationMemberResponse(UUID.randomUUID(), UUID.randomUUID(),
                    "ada@example.com", " ", null, OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);

            assertEquals("ada@example.com", organizationGroupMapper.toMemberResponse(member).fullName());
        }

        @Test
        void toGroupMemberResponse_userWithOnlyLastName_usesLastNameAndGivenRole() {
            User user = User.builder().email("ada@example.com").build();
            user.setId(UUID.randomUUID());
            user.getProfile().setLastName("Lovelace");
            OrganizationGroupMember row = OrganizationGroupMember.builder().user(user).build();

            OrganizationGroupMemberResponse response =
                    organizationGroupMapper.toGroupMemberResponse(row, OrganizationMemberRole.GUEST);

            assertEquals("Lovelace", response.fullName());
            assertEquals("ada@example.com", response.email());
            assertEquals(OrganizationMemberRole.GUEST, response.role());
        }

        @Test
        void toGroupMemberResponse_userWithoutProfile_fallsBackToEmail() {
            User user = User.builder().email("ada@example.com").build();
            user.setId(UUID.randomUUID());
            user.setProfile(null);
            OrganizationGroupMember row = OrganizationGroupMember.builder().user(user).build();

            assertEquals("ada@example.com", organizationGroupMapper.toGroupMemberResponse(row, null).fullName());
        }
    }
}
