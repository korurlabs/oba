package com.korur.oba.shrine.document.enums.document;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.access.enums.ResourceAccessType;

/**
 * Kind of owner a document belongs to. Mapped by name to the PostgreSQL enum
 * shrine_document_entity_type_enum, so a new constant needs a migration that adds the same
 * value to that type.
 *
 * Access to a document is access to its owner: each constant names the
 * {@link ResourceAccessType} whose rules
 * {@link com.korur.oba.shrine.access.service.ResourceAccessQueryService#verifyResourceAccess}
 * applies, as {@link com.korur.oba.shrine.storage.enums.common.StorageEntityType} does for
 * attachments.
 */
public enum DocumentEntityType {

    /**
     * An Otak relation; reading its documents needs Otak access, as reading the relation does.
     */
    RELATION(ResourceAccessType.RELATION);

    private final ResourceAccessType resourceAccessType;

    DocumentEntityType(ResourceAccessType resourceAccessType) {
        this.resourceAccessType = resourceAccessType;
    }

    /**
     * @return the access type whose rules decide who reads and writes documents of this owner kind
     */
    public ResourceAccessType getResourceAccessType() {
        return resourceAccessType;
    }
}
