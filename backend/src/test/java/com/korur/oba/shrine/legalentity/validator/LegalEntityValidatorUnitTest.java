package com.korur.oba.shrine.legalentity.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError;
import com.korur.oba.shrine.legalentity.repository.LegalEntityRepository;

@ExtendWith(MockitoExtension.class)
class LegalEntityValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID legalEntityId = UUID.randomUUID();

    @Mock
    private LegalEntityRepository legalEntityRepository;

    @Mock
    private LoggingService loggingService;

    private LegalEntityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LegalEntityValidator(legalEntityRepository, loggingService);
    }

    @Nested
    class EnsureLegalEntityExists {

        @Test
        void legalEntityOfTheOrganization_returnsIt() {
            // Arrange
            LegalEntity legalEntity = LegalEntity.builder().id(legalEntityId).organizationId(organizationId).build();
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.of(legalEntity));

            // Act
            LegalEntity result = validator.ensureLegalEntityExists(organizationId, legalEntityId);

            // Assert
            assertThat(result).isSameAs(legalEntity);
        }

        @Test
        void missingOrOfAnotherOrganization_throwsNotFound() {
            // Arrange
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> validator.ensureLegalEntityExists(organizationId, legalEntityId))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND.getCode());
        }
    }

    @Nested
    class EnsureShortNameUnique {

        @Test
        void createWithFreeName_passes() {
            // Arrange
            when(legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCase(organizationId, "Korur B.V."))
                    .thenReturn(false);

            // Act and assert
            assertThatCode(() -> validator.ensureShortNameUnique(organizationId, "Korur B.V.", null))
                    .doesNotThrowAnyException();
            verify(legalEntityRepository, never())
                    .existsByOrganizationIdAndShortNameIgnoreCaseAndIdNot(any(), anyString(), any());
        }

        @Test
        void createWithTakenName_throwsConflict() {
            // Arrange
            when(legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCase(organizationId, "korur b.v."))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> validator.ensureShortNameUnique(organizationId, "korur b.v.", null))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode").isEqualTo(ExceptionDomainLegalEntityError.STA_LEGAL_ENTITY_SHORT_NAME_TAKEN.getCode());
        }

        @Test
        void updateKeepingOwnName_excludesItselfAndPasses() {
            // Arrange
            when(legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCaseAndIdNot(
                    organizationId, "Korur B.V.", legalEntityId)).thenReturn(false);

            // Act and assert
            assertThatCode(() -> validator.ensureShortNameUnique(organizationId, "Korur B.V.", legalEntityId))
                    .doesNotThrowAnyException();
            verify(legalEntityRepository, never()).existsByOrganizationIdAndShortNameIgnoreCase(any(), anyString());
        }

        @Test
        void updateToNameOfAnother_throwsConflict() {
            // Arrange
            when(legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCaseAndIdNot(
                    organizationId, "Korur Inc.", legalEntityId)).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> validator.ensureShortNameUnique(organizationId, "Korur Inc.", legalEntityId))
                    .isInstanceOf(ConflictException.class);
        }
    }
}
