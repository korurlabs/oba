package com.korur.oba.apps.otak.outreach.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The contact an outreach goes to, as far as the outreach lists show it.
 *
 * The address is left out on purpose: it is read at send time, and the address a sent
 * outreach went to is {@link OutreachDetailResponse#sentTo()}, which stays when the contact
 * changes.
 *
 * Built by {@link com.korur.oba.apps.otak.outreach.mapper.OutreachMapper#toContactResponse}
 * for {@link OutreachListResponse} and {@link OutreachDetailResponse}; null there once the
 * contact was deleted.
 *
 * @param id   the contact id
 * @param name the name of the contact
 */
@Schema(description = "Contact of an outreach")
public record OutreachContactResponse(

        @Schema(description = "Contact id", example = "7c6d5e4f-3a2b-4c1d-9e8f-7a6b5c4d3e2f")
        UUID id,

        @Schema(description = "Name of the contact", example = "Jan de Vries")
        String name
) {
}
