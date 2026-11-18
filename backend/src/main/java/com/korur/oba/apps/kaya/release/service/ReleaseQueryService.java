package com.korur.oba.apps.kaya.release.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.release.dto.request.ReleaseQueryRequest;
import com.korur.oba.apps.kaya.release.dto.response.EffectiveItemResponse;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseDetailResponse;
import com.korur.oba.apps.kaya.release.dto.response.ReleaseResponse;
import com.korur.oba.apps.kaya.release.entity.Release;
import com.korur.oba.apps.kaya.release.helper.Composition;
import com.korur.oba.apps.kaya.render.enums.FileMode;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.version.entity.Version;

/**
 * Reads releases and previews compositions. Other domains (session for a reported release, delivery for the signed
 * document) use it, never the release table. Every method checks the repository belongs to the
 * organization first, so a repository of another organization answers the same 404 as an unknown one.
 *
 * It reads {@link Release} and {@link com.korur.oba.apps.kaya.release.entity.RepositoryPack} rows
 * and composes previews from the current {@link Version}s, through
 * {@link com.korur.oba.apps.kaya.repository.service.RepositoryQueryService} and
 * {@link com.korur.oba.apps.kaya.validation.service.ValidationQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.release.controller.ReleaseController}, {@link ReleaseService},
 * {@link com.korur.oba.apps.kaya.review.service.ReviewService},
 * {@link com.korur.oba.apps.kaya.review.service.ReviewQueryService},
 * {@link com.korur.oba.apps.kaya.link.service.LinkQueryService},
 * {@link com.korur.oba.apps.kaya.seed.service.SeedService},
 * {@link com.korur.oba.apps.kaya.session.service.SessionQueryService},
 * {@link com.korur.oba.apps.kaya.session.service.SessionIngestService},
 * {@link com.korur.oba.apps.kaya.delivery.service.DeliveryQueryService},
 * {@link com.korur.oba.apps.kaya.delivery.service.DeliveryProposalService}, and by
 * {@link com.korur.oba.apps.kaya.version.service.VersionService} for the lock and mount rule checks
 * of a saved draft file.
 */
public interface ReleaseQueryService {

    /**
     * Lists a repository's releases, newest number first.
     *
     * @param organizationId the organization of the request
     * @param repositoryId   the repository
     * @param request        page and amount
     * @return a page of releases
     */
    Page<ReleaseResponse> getReleases(UUID organizationId, UUID repositoryId, ReleaseQueryRequest request);

    /**
     * Reads one release with its signature and delivered paths.
     *
     * @param organizationId the organization of the request
     * @param repositoryId   the repository
     * @param number         the release number
     * @return the release
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         repository has no release with that number
     */
    ReleaseDetailResponse getRelease(UUID organizationId, UUID repositoryId, int number);

    /**
     * Finds the newest release of a repository.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @return the release, or empty when the repository has none
     */
    Optional<Release> findLatest(UUID organizationId, UUID repositoryId);

    /**
     * Loads a release by number.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @param number         the release number
     * @return the release
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         repository has no release with that number
     */
    Release getEntityByNumber(UUID organizationId, UUID repositoryId, int number);

    /**
     * Finds a release by number, for a number a client reported.
     *
     * @param organizationId the organization of the repository
     * @param repositoryId   the repository
     * @param number         the release number
     * @return the release, or empty when the repository has no release with that number
     */
    Optional<Release> findByNumber(UUID organizationId, UUID repositoryId, int number);

    /**
     * Loads a release by id. A release of another organization's repository answers the same 404
     * as an unknown id.
     *
     * @param organizationId the organization of the request
     * @param releaseId      the release
     * @return the release
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when no
     *         release of the organization has that id
     */
    Release getEntityById(UUID organizationId, UUID releaseId);

    /**
     * Whether a release of one of the organization's repositories delivers content with this hash,
     * so that delivery serves only content that went through a release and never that of a draft.
     *
     * @param organizationId the organization of the request
     * @param sha256         the content hash
     * @return true when at least one release of the organization names it
     */
    boolean isDelivered(UUID organizationId, String sha256);

