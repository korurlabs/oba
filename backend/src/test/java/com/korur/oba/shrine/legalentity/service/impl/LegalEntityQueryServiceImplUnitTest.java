package com.korur.oba.shrine.legalentity.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;
import com.korur.oba.shrine.legalentity.mapper.LegalEntityMapperImpl;
import com.korur.oba.shrine.legalentity.repository.LegalEntityRepository;
import com.korur.oba.shrine.legalentity.validator.LegalEntityValidator;

@ExtendWith(MockitoExtension.class)
class LegalEntityQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private LegalEntityRepository legalEntityRepository;

    @Mock
    private LoggingService loggingService;

    private LegalEntityQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LegalEntityQueryServiceImpl(legalEntityRepository, new LegalEntityMapperImpl(),
                new LegalEntityValidator(legalEntityRepository, loggingService), loggingService);
    }

    private LegalEntity legalEntity(String shortName) {
        return LegalEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .shortName(shortName)
                .legalName(shortName)
                .country("NLD")
                .registrationNumber("12345678")
                .court("Rechtbank Amsterdam")
                .build();
    }

    @Test
    void list_organizationWithLegalEntities_returnsItemsSortedByShortNameIgnoringCase() {
        // Arrange
        LegalEntity first = legalEntity("Korur B.V.");
        LegalEntity second = legalEntity("korur Inc.");
        when(legalEntityRepository.findAllByOrganizationId(eq(organizationId), any(Sort.class)))
                .thenReturn(List.of(first, second));

        // Act
        List<LegalEntityListResponse> result = service.list(organizationId);

        // Assert
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(legalEntityRepository).findAllByOrganizationId(eq(organizationId), sort.capture());
        Sort.Order byShortName = sort.getValue().getOrderFor("shortName");
        assertThat(byShortName).isNotNull();
        assertThat(byShortName.isAscending()).isTrue();
        assertThat(byShortName.isIgnoreCase()).isTrue();
        assertThat(result).extracting(LegalEntityListResponse::shortName).containsExactly("Korur B.V.", "korur Inc.");
        assertThat(result.get(0).registrationNumber()).isEqualTo("12345678");
    }

    @Test
    void list_organizationWithoutLegalEntities_returnsEmptyList() {
        // Arrange
        when(legalEntityRepository.findAllByOrganizationId(eq(organizationId), any(Sort.class)))
                .thenReturn(List.of());

        // Act
        List<LegalEntityListResponse> result = service.list(organizationId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void get_legalEntityOfTheOrganization_returnsEveryField() {
        // Arrange
        LegalEntity stored = legalEntity("Korur B.V.");
        when(legalEntityRepository.findByIdAndOrganizationId(stored.getId(), organizationId))
                .thenReturn(Optional.of(stored));

        // Act
        LegalEntityDetailResponse result = service.get(organizationId, stored.getId());

        // Assert
        assertThat(result.id()).isEqualTo(stored.getId());
        assertThat(result.court()).isEqualTo("Rechtbank Amsterdam");
        assertThat(result.country()).isEqualTo("NLD");
    }

    @Test
    void getEntity_legalEntityOfAnotherOrganization_throwsNotFound() {
        // Arrange
        UUID legalEntityId = UUID.randomUUID();
        when(legalEntityRepository.findByIdAndOrganizationId(legalEntityId, organizationId))
                .thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.getEntity(organizationId, legalEntityId))
                .isInstanceOf(NotFoundException.class);
    }
}
