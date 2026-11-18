package com.korur.oba.apps.otak.contact.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;

/**
 * One row of the contact list of a relation. It leaves out the timestamps, which no list
 * shows, and keeps the source URL, which the CLI contact list prints.
 *
 * Built by {@link com.korur.oba.apps.otak.contact.mapper.ContactMapper#toListResponse} in
 * {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl#list}, which
 * puts the main person contact first, then the main business contact, then the others by
 * name. Answered by
 * {@link com.korur.oba.apps.otak.contact.controller.ContactController#listContacts}, and
 * read by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl} for
 * the contact of each outreach.
 *
 * Both main flags are always present: at most one of them is true, the one of the contact's
 * kind, since a contact can only be main of its own kind.
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
 * @see ContactDetailResponse
 */
@Schema(description = "Contact list item")
public record ContactListResponse(

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
        String sourceUrl
) {
}
