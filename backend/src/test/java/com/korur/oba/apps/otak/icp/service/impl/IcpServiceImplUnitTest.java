package com.korur.oba.apps.otak.icp.service.impl;

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
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.icp.dto.request.IcpCreateRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpUpdateRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.entity.Icp;
import com.korur.oba.apps.otak.icp.enums.Industry;
import com.korur.oba.apps.otak.icp.enums.TargetPersonRole;
import com.korur.oba.apps.otak.icp.mapper.IcpMapper;
import com.korur.oba.apps.otak.icp.mapper.IcpMapperImpl;
import com.korur.oba.apps.otak.icp.repository.IcpRepository;
import com.korur.oba.apps.otak.icp.validator.IcpValidator;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class IcpServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private IcpRepository icpRepository;

    @Mock
    private LoggingService loggingService;

    private IcpValidator validator;
    private IcpServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        // The real validator and mapper run over the mocked repository, so the error codes and
        // the mapped fields are the production ones.
        validator = new IcpValidator(icpRepository, loggingService);
        IcpMapper mapper = new IcpMapperImpl();
        service = new IcpServiceImpl(icpRepository, mapper, validator, loggingService);
        user = User.builder().build();
        user.setId(UUID.randomUUID());
    }

    private static IcpCreateRequest createRequest(String name, RelationSize min, RelationSize max) {
        return new IcpCreateRequest(name, Industry.MANUFACTURING, Country.NL, min, max, TargetPersonRole.CEO,
                "Family-owned producers");
    }

    private static IcpUpdateRequest updateRequest(String name, RelationSize min, RelationSize max) {
        return new IcpUpdateRequest(name, Industry.MANUFACTURING, Country.NL, min, max, TargetPersonRole.CEO,
                "Family-owned producers");
    }

    private Icp stored(String name) {
        Icp icp = Icp.builder().name(name).industry(Industry.RETAIL).organizationId(organizationId).build();
        icp.setId(UUID.randomUUID());
        when(icpRepository.findByIdAndOrganizationId(icp.getId(), organizationId)).thenReturn(Optional.of(icp));
        return icp;
    }

    @Nested
    class Create {

        @Test
        void validRequest_savesTrimmedNameInTheHeaderOrganization() {
            // Arrange
            when(icpRepository.save(any(Icp.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            IcpDetailResponse response = service.create(organizationId, user,
                    createRequest("  Dutch textile  ", RelationSize.SIZE_11_50, RelationSize.SIZE_201_500));

            // Assert
            ArgumentCaptor<Icp> captor = ArgumentCaptor.forClass(Icp.class);
            verify(icpRepository).save(captor.capture());
            Icp saved = captor.getValue();
            assertThat(saved.getOrganizationId()).isEqualTo(organizationId);
            assertThat(saved.getName()).isEqualTo("Dutch textile");
            assertThat(saved.getIndustry()).isEqualTo(Industry.MANUFACTURING);
            assertThat(saved.getCountry()).isEqualTo(Country.NL);
            assertThat(response.name()).isEqualTo("Dutch textile");
            assertThat(response.companySizeMin()).isEqualTo(RelationSize.SIZE_11_50);
        }

        @Test
        void validRequest_checksSizeAndNameOnTheTrimmedNameBeforeSaving() {
            // Arrange
            when(icpRepository.save(any(Icp.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            service.create(organizationId, user,
                    createRequest(" Retail ", RelationSize.SIZE_1_10, RelationSize.SIZE_1_10));

            // Assert
            InOrder order = inOrder(icpRepository);
            order.verify(icpRepository).existsByOrganizationIdAndNameIgnoreCase(organizationId, "Retail");
            order.verify(icpRepository).save(any(Icp.class));
        }

        @Test
        void minAboveMax_throwsValidationAndSavesNothing() {
            assertThatThrownBy(() -> service.create(organizationId, user,
                    createRequest("Retail", RelationSize.SIZE_501_1000, RelationSize.SIZE_1_10)))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.VAL_ICP_SIZE_RANGE.getCode()));
            verify(icpRepository, never()).save(any());
        }

        @Test
        void nameTaken_throwsConflictAndSavesNothing() {
            // Arrange
            when(icpRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "Retail")).thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.create(organizationId, user, createRequest("Retail", null, null)))
                    .isInstanceOfSatisfying(ConflictException.class, (ConflictException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.STA_ICP_NAME_TAKEN.getCode()));
            verify(icpRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void validRequest_excludesOwnIdFromTheNameCheckAndReplacesEveryField() {
            // Arrange
            Icp icp = stored("Retail");
            when(icpRepository.saveAndFlush(icp)).thenReturn(icp);

            // Act
            IcpDetailResponse response = service.update(organizationId, icp.getId(),
                    new IcpUpdateRequest(" Retail ", Industry.ENERGY, null, null, null, null, null));

            // Assert
            verify(icpRepository).existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "Retail", icp.getId());
            verify(icpRepository, never()).existsByOrganizationIdAndNameIgnoreCase(any(), any());
            assertThat(icp.getName()).isEqualTo("Retail");
            assertThat(icp.getIndustry()).isEqualTo(Industry.ENERGY);
            assertThat(icp.getOrganizationId()).isEqualTo(organizationId);
            assertThat(response.industry()).isEqualTo(Industry.ENERGY);
        }

        @Test
        void icpOfAnotherOrganization_throwsNotFoundBeforeAnyOtherCheck() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            when(icpRepository.findByIdAndOrganizationId(icpId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, icpId,
                    updateRequest("Retail", RelationSize.SIZE_501_1000, RelationSize.SIZE_1_10)))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_ICP_FOUND.getCode()));
            verify(icpRepository, never()).saveAndFlush(any());
        }

        @Test
        void minAboveMax_throwsValidationAndSavesNothing() {
            // Arrange
            Icp icp = stored("Retail");

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, icp.getId(),
                    updateRequest("Retail", RelationSize.SIZE_501_1000, RelationSize.SIZE_1_10)))
                    .isInstanceOf(ValidationException.class);
            verify(icpRepository, never()).saveAndFlush(any());
        }

        @Test
        void nameOfAnotherIcp_throwsConflictAndSavesNothing() {
            // Arrange
            Icp icp = stored("Retail");
            when(icpRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, "Energy", icp.getId()))
                    .thenReturn(true);

            // Act and assert
            assertThatThrownBy(() -> service.update(organizationId, icp.getId(), updateRequest("Energy", null, null)))
                    .isInstanceOf(ConflictException.class);
            verify(icpRepository, never()).saveAndFlush(any());
            assertThat(icp.getName()).isEqualTo("Retail");
        }
    }

    @Nested
    class Delete {

        @Test
        void icpOfOrganization_isDeleted() {
            // Arrange
            Icp icp = stored("Retail");

            // Act
            service.delete(organizationId, icp.getId());

            // Assert
            verify(icpRepository).delete(icp);
        }

        @Test
        void icpOfAnotherOrganization_throwsNotFoundAndDeletesNothing() {
            // Arrange
            UUID icpId = UUID.randomUUID();
            when(icpRepository.findByIdAndOrganizationId(icpId, organizationId)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.delete(organizationId, icpId))
                    .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOtakError.NOT_ICP_FOUND.getCode()));
            verify(icpRepository, never()).delete(any(Icp.class));
        }
    }
}
