package com.korur.oba.apps.otak.outreach.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Filters of the outreach list. Outreaches are always ordered newest first, so sortBy and
 * sortDirection are ignored.
 *
 * Paging comes from {@link QueryRequest}. Every filter is optional and they combine with AND;
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

    /**
     * Only outreaches of this campaign; null for every campaign and for none.
     */
    @Schema(description = "Only outreaches of this campaign; null for every outreach", nullable = true,
            example = "3b7c9d1e-4f5a-4b6c-8d7e-9f0a1b2c3d4e")
    private UUID campaignId;

    /**
     * Part of the outreach title, the contact's name or mail address, or the relation's name,
     * case ignored and trimmed; null or blank for no search. A blank value is ignored rather
     * than refused, so a cleared search box can still send the parameter.
     */
    @Schema(description = "Part of the title, the contact's name or mail address, or the relation's name; "
            + "case ignored, trimmed, blank ignored", nullable = true, example = "fabrikam")
    @Size(max = OutreachConstant.SEARCH_MAX_LENGTH, message = OutreachConstant.SEARCH_SIZE_MESSAGE)
    private String search;

    /**
     * Only outreaches through this {@link OutreachType}; null for every channel.
     */
    @Schema(description = "Only outreaches through this channel; null for every channel", nullable = true,
            example = "MAIL")
    private OutreachType channel;

    /**
     * Only outreaches sent from this mail sender, or whose failed send last tried it; null for
     * every outreach. A draft, a WhatsApp and a call outreach have no sender, so they never
     * match a sender filter.
     */
    @Schema(description = "Only outreaches sent, or last tried, from this mail sender; drafts, WhatsApp "
            + "and calls have none and never match. Null for every outreach", nullable = true, example = "6c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
    private UUID mailSenderId;
}
