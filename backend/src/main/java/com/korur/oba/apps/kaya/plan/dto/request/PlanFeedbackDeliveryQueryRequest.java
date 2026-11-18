package com.korur.oba.apps.kaya.plan.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;

/**
 * Which pending feedback the CLI asks for: that of its live sessions, or, when a new session starts,
 * that of the ended sessions of its checkout's branch. Exactly one of the two forms is given. No
 * toString, since a raw remote may carry an access token.
 */
@Schema(description = "Either sessionId (repeated), or remoteUrl and branch")
@Getter
@Setter
@NoArgsConstructor
public class PlanFeedbackDeliveryQueryRequest {

    @Schema(description = "Claude Code session ids of the token's user, 1 to 20; repeat the parameter",
            example = "[\"6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f\"]")
    @Size(max = PlanConstant.DELIVERY_SESSION_IDS_MAX, message = PlanConstant.DELIVERY_SESSION_IDS_SIZE_MESSAGE)
    private List<@Pattern(regexp = SessionConstant.SESSION_ID_PATTERN, message = PlanConstant.SESSION_ID_FILTER_MESSAGE)
            String> sessionId = new ArrayList<>();

    @Schema(description = "The checkout's git remote, in any form git accepts; Kaya normalises it", nullable = true,
            example = "git@github.com:korurlabs/village.git")
    @Size(max = PlanConstant.REMOTE_URL_MAX_LENGTH, message = PlanConstant.REMOTE_URL_SIZE_MESSAGE)
    private String remoteUrl;

    @Schema(description = "The checkout's branch", nullable = true, example = "feature/plans")
    @Size(max = PlanConstant.BRANCH_MAX_LENGTH, message = PlanConstant.BRANCH_SIZE_MESSAGE)
    private String branch;
}
