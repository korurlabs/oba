package com.korur.oba.apps.kaya.repository.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.repository.constant.RepositoryConstant;
import com.korur.oba.apps.kaya.repository.constant.service.RepositoryServiceLogConstant;
import com.korur.oba.apps.kaya.repository.dto.request.RepositoryUpdateRequest;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.helper.RemoteUrlHelper;
import com.korur.oba.apps.kaya.repository.mapper.RepositoryMapper;
import com.korur.oba.apps.kaya.repository.repository.RepositoryRepository;
import com.korur.oba.apps.kaya.repository.service.RepositoryQueryService;
import com.korur.oba.apps.kaya.repository.service.RepositoryService;

@Service
@RequiredArgsConstructor
public class RepositoryServiceImpl implements RepositoryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RepositoryServiceImpl.class);

    private final RepositoryRepository repositoryRepository;
    private final RepositoryQueryService repositoryQueryService;
    private final RepositoryMapper repositoryMapper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public RepositoryResponse updateRepository(
            User actor, UUID organizationId, UUID repositoryId, RepositoryUpdateRequest request) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                RepositoryServiceLogConstant.UPDATING_REPOSITORY, actor.getId(), repositoryId, organizationId);

        Repository repository = repositoryQueryService.getEntityById(organizationId, repositoryId);
        String oldName = repository.getName();
        String oldTicketUrlTemplate = repository.getTicketUrlTemplate();
        RepositoryStatus oldStatus = repository.getStatus();

        repositoryMapper.updateEntity(repository, request);
        Repository saved = repositoryRepository.saveAndFlush(repository);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RepositoryServiceLogConstant.REPOSITORY_UPDATED,
                actor.getId(), repositoryId, organizationId, oldName, oldTicketUrlTemplate, oldStatus,
                saved.getName(), saved.getTicketUrlTemplate(), saved.getStatus());

        return repositoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public Repository connectRepository(UUID organizationId, String remoteUrl, String name) {
        String normalizedRemoteUrl = RemoteUrlHelper.normalize(remoteUrl);
        if (normalizedRemoteUrl == null) {
            // The raw remote is neither logged nor echoed: it may carry an access token.
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    RepositoryServiceLogConstant.REMOTE_URL_INVALID, organizationId);
            throw new BadRequestException(ExceptionApiError.BAD_REQUEST,
                    Map.of(RepositoryConstant.FIELD_REMOTE_URL, RepositoryConstant.REMOTE_URL_INVALID_MESSAGE));
        }
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE,
                RepositoryServiceLogConstant.CONNECTING_REPOSITORY, normalizedRemoteUrl, organizationId);

        int inserted = repositoryRepository.insertIfAbsent(
                organizationId, RemoteUrlHelper.displayName(name, normalizedRemoteUrl), normalizedRemoteUrl);
        // Present now: either inserted above or already there, and rows are never deleted on their own.
        Repository repository = repositoryRepository
                .findByOrganizationIdAndRemoteUrl(organizationId, normalizedRemoteUrl)
                .orElseThrow();

        if (inserted > 0) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, RepositoryServiceLogConstant.REPOSITORY_CONNECTED,
                    repository.getId(), normalizedRemoteUrl, organizationId);
        }

        if (repository.getStatus() == RepositoryStatus.ARCHIVED) {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                    RepositoryServiceLogConstant.REPOSITORY_ARCHIVED, repository.getId(), organizationId);
            throw new ValidationException(ExceptionDomainKayaError.VAL_REPOSITORY_ARCHIVED,
                    Map.of(RepositoryConstant.FIELD_REPOSITORY_ID, repository.getId().toString()));
        }

        return repository;
    }
}
