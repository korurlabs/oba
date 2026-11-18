package com.korur.oba.apps.kaizen.dailyupdate.dto.request;

// 1. Framework-level
import org.springframework.format.annotation.DateTimeFormat;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal

/**
 * Query parameters for the list of an organization's members who have not
 * posted a daily update by the deadline of one day.
 */
@Getter
@Setter
@NoArgsConstructor
public class DailyUpdateMissingRequest {

    /**
     * Organization whose expected members are listed; the caller must be its OWNER or ADMIN.
     */
    @Schema(description = "Organization whose members are listed; the caller must be its OWNER or ADMIN",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    /**
     * Reported day in Europe/Istanbul. Null means today there; a later day is refused.
     */
    @Schema(description = "Reported day (YYYY-MM-DD) in Europe/Istanbul; omitted means today there. Future days are refused",
            nullable = true, example = "2026-10-19")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
}
