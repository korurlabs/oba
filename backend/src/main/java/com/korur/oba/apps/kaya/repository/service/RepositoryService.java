package com.korur.oba.apps.kaya.repository.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.repository.dto.request.RepositoryUpdateRequest;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;

/**
 * Connects repositories on ingest and changes their name, ticket URL template and status.
 *
 * Writes {@link Repository}; calls no other domain. Called by
 * {@link com.korur.oba.apps.kaya.repository.controller.RepositoryController},
 * {@link com.korur.oba.apps.kaya.ingest.service.IngestService} and
 * {@link com.korur.oba.apps.kaya.seed.service.SeedService}.
 */
public interface RepositoryService {

    /**
     * Changes the name, ticket URL template or status of a repository; a null field keeps its value and an empty ticket URL template removes it.
     *
     * @param actor          the signed-in user, ADMIN of the organization
     * @param organizationId the organization of the request
     * @param repositoryId   the repository to change
     * @param request        the fields to change
     * @return the changed repository
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository, a repository of another organization included
     */
    RepositoryResponse updateRepository(
            User actor, UUID organizationId, UUID repositoryId, RepositoryUpdateRequest request);

    /**
     * Returns the ACTIVE repository of the organization with this git remote, connecting it when the
     * organization has none yet. Runs in the caller's transaction
     * when there is one, so a refused ingest connects nothing.
     *
     * The remote is normalised first, see
     * {@link com.korur.oba.apps.kaya.repository.helper.RemoteUrlHelper#normalize}.
     *
     * @param organizationId the organization of the ingest
     * @param remoteUrl      the git remote the recorder reported
     * @param name           the repository name the recorder reported, may be null; used only
     *                       when the repository is connected
     * @return the ACTIVE repository
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException GEN_API_001 with
     *         remoteUrl when Kaya does not accept the remote
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 when the
     *         repository is ARCHIVED
     */
    Repository connectRepository(UUID organizationId, String remoteUrl, String name);
}
