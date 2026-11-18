package com.korur.oba.apps.kazan.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * Response payload for one repository of the GitHub App installation, for the
 * picker's repository select.
 */
@Schema(description = "GitHub repository response")
public record GithubRepositoryResponse(

        @Schema(description = "GitHub repository id", example = "812345678")
        long githubRepositoryId,

        @Schema(description = "Repository as owner/name", example = "korurlabs/village")
        String fullName,

        @Schema(description = "True when the repository is private on GitHub", example = "true")
        boolean privateRepository
) {
}
