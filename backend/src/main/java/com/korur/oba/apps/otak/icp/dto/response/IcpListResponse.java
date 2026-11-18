package com.korur.oba.apps.otak.icp.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.icp.enums.Industry;
import com.korur.oba.apps.otak.icp.enums.TargetPersonRole;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.RelationSize;

/**
 * One row of the ICP list.
 */
@Schema(description = "ICP list item")
public record IcpListResponse(

        @Schema(description = "ICP id", example = "5f0c2a8e-7d41-4b6a-9c3e-2e8b1d4f6a70")
        UUID id,

        @Schema(description = "Name of the ICP", example = "Dutch textile SMEs")
        String name,

        @Schema(description = "Industry the ICP targets", example = "MANUFACTURING")
        Industry industry,

        @Schema(description = "ISO 3166-1 alpha-2 country code; null for any country", nullable = true, example = "NL")
        Country country,

        @Schema(description = "Smallest head-count band; null for no lower bound", nullable = true,
                example = "SIZE_11_50")
        RelationSize companySizeMin,

        @Schema(description = "Largest head-count band; null for no upper bound", nullable = true,
                example = "SIZE_201_500")
        RelationSize companySizeMax,

        @Schema(description = "Role of the person to approach; null for any", nullable = true, example = "CEO")
        TargetPersonRole targetPersonRole,

        @Schema(description = "Last change", example = "2026-11-09T09:30:00Z")
        Instant updatedAt
) {
}
