package com.korur.oba.apps.otak.outreach.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Filters of the outreach list. Outreaches are always ordered newest first, so sortBy and
 * sortDirection are ignored.
 *
 * Paging comes from {@link QueryRequest}. Both filters are optional and combine with AND;
 * the organization is not a filter here but comes from the checked request header, so the
 * list never shows another organization's outreaches whatever these fields hold.
 *
 * Bound from query parameters by
 * {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#listOutreaches} and
 * turned into a query by {@link com.korur.oba.apps.otak.outreach.service.OutreachQueryService#list}
 * through {@link com.korur.oba.apps.otak.outreach.specification.OutreachSpecification}.
 *
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#list
 */
@Schema(description = "Paging and filters of the outreach list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class OutreachQueryRequest extends QueryRequest {

    /**
     * Only outreaches to this relation; null for every relation of the organization.
     */
    @Schema(description = "Only outreaches to this relation; null for every relation", nullable = true,
            example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b")
    private UUID relationId;

    /**
     * Only outreaches with this {@link OutreachStatus}; null for every status.
     */
    @Schema(description = "Only outreaches with this status; null for every status", nullable = true, example = "DRAFT")
    private OutreachStatus status;
}
