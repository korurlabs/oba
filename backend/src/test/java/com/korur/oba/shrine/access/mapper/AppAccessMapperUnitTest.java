package com.korur.oba.shrine.access.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.dto.response.AppGrantResponse;
import com.korur.oba.shrine.access.dto.response.MemberAppAccessResponse;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.entity.OrganizationGroup;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.dto.response.OrganizationMemberResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

class AppAccessMapperUnitTest {

    private AppAccessMapper appAccessMapper;

    @BeforeEach
    void setUp() {
        appAccessMapper = new AppAccessMapperImpl();
    }

    @Test
    void toEntity_request_copiesOnlyTheApp() {
        AppGrant grant = appAccessMapper.toEntity(new AppGrantCreateRequest(App.KAYA, UUID.randomUUID(), null));

        assertEquals(App.KAYA, grant.getApp());
        assertNull(grant.getUser());
        assertNull(grant.getGroup());
        assertNull(grant.getId());
    }

    @Test
    void toResponse_userGrant_hasUserTargetAndNoGroupFields() {
        User user = User.builder().email("ada@example.com").build();
        user.setId(UUID.randomUUID());
        user.getProfile().setFirstName("Ada");
        user.getProfile().setLastName("Lovelace");
        AppGrant grant = AppGrant.builder().app(App.KAZAN).user(user).build();

        AppGrantResponse response = appAccessMapper.toResponse(grant);

        assertEquals(AppGrantTargetType.USER, response.targetType());
        assertEquals(user.getId(), response.userId());
        assertEquals("Ada Lovelace", response.userFullName());
        assertNull(response.groupId());
        assertNull(response.groupName());
        assertNull(response.groupType());
    }

    @Test
    void toResponse_groupGrant_hasGroupTargetAndNoUserFields() {
        OrganizationGroup group = OrganizationGroup.builder().name("Everyone")
                .type(OrganizationGroupType.EVERYONE).build();
        group.setId(UUID.randomUUID());
        AppGrant grant = AppGrant.builder().app(App.KAYA).group(group).build();

        AppGrantResponse response = appAccessMapper.toResponse(grant);

        assertEquals(AppGrantTargetType.GROUP, response.targetType());
        assertEquals(group.getId(), response.groupId());
        assertEquals("Everyone", response.groupName());
        assertEquals(OrganizationGroupType.EVERYONE, response.groupType());
        assertNull(response.userId());
        assertNull(response.userFullName());
    }

    @Test
    void toMemberAccessResponse_member_carriesNameRoleAndApps() {
        UUID userId = UUID.randomUUID();
        OrganizationMemberResponse member = new OrganizationMemberResponse(UUID.randomUUID(), userId,
                "ada@example.com", "Ada", "Lovelace", OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE, UserKind.HUMAN, null, null, null);

        MemberAppAccessResponse response = appAccessMapper.toMemberAccessResponse(member, List.of(App.KAZAN));

        assertEquals(userId, response.userId());
        assertEquals("Ada Lovelace", response.fullName());
        assertEquals(OrganizationMemberRole.MEMBER, response.role());
        assertEquals(List.of(App.KAZAN), response.apps());
    }
}
