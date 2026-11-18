package com.korur.oba.apps.kaya.repository.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;
import com.korur.oba.apps.kaya.repository.constant.RepositoryConstant;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;

@Schema(description = "Partial update of a repository; a field left out or null keeps its value")
public record RepositoryUpdateRequest(
        @Schema(description = "New display name, trimmed", nullable = true, example = "Village")
        @NullOrNotBlank(message = RepositoryConstant.NAME_BLANK_MESSAGE)
        @Size(max = RepositoryConstant.NAME_MAX_LENGTH, message = RepositoryConstant.NAME_SIZE_MESSAGE)
        String name,

        // The one field where an empty string is meaningful: null keeps the template, so an empty
        // string is the only way to remove it.
        @Schema(description = "Ticket URL template: an https URL containing {key}, which is replaced with the "
                + "ticket key; an empty string removes it", nullable = true,
                example = "https://kazan.korur.com/tickets/{key}")
        @Size(max = RepositoryConstant.TICKET_URL_TEMPLATE_MAX_LENGTH,
                message = RepositoryConstant.TICKET_URL_TEMPLATE_SIZE_MESSAGE)
        @Pattern(regexp = RepositoryConstant.TICKET_URL_TEMPLATE_PATTERN,
                message = RepositoryConstant.TICKET_URL_TEMPLATE_FORMAT_MESSAGE)
        String ticketUrlTemplate,

        @Schema(description = "New status; ingest for an ARCHIVED repository answers 422 KAY_VAL_002",
                nullable = true, example = "ARCHIVED")
        RepositoryStatus status
) {
}
