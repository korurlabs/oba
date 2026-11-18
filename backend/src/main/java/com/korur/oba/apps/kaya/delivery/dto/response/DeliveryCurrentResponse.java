package com.korur.oba.apps.kaya.delivery.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The newest signed release of a checkout's repository.
 */
@Schema(description = "The newest signed release of a checkout's repository")
public record DeliveryCurrentResponse(
        @Schema(description = "The checkout's repository", example = "4f5a6b7c-8d9e-4f0a-1b2c-3d4e5f6a7b8c")
        UUID repositoryId,

        @Schema(description = "Newest release; null while the repository has none", nullable = true,
                example = "3e4f5a6b-7c8d-4e9f-0a1b-2c3d4e5f6a7b")
        UUID releaseId,

        @Schema(description = "Its number; null while the repository has none", nullable = true, example = "7")
        Integer number,

        @Schema(description = "SHA-256 of its signed manifest; null while the repository has none", nullable = true,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        String manifestSha256
) {
}
