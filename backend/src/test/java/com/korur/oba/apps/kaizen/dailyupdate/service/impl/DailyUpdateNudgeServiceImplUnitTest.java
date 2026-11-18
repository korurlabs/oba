package com.korur.oba.apps.kaizen.dailyupdate.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateNudgeResponse;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateNudge;
import com.korur.oba.apps.kaizen.dailyupdate.event.DailyUpdateNudgedEvent;
import com.korur.oba.apps.kaizen.dailyupdate.exception.ExceptionDomainDailyUpdateError;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateDeadlineResolver;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateDeadlineResolver.ReportDay;
import com.korur.oba.apps.kaizen.dailyupdate.mapper.DailyUpdateNudgeMapperImpl;
import com.korur.oba.apps.kaizen.dailyupdate.repository.DailyUpdateNudgeRepository;
import com.korur.oba.apps.kaizen.dailyupdate.validator.DailyUpdateNudgeValidator;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.exception.ExceptionDomainAccessError;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.entity.embedded.UserProfile;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

@ExtendWith(MockitoExtension.class)
class DailyUpdateNudgeServiceImplUnitTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final Instant DAY_START = Instant.parse("2026-11-05T21:00:00Z");

    @Mock
    private DailyUpdateNudgeRepository dailyUpdateNudgeRepository;
    @Mock
    private DailyUpdateNudgeValidator dailyUpdateNudgeValidator;
    @Mock
    private DailyUpdateDeadlineResolver dailyUpdateDeadlineResolver;
    @Mock
    private OrganizationAccessService organizationAccessService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private LoggingService loggingService;

    private DailyUpdateNudgeServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final ReportDay today = new ReportDay(TODAY, DAY_START, DAY_START.plusSeconds(86_400),
            Instant.parse("2026-11-06T07:30:00Z"), true, false);
    private User sender;

    @BeforeEach
    void setUp() {
        sender = User.builder().email("ayse@example.com")
                .profile(UserProfile.builder().firstName("Ayşe").lastName("Kaya").build()).build();
        sender.setId(UUID.randomUUID());
        service = new DailyUpdateNudgeServiceImpl(dailyUpdateNudgeRepository, dailyUpdateNudgeValidator,
                new DailyUpdateNudgeMapperImpl(), dailyUpdateDeadlineResolver, organizationAccessService,
                applicationEventPublisher, loggingService);
    }

    private void givenToday() {
        when(dailyUpdateDeadlineResolver.resolve(null)).thenReturn(today);
    }

    private void givenSaveAssignsId(UUID nudgeId) {
        when(dailyUpdateNudgeRepository.saveAndFlush(any(DailyUpdateNudge.class))).thenAnswer(invocation -> {
            DailyUpdateNudge nudge = invocation.getArgument(0);
            nudge.setId(nudgeId);
            return nudge;
        });
    }

    @Test
    void nudge_adminOrOwnerNudgingAMissingMember_savesTheNudgeAndPublishesTheEvent() {
        // Arrange
        UUID nudgeId = UUID.randomUUID();
        givenToday();
        givenSaveAssignsId(nudgeId);

        // Act
        DailyUpdateNudgeResponse response = service.nudge(sender, organizationId, recipientId);

        // Assert
        assertThat(response.recipientId()).isEqualTo(recipientId);
        assertThat(response.day()).isEqualTo(TODAY);
        ArgumentCaptor<DailyUpdateNudge> saved = ArgumentCaptor.forClass(DailyUpdateNudge.class);
        verify(dailyUpdateNudgeRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(organizationId);
        assertThat(saved.getValue().getRecipientId()).isEqualTo(recipientId);
        assertThat(saved.getValue().getSenderId()).isEqualTo(sender.getId());
        assertThat(saved.getValue().getDay()).isEqualTo(TODAY);
        ArgumentCaptor<DailyUpdateNudgedEvent> event = ArgumentCaptor.forClass(DailyUpdateNudgedEvent.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new DailyUpdateNudgedEvent(organizationId, nudgeId, recipientId,
                sender.getId(), "Ayşe Kaya", TODAY));
    }

    @Test
    void nudge_checksAccessFirstAsAdminThenTheRulesInOrderBeforeSaving() {
        // Arrange
        givenToday();
        givenSaveAssignsId(UUID.randomUUID());

        // Act
        service.nudge(sender, organizationId, recipientId);

        // Assert
        InOrder order = inOrder(organizationAccessService, dailyUpdateNudgeValidator, dailyUpdateNudgeRepository);
        order.verify(organizationAccessService).verifyOrganizationAccess(organizationId, sender.getId(),
                OrganizationMemberRole.ADMIN, App.KAIZEN);
        order.verify(dailyUpdateNudgeValidator).ensureNotSelf(organizationId, sender, recipientId);
        order.verify(dailyUpdateNudgeValidator).ensureExpectedPoster(organizationId, recipientId);
        order.verify(dailyUpdateNudgeValidator).ensureNotPosted(recipientId, today);
        order.verify(dailyUpdateNudgeValidator).ensureNotNudged(organizationId, recipientId, today);
        order.verify(dailyUpdateNudgeRepository).saveAndFlush(any(DailyUpdateNudge.class));
    }

    @Test
    void nudge_callerBelowAdmin_isForbiddenAndNothingElseRuns() {
        // Arrange
        doThrow(new ForbiddenException(ExceptionDomainOrganizationError.SEC_INSUFFICIENT_PERMISSIONS))
                .when(organizationAccessService)
                .verifyOrganizationAccess(organizationId, sender.getId(), OrganizationMemberRole.ADMIN,
                        App.KAIZEN);

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, recipientId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORG_SEC_003");
        verifyNoInteractions(dailyUpdateNudgeValidator, dailyUpdateNudgeRepository, applicationEventPublisher);
    }

    @Test
    void nudge_callerWithoutKaizen_isForbiddenAndNothingElseRuns() {
        // Arrange
        doThrow(new ForbiddenException(ExceptionDomainAccessError.SEC_APP_ACCESS_DENIED))
                .when(organizationAccessService)
                .verifyOrganizationAccess(organizationId, sender.getId(), OrganizationMemberRole.ADMIN,
                        App.KAIZEN);

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, recipientId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("errorCode", "APP_SEC_001");
        verifyNoInteractions(dailyUpdateNudgeValidator, dailyUpdateNudgeRepository, applicationEventPublisher);
    }

    @Test
    void nudge_callerNotAMember_isNotFoundAndNothingElseRuns() {
        // Arrange
        doThrow(new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_MEMBER_FOUND))
                .when(organizationAccessService)
                .verifyOrganizationAccess(organizationId, sender.getId(), OrganizationMemberRole.ADMIN,
                        App.KAIZEN);

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, recipientId))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(dailyUpdateNudgeValidator, dailyUpdateNudgeRepository, applicationEventPublisher);
    }

    @Test
    void nudge_selfNudge_isRefusedBeforeAnySave() {
        // Arrange
        doThrow(new ValidationException(ExceptionDomainDailyUpdateError.VAL_NUDGE_SELF))
                .when(dailyUpdateNudgeValidator).ensureNotSelf(organizationId, sender, sender.getId());

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, sender.getId()))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_VAL_001");
        verify(dailyUpdateNudgeRepository, never()).saveAndFlush(any(DailyUpdateNudge.class));
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void nudge_recipientAlreadyPosted_isRefusedBeforeAnySave() {
        // Arrange
        givenToday();
        doThrow(new ValidationException(ExceptionDomainDailyUpdateError.VAL_NUDGE_RECIPIENT_POSTED))
                .when(dailyUpdateNudgeValidator).ensureNotPosted(recipientId, today);

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, recipientId))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_VAL_002");
        verify(dailyUpdateNudgeRepository, never()).saveAndFlush(any(DailyUpdateNudge.class));
    }

    @Test
    void nudge_secondNudgeTheSameDay_isAConflictAndPublishesNothing() {
        // Arrange
        givenToday();
        doThrow(new ConflictException(ExceptionDomainDailyUpdateError.STA_NUDGE_ALREADY_SENT))
                .when(dailyUpdateNudgeValidator).ensureNotNudged(organizationId, recipientId, today);

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, recipientId))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "DLY_STA_001");
        verify(dailyUpdateNudgeRepository, never()).saveAndFlush(any(DailyUpdateNudge.class));
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void nudge_concurrentNudgeWinsTheUniqueKey_answersTheSameConflictAndPublishesNothing() {
        // Arrange
        givenToday();
        ConstraintViolationException violation = new ConstraintViolationException("duplicate",
                new SQLException("duplicate"), DailyUpdateConstant.NUDGE_UNIQUE_CONSTRAINT);
        when(dailyUpdateNudgeRepository.saveAndFlush(any(DailyUpdateNudge.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", violation));
        ConflictException conflict = new ConflictException(ExceptionDomainDailyUpdateError.STA_NUDGE_ALREADY_SENT);
        when(dailyUpdateNudgeValidator.alreadyNudged(recipientId, today)).thenReturn(conflict);

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, recipientId)).isSameAs(conflict);
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void nudge_otherIntegrityFailure_isRethrownAsIs() {
        // Arrange
        givenToday();
        ConstraintViolationException violation = new ConstraintViolationException("fk",
                new SQLException("fk"), "daily_update_nudges_recipient_fk");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("fk", violation);
        when(dailyUpdateNudgeRepository.saveAndFlush(any(DailyUpdateNudge.class))).thenThrow(failure);

        // Act and assert
        assertThatThrownBy(() -> service.nudge(sender, organizationId, recipientId)).isSameAs(failure);
        verify(dailyUpdateNudgeValidator, never()).alreadyNudged(any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }
}
