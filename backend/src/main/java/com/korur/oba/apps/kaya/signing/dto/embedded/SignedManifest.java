package com.korur.oba.apps.kaya.signing.dto.embedded;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.signing.helper.SignedManifestWriter;

/**
 * The document a release signature covers. It names the organization and the remote URL, so a
 * release of one repository cannot be replayed into another, and the number, so the CLI can refuse
 * a lower one. Its canonical form is written by {@link SignedManifestWriter}; the property order
 * here is the byte order of that form.
 *
 * @param organization id of the organization
 * @param remoteUrl    normalised remote of the repository, github.com/owner/name
 * @param number       the release number
 * @param createdAt    ISO-8601 instant in UTC with millisecond precision
 * @param files        the delivered paths, sorted by path code point by code point
 */
@JsonPropertyOrder({"organization", "remoteUrl", "number", "createdAt", "files"})
public record SignedManifest(
        String organization,
        String remoteUrl,
        int number,
        String createdAt,
        List<SignedManifestFile> files
) {
}
