package com.korur.oba.apps.kaya.repository.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.repository.constant.RepositoryConstant;
import com.korur.oba.apps.kaya.repository.constant.service.RepositoryServiceLogConstant;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.mapper.RepositoryMapper;
import com.korur.oba.apps.kaya.repository.repository.RepositoryRepository;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepositoryQueryServiceImpl implements RepositoryQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RepositoryQueryServiceImpl.class);

    private final RepositoryRepository repositoryRepository;
    private final RepositoryMapper repositoryMapper;
    private final LoggingService loggingService;

    @Override
    public List<RepositoryResponse> getRepositories(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, RepositoryServiceLogConstant.LISTING_REPOSITORIES, organizationId);

        return repositoryMapper.toResponses(
                repositoryRepository.findByOrganizationIdOrderByNameAscRemoteUrlAsc(organizationId));
    }

    @Override
    public Repository getEntityById(UUID organizationId, UUID repositoryId) {
        // Looked up within the organization, so a repository of another organization answers the
        // same 404 as an unknown id.
        return repositoryRepository.findByIdAndOrganizationId(repositoryId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    RepositoryServiceLogConstant.REPOSITORY_NOT_FOUND, repositoryId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_RESOURCE_FOUND,
                    Map.of(RepositoryConstant.FIELD_REPOSITORY_ID, repositoryId.toString()));
        });
    }
}
