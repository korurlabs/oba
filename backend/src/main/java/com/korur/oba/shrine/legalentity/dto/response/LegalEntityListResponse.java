package com.korur.oba.shrine.legalentity.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * One row of the legal entity list, answered by GET /legal-entities: the fields a table or a
 * picker shows. The address and contract fields are in {@link LegalEntityDetailResponse}.
 *
 * @param id                 the legal entity id
 * @param shortName          the label shown in pickers
 * @param legalName          the full registered name
 * @param country            the ISO 3166-1 country code
 * @param registrationNumber the trade registry number, or null
 * @param updatedAt          the last change, UTC
 */
@Schema(description = "Legal entity list item")
public record LegalEntityListResponse(

        @Schema(description = "Legal entity id", example = "8b3e1f2a-4c5d-4e6f-9a0b-1c2d3e4f5a6b")
        UUID id,

        @Schema(description = "Label shown in pickers", example = "Korur B.V.")
        String shortName,

        @Schema(description = "Full registered name", example = "Korur B.V.")
        String legalName,

        @Schema(description = "Country of registration (ISO 3166-1)", example = "NLD")
        String country,

        @Schema(description = "Trade registry number", nullable = true, example = "12345678")
        String registrationNumber,

        @Schema(description = "Last change", example = "2026-11-22T09:30:00Z")
        Instant updatedAt
) {
}
