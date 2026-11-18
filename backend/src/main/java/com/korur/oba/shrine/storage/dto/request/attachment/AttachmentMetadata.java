package com.korur.oba.shrine.storage.dto.request.attachment;

// 4. Internal
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;

/**
 * Common projection of the parent-entity coordinates plus the original
 * file name, exposed by every attachment-flow request that the validator
 * needs to inspect.
 *
 * Defined as a package-private interface (records implement it for free)
 * so that AttachmentValidator.ensureFileNameMatches and
 * ensureFileNotDuplicate can serve both AttachmentCreateRequest (upload)
 * and AttachmentExistsCheckRequest (pre-flight check) from a single
 * method body. Two overloads with identical bodies were the alternative;
 * the interface keeps the validator DRY without leaking the abstraction
 * outside the request package.
 */
public interface AttachmentMetadata {

    String fileName();

    StorageEntityType entityType();
}
