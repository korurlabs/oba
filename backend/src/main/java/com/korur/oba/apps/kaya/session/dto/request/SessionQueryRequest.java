package com.korur.oba.apps.kaya.session.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;

/**
 * Filters of the wall. Sessions are always ordered by their last event, newest first, so
 * sortBy and sortDirection are ignored. The wall pages by 60, above the 50 that other lists
 * allow, so this request keeps its own page size: getAmount and setAmount read and write it,
 * and the base class's field stays at its default.
 */
@Schema(description = "Paging and filters of the wall; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class SessionQueryRequest extends QueryRequest {

    @Schema(description = "Number of sessions per page", example = "60", nullable = true)
    @Min(value = 1, message = CommonConstant.AMOUNT_MIN_MESSAGE)
    @Max(value = SessionConstant.LIST_MAX_AMOUNT, message = SessionConstant.AMOUNT_MAX_MESSAGE)
    private int amount = SessionConstant.LIST_DEFAULT_AMOUNT;

    @Schema(description = "Matches the title, the branch or a Kazan ticket key, case ignored", nullable = true,
            example = "KAYA-42")
    @Size(max = SessionConstant.SEARCH_MAX_LENGTH, message = SessionConstant.SEARCH_SIZE_MESSAGE)
    private String search;

    @Schema(description = "Only sessions of this repository", nullable = true)
    private UUID repositoryId;

    @Schema(description = "Only sessions of this user", nullable = true)
    private UUID userId;

    @Schema(description = "Only sessions of this machine", nullable = true)
    private UUID machineId;

    @Schema(description = "Only sessions on this model", nullable = true, example = "claude-opus-5-5")
    private String model;

    @Schema(description = "Only sessions on this Claude organization (subscription)", nullable = true)
    private String accountOrganizationUuid;

    @Schema(description = "true: only ended sessions; false: only sessions still running", nullable = true)
    private Boolean ended;
}
