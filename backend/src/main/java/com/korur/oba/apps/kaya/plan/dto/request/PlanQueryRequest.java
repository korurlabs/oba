package com.korur.oba.apps.kaya.plan.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanReviewState;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;

/**
 * Filters of the plans list. Plans are always ordered by their last change, newest first, so
 * sortBy and sortDirection are ignored. The list allows pages of up to 100, above the 50 of the
 * base class, so this request keeps its own page size.
 */
@Schema(description = "Paging and filters of the plans list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class PlanQueryRequest extends QueryRequest {

    @Schema(description = "Number of plans per page", example = "25", nullable = true)
    @Min(value = 1, message = CommonConstant.AMOUNT_MIN_MESSAGE)
    @Max(value = PlanConstant.LIST_MAX_AMOUNT, message = PlanConstant.AMOUNT_MAX_MESSAGE)
    private int amount = PlanConstant.LIST_DEFAULT_AMOUNT;

    @Schema(description = "Only plans of this repository", nullable = true)
    private UUID repositoryId;

    @Schema(description = "Only plans of this pack", nullable = true, example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
    private UUID packId;

    @Schema(description = "Only plans of this kind", nullable = true, example = "SPEC")
    private PlanKind kind;

    @Schema(description = "Only plans in this review state", nullable = true, example = "OPEN")
    private PlanReviewState reviewState;

    @Schema(description = "Only plans whose latest version came from this user", nullable = true)
    private UUID userId;

    @Schema(description = "Only plans with a version written by this Claude Code session", nullable = true,
            example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
    @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN, message = PlanConstant.SESSION_ID_FILTER_MESSAGE)
    private String sessionId;

    @Schema(description = "Matches the title or the path, case ignored", nullable = true, example = "kaya plans")
    @Size(max = PlanConstant.SEARCH_MAX_LENGTH, message = PlanConstant.SEARCH_SIZE_MESSAGE)
    private String search;

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }
}
