package com.korur.oba.apps.otak.relation.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.math.BigDecimal;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.apps.otak.relation.enums.RelationSource;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Request payload used to replace the fields of a relation. A full update: every
 * field is written, and null clears an optional one. Type and stage are not part of
 * it; each changes through its own endpoint.
 */
@Schema(description = "Relation update request")
public record RelationUpdateRequest(

        // Display name of the company.
        @Schema(description = "Name of the company", requiredMode = Schema.RequiredMode.REQUIRED, example = "Fabrikam B.V.")
        @NotBlank(message = RelationConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = RelationConstant.NAME_MAX_LENGTH, message = RelationConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "Website, a full http or https address; null when unknown", nullable = true,
                example = "https://www.fabrikam.example")
        @NullOrNotBlank(message = RelationConstant.WEBSITE_BLANK_MESSAGE)
        @Size(max = RelationConstant.WEBSITE_MAX_LENGTH, message = RelationConstant.WEBSITE_SIZE_MESSAGE)
        @Pattern(regexp = RelationConstant.WEBSITE_PATTERN, message = RelationConstant.WEBSITE_PATTERN_MESSAGE)
        String website,

        @Schema(description = "Sector", nullable = true, example = "Textile")
        @NullOrNotBlank(message = RelationConstant.SECTOR_BLANK_MESSAGE)
        @Size(max = RelationConstant.SECTOR_MAX_LENGTH, message = RelationConstant.SECTOR_SIZE_MESSAGE)
        String sector,

        @Schema(description = "Head-count band", nullable = true, example = "SIZE_51_200")
        RelationSize size,

        @Schema(description = "Chamber of commerce registration number (KVK)", nullable = true, example = "12345678")
        @NullOrNotBlank(message = RelationConstant.REGISTRATION_NUMBER_BLANK_MESSAGE)
        @Size(max = RelationConstant.REGISTRATION_NUMBER_MAX_LENGTH,
                message = RelationConstant.REGISTRATION_NUMBER_SIZE_MESSAGE)
        String registrationNumber,

        @Schema(description = "VAT number", nullable = true, example = "NL123456789B01")
        @NullOrNotBlank(message = RelationConstant.VAT_NUMBER_BLANK_MESSAGE)
        @Size(max = RelationConstant.VAT_NUMBER_MAX_LENGTH, message = RelationConstant.VAT_NUMBER_SIZE_MESSAGE)
        String vatNumber,

        @Schema(description = "Street and number", nullable = true, example = "Keizersgracht 1")
        @NullOrNotBlank(message = RelationConstant.STREET_BLANK_MESSAGE)
        @Size(max = RelationConstant.STREET_MAX_LENGTH, message = RelationConstant.STREET_SIZE_MESSAGE)
        String street,

        @Schema(description = "Postal code", nullable = true, example = "1015 CJ")
        @NullOrNotBlank(message = RelationConstant.POSTAL_CODE_BLANK_MESSAGE)
        @Size(max = RelationConstant.POSTAL_CODE_MAX_LENGTH, message = RelationConstant.POSTAL_CODE_SIZE_MESSAGE)
        String postalCode,

        @Schema(description = "City", nullable = true, example = "Amsterdam")
        @NullOrNotBlank(message = RelationConstant.CITY_BLANK_MESSAGE)
        @Size(max = RelationConstant.CITY_MAX_LENGTH, message = RelationConstant.CITY_SIZE_MESSAGE)
        String city,

        @Schema(description = "ISO 3166-1 alpha-2 country code in upper case; null when unknown", nullable = true,
                example = "NL")
        Country country,

        @Schema(description = "How the organization came to know the company", nullable = true, example = "REFERRAL")
        RelationSource source,

        // Euros only, at most 10 digits before and 2 after the decimal point.
        @Schema(description = "Expected deal value in euros, zero or more", nullable = true, example = "25000.00")
        @DecimalMin(value = RelationConstant.EXPECTED_VALUE_MIN, message = RelationConstant.EXPECTED_VALUE_MIN_MESSAGE)
        @Digits(integer = RelationConstant.EXPECTED_VALUE_INTEGER_DIGITS,
                fraction = RelationConstant.EXPECTED_VALUE_FRACTION_DIGITS,
                message = RelationConstant.EXPECTED_VALUE_DIGITS_MESSAGE)
        BigDecimal expectedValue,

        // Must be an active member of the organization.
        @Schema(description = "User who looks after the relation, an active member of the organization; null for none",
                nullable = true, example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID ownerUserId,

        // Must be an ICP of the organization.
        @Schema(description = "ICP of the organization; null for none", nullable = true,
                example = "5c0f6a1e-2b7d-4e8f-9a3c-1d2e3f4a5b6c")
        UUID icpId,

        // Must be an event of the organization.
        @Schema(description = "Event of the organization where the company was met; null for none", nullable = true,
                example = "8a4d2c1b-6e5f-4a3b-9c8d-7e6f5a4b3c2d")
        UUID eventId,

        @Schema(description = "Free notes", nullable = true, example = "Met at the textile fair.")
        @NullOrNotBlank(message = RelationConstant.NOTES_BLANK_MESSAGE)
        @Size(max = RelationConstant.NOTES_MAX_LENGTH, message = RelationConstant.NOTES_SIZE_MESSAGE)
        String notes
) {
}
