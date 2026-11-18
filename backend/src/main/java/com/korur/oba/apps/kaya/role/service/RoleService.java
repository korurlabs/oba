package com.korur.oba.apps.kaya.role.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.role.dto.embedded.RoleIndex;
import com.korur.oba.apps.kaya.role.entity.Role;

/**
 * Writes the {@link Role}s of a version from the index the parse extracted. It parses nothing and
 * calls no other domain; the blobs it references are stored by the caller.
 *
 * Called only by the render domain, inside the transaction that stores a version's files, so the index
 * rows commit together with the bytes they describe.
 */
public interface RoleService {

    /**
     * Replaces every role of a version with the given ones.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param roles          the index of each role file of the version; may be empty
     * @return the roles written, in the given order
     */
    List<Role> replaceVersion(UUID organizationId, UUID versionId, List<RoleIndex> roles);

    /**
     * Writes the {@link Role} of one saved file: the row at its path is replaced, or the row is added
     * when the path holds none. The old row is deleted rather than updated, because a row's blob never
     * changes.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param role           the index of the saved file
     * @return the role written
     */
    Role saveFile(UUID organizationId, UUID versionId, RoleIndex role);

    /**
     * Deletes the {@link Role} at one path of a version.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @param relativePath   the path of the deleted file
     * @return whether the version had a role there
     */
    boolean deleteFile(UUID organizationId, UUID versionId, String relativePath);
}
