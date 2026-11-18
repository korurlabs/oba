package com.korur.oba.apps.kaya.seed.dto.embedded;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.blob.constant.BlobConstant;
import com.korur.oba.apps.kaya.seed.constant.SeedConstant;

/**
 * A managed file whose copy in a consumer repository differs from the hash .agents/.managed.json
 * records for it. The CLI finds drift, because only it reads the disk; a run with drift fails, since
 * the release then cannot equal the repository byte for byte.
 */
@Schema(description = "A managed file whose disk copy differs from .agents/.managed.json")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeedDrift(
        @Schema(description = "Path relative to the consumer repository", requiredMode = Schema.RequiredMode.REQUIRED,
                example = ".agents/rules/core.md")
        @NotBlank
        @Size(max = SeedConstant.REPORTED_PATH_MAX_LENGTH)
        String path,

        @Schema(description = "The pack the file is managed by", nullable = true, example = "org")
        @Size(max = SeedConstant.SET_NAME_MAX_LENGTH)
        String pack,

        @Schema(description = "SHA-256 that .agents/.managed.json records", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        @NotBlank
        @Pattern(regexp = BlobConstant.SHA256_PATTERN, message = BlobConstant.SHA256_INVALID_MESSAGE)
        String expectedSha256,

        @Schema(description = "SHA-256 of the file on disk; null when the file is missing", nullable = true,
                example = "60303ae22b998861bce3b28f33eec1be758a213c86c93c076dbe9f558c11c752")
        @Pattern(regexp = BlobConstant.SHA256_PATTERN, message = BlobConstant.SHA256_INVALID_MESSAGE)
        String actualSha256
) {
}
