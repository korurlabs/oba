package com.korur.oba.apps.otak.event.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.EventConstant;
import com.korur.oba.apps.otak.event.enums.EventType;
import com.korur.oba.apps.otak.relation.constant.RelationConstant;
import com.korur.oba.apps.otak.relation.enums.Country;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Request payload used to create an event, such as an expo the organization visits, the
 * body of {@link com.korur.oba.apps.otak.event.controller.EventController#createEvent}. The
 * organization comes from the checked request header, never from the body, so an event can
 * not be written into another organization.
 *
 * Field rules, checked by Bean Validation before the service runs (422 GEN_VAL_001): the
 * name, the type and the start date are required; an optional text is null or holds
 * text, never blank ({@link com.korur.oba.dojo.annotation.NullOrNotBlank}), and is capped
 * at its column length; the website must be a full http or https address. The dates are
 * calendar days without a timezone, because an event happens on a day wherever the viewer
 * is.
 *
 * Rules that need two fields or the stored events are checked in
 * {@link com.korur.oba.apps.otak.event.service.impl.EventServiceImpl#create}:
 *
 * - {@link com.korur.oba.apps.otak.event.validator.EventValidator#ensureDateRange} refuses
 *   an end date before the start date,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_EVENT_DATE_RANGE}
 *   (OTK_VAL_010, 422);
 * - {@link com.korur.oba.apps.otak.event.validator.EventValidator#ensureNameFree} refuses a
 *   name another event of the organization already has, case ignored,
 *   {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_EVENT_NAME_TAKEN}
 *   (OTK_STA_013, 409), so an event can be picked by its name without ambiguity.
 *
 * The request is mapped with {@link com.korur.oba.apps.otak.event.mapper.EventMapper#toEntity}.
 * Sent by the Otak web app and by the oba CLI (oba otak event).
 *
 * @param name      the event name, unique within the organization with case ignored;
 *                  required, stored trimmed
 * @param type      the kind of event; required
 * @param startDate the first day, a calendar day without timezone; required
 * @param endDate   the last day, or null for a one-day event; not before startDate
 * @param city      the city, or null when unknown
 * @param country   the country, or null when unknown
 * @param website   the website, a full http or https address, or null when unknown
 * @param notes     free notes, or null
 * @see EventUpdateRequest
 */
@Schema(description = "Event create request")
public record EventCreateRequest(

        @Schema(description = "Name of the event, unique within the organization with case ignored",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "IGEF 2026")
        @NotBlank(message = EventConstant.NAME_REQUIRED_MESSAGE)
        @Size(max = EventConstant.NAME_MAX_LENGTH, message = EventConstant.NAME_SIZE_MESSAGE)
        String name,

        @Schema(description = "Kind of event", requiredMode = Schema.RequiredMode.REQUIRED, example = "EXPO")
        @NotNull(message = EventConstant.TYPE_REQUIRED_MESSAGE)
        EventType type,

        @Schema(description = "First day of the event (YYYY-MM-DD, a calendar day without timezone)",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-19")
        @NotNull(message = EventConstant.START_DATE_REQUIRED_MESSAGE)
        LocalDate startDate,

        @Schema(description = "Last day of the event (YYYY-MM-DD); null for a one-day event. Not before startDate",
                nullable = true, example = "2026-11-22")
        LocalDate endDate,

        @Schema(description = "City; null when unknown", nullable = true, example = "Ankara")
        @NullOrNotBlank(message = EventConstant.CITY_BLANK_MESSAGE)
        @Size(max = EventConstant.CITY_MAX_LENGTH, message = EventConstant.CITY_SIZE_MESSAGE)
        String city,

        @Schema(description = "ISO 3166-1 alpha-2 country code in upper case; null when unknown", nullable = true,
                example = "TR")
        Country country,

        @Schema(description = "Website, a full http or https address; null when unknown", nullable = true,
                example = "https://igef.com.tr/")
        @NullOrNotBlank(message = EventConstant.WEBSITE_BLANK_MESSAGE)
        @Size(max = EventConstant.WEBSITE_MAX_LENGTH, message = EventConstant.WEBSITE_SIZE_MESSAGE)
        @Pattern(regexp = RelationConstant.WEBSITE_PATTERN, message = EventConstant.WEBSITE_PATTERN_MESSAGE)
        String website,

        @Schema(description = "Free notes; null for none", nullable = true, example = "Booth in hall 4.")
        @NullOrNotBlank(message = EventConstant.NOTES_BLANK_MESSAGE)
        @Size(max = EventConstant.NOTES_MAX_LENGTH, message = EventConstant.NOTES_SIZE_MESSAGE)
        String notes
) {
}
