package com.korur.oba.apps.kaya.blob.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.blob.entity.Blob;

/**
 * Reads and writes the {@link Blob} rows of the blob domain. Other domains reach blobs only through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService} and
 * {@link com.korur.oba.apps.kaya.blob.service.BlobService}.
 */
@Repository
public interface BlobRepository extends JpaRepository<Blob, UUID>, JpaSpecificationExecutor<Blob> {

    Optional<Blob> findByOrganizationIdAndSha256(UUID organizationId, String sha256);

    Optional<Blob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * The sizes of the given blobs the organization stores, read without loading their content, so
     * a file list can show sizes without loading up to 5 MiB per file.
     *
     * @param organizationId the organization of the blobs
     * @param ids            the blob ids, not empty
     * @return one size per stored blob among them; an id of another organization is left out
     */
    @Query("SELECT b.id AS id, b.size AS size FROM Blob b WHERE b.organizationId = :organizationId AND b.id IN :ids")
    List<BlobSize> findSizes(@Param("organizationId") UUID organizationId, @Param("ids") Collection<UUID> ids);

    /**
     * Loads several blobs with their content by id, within the organization.
     */
    List<Blob> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    /**
     * Loads several blobs with their content, for the few files whose content a check reads.
     */
    List<Blob> findByOrganizationIdAndSha256In(UUID organizationId, Collection<String> sha256s);

    /**
     * Which of the given hashes the organization already stores, read without loading content.
     *
     * @param organizationId the organization of the blobs
     * @param sha256s        the hashes, not empty
     * @return the stored hashes among them
     */
    @Query("SELECT b.sha256 FROM Blob b WHERE b.organizationId = :organizationId AND b.sha256 IN :sha256s")
    List<String> findStoredSha256s(@Param("organizationId") UUID organizationId, @Param("sha256s") Collection<String> sha256s);

    /**
     * The total byte length of the given blobs the organization stores, read without loading their
     * content, so a request can be measured before its content is loaded.
     *
     * @param organizationId the organization of the blobs
     * @param sha256s        the hashes, not empty
     * @return the sum of their sizes; 0 when none is stored
     */
    @Query("SELECT COALESCE(SUM(b.size), 0) FROM Blob b WHERE b.organizationId = :organizationId AND b.sha256 IN :sha256s")
    long sumSizes(@Param("organizationId") UUID organizationId, @Param("sha256s") Collection<String> sha256s);

    /**
     * Stores content under its hash unless the organization already has it. Content is immutable
     * and addressed by its hash, so a second upload of the same bytes changes nothing, and two
     * uploads at once cannot fail on the unique key.
     *
     * @param organizationId the organization
     * @param sha256         the hash the caller has checked against the content
     * @param content        the bytes, at most 5 MiB
     * @param size           the byte length of the content
     * @return 1 when stored now, 0 when it was already stored
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO kaya.blobs (organization_id, sha256, content, size)
            VALUES (:organizationId, :sha256, :content, :size)
            ON CONFLICT (organization_id, sha256) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("organizationId") UUID organizationId,
                       @Param("sha256") String sha256,
                       @Param("content") byte[] content,
                       @Param("size") int size);

    /**
     * The id and byte length of one blob, without its content.
     */
    interface BlobSize {

        UUID getId();

        Integer getSize();
    }
}
