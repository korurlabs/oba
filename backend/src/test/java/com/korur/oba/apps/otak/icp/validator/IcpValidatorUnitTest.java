package com.korur.oba.apps.otak.icp.validator;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.icp.entity.Icp;
import com.korur.oba.apps.otak.icp.repository.IcpRepository;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class IcpValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private IcpRepository icpRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private IcpValidator icpValidator;

    @Nested
    class EnsureIcpExists {

        @Test
        void icpOfOrganization_returnsIt() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            Icp icp = Icp.builder().name("Textile").build();
            when(icpRepository.findByIdAndOrganizationId(icpId, organizationId)).thenReturn(Optional.of(icp));

            // Act
            Icp result = icpValidator.ensureIcpExists(organizationId, icpId);

            // Assert
            assertThat(result).isSameAs(icp);
        }

        @Test
        void icpMissingOrOfAnotherOrganization_throwsNotFound() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            when(icpRepository.findByIdAndOrganizationId(icpId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> icpValidator.ensureIcpExists(organizationId, icpId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_ICP_FOUND.getCode()));
        }
    }

    @Nested
    class EnsureSizeRange {

        @Test
        void minAboveMax_throwsValidationException() {
            assertThatThrownBy(() -> icpValidator.ensureSizeRange(RelationSize.SIZE_201_500, RelationSize.SIZE_11_50))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ICP_SIZE_RANGE.getCode()));
        }

        @Test
        void minEqualsMax_passes() {
            assertThatCode(() -> icpValidator.ensureSizeRange(RelationSize.SIZE_51_200, RelationSize.SIZE_51_200))
                    .doesNotThrowAnyException();
        }

        @Test
        void minBelowMax_passes() {
            assertThatCode(() -> icpValidator.ensureSizeRange(RelationSize.SIZE_1_10, RelationSize.SIZE_5001_PLUS))
                    .doesNotThrowAnyException();
        }

        @Test
        void onlyMinSet_passes() {
            assertThatCode(() -> icpValidator.ensureSizeRange(RelationSize.SIZE_5001_PLUS, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void onlyMaxSet_passes() {
            assertThatCode(() -> icpValidator.ensureSizeRange(null, RelationSize.SIZE_1_10))
                    .doesNotThrowAnyException();
        }

        @Test
        void bothNull_passes() {
            assertThatCode(() -> icpValidator.ensureSizeRange(null, null)).doesNotThrowAnyException();
        }
    }

    @Nested
    class EnsureNameFree {

        @Test
        void createWithFreeName_passesAndExcludesNoIcp() {
            // Arrange
            when(icpRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "Textile")).thenReturn(false);

            // Act and assert
            assertThatCode(() -> icpValidator.ensureNameFree(organizationId, "Textile", null))
                    .doesNotThrowAnyException();
            verify(icpRepository, never()).existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
                    any(), any(),
                    any());
        }

        @Test
        void createWithTakenNameDifferingInCase_throwsConflict() {
            // Arrange: the repository method ignores case, so the stored "TEXTILE" matches "textile"
            when(icpRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "textile")).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> icpValidator.ensureNameFree(organizationId, "textile", null))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_ICP_NAME_TAKEN.getCode()));
        }

        @Test
        void updateKeepingOwnName_excludesOwnIdAndPasses() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            when(icpRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "Textile", icpId))
                    .thenReturn(false);

            // Act and assert
            assertThatCode(() -> icpValidator.ensureNameFree(organizationId, "Textile", icpId))
                    .doesNotThrowAnyException();
            verify(icpRepository, never()).existsByOrganizationIdAndNameIgnoreCase(
                    any(), any());
        }

        @Test
        void updateToNameOfAnotherIcp_throwsConflict() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            when(icpRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "Retail", icpId))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> icpValidator.ensureNameFree(organizationId, "Retail", icpId))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_ICP_NAME_TAKEN.getCode()));
        }
    }
}
