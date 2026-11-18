package com.korur.oba.apps.kaya.review.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.apps.kaya.version.enums.VersionStatus;

/**
 * Paging and filters of the changes list of an organization.
 */
@Schema(description = "Paging and filters of the changes of an organization; most recently changed version first, "
        + "sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class ChangeQueryRequest extends QueryRequest {

    @Schema(description = "Only versions in this status", nullable = true, example = "IN_REVIEW")
    private VersionStatus status;

    @Schema(description = "Only versions of this pack", nullable = true, example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
    private UUID packId;
}
