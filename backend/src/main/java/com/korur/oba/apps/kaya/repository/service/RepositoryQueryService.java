package com.korur.oba.apps.kaya.repository.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;

/**
 * Reads the repositories of an organization. Other domains use it to resolve a repository id,
 * never the repository table itself.
 *
 * Reads {@link Repository}; calls no other domain. Called by
 * {@link com.korur.oba.apps.kaya.repository.controller.RepositoryController}, the other services of
 * this domain, and the release, project, delivery, plan and session domains, for example
 * {@link com.korur.oba.apps.kaya.release.service.ReleaseService} and
 * {@link com.korur.oba.apps.kaya.session.service.SessionQueryService}.
 */
public interface RepositoryQueryService {

    /**
     * Lists every repository of the organization, ARCHIVED ones included, ordered by name.
     *
     * @param organizationId the organization of the request
     * @return the repositories
     */
    List<RepositoryResponse> getRepositories(UUID organizationId);

    /**
     * Loads a repository of the organization.
     *
     * @param organizationId the organization of the request
     * @param repositoryId   the repository
     * @return the repository
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository; a repository of another organization answers the
     *         same, so its existence is not revealed
     */
    Repository getEntityById(UUID organizationId, UUID repositoryId);
}
