package com.korur.oba.apps.kazan.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * Response payload for one contributor of a repository, an option of the
 * picker's author filter. It carries the login alone, so GitHub's emails and
 * names never reach a client.
 */
@Schema(description = "GitHub contributor response")
public record GithubContributorResponse(

        @Schema(description = "GitHub login", example = "devssensey")
        String login
) {
}
