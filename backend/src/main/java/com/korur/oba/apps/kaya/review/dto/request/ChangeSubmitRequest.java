package com.korur.oba.apps.kaya.review.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.version.constant.VersionConstant;

/**
 * The reason a proposer gives when submitting a draft for review.
 */
@Schema(description = "Submits a draft for review")
public record ChangeSubmitRequest(
        @Schema(description = "Why the change is needed; the reviewer reads it", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Allow the oba CLI in every repository")
        @NotBlank(message = VersionConstant.REASON_REQUIRED_MESSAGE)
        @Size(min = VersionConstant.REASON_MIN_LENGTH, max = VersionConstant.REASON_MAX_LENGTH,
                message = VersionConstant.REASON_SIZE_MESSAGE)
        String reason
) {
}
