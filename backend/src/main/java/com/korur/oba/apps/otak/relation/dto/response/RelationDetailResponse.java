package com.korur.oba.apps.otak.relation.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.apps.otak.relation.enums.RelationSource;
import com.korur.oba.apps.otak.relation.enums.RelationType;

/**
 * Every field of one relation.
 */
@Schema(description = "Relation detail")
public record RelationDetailResponse(

        @Schema(description = "Relation id", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID id,

        @Schema(description = "Name of the company", example = "Fabrikam B.V.")
        String name,

        @Schema(description = "Kind of relation", example = "LEAD")
        RelationType type,

        @Schema(description = "Stage: a lead stage for a lead, CLOSED or DIRECT for a customer, null for a "
                + "supplier", nullable = true, example = "NEW")
        PipelineStage stage,

        @Schema(description = "Website", nullable = true, example = "https://www.fabrikam.example")
        String website,

        @Schema(description = "Sector", nullable = true, example = "Textile")
        String sector,

        @Schema(description = "Head-count band", nullable = true, example = "SIZE_51_200")
        RelationSize size,

        @Schema(description = "Chamber of commerce registration number (KVK)", nullable = true, example = "12345678")
        String registrationNumber,

        @Schema(description = "VAT number", nullable = true, example = "NL123456789B01")
        String vatNumber,

        @Schema(description = "Street and number", nullable = true, example = "Keizersgracht 1")
        String street,

        @Schema(description = "Postal code", nullable = true, example = "1015 CJ")
        String postalCode,

        @Schema(description = "City", nullable = true, example = "Amsterdam")
        String city,

        @Schema(description = "ISO 3166-1 alpha-2 country code", nullable = true, example = "NL")
        Country country,

        @Schema(description = "How the organization came to know the company", nullable = true, example = "REFERRAL")
        RelationSource source,

        @Schema(description = "Expected deal value in euros", nullable = true, example = "25000.00")
        BigDecimal expectedValue,

        @Schema(description = "Owner; null when the relation has none", nullable = true)
        RelationOwnerResponse owner,

        @Schema(description = "ICP of the organization; null when the relation has none", nullable = true,
                example = "5c0f6a1e-2b7d-4e8f-9a3c-1d2e3f4a5b6c")
        UUID icpId,

        @Schema(description = "Event of the organization where the company was met; null when the relation has none",
                nullable = true, example = "8a4d2c1b-6e5f-4a3b-9c8d-7e6f5a4b3c2d")
        UUID eventId,

        @Schema(description = "Campaign of the organization that brought the relation in; null when the relation "
                + "has none", nullable = true, example = "3b7c9d1e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
        UUID campaignId,

        @Schema(description = "Free notes", nullable = true, example = "Met at the textile fair.")
        String notes,

        @Schema(description = "Creation timestamp", example = "2026-11-09T09:00:00Z")
        Instant createdAt,

        @Schema(description = "Last change", example = "2026-11-09T09:30:00Z")
        Instant updatedAt
) {
}
