package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// 2. Third-party
import org.hibernate.exception.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckOutcomeRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckStatusRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CheckResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckStatus;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;
import com.korur.oba.apps.kazan.cyberscore.mapper.CheckMapper;
import com.korur.oba.apps.kazan.cyberscore.repository.CheckRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreService;
import com.korur.oba.apps.kazan.cyberscore.validator.CheckValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class CheckServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID checkId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Mock
    private CheckRepository checkRepository;

    @Mock
    private CyberscoreService cyberscoreService;

    @Mock
    private LoggingService loggingService;

    private CheckServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real mapper and validator run over the mocked repository, so mapping rules and error codes are the production ones.
        CheckValidator validator = new CheckValidator(checkRepository, loggingService);
        service = new CheckServiceImpl(checkRepository, Mappers.getMapper(CheckMapper.class), validator,
                cyberscoreService, loggingService);
        user = User.builder().id(userId).build();
        lenient().when(checkRepository.saveAndFlush(any(Check.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static CheckRequest request(String name, int weight, CheckCategory category, CheckOutcome outcome,
                                        CheckStatus status) {
        return new CheckRequest(name, "description", "expected", weight, null, category, outcome, status);
    }

    private Check stored(int weight, CheckCategory category, CheckOutcome outcome, CheckStatus status) {
        return Check.builder().id(checkId).organizationId(organizationId).name("name").description("d")
                .expected("e").weight(weight).sortOrder(3).category(category).outcome(outcome).status(status).build();
    }

    private void givenStored(Check check) {
        when(checkRepository.findByIdAndOrganizationId(checkId, organizationId)).thenReturn(Optional.of(check));
    }

    @Nested
    class CreateCheck {

        @Test
        void noOutcomeAndNoStatus_startsAsTodoNotStartedAndRecalculates() {
            CheckResponse response = service.createCheck(user, organizationId,
                    request("n", 5, CheckCategory.DEVICES, null, null));

            assertThat(response.outcome()).isEqualTo(CheckOutcome.TODO);
            assertThat(response.status()).isEqualTo(CheckStatus.NOT_STARTED);
            assertThat(response.sortOrder()).isZero();
            InOrder order = inOrder(cyberscoreService, checkRepository);
            order.verify(cyberscoreService).lockScore(organizationId);
            order.verify(checkRepository).saveAndFlush(any(Check.class));
            order.verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_CREATED, userId);
        }

        @Test
        void organizationComesFromTheCallerNotTheBody() {
            service.createCheck(user, organizationId, request("n", 5, null, null, null));

            verify(checkRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                    check -> organizationId.equals(check.getOrganizationId())));
        }

        @Test
        void outcomeInTheBody_derivesTheStatusFromIt() {
            CheckResponse response = service.createCheck(user, organizationId,
                    request("n", 5, null, CheckOutcome.PASS, null));

            assertThat(response.status()).isEqualTo(CheckStatus.DONE);
        }

        @Test
        void statusInTheBody_winsOverTheDerivedOne() {
            CheckResponse response = service.createCheck(user, organizationId,
                    request("n", 5, null, CheckOutcome.PASS, CheckStatus.IN_PROGRESS));

            assertThat(response.status()).isEqualTo(CheckStatus.IN_PROGRESS);
        }

        @Test
        void nameTaken_throwsConflictAndSavesNothing() {
            when(checkRepository.existsByOrganizationIdAndName(organizationId, "n")).thenReturn(true);

            assertThatThrownBy(() -> service.createCheck(user, organizationId, request("n", 5, null, null, null)))
                    .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.STA_CHECK_NAME_DUPLICATE.getCode()));

            verify(checkRepository, never()).saveAndFlush(any());
            verify(cyberscoreService, never()).recalculate(any(), any(), any());
        }

        @Test
        void nameRaceLostOnTheUniqueConstraint_throwsConflict() {
            when(checkRepository.saveAndFlush(any(Check.class))).thenThrow(raceOn("checks_organization_id_name_uk"));

            assertThatThrownBy(() -> service.createCheck(user, organizationId, request("n", 5, null, null, null)))
                    .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.STA_CHECK_NAME_DUPLICATE.getCode()));

            verify(cyberscoreService, never()).recalculate(any(), any(), any());
        }

        @Test
        void otherIntegrityFailure_isRethrownUnchanged() {
            DataIntegrityViolationException other = raceOn("checks_weight_range_ck");
            when(checkRepository.saveAndFlush(any(Check.class))).thenThrow(other);

            assertThatThrownBy(() -> service.createCheck(user, organizationId, request("n", 5, null, null, null)))
                    .isSameAs(other);
        }

        private DataIntegrityViolationException raceOn(String constraint) {
            return new DataIntegrityViolationException("violation",
                    new ConstraintViolationException("violation", new SQLException("violation"), constraint));
        }
    }

    @Nested
    class UpdateCheck {

        @Test
        void checkOfAnotherOrganization_throwsNotFoundAndWritesNothing() {
            when(checkRepository.findByIdAndOrganizationId(checkId, organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCheck(user, organizationId, checkId,
                    request("n", 5, null, null, null)))
                    .isInstanceOfSatisfying(NotFoundException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(ExceptionDomainKazanError.NOT_CHECK_FOUND.getCode()));

            verify(checkRepository, never()).saveAndFlush(any());
        }

        @Test
        void textOnlyEdit_keepsOutcomeAndStatusAndDoesNotRecalculate() {
            givenStored(stored(5, CheckCategory.DEVICES, CheckOutcome.PASS, CheckStatus.IN_PROGRESS));

            CheckResponse response = service.updateCheck(user, organizationId, checkId,
                    request("renamed", 5, CheckCategory.DEVICES, null, null));

            assertThat(response.name()).isEqualTo("renamed");
            assertThat(response.outcome()).isEqualTo(CheckOutcome.PASS);
            assertThat(response.status()).isEqualTo(CheckStatus.IN_PROGRESS);
            verify(cyberscoreService).lockScore(organizationId);
            verify(cyberscoreService, never()).recalculate(any(), any(), any());
        }

        @Test
        void statusOnlyEditThroughTheBody_doesNotRecalculate() {
            givenStored(stored(5, null, CheckOutcome.TODO, CheckStatus.NOT_STARTED));

            CheckResponse response = service.updateCheck(user, organizationId, checkId,
                    request("name", 5, null, CheckOutcome.TODO, CheckStatus.IN_PROGRESS));

            assertThat(response.status()).isEqualTo(CheckStatus.IN_PROGRESS);
            verify(cyberscoreService, never()).recalculate(any(), any(), any());
        }

        @Test
        void sortOrderOnlyEdit_doesNotRecalculate() {
            givenStored(stored(5, null, CheckOutcome.TODO, CheckStatus.NOT_STARTED));

            service.updateCheck(user, organizationId, checkId,
                    new CheckRequest("name", "d", "e", 5, 9, null, null, null));

            verify(cyberscoreService, never()).recalculate(any(), any(), any());
        }

        @Test
        void weightChange_recalculatesAsUpdated() {
            givenStored(stored(5, CheckCategory.DEVICES, CheckOutcome.PASS, CheckStatus.DONE));

            service.updateCheck(user, organizationId, checkId, request("name", 9, CheckCategory.DEVICES, null, null));

            verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_UPDATED, userId);
        }

        @Test
        void categoryChange_recalculatesAsUpdated() {
            givenStored(stored(5, CheckCategory.DEVICES, CheckOutcome.PASS, CheckStatus.DONE));

            service.updateCheck(user, organizationId, checkId, request("name", 5, null, null, null));

            verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_UPDATED, userId);
        }

        @Test
        void outcomeOnlyChange_recalculatesAsOutcomeChangedAndDerivesTheStatus() {
            givenStored(stored(5, CheckCategory.DEVICES, CheckOutcome.TODO, CheckStatus.NOT_STARTED));

            CheckResponse response = service.updateCheck(user, organizationId, checkId,
                    request("name", 5, CheckCategory.DEVICES, CheckOutcome.FAIL, null));

            assertThat(response.status()).isEqualTo(CheckStatus.DONE);
            verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_OUTCOME_CHANGED, userId);
        }

        @Test
        void weightAndOutcomeChangeTogether_recalculatesOnceAsUpdated() {
            givenStored(stored(5, CheckCategory.DEVICES, CheckOutcome.TODO, CheckStatus.NOT_STARTED));

            service.updateCheck(user, organizationId, checkId,
                    request("name", 8, CheckCategory.DEVICES, CheckOutcome.PASS, null));

            verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_UPDATED, userId);
            verify(cyberscoreService, org.mockito.Mockito.times(1)).recalculate(any(), any(), any());
        }

        @Test
        void outcomeChangeWithStatusInTheBody_bodyStatusWins() {
            givenStored(stored(5, null, CheckOutcome.TODO, CheckStatus.NOT_STARTED));

            CheckResponse response = service.updateCheck(user, organizationId, checkId,
                    request("name", 5, null, CheckOutcome.PASS, CheckStatus.IN_PROGRESS));

            assertThat(response.status()).isEqualTo(CheckStatus.IN_PROGRESS);
            verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_OUTCOME_CHANGED, userId);
        }

        @Test
        void renameToNameOfAnotherCheck_throwsConflict() {
            givenStored(stored(5, null, CheckOutcome.TODO, CheckStatus.NOT_STARTED));
            when(checkRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, "taken", checkId)).thenReturn(true);

            assertThatThrownBy(() -> service.updateCheck(user, organizationId, checkId,
                    request("taken", 5, null, null, null))).isInstanceOf(ConflictException.class);

            verify(checkRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class SetCheckOutcome {

        @ParameterizedTest
        @CsvSource({"TODO,NOT_STARTED", "NOT_APPLICABLE,NOT_APPLICABLE", "PASS,DONE", "PARTIAL,DONE", "FAIL,DONE",
                "NO_LICENSE,DONE"})
        void changedOutcome_derivesTheStatusAndRecalculates(CheckOutcome outcome, CheckStatus expected) {
            CheckOutcome current = outcome == CheckOutcome.FAIL ? CheckOutcome.PASS : CheckOutcome.FAIL;
            givenStored(stored(5, null, current, CheckStatus.IN_PROGRESS));

            CheckResponse response = service.setCheckOutcome(user, organizationId, checkId,
                    new CheckOutcomeRequest(outcome));

            assertThat(response.outcome()).isEqualTo(outcome);
            assertThat(response.status()).isEqualTo(expected);
            verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_OUTCOME_CHANGED, userId);
        }

        @Test
        void sameOutcome_setsTheDerivedStatusWithoutRecalculating() {
            givenStored(stored(5, null, CheckOutcome.PASS, CheckStatus.IN_PROGRESS));

            CheckResponse response = service.setCheckOutcome(user, organizationId, checkId,
                    new CheckOutcomeRequest(CheckOutcome.PASS));

            assertThat(response.status()).isEqualTo(CheckStatus.DONE);
            verify(cyberscoreService, never()).recalculate(any(), any(), any());
        }

        @Test
        void checkOfAnotherOrganization_throwsNotFound() {
            when(checkRepository.findByIdAndOrganizationId(checkId, organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setCheckOutcome(user, organizationId, checkId,
                    new CheckOutcomeRequest(CheckOutcome.PASS))).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class SetCheckStatus {

        @Test
        void validStatus_setsItWithoutRecalculatingButStillLocks() {
            givenStored(stored(5, null, CheckOutcome.PASS, CheckStatus.DONE));

            CheckResponse response = service.setCheckStatus(user, organizationId, checkId,
                    new CheckStatusRequest(CheckStatus.IN_PROGRESS));

            assertThat(response.status()).isEqualTo(CheckStatus.IN_PROGRESS);
            assertThat(response.outcome()).isEqualTo(CheckOutcome.PASS);
            verify(cyberscoreService).lockScore(organizationId);
            verify(cyberscoreService, never()).recalculate(any(), any(), any());
        }

        @Test
        void checkOfAnotherOrganization_throwsNotFound() {
            when(checkRepository.findByIdAndOrganizationId(checkId, organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setCheckStatus(user, organizationId, checkId,
                    new CheckStatusRequest(CheckStatus.DONE))).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class DeleteCheck {

        @Test
        void existingCheck_deletesFlushesThenRecalculatesAsDeleted() {
            Check check = stored(5, null, CheckOutcome.PASS, CheckStatus.DONE);
            givenStored(check);

            service.deleteCheck(user, organizationId, checkId);

            InOrder order = inOrder(cyberscoreService, checkRepository);
            order.verify(cyberscoreService).lockScore(organizationId);
            order.verify(checkRepository).delete(check);
            order.verify(checkRepository).flush();
            order.verify(cyberscoreService).recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_DELETED, userId);
        }

        @Test
        void checkOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            when(checkRepository.findByIdAndOrganizationId(checkId, organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteCheck(user, organizationId, checkId))
                    .isInstanceOf(NotFoundException.class);

            verify(checkRepository, never()).delete(any(Check.class));
            verify(cyberscoreService, never()).recalculate(any(), eq(CyberscoreSnapshotEventType.CHECK_DELETED), any());
        }
    }
}
