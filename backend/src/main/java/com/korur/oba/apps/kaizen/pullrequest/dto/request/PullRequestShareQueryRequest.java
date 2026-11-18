package com.korur.oba.apps.kaizen.pullrequest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Query parameters for the share list of an organization's pull requests
 * channel. The list is always sorted by share time, newest first, so sortBy
 * and sortDirection are ignored.
 */
@Getter
@Setter
@NoArgsConstructor
public class PullRequestShareQueryRequest extends QueryRequest {

    /**
     * Keep shares with one of these statuses. Null or empty means every status.
     */
    @Schema(description = "Keep shares with one of these statuses", nullable = true, example = "IN_REVIEW")
    private List<PullRequestShareStatus> statuses;

    /**
     * Keep shares by one of these authors. Null or empty means every author.
     */
    @Schema(description = "Keep shares by one of these authors", nullable = true,
            example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
    private List<UUID> authorIds;

    /**
     * True keeps the shares waiting for the caller's review: the caller is a
     * reviewer who has not approved, and the pull request is not merged. Null
     * or false applies no filter.
     */
    @Schema(description = "True keeps shares where the caller is a reviewer who has not approved and the status "
            + "is not MERGED", nullable = true, example = "true")
    private Boolean awaitingMyReview;
}
