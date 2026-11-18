package com.korur.oba.apps.kaya.toollink.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;

/**
 * Reads the {@link ToolLink}s of an organization's versions.
 *
 * Called by the render domain, which renders each link at its fixed path and counts them for the
 * path mapping page.
 */
public interface ToolLinkQueryService {

    /**
     * Loads the tool links of one version of the organization, ordered by name.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the links; empty when the version holds none or belongs to another organization
     */
    List<ToolLink> getEntitiesByVersionId(UUID organizationId, UUID versionId);

    /**
     * Loads the tool links of several versions of the organization in one query.
     *
     * @param organizationId the organization of the versions
     * @param versionIds     the versions; an id of another organization matches nothing
     * @return the links, in no particular order; empty when no id is given
     */
    List<ToolLink> getEntitiesByVersionIds(UUID organizationId, Collection<UUID> versionIds);
}
