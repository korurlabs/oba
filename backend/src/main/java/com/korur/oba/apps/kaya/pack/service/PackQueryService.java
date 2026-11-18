package com.korur.oba.apps.kaya.pack.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.pack.entity.Pack;

/**
 * Reads the {@link Pack}s of an organization. Other domains resolve a pack id through it, never
 * through the pack table.
 *
 * Called by {@link com.korur.oba.apps.kaya.pack.controller.PackController} for the pack list, and by
 * the version, release and seed domains to load the packs they work on.
 */
public interface PackQueryService {

    /**
     * Lists every pack of the organization, ordered by name.
     *
     * @param organizationId the organization of the request
     * @return the packs, with their current version ids
     */
    List<PackResponse> getPacks(UUID organizationId);

    /**
     * Loads a pack of the organization.
     *
     * A pack of another organization answers the same 404 as an unknown id, so its existence is
     * not revealed.
     *
     * @param organizationId the organization of the request
     * @param packId         the pack
     * @return the pack
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when
     *         the organization has no such pack
     */
    Pack getEntityById(UUID organizationId, UUID packId);

    /**
     * Loads the packs of the organization among the given ids; an id of another organization or
     * an unknown id is left out.
     *
     * @param organizationId the organization of the request
     * @param packIds        the pack ids
     * @return the packs found, in no particular order; empty for no ids
     */
    List<Pack> getEntitiesByIds(UUID organizationId, Collection<UUID> packIds);

    /**
     * The ids of the organization's required packs, which every repository composes first.
     *
     * @param organizationId the organization
     * @return the pack ids, ordered by pack name; empty when no pack is required
     */
    List<UUID> getRequiredPackIds(UUID organizationId);

    /**
     * The chain of a pack: its ancestors and the pack itself, root first. The walk stops after as
     * many packs as the organization has, so a parent link that loops cannot hang it.
     *
     * @param organizationId the organization of the request
     * @param packId         the pack
     * @return the chain, root first and the pack last
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when
     *         the organization has no such pack
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException
     *         GEN_INT_001 when the parent links loop or lead out of the organization
     */
    List<Pack> getChain(UUID organizationId, UUID packId);

    /**
     * The ids of every pack below a pack: its children, their children and so on, the pack itself
     * left out. The walk stops after as many packs as the organization has.
     *
     * @param organizationId the organization of the request
     * @param packId         the pack
     * @return the descendant ids, nearest level first; empty for a pack without children
     */
    List<UUID> getDescendantIds(UUID organizationId, UUID packId);

    /**
     * Finds a pack of the organization by its name.
     *
     * @param organizationId the organization
     * @param name           the pack name
     * @return the pack, or empty when the organization has none of that name
     */
    Optional<Pack> findByName(UUID organizationId, String name);
}
