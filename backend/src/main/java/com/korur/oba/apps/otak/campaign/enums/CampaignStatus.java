package com.korur.oba.apps.otak.campaign.enums;

/**
 * Where a campaign stands. Mapped by name to the PostgreSQL enum otak_campaign_status_enum,
 * in its order. Any status may follow any other; only the planned and published moments are
 * tied to it (see {@link com.korur.oba.apps.otak.campaign.validator.CampaignValidator#ensureStatusDates}).
 */
public enum CampaignStatus {

    /**
     * Being written; not planned and not published, so it has no published moment.
     */
    DRAFT,

    /**
     * Planned to go out at its planned moment, which it must have; not published yet.
     */
    SCHEDULED,

    /**
     * Posted by hand by a member, at its published moment.
     */
    PUBLISHED,

    /**
     * Kept for reference only; keeps whatever moments it had.
     */
    ARCHIVED
}
