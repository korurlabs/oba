package com.korur.oba.apps.otak.contact.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.contact.constant.ContactConstant;
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Request payload used to add a contact to a relation, the body of
 * {@link com.korur.oba.apps.otak.contact.controller.ContactController#createContact}. The
 * relation comes from the path and the organization from the checked X-Organization-ID
 * header, never from the body.
 *
 * Field rules, checked by Bean Validation before the service runs (422 GEN_VAL_001):
 *
 * - Optional text fields take null for "none", not an empty string:
 *   {@link com.korur.oba.dojo.annotation.NullOrNotBlank} refuses "" and whitespace, so a
 *   blank value is never stored as if it were data. Each text is capped at its column
 *   length.
 * - The booleans are boxed and NotNull, as their columns are NOT NULL: an omitted flag is
 *   refused instead of being read as false, so the caller always states what it asks.
 * - The kind is required: a contact is a {@link ContactKind#PERSON} or a
 *   {@link ContactKind#BUSINESS} contact, and the main flags only make sense against it.
 *
 * Rules that compare fields are checked by
 * {@link com.korur.oba.apps.otak.contact.validator.ContactValidator} in
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}: an email
 * type needs an email address (OTK_VAL_005), and a true flag of the other kind is refused
 * (OTK_VAL_013). The first contact of a kind is main whatever its flag says, so a relation
 * with contacts of a kind always has a main one. The request is mapped with
 * {@link com.korur.oba.apps.otak.contact.mapper.ContactMapper#toEntity}, which leaves the
 * kind and the main flags to the service.
 *
 * Sent by the Otak web app and by the oba CLI (oba otak contact create).
 *
 * @param name                the full name; required
 * @param kind                PERSON or BUSINESS; required
 * @param role                the job title or function, or null
 * @param email               the email address, or null
 * @param emailType           the kind of the email address, or null; only with an email
 * @param phone               the phone number as typed, or null
 * @param linkedinUrl         the LinkedIn profile URL, or null
 * @param decisionMaker       whether the person decides on a deal; required
 * @param sourceUrl           the page the contact was found on, or null; set on create only
 * @param mainPersonContact   true to make a PERSON contact the main person contact; required
 * @param mainBusinessContact true to make a BUSINESS contact the main business contact;
 *                            required
 * @see ContactUpdateRequest
 */
@Schema(description = "Contact create request")
public record ContactCreateRequest(

        @Schema(description = "Full name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Jan de Vries")
        @NotBlank(message = ContactConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = ContactConstant.NAME_MAX_LENGTH, message = ContactConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "PERSON for a person at the relation, BUSINESS for the company's general details "
                + "such as an info@ inbox", requiredMode = Schema.RequiredMode.REQUIRED, example = "PERSON")
        @NotNull(message = ContactConstant.KIND_REQUIRED_MESSAGE)
        ContactKind kind,

        @Schema(description = "Job title or function; null for none", nullable = true, example = "Operations manager")
        @NullOrNotBlank(message = ContactConstant.ROLE_BLANK_MESSAGE)
        @Size(max = ContactConstant.ROLE_MAX_LENGTH, message = ContactConstant.ROLE_SIZE_MESSAGE)
        String role,

        @Schema(description = "Email address; null for none", nullable = true, example = "jan@fabrikam.example")
        @NullOrNotBlank(message = ContactConstant.EMAIL_BLANK_MESSAGE)
        @Size(max = ContactConstant.EMAIL_MAX_LENGTH, message = ContactConstant.EMAIL_SIZE_MESSAGE)
        @Email(message = ContactConstant.EMAIL_FORMAT_MESSAGE)
        String email,

        @Schema(description = "Kind of the email address: COMPANY for the person's own address at the company, "
                + "SHARED for an inbox others also read, PERSONAL for a private address; only with an email "
                + "address", nullable = true, example = "COMPANY")
        ContactEmailType emailType,

        @Schema(description = "Phone number as the user typed it; null for none", nullable = true,
                example = "+31 20 123 4567")
        @NullOrNotBlank(message = ContactConstant.PHONE_BLANK_MESSAGE)
        @Size(max = ContactConstant.PHONE_MAX_LENGTH, message = ContactConstant.PHONE_SIZE_MESSAGE)
        String phone,

        @Schema(description = "LinkedIn profile, a full http or https address; null for none", nullable = true,
                example = "https://www.linkedin.com/in/jandevries")
        @NullOrNotBlank(message = ContactConstant.LINKEDIN_URL_BLANK_MESSAGE)
        @Size(max = ContactConstant.LINKEDIN_URL_MAX_LENGTH, message = ContactConstant.LINKEDIN_URL_SIZE_MESSAGE)
        @Pattern(regexp = ContactConstant.LINKEDIN_URL_PATTERN, message = ContactConstant.LINKEDIN_URL_PATTERN_MESSAGE)
        String linkedinUrl,

        @Schema(description = "The person decides on a deal", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "true")
        @NotNull(message = ContactConstant.DECISION_MAKER_REQUIRED_MESSAGE)
        Boolean decisionMaker,

        @Schema(description = "Page the contact was found on, a full http or https address; set on create only; "
                + "null for none", nullable = true, example = "https://www.fabrikam.example/team")
        @NullOrNotBlank(message = ContactConstant.SOURCE_URL_BLANK_MESSAGE)
        @Size(max = ContactConstant.SOURCE_URL_MAX_LENGTH, message = ContactConstant.SOURCE_URL_SIZE_MESSAGE)
        @Pattern(regexp = ContactConstant.SOURCE_URL_PATTERN, message = ContactConstant.SOURCE_URL_PATTERN_MESSAGE)
        String sourceUrl,

        @Schema(description = "True makes a PERSON contact the relation's main person contact and unmarks the "
                + "previous one; true on a BUSINESS contact is refused. The first person contact of a relation is "
                + "main whatever this says", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull(message = ContactConstant.MAIN_PERSON_CONTACT_REQUIRED_MESSAGE)
        Boolean mainPersonContact,

        @Schema(description = "True makes a BUSINESS contact the relation's main business contact and unmarks the "
                + "previous one; true on a PERSON contact is refused. The first business contact of a relation is "
                + "main whatever this says", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        @NotNull(message = ContactConstant.MAIN_BUSINESS_CONTACT_REQUIRED_MESSAGE)
        Boolean mainBusinessContact
) {
}
