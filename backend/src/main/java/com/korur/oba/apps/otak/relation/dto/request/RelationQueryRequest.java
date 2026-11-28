package com.korur.oba.apps.otak.relation.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.enums.PipelineStage;
import com.korur.oba.apps.otak.relation.enums.RelationType;
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Filters of the relations list, one list of every type: the type and stage filters narrow
 * it independently, and a stage implies its type. Relations are always ordered by their
 * last change, newest first, so sortBy and sortDirection are ignored.
 */
@Schema(description = "Paging and filters of the relations list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class RelationQueryRequest extends QueryRequest {

    @Schema(description = "Only relations of these types, repeat the parameter for several. Empty: every type",
            nullable = true, example = "LEAD")
    private List<RelationType> type;

    @Schema(description = "Only relations at these stages, repeat the parameter for several, whatever the type "
            + "filter; a supplier has no stage and never matches. Empty: every relation but the leads at LOST "
            + "or PARKED", nullable = true, example = "NEW")
    private List<PipelineStage> stage;

    @Schema(description = "Only relations of this owner", nullable = true,
            example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
    private UUID ownerUserId;

    @Schema(description = "Part of the name, case ignored; not blank when given", nullable = true, example = "fepa")
    @NullOrNotBlank(message = RelationConstant.SEARCH_BLANK_MESSAGE)
    @Size(max = RelationConstant.SEARCH_MAX_LENGTH, message = RelationConstant.SEARCH_SIZE_MESSAGE)
    private String search;

    @Schema(description = "true: only relations with at least one contact; false: only relations without "
            + "one. Empty: both", nullable = true, example = "false")
    private Boolean hasContacts;

    @Schema(description = "Only relations linked to this event", nullable = true,
            example = "8a4d2c1b-6e5f-4a3b-9c8d-7e6f5a4b3c2d")
    private UUID eventId;

    @Schema(description = "Only relations linked to this campaign", nullable = true,
            example = "3b7c9d1e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
    private UUID campaignId;
}
