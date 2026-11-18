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
 * Request payload used to replace the fields of a contact, the body of
 * {@link com.korur.oba.apps.otak.contact.controller.ContactController#updateContact}.
 *
 * A full update (PUT): every field is written by
 * {@link com.korur.oba.apps.otak.contact.mapper.ContactMapper#updateEntity}, and null clears
 * an optional one, so a client sends the whole contact, not only what changed. There is no
 * "null keeps the stored value": that would make a cleared field and an omitted one look
 * the same. The source URL is not part of it, because it records where the contact was
 * found and never changes.
 *
 * Field rules, checked by Bean Validation before the service runs (422 GEN_VAL_001): the
 * same as {@link ContactCreateRequest}. Optional texts are null or hold text
 * ({@link com.korur.oba.dojo.annotation.NullOrNotBlank}); the kind and the booleans are
 * required, so an omitted flag is refused instead of being read as false.
 *
 * The kind replaces the stored one. A true main flag of the contact's kind makes it main
 * of that kind and unmarks the previous one. False on the current main contact of an
 * unchanged kind is refused by
 * {@link com.korur.oba.apps.otak.contact.validator.ContactValidator#ensureMainContactKept}
 * (GEN_VAL_001 on the flag), since the main contact changes only by marking another one;
 * a client that does not mean to change the flag sends the stored value back. A kind change
 * moves the main flag of the old kind to the oldest remaining contact of that kind, so the
 * flag of the old kind is not checked then. An email type needs an email address
 * (OTK_VAL_005) and a true flag of the other kind is refused (OTK_VAL_013). All of this runs
 * in {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update}.
 *
 * Sent by the Otak web app and by the oba CLI (oba otak contact update).
 *
 * @param name                the full name; required
 * @param kind                PERSON or BUSINESS, replacing the stored kind; required
 * @param role                the job title or function, or null to clear it
 * @param email               the email address, or null to clear it
 * @param emailType           the kind of the email address, or null; cleared with the address
 * @param phone               the phone number as typed, or null to clear it
 * @param linkedinUrl         the LinkedIn profile URL, or null to clear it
 * @param decisionMaker       whether the person decides on a deal; required
 * @param mainPersonContact   the main person flag; false on the current main person contact
 *                            is refused unless the kind changes; required
 * @param mainBusinessContact the main business flag; false on the current main business
 *                            contact is refused unless the kind changes; required
 * @see ContactCreateRequest
 */
@Schema(description = "Contact update request")
public record ContactUpdateRequest(

        @Schema(description = "Full name", requiredMode = Schema.RequiredMode.REQUIRED, example = "Jan de Vries")
        @NotBlank(message = ContactConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = ContactConstant.NAME_MAX_LENGTH, message = ContactConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "PERSON for a person at the relation, BUSINESS for the company's general details "
                + "such as an info@ inbox; replaces the stored kind", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "PERSON")
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
                + "address, so clear it together with the address", nullable = true, example = "COMPANY")
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

        @Schema(description = "True makes a PERSON contact the relation's main person contact and unmarks the "
                + "previous one; true on a BUSINESS contact is refused; false on the current main person contact "
                + "is refused unless the kind changes", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull(message = ContactConstant.MAIN_PERSON_CONTACT_REQUIRED_MESSAGE)
        Boolean mainPersonContact,

        @Schema(description = "True makes a BUSINESS contact the relation's main business contact and unmarks the "
                + "previous one; true on a PERSON contact is refused; false on the current main business contact "
                + "is refused unless the kind changes", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
        @NotNull(message = ContactConstant.MAIN_BUSINESS_CONTACT_REQUIRED_MESSAGE)
        Boolean mainBusinessContact
) {
}
