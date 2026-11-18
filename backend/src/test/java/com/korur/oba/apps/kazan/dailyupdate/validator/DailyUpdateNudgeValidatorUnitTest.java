package com.korur.oba.apps.kazan.dailyupdate.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.projection.DailyUpdateFirstPostProjection;
import com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateDeadlineResolver.ReportDay;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateNudgeQueryService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

@ExtendWith(MockitoExtension.class)
class DailyUpdateNudgeValidatorUnitTest {

    @Mock
    private DailyUpdateRepository dailyUpdateRepository;
    @Mock
    private DailyUpdateNudgeQueryService dailyUpdateNudgeQueryService;
    @Mock
    private OrganizationMemberQueryService organizationMemberQueryService;
    @Mock
    private AppAccessQueryService appAccessQueryService;
    @Mock
    private LoggingService loggingService;

    private DailyUpdateNudgeValidator validator;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final ReportDay day = new ReportDay(LocalDate.of(2026, 11, 6), Instant.parse("2026-11-05T21:00:00Z"),
            Instant.parse("2026-11-06T21:00:00Z"), Instant.parse("2026-11-06T07:30:00Z"), true, false);

    @BeforeEach
    void setUp() {
        validator = new DailyUpdateNudgeValidator(dailyUpdateRepository, dailyUpdateNudgeQueryService,
                organizationMemberQueryService, appAccessQueryService, loggingService);
    }

    private OrganizationMember member(OrganizationMemberRole role, OrganizationMemberStatus status,
            UserStatus userStatus) {
        User user = User.builder().email("m@example.com").status(userStatus).build();
        return OrganizationMember.builder().user(user).role(role).status(status).build();
    }

    @Test
    void ensureNotSelf_senderIsRecipient_throwsValidationException() {
        // Arrange
        User sender = User.builder().email("s@example.com").build();
        sender.setId(recipientId);

        // Act and assert
        assertThatThrownBy(() -> validator.ensureNotSelf(organizationId, sender, recipientId))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_VAL_001");
    }

    @Test
    void ensureNotSelf_differentRecipient_passes() {
        // Arrange
        User sender = User.builder().email("s@example.com").build();
        sender.setId(UUID.randomUUID());

        // Act and assert
        assertThatCode(() -> validator.ensureNotSelf(organizationId, sender, recipientId)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationMemberRole.class, names = {"OWNER", "ADMIN", "MEMBER"})
    void ensureExpectedPoster_activeMemberWithPostingRole_passes(OrganizationMemberRole role) {
        // Arrange
        OrganizationMember recipient = member(role, OrganizationMemberStatus.ACTIVE, UserStatus.ACTIVE);
        when(organizationMemberQueryService.findMember(organizationId, recipientId)).thenReturn(
                Optional.of(recipient));
        when(appAccessQueryService.hasAppAccess(recipient, App.KAZAN)).thenReturn(true);

        // Act and assert
        assertThatCode(() -> validator.ensureExpectedPoster(organizationId, recipientId))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureExpectedPoster_memberWithoutKazan_throwsNotFoundException() {
        // Arrange
        OrganizationMember recipient = member(OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE,
                UserStatus.ACTIVE);
        when(organizationMemberQueryService.findMember(organizationId, recipientId)).thenReturn(
                Optional.of(recipient));
        when(appAccessQueryService.hasAppAccess(recipient, App.KAZAN)).thenReturn(false);

        // Act and assert
        assertThatThrownBy(() -> validator.ensureExpectedPoster(organizationId, recipientId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_NOT_001");
    }

    @Test
    void ensureExpectedPoster_guest_throwsNotFoundException() {
        // Arrange
        when(organizationMemberQueryService.findMember(organizationId, recipientId)).thenReturn(Optional.of(
                member(OrganizationMemberRole.GUEST, OrganizationMemberStatus.ACTIVE, UserStatus.ACTIVE)));

        // Act and assert
        assertThatThrownBy(() -> validator.ensureExpectedPoster(organizationId, recipientId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_NOT_001");
    }

    @Test
    void ensureExpectedPoster_notAMember_throwsNotFoundException() {
        // Arrange
        when(organizationMemberQueryService.findMember(organizationId, recipientId)).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> validator.ensureExpectedPoster(organizationId, recipientId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_NOT_001");
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationMemberStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void ensureExpectedPoster_membershipNotActive_throwsNotFoundException(OrganizationMemberStatus status) {
        // Arrange
        when(organizationMemberQueryService.findMember(organizationId, recipientId)).thenReturn(
                Optional.of(member(OrganizationMemberRole.MEMBER, status, UserStatus.ACTIVE)));

        // Act and assert
        assertThatThrownBy(() -> validator.ensureExpectedPoster(organizationId, recipientId))
                .isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void ensureExpectedPoster_userNotActive_throwsNotFoundException(UserStatus userStatus) {
        // Arrange
        when(organizationMemberQueryService.findMember(organizationId, recipientId)).thenReturn(Optional.of(
                member(OrganizationMemberRole.MEMBER, OrganizationMemberStatus.ACTIVE, userStatus)));

        // Act and assert
        assertThatThrownBy(() -> validator.ensureExpectedPoster(organizationId, recipientId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void ensureNotPosted_recipientPostedToday_throwsValidationException() {
        // Arrange
        when(dailyUpdateRepository.findFirstPostsByAuthors(List.of(recipientId), day.dayStart(), day.dayEnd()))
                .thenReturn(List.of(mock(DailyUpdateFirstPostProjection.class)));

        // Act and assert
        assertThatThrownBy(() -> validator.ensureNotPosted(recipientId, day))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_VAL_002");
    }

    @Test
    void ensureNotPosted_noPostToday_passes() {
        // Arrange
        when(dailyUpdateRepository.findFirstPostsByAuthors(List.of(recipientId), day.dayStart(), day.dayEnd()))
                .thenReturn(List.of());

        // Act and assert
        assertThatCode(() -> validator.ensureNotPosted(recipientId, day)).doesNotThrowAnyException();
    }

    @Test
    void ensureNotNudged_alreadyNudgedToday_throwsConflictException() {
        // Arrange
        when(dailyUpdateNudgeQueryService.isNudged(organizationId, recipientId, day.date())).thenReturn(true);

        // Act and assert
        assertThatThrownBy(() -> validator.ensureNotNudged(organizationId, recipientId, day))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_STA_001");
    }

    @Test
    void ensureNotNudged_notYetNudged_passes() {
        // Arrange
        when(dailyUpdateNudgeQueryService.isNudged(any(), any(), any())).thenReturn(false);

        // Act and assert
        assertThatCode(() -> validator.ensureNotNudged(organizationId, recipientId, day)).doesNotThrowAnyException();
    }

    @Test
    void alreadyNudged_buildsTheSameConflictTheRaceLoserAnswers() {
        // Act
        ConflictException conflict = validator.alreadyNudged(recipientId, day);

        // Assert
        assertThat(conflict).hasFieldOrPropertyWithValue("errorCode", "DLY_STA_001");
    }
}
