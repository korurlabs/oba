package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;

@Schema(description = "The git repository the session runs in")
public record IngestRepositoryRequest(
        @Schema(description = "The git remote of the checkout", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "git@github.com:korurlabs/village.git")
        @NotBlank(message = IngestConstant.REMOTE_URL_REQUIRED_MESSAGE)
        @Size(max = IngestConstant.REMOTE_URL_MAX_LENGTH, message = IngestConstant.REMOTE_URL_SIZE_MESSAGE)
        String remoteUrl,

        @Schema(description = "Repository name, used only when Kaya connects the repository", nullable = true,
                example = "village")
        @Size(max = IngestConstant.REPOSITORY_NAME_MAX_LENGTH, message = IngestConstant.REPOSITORY_NAME_SIZE_MESSAGE)
        String name
) {
}
