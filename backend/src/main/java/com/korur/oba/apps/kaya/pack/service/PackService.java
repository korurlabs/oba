package com.korur.oba.apps.kaya.pack.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.apps.kaya.pack.dto.request.PackCreateRequest;
import com.korur.oba.apps.kaya.pack.dto.request.PackUpdateRequest;
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.pack.entity.Pack;

/**
 * Writes the {@link Pack}s of an organization: creates and updates a pack, moves its current-version
 * pointer and sets its required flag. Only a rename reads other domains, through their query
 * services, to find out whether anything depends on the pack's name yet.
 *
 * Called by {@link com.korur.oba.apps.kaya.pack.controller.PackController} to create and update a pack, by the
 * seed domain to create the packs it imports, by the review domain when a pack version is
 * approved, and by {@link com.korur.oba.apps.kaya.release.service.RepositoryPackService} when an OWNER
 * marks a pack required.
 */
public interface PackService {

    /**
     * Creates a pack without versions: a root pack, or a child pack of a parent of the same
     * organization. The parent is fixed from then on.
     *
     * @param actor          the OWNER creating it
     * @param organizationId the organization of the request
     * @param request        the name, the optional description and the optional parent
     * @return the created pack
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when
     *         the organization has no pack of the parent id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_008 when
     *         the organization already has a pack of that name
     */
    PackResponse createPack(User actor, UUID organizationId, PackCreateRequest request);

    /**
     * Writes a pack's name and description as given; a null description removes it. The
     * description is free text and changes at any time. The name keys the namespace of a mounted
     * pack's files, seed imports and plan matching, so a new name is refused once the pack is
     * required, composed by a repository (itself or a pack below it), has a version that is not a
     * draft, has a draft that holds a file in its name's namespace, or has a plan filed under it.
     * The same name and description as now answer the pack without writing.
     *
     * @param actor          the OWNER updating it
     * @param organizationId the organization of the request
     * @param packId         the pack
     * @param request        the name and the description
     * @return the pack, with its mount folders
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when
     *         the organization has no such pack
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException KAY_CFG_035 when
     *         the name changes and the pack is in use, naming the condition; KAY_CFG_008 when the
     *         organization already has a pack of the new name
     */
    PackResponse updatePack(User actor, UUID organizationId, UUID packId, PackUpdateRequest request);

    /**
     * Points a pack at the version it now delivers.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @param versionId      the newly approved version of that pack
     * @return the updated pack
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when
     *         the organization has no such pack
     */
    Pack setCurrentVersion(UUID organizationId, UUID packId, UUID versionId);

    /**
     * Sets whether every repository of the organization composes a pack. It writes the flag only;
     * the caller validates and releases the compositions the flag changes.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack
     * @param required       true to compose the pack into every repository
     * @return the updated pack
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when
     *         the organization has no such pack
     */
    Pack setRequired(UUID organizationId, UUID packId, boolean required);
}
