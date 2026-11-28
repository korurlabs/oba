package com.korur.oba.apps.otak.relation.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;

/**
 * One row of the relations list: the Leads, Customers and Suppliers lists and the
 * battlefield.
 *
 * Built by {@link com.korur.oba.apps.otak.relation.mapper.RelationMapper#toListResponse} in
 * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#list} and
 * {@link com.korur.oba.apps.otak.relation.service.impl.RelationQueryServiceImpl#listPipeline};
 * answered by
 * {@link com.korur.oba.apps.otak.relation.controller.RelationController#listRelations} and
 * {@link com.korur.oba.apps.otak.battlefield.controller.BattlefieldController#getBattlefield}.
 *
 * Person versus business contacts: a row shows the name and email of the main person
 * contact, the one to talk to first, and the email of the main business contact, the inbox
 * to write to. A relation has at most one main contact of each kind, so each column holds
 * one value. The main contacts of the whole list are read in one query, so a row costs no
 * extra query; a full contact list is
 * {@link com.korur.oba.apps.otak.contact.controller.ContactController#listContacts}.
 *
 * @param id                       the relation id
 * @param name                     the name of the company
 * @param website                  the website, or null
 * @param type                     LEAD, CUSTOMER or SUPPLIER
 * @param stage                    the stage, or null for a supplier
 * @param owner                    the owner, or null when the relation has none
 * @param mainPersonContactName    the name of the main person contact, or null
 * @param mainPersonContactEmail   the email of the main person contact, or null
 * @param mainBusinessContactEmail the email of the main business contact, or null
 * @param expectedValue            the expected deal value in euros, or null
 * @param updatedAt                when the relation last changed, UTC
 */
@Schema(description = "Relation list item")
public record RelationListResponse(

        @Schema(description = "Relation id", example = "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        UUID id,

        @Schema(description = "Name of the company", example = "Fabrikam B.V.")
        String name,

        @Schema(description = "Website", nullable = true, example = "https://www.fabrikam.example")
        String website,

        @Schema(description = "Kind of relation", example = "LEAD")
        RelationType type,

        @Schema(description = "Stage: a lead stage for a lead, CLOSED or DIRECT for a customer, null for a "
                + "supplier", nullable = true, example = "NEW")
        PipelineStage stage,

        @Schema(description = "Owner; null when the relation has none", nullable = true)
        RelationOwnerResponse owner,

        @Schema(description = "Name of the main person contact; null when the relation has no person contact",
                nullable = true, example = "Jan de Vries")
        String mainPersonContactName,

        @Schema(description = "Email of the main person contact; null when the relation has no person contact "
                + "or it has no email", nullable = true, example = "jan@fabrikam.example")
        String mainPersonContactEmail,

        @Schema(description = "Email of the main business contact; null when the relation has no business "
                + "contact or it has no email", nullable = true, example = "info@fabrikam.example")
        String mainBusinessContactEmail,

        @Schema(description = "Expected deal value in euros", nullable = true, example = "25000.00")
        BigDecimal expectedValue,

        @Schema(description = "Campaign of the organization that brought the relation in; null when the relation "
                + "has none", nullable = true, example = "3b7c9d1e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
        UUID campaignId,

        @Schema(description = "Last change", example = "2026-11-09T09:30:00Z")
        Instant updatedAt
) {
}
