package com.korur.oba.apps.kaya.reference.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.reference.dto.response.ReferenceDetailResponse;
import com.korur.oba.apps.kaya.reference.entity.Reference;

/**
 * Reads the {@link Reference}s of an organization's versions, and their size and content through
 * {@link com.korur.oba.apps.kaya.blob.service.BlobQueryService}.
 *
 * Called by {@link com.korur.oba.apps.kaya.reference.controller.ReferenceController} for a reference opened from a
 * skill, and by the render and skill domains to load the references of a version.
 */
public interface ReferenceQueryService {

    /**
     * Reads one reference with its whole file: as UTF-8 text when the bytes are valid UTF-8, as base64
     * otherwise, so an image or other binary asset reaches the client unchanged.
     *
     * @param organizationId the organization of the request
     * @param referenceId the reference
     * @return the reference with its content
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such reference, or its blob is missing
     */
    ReferenceDetailResponse getReference(UUID organizationId, UUID referenceId);

    /**
     * Loads a reference of the organization.
     *
     * A reference of another organization answers the same 404 as an unknown id, so its existence is not
     * revealed.
     *
     * @param organizationId the organization of the request
     * @param referenceId the reference
     * @return the reference
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         organization has no such reference
     */
    Reference getEntityById(UUID organizationId, UUID referenceId);

    /**
     * Loads every reference of one version of the organization, ordered by name and id.
     *
     * @param organizationId the organization of the version
     * @param versionId      the version
     * @return the references; empty when the version has none or belongs to another organization
     */
    List<Reference> getEntitiesByVersionId(UUID organizationId, UUID versionId);
}
