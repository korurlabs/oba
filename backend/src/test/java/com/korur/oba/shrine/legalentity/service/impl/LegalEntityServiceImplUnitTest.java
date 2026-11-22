package com.korur.oba.shrine.legalentity.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import com.korur.oba.shrine.document.service.document.DocumentQueryService;
import com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityCreateRequest;
import com.korur.oba.shrine.legalentity.dto.request.LegalEntityUpdateRequest;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.exception.ExceptionDomainLegalEntityError;
import com.korur.oba.shrine.legalentity.mapper.LegalEntityMapperImpl;
import com.korur.oba.shrine.legalentity.repository.LegalEntityRepository;
import com.korur.oba.shrine.legalentity.validator.LegalEntityValidator;

@ExtendWith(MockitoExtension.class)
class LegalEntityServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID legalEntityId = UUID.randomUUID();

    @Mock
    private LegalEntityRepository legalEntityRepository;

    @Mock
    private DocumentQueryService documentQueryService;

    @Mock
    private DocumentTemplateQueryService documentTemplateQueryService;

    @Mock
    private LoggingService loggingService;

    private LegalEntityServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository, so the error codes and
        // the mapped fields are the production ones.
        service = new LegalEntityServiceImpl(legalEntityRepository, new LegalEntityMapperImpl(),
                new LegalEntityValidator(legalEntityRepository, loggingService), documentQueryService,
                documentTemplateQueryService, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private LegalEntity stored() {
        return LegalEntity.builder()
                .id(legalEntityId)
                .organizationId(organizationId)
                .shortName("Korur B.V.")
                .legalName("Korur B.V.")
                .country("NLD")
                .vatNumber("NL123456789B01")
                .city("Amsterdam")
                .build();
    }

    @Nested
    class Create {

        @Test
        void request_savesTrimmedFieldsWithTheHeaderOrganization() {
            // Arrange
            when(legalEntityRepository.save(any(LegalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            LegalEntityCreateRequest request = new LegalEntityCreateRequest(" Korur Siber ",
                    " Korur Siber Guvenlik Limited Sirketi ", " TUR ", " 123 ", null, " Levent Cd. 1 ", "34330",
                    "Istanbul", "Turkish law", " Istanbul Courts ");

            // Act
            LegalEntityDetailResponse response = service.create(organizationId, user, request);

            // Assert
            ArgumentCaptor<LegalEntity> saved = ArgumentCaptor.forClass(LegalEntity.class);
            verify(legalEntityRepository).save(saved.capture());
            LegalEntity entity = saved.getValue();
            assertThat(entity.getOrganizationId()).isEqualTo(organizationId);
            assertThat(entity.getShortName()).isEqualTo("Korur Siber");
            assertThat(entity.getLegalName()).isEqualTo("Korur Siber Guvenlik Limited Sirketi");
            assertThat(entity.getCountry()).isEqualTo("TUR");
            assertThat(entity.getRegistrationNumber()).isEqualTo("123");
            assertThat(entity.getVatNumber()).isNull();
            assertThat(entity.getStreet()).isEqualTo("Levent Cd. 1");
            assertThat(entity.getCourt()).isEqualTo("Istanbul Courts");
            assertThat(response.shortName()).isEqualTo("Korur Siber");
            assertThat(response.governingLaw()).isEqualTo("Turkish law");
        }

        @Test
        void takenShortName_throwsConflictAndSavesNothing() {
            // Arrange
            when(legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCase(organizationId, "korur b.v."))
                    .thenReturn(true);
            LegalEntityCreateRequest request = new LegalEntityCreateRequest(" korur b.v. ", "Korur B.V.", "NLD",
                    null, null, null, null, null, null, null);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, request))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainLegalEntityError.STA_LEGAL_ENTITY_SHORT_NAME_TAKEN.getCode());
            verify(legalEntityRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void request_replacesEveryFieldAndClearsNullOnes() {
            // Arrange
            LegalEntity legalEntity = stored();
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.of(legalEntity));
            when(legalEntityRepository.saveAndFlush(legalEntity)).thenReturn(legalEntity);
            LegalEntityUpdateRequest request = new LegalEntityUpdateRequest("Korur B.V. ", "Korur Holding B.V.",
                    "NLD", "87654321", null, null, null, null, "Dutch law", null);

            // Act
            LegalEntityDetailResponse response = service.update(organizationId, legalEntityId, request);

            // Assert
            verify(legalEntityRepository).existsByOrganizationIdAndShortNameIgnoreCaseAndIdNot(
                    organizationId, "Korur B.V.", legalEntityId);
            assertThat(legalEntity.getLegalName()).isEqualTo("Korur Holding B.V.");
            assertThat(legalEntity.getRegistrationNumber()).isEqualTo("87654321");
            assertThat(legalEntity.getVatNumber()).isNull();
            assertThat(legalEntity.getCity()).isNull();
            assertThat(legalEntity.getOrganizationId()).isEqualTo(organizationId);
            assertThat(response.id()).isEqualTo(legalEntityId);
            assertThat(response.governingLaw()).isEqualTo("Dutch law");
        }

        @Test
        void legalEntityOfAnotherOrganization_throwsNotFoundAndSavesNothing() {
            // Arrange
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.empty());
            LegalEntityUpdateRequest request = new LegalEntityUpdateRequest("Korur B.V.", "Korur B.V.", "NLD",
                    null, null, null, null, null, null, null);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, legalEntityId, request))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainLegalEntityError.NOT_LEGAL_ENTITY_FOUND.getCode());
            verify(legalEntityRepository, never()).saveAndFlush(any());
        }

        @Test
        void shortNameOfAnother_throwsConflictAndSavesNothing() {
            // Arrange
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.of(stored()));
            when(legalEntityRepository.existsByOrganizationIdAndShortNameIgnoreCaseAndIdNot(
                    organizationId, "Korur Inc.", legalEntityId)).thenReturn(true);
            LegalEntityUpdateRequest request = new LegalEntityUpdateRequest("Korur Inc.", "Korur Inc.", "USA",
                    null, null, null, null, null, null, null);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, legalEntityId, request))
                    .isInstanceOf(ConflictException.class);
            verify(legalEntityRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void legalEntityOfTheOrganization_deletesAndFlushes() {
            // Arrange
            LegalEntity legalEntity = stored();
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.of(legalEntity));

            // Act
            service.delete(organizationId, legalEntityId);

            // Assert
            InOrder order = inOrder(legalEntityRepository);
            order.verify(legalEntityRepository).delete(legalEntity);
            order.verify(legalEntityRepository).flush();
        }

        @Test
        void legalEntityOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            // Arrange
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, legalEntityId))
                    .isInstanceOf(NotFoundException.class);
            verify(legalEntityRepository, never()).delete(any(LegalEntity.class));
        }

        @Test
        void legalEntityNamedByADocument_throwsConflictInUseAndDeletesNothing() {
            // Arrange
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.of(stored()));
            when(documentQueryService.existsByLegalEntity(organizationId, legalEntityId)).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, legalEntityId))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainLegalEntityError.STA_LEGAL_ENTITY_IN_USE.getCode());
            verify(legalEntityRepository, never()).delete(any(LegalEntity.class));
        }

        @Test
        void legalEntityNamedByATemplate_throwsConflictInUseAndDeletesNothing() {
            // Arrange
            when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                    .thenReturn(Optional.of(stored()));
            when(documentTemplateQueryService.existsByLegalEntity(organizationId, legalEntityId)).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, legalEntityId))
                    .isInstanceOf(ConflictException.class)
                    .extracting("errorCode")
                    .isEqualTo(ExceptionDomainLegalEntityError.STA_LEGAL_ENTITY_IN_USE.getCode());
            verify(legalEntityRepository, never()).delete(any(LegalEntity.class));
        }
    }
}
