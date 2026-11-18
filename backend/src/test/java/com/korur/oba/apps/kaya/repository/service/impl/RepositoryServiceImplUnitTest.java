package com.korur.oba.apps.kaya.repository.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.repository.dto.request.RepositoryUpdateRequest;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.mapper.RepositoryMapper;
import com.korur.oba.apps.kaya.repository.mock_data.RepositoryMocks;
import com.korur.oba.apps.kaya.repository.repository.RepositoryRepository;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryServiceImplUnitTest {

    private static final String REMOTE = "github.com/senseylabs/village";

    @Mock
    private RepositoryRepository repositoryRepository;
    @Mock
    private RepositoryQueryService repositoryQueryService;
    @Mock
    private LoggingService loggingService;

    private RepositoryServiceImpl service;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        service = new RepositoryServiceImpl(repositoryRepository, repositoryQueryService, Mappers.getMapper(RepositoryMapper.class), loggingService);
        organizationId = UUID.randomUUID();
    }

    @Nested
    class UpdateRepository {

        @Test
        void changedFields_areSavedAndReturned() {
            User actor = User.builder().id(UUID.randomUUID()).build();
            Repository repository = RepositoryMocks.repository(organizationId, REMOTE, RepositoryStatus.ACTIVE);
            when(repositoryQueryService.getEntityById(organizationId, repository.getId())).thenReturn(repository);
            when(repositoryRepository.saveAndFlush(repository)).thenReturn(repository);

            RepositoryResponse result = service.updateRepository(actor, organizationId, repository.getId(),
                    new RepositoryUpdateRequest(" Kaya ", "https://k.io/{key}", RepositoryStatus.ARCHIVED));

            assertEquals("Kaya", result.name());
            assertEquals("https://k.io/{key}", result.ticketUrlTemplate());
            assertEquals(RepositoryStatus.ARCHIVED, result.status());
            verify(repositoryRepository).saveAndFlush(repository);
        }

        @Test
        void emptyTemplate_removesIt() {
            User actor = User.builder().id(UUID.randomUUID()).build();
            Repository repository = RepositoryMocks.repository(organizationId, REMOTE, RepositoryStatus.ACTIVE);
            repository.setTicketUrlTemplate("https://k.io/{key}");
            when(repositoryQueryService.getEntityById(organizationId, repository.getId())).thenReturn(repository);
            when(repositoryRepository.saveAndFlush(repository)).thenReturn(repository);

            RepositoryResponse result = service.updateRepository(actor, organizationId, repository.getId(),
                    new RepositoryUpdateRequest(null, "", null));

            assertNull(result.ticketUrlTemplate());
            assertEquals("Village", result.name());
        }
    }

    @Nested
    class ConnectRepository {

        @Test
        void newRepository_insertsAndReturnsTheRepository() {
            Repository repository = RepositoryMocks.repository(organizationId, REMOTE, RepositoryStatus.ACTIVE);
            when(repositoryRepository.insertIfAbsent(organizationId, "Village", REMOTE)).thenReturn(1);
            when(repositoryRepository.findByOrganizationIdAndRemoteUrl(organizationId, REMOTE))
                    .thenReturn(Optional.of(repository));

            Repository result = service.connectRepository(organizationId, "git@GitHub.com:senseylabs/Village.git",
                    " Village ");

            assertSame(repository, result);
            verify(repositoryRepository).insertIfAbsent(organizationId, "Village", REMOTE);
        }

        @Test
        void existingRepository_returnsTheStoredRow() {
            Repository repository = RepositoryMocks.repository(organizationId, REMOTE, RepositoryStatus.ACTIVE);
            when(repositoryRepository.insertIfAbsent(any(), anyString(), anyString())).thenReturn(0);
            when(repositoryRepository.findByOrganizationIdAndRemoteUrl(organizationId, REMOTE))
                    .thenReturn(Optional.of(repository));

            Repository result = service.connectRepository(organizationId, "https://github.com/senseylabs/village",
                    null);

            assertSame(repository, result);
        }

        @Test
        void archivedRepository_throwsValidationWithRepositoryId() {
            Repository repository = RepositoryMocks.repository(organizationId, REMOTE, RepositoryStatus.ARCHIVED);
            when(repositoryRepository.insertIfAbsent(any(), anyString(), anyString())).thenReturn(0);
            when(repositoryRepository.findByOrganizationIdAndRemoteUrl(organizationId, REMOTE))
                    .thenReturn(Optional.of(repository));

            ValidationException exception = assertThrows(ValidationException.class, () ->
                    service.connectRepository(organizationId, "https://github.com/senseylabs/village", null));

            assertEquals("KAY_VAL_002", exception.getErrorCode());
            assertEquals(repository.getId().toString(), exception.getErrorValues().get("repositoryId"));
        }

        @Test
        void invalidRemote_throwsBadRequestWithoutEchoingTheRemote() {
            BadRequestException exception = assertThrows(BadRequestException.class, () ->
                    service.connectRepository(organizationId, "https://tok3n@github.com/only-one", null));

            assertEquals("GEN_API_001", exception.getErrorCode());
            assertEquals(false, exception.getErrorValues().toString().contains("tok3n"));
            verify(repositoryRepository, never()).insertIfAbsent(any(), anyString(), anyString());
        }
    }
}
