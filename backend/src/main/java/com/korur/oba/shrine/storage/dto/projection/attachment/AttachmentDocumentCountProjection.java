package com.korur.oba.shrine.storage.dto.projection.attachment;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The number of attachments made from one document, read by
 * {@link com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository#countByDocumentIds}
 * in one grouped query for a page of documents.
 */
public interface AttachmentDocumentCountProjection {

    /**
     * @return the document the attachments are snapshots of
     */
    UUID getDocumentId();

    /**
     * @return the number of its snapshots
     */
    long getSnapshotCount();
}
