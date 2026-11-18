package com.korur.oba.apps.kaya.delivery.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * A signed release as the CLI verifies it: the exact manifest text, its hash and its signature.
 */
@Schema(description = "A signed release: the exact signed manifest text, its hash and its signature")
public record DeliveryReleaseResponse(
        @Schema(description = "Release id", example = "3e4f5a6b-7c8d-4e9f-0a1b-2c3d4e5f6a7b")
        UUID releaseId,

        @Schema(description = "The checkout's repository", example = "4f5a6b7c-8d9e-4f0a-1b2c-3d4e5f6a7b8c")
        UUID repositoryId,

        @Schema(description = "Release number", example = "7")
        Integer number,

        @Schema(description = "The signed manifest as compact JSON: {organization, remoteUrl, number, createdAt, "
                + "files: [{path, sha256?, mode, target?, alwaysOn}]}. The signature covers its UTF-8 bytes exactly, "
                + "so verify the string before parsing it")
        String manifest,

        @Schema(description = "SHA-256 hex of the manifest's UTF-8 bytes",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String manifestSha256,

        @Schema(description = "Base64 Ed25519 signature of the manifest's UTF-8 bytes")
        String signature,

        @Schema(description = "Id of the signing key; GET /signing-keys lists the public keys", example = "kaya-2026-10")
        String keyId
) {
}
