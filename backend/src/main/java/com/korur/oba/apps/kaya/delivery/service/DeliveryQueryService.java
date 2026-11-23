package com.korur.oba.apps.kaya.delivery.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryBlob;
import com.korur.oba.apps.kaya.delivery.dto.derived.DeliveryCurrent;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryReleaseResponse;
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryRepositoriesResponse;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;

/**
 * What the CLI downloads with a machine token: the organization's repositories a checkout is
 * recognised by, the current release of a checkout's repository, a signed release and the content
 * it names. Every read is limited to the token's organization;
 * a repository, release or blob of another organization answers the same 404 as an unknown one.
 * Only releases are served, and a release exists only for approved versions, so a draft is never
 * delivered.
 *
 * It owns no table: it reads the repository through
 * {@link com.korur.oba.apps.kaya.repository.service.RepositoryQueryService},
 * {@link com.korur.oba.apps.kaya.release.entity.Release}s through
 * {@link com.korur.oba.apps.kaya.release.service.ReleaseQueryService} and the content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.delivery.controller.DeliveryController} and by
 * {@link DeliveryProposalService}, which resolves the repository of a proposal.
 */
public interface DeliveryQueryService {

    /**
     * Lists the repositories the organization delivers to: every ACTIVE one, by its normalised
     * remote URL, sorted. An ARCHIVED repository is left out, because delivery to it is refused.
     *
     * @param principal the machine token's principal
     * @return the organization and its active repositories; an empty list when it has none
     */
    DeliveryRepositoriesResponse getRepositories(MachinePrincipal principal);

    /**
     * Resolves the newest release of the checkout's repository.
     *
     * @param principal the machine token's principal
     * @param remoteUrl the checkout's git remote, raw
     * @return the response and its ETag
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException GEN_API_001 for a
     *         remote Kaya does not accept
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no repository with that remote
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 when the
     *         repository is archived
     */
    DeliveryCurrent getCurrent(MachinePrincipal principal, String remoteUrl);

    /**
     * Reads a signed release by id.
     *
     * @param principal the machine token's principal
     * @param releaseId the release
     * @return the signed manifest text, its hash and signature
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 for an
     *         unknown or foreign release
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_012 when the
     *         repository's remote URL changed after the release, so its signature no longer holds
     */
    DeliveryReleaseResponse getRelease(MachinePrincipal principal, UUID releaseId);

    /**
     * Reads a signed release of the checkout's repository by number.
     *
     * @param principal the machine token's principal
     * @param remoteUrl the checkout's git remote, raw
     * @param number    the release number
     * @return the signed manifest text, its hash and signature
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 for an
     *         unknown repository or number
     */
    DeliveryReleaseResponse getReleaseByNumber(MachinePrincipal principal, String remoteUrl, int number);

    /**
     * Reads the content of a file a release of the organization delivers.
     *
     * @param principal the machine token's principal
     * @param sha256    the content hash, 64 lower-case hex characters
     * @return the content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when no
     *         release of the organization names the hash, even if a draft holds it
     */
    DeliveryBlob getBlob(MachinePrincipal principal, String sha256);

    /**
     * Resolves the organization's repository of a checkout's remote, as every delivery call does.
     *
     * @param organizationId the machine token's organization
     * @param remoteUrl      the checkout's git remote, raw
     * @return the repository
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException GEN_API_001 for a
     *         remote Kaya does not accept
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no repository with that remote
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException KAY_VAL_002 when the
     *         repository is archived
     */
    RepositoryResponse getRepository(UUID organizationId, String remoteUrl);
}
