package com.korur.oba.apps.kaya.release.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.dto.response.PackDetailResponse;
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackEntryResponse;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackResponse;
import com.korur.oba.apps.kaya.release.helper.PackMount;

/**
 * Reads the packs each repository composes.
 *
 * It reads {@link com.korur.oba.apps.kaya.release.entity.RepositoryPack} rows, resolving the packs
 * through {@link com.korur.oba.apps.kaya.pack.service.PackQueryService} and the repository through
 * {@link com.korur.oba.apps.kaya.repository.service.RepositoryQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.release.controller.RepositoryPackController}, by
 * {@link com.korur.oba.apps.kaya.pack.controller.PackController} for the mount folders of each pack
 * and the repositories that compose one,
 * and by {@link com.korur.oba.apps.kaya.seed.service.SeedService}, which finds the packs and
 * repositories a seed must lock.
 */
public interface RepositoryPackQueryService {

    /**
     * The organization's packs by name, each with the distinct folders it is mounted in across the
     * organization's repositories.
     *
     * @param organizationId the organization of the request
     * @return the packs
     */
    List<PackResponse> getPacksWithMountPaths(UUID organizationId);

    /**
     * A pack with the distinct folders it is mounted in and the repositories of the organization
     * that compose it, by name: those that list it, or every active repository when the pack is
     * required. Each repository carries the folder it mounts the pack in, null at its root, and the
     * number of its latest release.
     *
     * @param organizationId the organization of the request
     * @param packId         the pack
     * @return the pack and its repositories
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    PackDetailResponse getPackWithRepositories(UUID organizationId, UUID packId);

    /**
     * The packs a repository composes with its latest release: the organization's required packs
     * by name, also those the repository does not list, then the repository's own packs by
     * position, each with its mount folder.
     *
     * @param organizationId the organization of the request
     * @param repositoryId   the repository
     * @return the packs and the latest release
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository
     */
    RepositoryPackResponse getPacks(UUID organizationId, UUID repositoryId);

    /**
     * The packs a pack list shows: the organization's required packs by name at the root, then the
     * listed packs in their order with their folders, each pack once.
     *
     * @param organizationId the organization of the packs
     * @param mounts         the packs a repository lists, in position order
     * @return the packs; an id the organization does not have is left out
     */
    List<RepositoryPackEntryResponse> getComposedPacks(UUID organizationId, List<PackMount> mounts);

    /**
     * The packs a repository lists, by position, with their mount folders; required packs it does
     * not list are not among them.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @return the packs and folders
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository
     */
    List<PackMount> getMounts(UUID organizationId, UUID repositoryId);

    /**
     * The repositories that compose a pack, in id order: those that list it, or every active
     * repository of the organization when the pack is required.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @return the repository ids; empty for a pack that is not required and that no repository lists
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    List<UUID> getRepositoryIdsOfPack(UUID organizationId, UUID packId);
}
