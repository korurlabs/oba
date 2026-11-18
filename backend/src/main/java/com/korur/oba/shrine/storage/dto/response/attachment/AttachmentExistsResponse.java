package com.korur.oba.shrine.storage.dto.response.attachment;

/**
 * Response payload for the attachment exists-check endpoint.
 *
 * Boolean-only by design: the endpoint is a pre-flight check the frontend
 * makes before kicking off a real multipart upload, so it should not
 * expose any metadata about an existing attachment. Returning the
 * matching attachment's id, file name, or url here would unnecessarily
 * broaden the read surface for a path whose only legitimate output is
 * "yes" or "no".
 */
public record AttachmentExistsResponse(boolean exists) {
}
