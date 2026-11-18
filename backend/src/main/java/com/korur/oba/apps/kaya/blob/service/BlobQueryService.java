package com.korur.oba.apps.kaya.blob.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.blob.entity.Blob;

/**
 * Reads {@link Blob}s. Blobs are addressed by organization and SHA-256, so a hash known to another
 * organization never reveals or serves its content.
 *
 * Called by the domains that read stored file bytes (the kind domains, version, render, release,
 * delivery, seed); none of them reads the blob table itself.
 */
public interface BlobQueryService {

    /**
     * Loads a blob with its content.
     *
     * @param organizationId the organization of the request
     * @param sha256         the lower-case hex hash
     * @return the blob
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization stores no blob with this hash
     */
    Blob getEntityBySha256(UUID organizationId, String sha256);

    /**
     * Loads a blob with its content by its id, as the kind tables reference it.
     *
     * @param organizationId the organization of the request
     * @param blobId         the blob id
     * @return the blob
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization stores no blob with this id, including a blob of another organization
     */
    Blob getEntityById(UUID organizationId, UUID blobId);

    /**
     * The byte lengths of the given blobs, read without loading their content, so a file list can
     * show sizes in one query.
     *
     * @param organizationId the organization of the request
     * @param blobIds        the blob ids; may be empty
     * @return the size by blob id; an id the organization does not store has no entry
     */
    Map<UUID, Integer> getSizesByIds(UUID organizationId, Collection<UUID> blobIds);

    /**
     * Loads the given blobs with their content, as the kind tables reference them, in one query, so
     * a version renders without a query per file.
     *
     * @param organizationId the organization of the request
     * @param blobIds        the blob ids; may be empty
     * @return the blob by id; an id the organization does not store has no entry
     */
    Map<UUID, Blob> getEntitiesByIds(UUID organizationId, Collection<UUID> blobIds);

    /**
     * Loads the stored blobs among the given hashes, with their content. A hash the organization
     * does not store is left out.
     *
     * @param organizationId the organization of the request
     * @param sha256s        the hashes; may be empty
     * @return the stored blobs, in no particular order
     */
    List<Blob> getEntitiesBySha256s(UUID organizationId, Collection<String> sha256s);

    /**
     * Loads the content of the stored blobs among the given hashes.
     *
     * @param organizationId the organization of the request
     * @param sha256s        the hashes; may be empty
     * @return the content by hash; a hash the organization does not store has no entry
     */
    Map<String, byte[]> getContents(UUID organizationId, Collection<String> sha256s);

    /**
     * Which of the given hashes the organization already stores, without loading any content, so
     * an upload can skip what is there.
     *
     * @param organizationId the organization of the request
     * @param sha256s        the hashes; may be empty
     * @return the stored hashes among them
     */
    Set<String> getStoredSha256s(UUID organizationId, Collection<String> sha256s);

    /**
     * The total byte length of the stored blobs among the given hashes, read without loading their
     * content, so a request can be measured before its content is loaded.
     *
     * @param organizationId the organization of the request
     * @param sha256s        the hashes; may be empty
     * @return the sum of their sizes; 0 when none is stored
     */
    long sumSizes(UUID organizationId, Collection<String> sha256s);
}
