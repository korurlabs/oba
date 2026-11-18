package com.korur.oba.apps.otak.icp.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.icp.dto.request.IcpQueryRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.dto.response.IcpListResponse;
import com.korur.oba.apps.otak.icp.entity.Icp;
import com.korur.oba.apps.otak.icp.enums.Industry;
import com.korur.oba.apps.otak.icp.mapper.IcpMapperImpl;
import com.korur.oba.apps.otak.icp.repository.IcpRepository;
import com.korur.oba.apps.otak.icp.validator.IcpValidator;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class IcpQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private IcpRepository icpRepository;

    @Mock
    private LoggingService loggingService;

    private IcpQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IcpQueryServiceImpl(icpRepository, new IcpMapperImpl(),
                new IcpValidator(icpRepository, loggingService), loggingService);
    }

    private Icp icp(String name) {
        Icp icp = Icp.builder().name(name).industry(Industry.RETAIL).organizationId(organizationId).build();
        icp.setId(UUID.randomUUID());
        return icp;
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_pageTwoOfTen_ordersByNameThenIdAndMapsTheItems() {
        // Arrange
        Icp stored = icp("Alpha");
        IcpQueryRequest request = new IcpQueryRequest();
        request.setPage(2);
        request.setAmount(10);
        request.setSearch("alp");
        when(icpRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stored)));

        // Act
        Page<IcpListResponse> result = service.list(organizationId, request);

        // Assert
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(icpRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList())
                .containsExactly("name", "id");
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(stored.getId());
        assertThat(result.getContent().get(0).name()).isEqualTo("Alpha");
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_arbitrarySortBy_isIgnored() {
        // Arrange
        IcpQueryRequest request = new IcpQueryRequest();
        request.setSortBy("organizationId");
        when(icpRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        service.list(organizationId, request);

        // Assert
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(icpRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().stream().map(order -> order.getProperty()).toList())
                .containsExactly("name", "id");
    }

    @Test
    void get_icpOfOrganization_returnsDetail() {
        // Arrange
        Icp stored = icp("Beta");
        when(icpRepository.findByIdAndOrganizationId(stored.getId(), organizationId)).thenReturn(Optional.of(stored));

        // Act
        IcpDetailResponse result = service.get(organizationId, stored.getId());

        // Assert
        assertThat(result.id()).isEqualTo(stored.getId());
        assertThat(result.name()).isEqualTo("Beta");
    }

    @Test
    void get_icpOfAnotherOrganization_throwsNotFound() {
        // Arrange
        UUID icpId = UUID.randomUUID();
        when(icpRepository.findByIdAndOrganizationId(eq(icpId), eq(organizationId))).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.get(organizationId, icpId))
                .isInstanceOfSatisfying(NotFoundException.class, (NotFoundException exception) ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ExceptionDomainOtakError.NOT_ICP_FOUND.getCode()));
    }

    @Test
    void ensureExists_icpOfOrganization_returnsNormally() {
        // Arrange
        Icp stored = icp("Gamma");
        when(icpRepository.findByIdAndOrganizationId(stored.getId(), organizationId)).thenReturn(Optional.of(stored));

        // Act and assert
        service.ensureExists(organizationId, stored.getId());
        verify(icpRepository).findByIdAndOrganizationId(stored.getId(), organizationId);
    }

    @Test
    void ensureExists_unknownIcp_throwsNotFound() {
        // Arrange
        UUID icpId = UUID.randomUUID();
        when(icpRepository.findByIdAndOrganizationId(icpId, organizationId)).thenReturn(Optional.empty());

        // Act and assert
        assertThatThrownBy(() -> service.ensureExists(organizationId, icpId))
                .isInstanceOf(NotFoundException.class);
    }
}
