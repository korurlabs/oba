package com.korur.oba.apps.otak.relation.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.enums.RelationType;

/**
 * Request payload used to change the type of a relation, such as a lead that became a customer.
 */
@Schema(description = "Relation type change request")
public record RelationTypeRequest(

        @Schema(description = "New type of the relation", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "CUSTOMER")
        @NotNull(message = RelationConstant.TYPE_REQUIRED_MESSAGE)
        RelationType type
) {
}
