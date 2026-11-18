package com.korur.oba.apps.kaya.release.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.dto.request.PackRequiredRequest;
import com.korur.oba.apps.kaya.release.dto.request.RepositoryPackUpdateRequest;
import com.korur.oba.apps.kaya.release.dto.response.PackRequiredResponse;
import com.korur.oba.apps.kaya.release.dto.response.RepositoryPackResponse;
import com.korur.oba.apps.kaya.release.helper.PackMount;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Changes the packs a repository composes: its own pack order, and the required packs every
 * repository of the organization composes. A changed composition is released at once.
 *
 * It writes the {@link com.korur.oba.apps.kaya.release.entity.RepositoryPack} rows of a repository,
 * sets the required flag through {@link com.korur.oba.apps.kaya.pack.service.PackService},
 * checks each pack with {@link com.korur.oba.apps.kaya.pack.service.PackQueryService} and the
 * repository with {@link com.korur.oba.apps.kaya.repository.service.RepositoryQueryService}, and
 * releases the new order through {@link ReleaseService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.release.controller.RepositoryPackController},
 * {@link com.korur.oba.apps.kaya.release.controller.PackRequiredController} and
 * {@link com.korur.oba.apps.kaya.seed.service.SeedService}, which stores the pack list a root project
 * names.
 */
public interface RepositoryPackService {

    /**
     * Replaces the packs of a repository with the given ones, in that order, each at the root or
     * mounted in a folder. An unchanged list changes nothing and releases nothing. Otherwise the composition it leads to is validated, the
     * rows are replaced and the repository is released.
     *
     * @param actor          the user making the change, OWNER of the organization
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @param request        the packs in position order with their mount folders
     * @return the packs and the latest release
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository or pack
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_001 when a
     *         pack is listed twice or the composition breaks a rule, KAY_CFG_026 when a mount folder
     *         is malformed, below a delivered config folder, given twice, or inside or around another
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_003 when the
     *         composition gives a path from two packs not on one chain
     */
    RepositoryPackResponse replacePacks(User actor, UUID organizationId, UUID repositoryId, RepositoryPackUpdateRequest request);

    /**
     * Writes the packs of a repository for a seed, which validates and releases the compositions it
     * reaches itself: the rows are replaced when the list differs, under the locks
     * {@link #replacePacks} takes, and the folders are checked as {@link #replacePacks} checks
     * them; no composition is validated or released here.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @param mounts         the packs in position order, each at the root or in its mount folder
     * @return true when the list changed
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_001 when a
     *         pack is listed twice, KAY_CFG_026 when a mount folder is malformed, below a delivered
     *         config folder, given twice, or inside or around another
     */
    boolean writePacks(UUID organizationId, UUID repositoryId, List<PackMount> mounts);

    /**
     * Sets whether every repository of the organization composes a pack, ahead of its own packs.
     * Every active repository is composed before and after the change; each whose composition
     * changes is validated and then released, all in one transaction. An unchanged flag changes
     * nothing and releases nothing; a pack without an approved version changes no composition.
     *
     * @param actor          the user making the change, OWNER of the organization
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @param request        the new flag
     * @return the flag and the number of releases created
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_CFG_001 when a
     *         changed composition breaks a rule, such as a guardrail without a settings file
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_003 when a
     *         changed composition gives a path from two owners
     */
    PackRequiredResponse setPackRequired(User actor, UUID organizationId, UUID packId, PackRequiredRequest request);
}
