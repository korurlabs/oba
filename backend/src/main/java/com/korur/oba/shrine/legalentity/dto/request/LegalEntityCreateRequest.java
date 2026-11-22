package com.korur.oba.shrine.legalentity.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.shrine.legalentity.constant.LegalEntityConstant;

/**
 * Body of POST /legal-entities: the fields of a new legal entity.
 *
 * The organization is not in the body: it comes from the checked X-Organization-ID header,
 * so a client can never create a legal entity in an organization it was not checked against.
 *
 * Checked here by annotations: the short name, legal name and country are present and not
 * blank, an optional field is null or not blank, and every length stays within its column.
 * Checked by the service, because the rule needs the database: the trimmed short name is free
 * within the organization, case ignored
 * ({@link com.korur.oba.shrine.legalentity.validator.LegalEntityValidator#ensureShortNameUnique}).
 *
 * @param shortName          the label shown in pickers, stored trimmed; unique in the organization, case ignored
 * @param legalName          the full registered name
 * @param country            the ISO 3166-1 country code of registration
 * @param registrationNumber the trade registry number, or null
 * @param vatNumber          the VAT number, or null
 * @param street             the street and number, or null
 * @param postalCode         the postal code, or null
 * @param city               the city, or null
 * @param governingLaw       the law the contracts are governed by, or null
 * @param court              the court the contracts name, or null
 * @see LegalEntityUpdateRequest
 */
@Schema(description = "Legal entity create request")
public record LegalEntityCreateRequest(

        @Schema(description = "Label shown in pickers, unique within the organization with case ignored",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Korur B.V.")
        @NotBlank(message = LegalEntityConstant.SHORT_NAME_REQUIRED_MESSAGE)
        @Size(max = LegalEntityConstant.SHORT_NAME_MAX_LENGTH, message = LegalEntityConstant.SHORT_NAME_SIZE_MESSAGE)
        String shortName,

        @Schema(description = "Full registered name printed in contracts", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Korur B.V.")
        @NotBlank(message = LegalEntityConstant.LEGAL_NAME_REQUIRED_MESSAGE)
        @Size(max = LegalEntityConstant.LEGAL_NAME_MAX_LENGTH, message = LegalEntityConstant.LEGAL_NAME_SIZE_MESSAGE)
        String legalName,

        @Schema(description = "Country of registration (ISO 3166-1)", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "NLD")
        @NotBlank(message = LegalEntityConstant.COUNTRY_REQUIRED_MESSAGE)
        @Size(max = LegalEntityConstant.COUNTRY_MAX_LENGTH, message = LegalEntityConstant.COUNTRY_SIZE_MESSAGE)
        String country,

        @Schema(description = "Chamber of commerce or trade registry number", nullable = true, example = "12345678")
        @NullOrNotBlank(message = LegalEntityConstant.REGISTRATION_NUMBER_BLANK_MESSAGE)
        @Size(max = LegalEntityConstant.REGISTRATION_NUMBER_MAX_LENGTH,
                message = LegalEntityConstant.REGISTRATION_NUMBER_SIZE_MESSAGE)
        String registrationNumber,

        @Schema(description = "VAT number", nullable = true, example = "NL123456789B01")
        @NullOrNotBlank(message = LegalEntityConstant.VAT_NUMBER_BLANK_MESSAGE)
        @Size(max = LegalEntityConstant.VAT_NUMBER_MAX_LENGTH, message = LegalEntityConstant.VAT_NUMBER_SIZE_MESSAGE)
        String vatNumber,

        @Schema(description = "Street and number", nullable = true, example = "Keizersgracht 1")
        @NullOrNotBlank(message = LegalEntityConstant.STREET_BLANK_MESSAGE)
        @Size(max = LegalEntityConstant.STREET_MAX_LENGTH, message = LegalEntityConstant.STREET_SIZE_MESSAGE)
        String street,

        @Schema(description = "Postal code", nullable = true, example = "1015 CJ")
        @NullOrNotBlank(message = LegalEntityConstant.POSTAL_CODE_BLANK_MESSAGE)
        @Size(max = LegalEntityConstant.POSTAL_CODE_MAX_LENGTH, message = LegalEntityConstant.POSTAL_CODE_SIZE_MESSAGE)
        String postalCode,

        @Schema(description = "City", nullable = true, example = "Amsterdam")
        @NullOrNotBlank(message = LegalEntityConstant.CITY_BLANK_MESSAGE)
        @Size(max = LegalEntityConstant.CITY_MAX_LENGTH, message = LegalEntityConstant.CITY_SIZE_MESSAGE)
        String city,

        @Schema(description = "The law the company's contracts are governed by", nullable = true,
                example = "Dutch law")
        @NullOrNotBlank(message = LegalEntityConstant.GOVERNING_LAW_BLANK_MESSAGE)
        @Size(max = LegalEntityConstant.GOVERNING_LAW_MAX_LENGTH,
                message = LegalEntityConstant.GOVERNING_LAW_SIZE_MESSAGE)
        String governingLaw,

        @Schema(description = "The court the company's contracts name for disputes", nullable = true,
                example = "Rechtbank Amsterdam")
        @NullOrNotBlank(message = LegalEntityConstant.COURT_BLANK_MESSAGE)
        @Size(max = LegalEntityConstant.COURT_MAX_LENGTH, message = LegalEntityConstant.COURT_SIZE_MESSAGE)
        String court
) implements LegalEntityFields {
}
