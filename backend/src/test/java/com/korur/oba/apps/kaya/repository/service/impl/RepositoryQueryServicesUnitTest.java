package com.korur.oba.apps.kaya.repository.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.mapper.RepositoryMapper;
import com.korur.oba.apps.kaya.repository.mock_data.RepositoryMocks;
import com.korur.oba.apps.kaya.repository.repository.RepositoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryQueryServicesUnitTest {

    @Mock
    private RepositoryRepository repositoryRepository;
    @Mock
    private LoggingService loggingService;

    private RepositoryQueryServiceImpl repositoryQuery;
    private UUID organizationId;
    private Repository repository;

    @BeforeEach
    void setUp() {
        repositoryQuery = new RepositoryQueryServiceImpl(repositoryRepository,
                Mappers.getMapper(RepositoryMapper.class), loggingService);
        organizationId = UUID.randomUUID();
        repository = RepositoryMocks.repository(organizationId, "github.com/o/r", RepositoryStatus.ACTIVE);
    }

    @Test
    void getRepositories_returnsResponsesInRepositoryOrder() {
        when(repositoryRepository.findByOrganizationIdOrderByNameAscRemoteUrlAsc(organizationId))
                .thenReturn(List.of(repository));

        List<RepositoryResponse> result = repositoryQuery.getRepositories(organizationId);

        assertEquals(1, result.size());
        assertEquals(repository.getId(), result.get(0).id());
    }

    @Test
    void getEntityById_foundInOrganization_returnsEntity() {
        when(repositoryRepository.findByIdAndOrganizationId(repository.getId(), organizationId))
                .thenReturn(Optional.of(repository));

        assertSame(repository, repositoryQuery.getEntityById(organizationId, repository.getId()));
    }

    @Test
    void getEntityById_notInOrganization_throwsNotFoundWithRepositoryId() {
        UUID id = UUID.randomUUID();
        when(repositoryRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> repositoryQuery.getEntityById(organizationId, id));

        assertEquals("KAY_NF_001", exception.getErrorCode());
        assertEquals(id.toString(), exception.getErrorValues().get("repositoryId"));
    }
}
