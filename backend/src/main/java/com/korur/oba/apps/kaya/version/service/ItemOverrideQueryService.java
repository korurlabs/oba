package com.korur.oba.apps.kaya.version.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.RenderedFile;
import com.korur.oba.apps.kaya.version.dto.embedded.ItemSource;
import com.korur.oba.apps.kaya.version.entity.ItemOverride;

/**
 * Reads the overrides versions record and what they are measured against: the items a pack's
 * ancestors deliver, an item's content in two versions, and an item's derived version number.
 * An item is a file path, or a skill directory as
 * {@link com.korur.oba.apps.kaya.validation.helper.PathRules#itemOf} gives it.
 */
public interface ItemOverrideQueryService {

    /**
     * The overrides one version records, by item.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the overrides sorted by path; empty for another organization's version
     */
    List<ItemOverride> getEntitiesByVersionId(UUID organizationId, UUID versionId);

    /**
     * The overrides several versions record, for a read across a pack's descendants.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions
     * @return the overrides, in no particular order
     */
    List<ItemOverride> getEntitiesByVersionIds(UUID organizationId, Collection<UUID> versionIds);

    /**
     * The nearest ancestor delivering each item a pack inherits: the ancestors' current versions
     * are read root first and a nearer ancestor replaces a farther one.
     *
     * @param organizationId the organization of the pack
     * @param packId         the pack, whose own versions are not read
     * @return the source of each inherited item, by item
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such pack
     */
    Map<String, ItemSource> getInheritedSources(UUID organizationId, UUID packId);

    /**
     * Whether an item has the same files, with the same mode, hash and link target, in two
     * versions. An item absent from both is unchanged.
     *
     * @param organizationId the organization of the versions
     * @param fromVersionId  one version
     * @param toVersionId    the other version
     * @param item           the item
     * @return true when the item is the same in both
     */
    boolean isItemUnchanged(UUID organizationId, UUID fromVersionId, UUID toVersionId, String item);

    /**
     * The version number of an item at a pack version, derived from the file history: the count
     * of the pack's approved versions up to that version's number that changed the item against
     * the approved version before them.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param item           the item
     * @return the item's version number; 0 when no approved version up to it gave the item
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such version
     */
    int getItemNumber(UUID organizationId, UUID versionId, String item);

    /**
     * The files of one item among a version's files.
     *
     * @param files the rendered files of a version
     * @param item  the item
     * @return the files whose item is the given one, in the given order
     */
    List<RenderedFile> filesOf(List<RenderedFile> files, String item);
}
