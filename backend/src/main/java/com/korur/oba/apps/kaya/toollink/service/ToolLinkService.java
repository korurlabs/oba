package com.korur.oba.apps.kaya.toollink.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.toollink.entity.ToolLink;
import com.korur.oba.apps.kaya.toollink.enums.ToolLinkName;

/**
 * Writes the {@link ToolLink}s of a version. It checks no path or target, which the render domain
 * derives from the name and checks before it calls.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the
 * links commit together with the files they point at.
 */
public interface ToolLinkService {

    /**
     * Keeps a tool link in a version; a link the version already holds is left as it is.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param name           the link
     * @return the stored link
     */
    ToolLink saveLink(UUID organizationId, UUID versionId, ToolLinkName name);

    /**
     * Removes a tool link from a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param name           the link
     * @return the number of rows deleted, 0 or 1
     */
    int deleteLink(UUID organizationId, UUID versionId, ToolLinkName name);

    /**
     * Replaces every tool link of a version, as a whole file tree is stored.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param names          the links the tree holds; empty removes them all
     * @return the links written
     */
    List<ToolLink> replaceVersion(UUID organizationId, UUID versionId, Collection<ToolLinkName> names);
}
