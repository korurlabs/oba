package com.korur.oba.apps.otak.contact.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;

/**
 * Every field of one contact, the timestamps included, answered on create and update so
 * the client sees the main flags the service settled (the first contact of a kind is main
 * whatever the request said) and the new last-change time.
 *
 * Built by {@link com.korur.oba.apps.otak.contact.mapper.ContactMapper#toDetailResponse} in
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create} and
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update}; answered
 * by {@link com.korur.oba.apps.otak.contact.controller.ContactController#createContact} and
 * {@link com.korur.oba.apps.otak.contact.controller.ContactController#updateContact}.
 *
 * @param id                  the contact id
 * @param relationId          the relation the contact works at
 * @param name                the full name
 * @param kind                PERSON or BUSINESS
 * @param role                the job title or function, or null
 * @param email               the email address, or null
 * @param emailType           the kind of the email address, or null
 * @param phone               the phone number, or null
 * @param linkedinUrl         the LinkedIn profile URL, or null
 * @param decisionMaker       whether the person decides on a deal
 * @param mainPersonContact   whether the contact is the relation's main person contact
 * @param mainBusinessContact whether the contact is the relation's main business contact
 * @param sourceUrl           the page the contact was found on, or null
 * @param createdAt           when the contact was created, UTC
 * @param updatedAt           when the contact last changed, UTC
 * @see ContactListResponse
 */
@Schema(description = "Contact detail")
public record ContactDetailResponse(

        @Schema(description = "Contact id", example = "5c1d7f0e-2a4b-4c3d-8e9f-0a1b2c3d4e5f")
        UUID id,

        @Schema(description = "Relation the contact works at", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID relationId,

        @Schema(description = "Full name", example = "Jan de Vries")
        String name,

        @Schema(description = "PERSON for a person at the relation, BUSINESS for the company's general details",
                example = "PERSON")
        ContactKind kind,

        @Schema(description = "Job title or function", nullable = true, example = "Operations manager")
        String role,

        @Schema(description = "Email address", nullable = true, example = "jan@fabrikam.example")
        String email,

        @Schema(description = "Kind of the email address", nullable = true, example = "COMPANY")
        ContactEmailType emailType,

        @Schema(description = "Phone number", nullable = true, example = "+31 20 123 4567")
        String phone,

        @Schema(description = "LinkedIn profile", nullable = true, example = "https://www.linkedin.com/in/jandevries")
        String linkedinUrl,

        @Schema(description = "The person decides on a deal", example = "true")
        boolean decisionMaker,

        @Schema(description = "The person is the relation's main person contact, the one to talk to first",
                example = "true")
        boolean mainPersonContact,

        @Schema(description = "The business contact is the relation's main business contact, the inbox to write to",
                example = "false")
        boolean mainBusinessContact,

        @Schema(description = "Page the contact was found on", nullable = true, example = "https://www.fabrikam.example/team")
        String sourceUrl,

        @Schema(description = "Creation timestamp", example = "2026-11-09T09:00:00Z")
        Instant createdAt,

        @Schema(description = "Last change", example = "2026-11-09T09:30:00Z")
        Instant updatedAt
) {
}
