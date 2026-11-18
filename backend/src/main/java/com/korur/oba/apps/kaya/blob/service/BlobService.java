package com.korur.oba.apps.kaya.blob.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.io.InputStream;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.blob.dto.response.BlobUploadResponse;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Writes {@link Blob}s: file content stored once per organization under its SHA-256. The server
 * hashes the bytes itself wherever a client names a hash, so a stored blob always matches its hash.
 *
 * Called by {@link com.korur.oba.apps.kaya.blob.controller.BlobController} for the uploads of
 * kaya seed, and by the domains that store rendered or edited file bytes (version, release, seed).
 */
public interface BlobService {

    /**
     * Stores uploaded content unless the organization already has it.
     *
     * @param actor          the OWNER uploading it
     * @param organizationId the organization of the request
     * @param sha256         the hash the client names, 64 lower-case hex characters
     * @param content        the request body, read up to the 5 MiB file limit
     * @return the hash, the size and whether this upload stored it
     * @throws com.korur.oba.dojo.exception.internal.business.BadRequestException KAY_CFG_005 when
     *         the content does not hash to sha256
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException 413
     *         KAY_VAL_012 when the content is over 5 MiB
     */
    BlobUploadResponse uploadBlob(User actor, UUID organizationId, String sha256, InputStream content);

    /**
     * Stores content the server already holds under the hash it computes itself, unless the
     * organization has it, and returns the stored row. Joins the caller's transaction.
     *
     * @param organizationId the organization
     * @param content        the bytes, at most 5 MiB
     * @return the blob of this content
     */
    Blob storeContent(UUID organizationId, byte[] content);

    /**
     * Stores content under a hash the caller has computed from it, unless the organization has it.
     * Joins the caller's transaction.
     *
     * @param organizationId the organization
     * @param sha256         the hash of content, 64 lower-case hex characters
     * @param content        the bytes, at most 5 MiB
     * @return true when stored now, false when it was already stored
     */
    boolean storeIfAbsent(UUID organizationId, String sha256, byte[] content);
}
