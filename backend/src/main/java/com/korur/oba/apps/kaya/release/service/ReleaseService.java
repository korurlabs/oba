package com.korur.oba.apps.kaya.release.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.version.entity.Version;

/**
 * Creates signed releases. A release always composes the current version of each pack of the
 * repository; nothing pins an older one. Release numbers only grow per repository, a
 * rollback included, and two releases of one repository are numbered one after the other.
 *
 * It writes {@link Release} rows: composes the packs of a repository in
 * {@link com.korur.oba.apps.kaya.release.entity.RepositoryPack} order, reads the repository with
 * {@link com.korur.oba.apps.kaya.repository.service.RepositoryQueryService}, checks the composition
 * with {@link com.korur.oba.apps.kaya.validation.service.ValidationQueryService}, stores missing
 * content with {@link com.korur.oba.apps.kaya.blob.service.BlobService} and signs the manifest with
 * {@link com.korur.oba.apps.kaya.signing.service.SigningService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.review.service.ReviewService} on approval, by
 * {@link RepositoryPackService} when a pack order changes and by
 * {@link com.korur.oba.apps.kaya.seed.service.SeedService}; it joins the caller's transaction, so an
 * approval and its releases commit together.
 */
public interface ReleaseService {

    /**
     * Releases an approved version to every repository that composes its pack: each repository
     * that lists the pack, or every active repository for a required pack. Every composition is validated before the
     * first release is written, so a refused version releases nowhere.
     * Runs in the caller's transaction, the one that approved the version, after the owner's
     * current-version pointer names it.
     *
     * @param organizationId the organization of the version
     * @param approved       the approved version
     * @return the new releases, one per composing repository that delivers anything
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_003 when a
     *         composition gives a path from two owners
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_001 when a
     *         composition breaks another rule
     */
    List<Release> releaseVersion(UUID organizationId, Version approved);

    /**
     * Composes a repository from its current versions, stores the bytes no blob holds yet, signs
     * the manifest and stores the release. The caller has validated the composition, or the change
     * that leads to it, in the same transaction.
     *
     * @param organizationId  the organization of the repository
     * @param repositoryId    the repository
     * @param causeVersionId  the approved version that led to the release
     * @return the release, or empty when nothing the repository composes has an approved version
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository
     */
    Optional<Release> releaseRepository(UUID organizationId, UUID repositoryId, UUID causeVersionId);

    /**
     * Serialises the releases of a repository until the caller's transaction ends.
     *
     * @param repositoryId the repository
     */
    void lockReleases(UUID repositoryId);

    /**
     * Takes the lock an approval of a pack's versions takes, until the caller's
     * transaction ends. A change that reads which repositories compose a pack takes it first.
     *
     * @param ownerId the pack
     */
    void lockOwner(UUID ownerId);
}
