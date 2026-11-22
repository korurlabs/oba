package com.korur.oba.shrine.legalentity.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Every field of one legal entity, answered by the detail and by every write of
 * {@link com.korur.oba.shrine.legalentity.controller.LegalEntityController}.
 *
 * @param id                 the legal entity id
 * @param shortName          the label shown in pickers
 * @param legalName          the full registered name
 * @param country            the ISO 3166-1 country code
 * @param registrationNumber the trade registry number, or null
 * @param vatNumber          the VAT number, or null
 * @param street             the street and number, or null
 * @param postalCode         the postal code, or null
 * @param city               the city, or null
 * @param governingLaw       the law the contracts are governed by, or null
 * @param court              the court the contracts name, or null
 * @param createdAt          the creation time, UTC
 * @param updatedAt          the last change, UTC
 * @see LegalEntityListResponse
 */
@Schema(description = "Legal entity detail")
public record LegalEntityDetailResponse(

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

        @Schema(description = "VAT number", nullable = true, example = "NL123456789B01")
        String vatNumber,

        @Schema(description = "Street and number", nullable = true, example = "Keizersgracht 1")
        String street,

        @Schema(description = "Postal code", nullable = true, example = "1015 CJ")
        String postalCode,

        @Schema(description = "City", nullable = true, example = "Amsterdam")
        String city,

        @Schema(description = "The law the company's contracts are governed by", nullable = true,
                example = "Dutch law")
        String governingLaw,

        @Schema(description = "The court the company's contracts name for disputes", nullable = true,
                example = "Rechtbank Amsterdam")
        String court,

        @Schema(description = "Creation timestamp", example = "2026-11-22T09:00:00Z")
        Instant createdAt,

        @Schema(description = "Last change", example = "2026-11-22T09:30:00Z")
        Instant updatedAt
) {
}
