package com.korur.oba.shrine.identity.mapper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.dto.request.UserSelfUpdateRequest;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.dto.response.embedded.UserOrganizationEmbeddedResponse;
import com.korur.oba.shrine.identity.dto.request.embedded.UserProfileEmbeddedUpdateRequest;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

class UserMapperUnitTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper = new UserMapperImpl(new UserEmbeddedMapperImpl());
    }

    private User userWith(UserStatus status) {
        return User.builder()
                .email("old@example.com")
                .status(status)
                .profile(new UserProfile())
                .build();
    }

    @Test
    void updateEntity_selfRequest_keepsStatus() {
        User user = userWith(UserStatus.ACTIVE);
        UserSelfUpdateRequest request = new UserSelfUpdateRequest("new@example.com",
                new UserProfileEmbeddedUpdateRequest("Jane", "Doe", null, null, null, null),
                null, Map.of("theme", "dark"));

        userMapper.updateEntity(user, request);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getProfile().getFirstName()).isEqualTo("Jane");
    }

    @Test
    void updateEntity_selfRequestOnPendingOnboardingUser_keepsStatus() {
        User user = userWith(UserStatus.PENDING_ONBOARDING);
        UserSelfUpdateRequest request = new UserSelfUpdateRequest(null,
                new UserProfileEmbeddedUpdateRequest("Jane", "Doe", null, null, null, null), null, null);

        userMapper.updateEntity(user, request);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ONBOARDING);
        assertThat(user.getProfile().getLastName()).isEqualTo("Doe");
    }

    @Test
    void selfUpdateRequest_recordComponents_haveNoStatus() {
        List<String> components = Arrays.stream(UserSelfUpdateRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components).doesNotContain("userStatus", "status");
    }

    private OrganizationMember membershipIn(OrganizationStatus organizationStatus, OrganizationMemberRole role,
            OrganizationMemberStatus memberStatus) {
        Organization organization = Organization.builder()
                .name("Acme")
                .status(organizationStatus)
                .build();
        organization.setId(UUID.randomUUID());
        return OrganizationMember.builder()
                .organization(organization)
                .role(role)
                .status(memberStatus)
                .build();
    }

    @Test
    void toDetailResponse_activeAndDisabledOrganizations_exposeOrganizationStatusFromTheOrganization() {
        User user = userWith(UserStatus.ACTIVE);
        OrganizationMember active = membershipIn(OrganizationStatus.ACTIVE, OrganizationMemberRole.ADMIN,
                OrganizationMemberStatus.ACTIVE);
        OrganizationMember disabled = membershipIn(OrganizationStatus.DISABLED, OrganizationMemberRole.OWNER,
                OrganizationMemberStatus.ACTIVE);
        user.setOrganizationMemberships(List.of(active, disabled));
        // A disabled organization opens no app, so the apps read leaves it out.
        Map<UUID, List<App>> appsByOrganization = Map.of(active.getOrganization().getId(),
                List.of(App.KAIZEN, App.KAYA));

        UserDetailResponse response = userMapper.toDetailResponse(user, appsByOrganization);

        // The membership is ACTIVE in both rows, so only the organization can explain DISABLED.
        assertThat(response.organizations()).extracting(UserOrganizationEmbeddedResponse::organizationStatus)
                .containsExactly(OrganizationStatus.ACTIVE, OrganizationStatus.DISABLED);
        assertThat(response.organizations()).extracting(UserOrganizationEmbeddedResponse::role)
                .containsExactly(OrganizationMemberRole.ADMIN, OrganizationMemberRole.OWNER);
        assertThat(response.organizations().get(1).id()).isEqualTo(disabled.getOrganization().getId());
        assertThat(response.organizations().get(1).name()).isEqualTo("Acme");
        assertThat(response.organizations()).extracting(UserOrganizationEmbeddedResponse::apps)
                .containsExactly(List.of(App.KAIZEN, App.KAYA), List.of());
    }

    @Test
    void toDetailResponse_inactiveMembership_isNotListed() {
        User user = userWith(UserStatus.ACTIVE);
        user.setOrganizationMemberships(List.of(membershipIn(OrganizationStatus.ACTIVE,
                OrganizationMemberRole.OWNER, OrganizationMemberStatus.DISABLED)));

        UserDetailResponse response = userMapper.toDetailResponse(user, Map.of());

        assertThat(response.organizations()).isEmpty();
    }

    @Test
    void toDetailResponse_withPendingInvitations_listsTheAppsOfEachOrganization() {
        User user = userWith(UserStatus.ACTIVE);
        OrganizationMember first = membershipIn(OrganizationStatus.ACTIVE, OrganizationMemberRole.GUEST,
                OrganizationMemberStatus.ACTIVE);
        OrganizationMember second = membershipIn(OrganizationStatus.ACTIVE, OrganizationMemberRole.OWNER,
                OrganizationMemberStatus.ACTIVE);
        user.setOrganizationMemberships(List.of(first, second));
        Map<UUID, List<App>> appsByOrganization = Map.of(
                first.getOrganization().getId(), List.of(App.KAIZEN),
                second.getOrganization().getId(), List.of(App.KAIZEN, App.KAYA, App.KALKAN, App.OTAK));

        UserDetailResponse response = userMapper.toDetailResponse(user, List.of(), appsByOrganization);

        assertThat(response.organizations()).extracting(UserOrganizationEmbeddedResponse::apps)
                .containsExactly(List.of(App.KAIZEN), List.of(App.KAIZEN, App.KAYA, App.KALKAN, App.OTAK));
        assertThat(response.pendingInvitations()).isEmpty();
    }
}