    /**
     * Rebuilds the signed text of a release, which its signature covers, for delivery.
     *
     * @param organizationId the organization of the release
     * @param release        the release
     * @return the canonical document; it no longer verifies when the repository's remote URL changed
     *         after the release, which the CLI then refuses as a release of another repository
     */
    String toCanonicalJson(UUID organizationId, Release release);

    /**
     * Composes a repository as it would be released with some owners at another version, for a
     * review that checks a version in review, a seed that checks its import, and the links of a
     * draft. Each pack not replaced is composed at its current version.
     *
     * @param organizationId    the organization of the repository
     * @param repositoryId      the repository
     * @param versionIdsByOwner version ids by pack id, composed instead of the current
     *                          ones; empty for what the repository delivers now
     * @return the composition with its contents
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such repository or version
     */
    Composition previewComposition(UUID organizationId, UUID repositoryId, Map<UUID, UUID> versionIdsByOwner);

    /**
     * The repositories that compose a version's pack, at the root or as an ancestor of a pack they
     * list: each repository that lists the pack or one of its descendants, in id order, or each
     * active repository of the organization when the pack or one of its descendants is required,
     * since a required pack's chain is composed everywhere. An approval of a parent therefore
     * releases every repository its children reach, and no descendant gets a version of its own.
     *
     * @param organizationId the organization of the version
     * @param version        the version
     * @return the repository ids
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    List<UUID> getComposingRepositoryIds(UUID organizationId, Version version);

    /**
     * The active repositories of an organization, which every required pack reaches. An archived
     * repository takes no new release from a required pack.
     *
     * @param organizationId the organization
     * @return the repository ids, in id order
     */
    List<UUID> getActiveRepositoryIds(UUID organizationId);

    /**
     * Checks every composition a version would change: each composing repository with the version
     * in place of its owner's current one. The version's items are also checked against the locks of its pack's ancestors, see {@link #getLockViolations}, even
     * when no repository composes the pack.
     *
     * @param organizationId the organization of the version
     * @param version        the version, usually in review
     * @return the violations, empty when every composition is valid
     */
    List<Violation> validateVersion(UUID organizationId, Version version);

    /**
     * The items a pack would replace though an ancestor locks them: an item, a file or a whole
     * skill directory, that an ancestor's current version gives while the ancestor is required or
     * the item is a guardrail. Checked whatever repositories compose the pack, so a pack no
     * repository uses yet cannot store such an item either.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack that stores the paths
     * @param paths          the stored paths to check
     * @return one OVERRIDE_LOCKED violation per locked item and locking ancestor, without a repository
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    List<Violation> getLockViolations(UUID organizationId, UUID packId, Collection<String> paths);

    /**
     * Checks a file a pack stores against every folder the pack is mounted at, in any repository
     * of the organization: a rule in its namespace needs paths whose every pattern lies under each
     * of those folders. Read only; the stored bytes are never rewritten.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @param path           the stored path
     * @param mode           the file mode
     * @param content        the file bytes; null for a symlink
     * @return one MOUNT_RULE_SCOPE violation per folder the rule is not scoped to, without a repository
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    List<Violation> getRuleScopeViolations(UUID organizationId, UUID packId, String path, FileMode mode, byte[] content);

    /**
     * The overrides a pack would lock if it were required: each item a descendant's current version
     * gives that the pack's current version gives too, whether or not an override was recorded for it,
     * since a required pack locks its items for every level below it.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @return one OVERRIDE_LOCKED violation per overriding descendant and item, descendants in
     *         {@link com.korur.oba.apps.kaya.pack.service.PackQueryService#getDescendantIds} order and items by
     *         path, naming the pack first
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    List<Violation> getLockedOverrides(UUID organizationId, UUID packId);

    /**
     * A pack's effective config: the items a version of the pack stores, each with the override it
     * makes of an ancestor's item, and on request the items its ancestors' current versions give
     * that it does not store, each from the nearest ancestor that gives it. An own item is an
     * override when an ancestor delivers it or the version records one; its status compares the
     * nearest ancestor's current item with the item at the recorded base.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @param versionId      a version or draft of the pack; null for its current version
     * @param inherited      true to add the inherited items
     * @return the items sorted by path
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack, or the version is not one of the pack's
     */
    List<EffectiveItemResponse> getEffective(UUID organizationId, UUID packId, UUID versionId, boolean inherited);
}
