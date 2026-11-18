package com.korur.oba.apps.kaya.repository.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party
import org.mapstruct.factory.Mappers;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.dto.request.RepositoryUpdateRequest;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.mock_data.RepositoryMocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RepositoryMappersUnitTest {

    private final RepositoryMapper repositoryMapper = Mappers.getMapper(RepositoryMapper.class);

    private Repository repository() {
        Repository repository = RepositoryMocks.repository(UUID.randomUUID(), "github.com/senseylabs/village",
                RepositoryStatus.ACTIVE);
        repository.setTicketUrlTemplate("https://k.io/t/{key}");
        return repository;
    }

    @Test
    void toResponse_repository_copiesEveryField() {
        Repository repository = repository();

        RepositoryResponse result = repositoryMapper.toResponse(repository);

        assertEquals(repository.getId(), result.id());
        assertEquals("Village", result.name());
        assertEquals("github.com/senseylabs/village", result.remoteUrl());
        assertEquals("https://k.io/t/{key}", result.ticketUrlTemplate());
        assertEquals(RepositoryStatus.ACTIVE, result.status());
    }

    @Test
    void toResponses_list_keepsOrder() {
        Repository first = repository();
        Repository second = repository();

        List<RepositoryResponse> result = repositoryMapper.toResponses(List.of(first, second));

        assertEquals(List.of(first.getId(), second.getId()), result.stream().map(RepositoryResponse::id).toList());
    }

    @Test
    void updateEntity_nullFields_keepCurrentValues() {
        Repository repository = repository();

        repositoryMapper.updateEntity(repository, new RepositoryUpdateRequest(null, null, null));

        assertEquals("Village", repository.getName());
        assertEquals("https://k.io/t/{key}", repository.getTicketUrlTemplate());
        assertEquals(RepositoryStatus.ACTIVE, repository.getStatus());
    }

    @Test
    void updateEntity_name_isTrimmed() {
        Repository repository = repository();

        repositoryMapper.updateEntity(repository, new RepositoryUpdateRequest("  Kaya  ", null, null));

        assertEquals("Kaya", repository.getName());
    }

    @Test
    void updateEntity_emptyTemplate_removesTemplate() {
        Repository repository = repository();

        repositoryMapper.updateEntity(repository, new RepositoryUpdateRequest(null, "", null));

        assertNull(repository.getTicketUrlTemplate());
    }

    @Test
    void updateEntity_templateAndStatus_replaceValues() {
        Repository repository = repository();
        UUID id = repository.getId();

        repositoryMapper.updateEntity(repository,
                new RepositoryUpdateRequest(null, "https://x.io/{key}", RepositoryStatus.ARCHIVED));

        assertEquals("https://x.io/{key}", repository.getTicketUrlTemplate());
        assertEquals(RepositoryStatus.ARCHIVED, repository.getStatus());
        assertEquals(id, repository.getId());
        assertEquals("github.com/senseylabs/village", repository.getRemoteUrl());
    }
}
