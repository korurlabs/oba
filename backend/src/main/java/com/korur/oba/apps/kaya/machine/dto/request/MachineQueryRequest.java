package com.korur.oba.apps.kaya.machine.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.enums.MachineStatus;

/**
 * Filters of the machine list. The list is not paged, because an organization has a handful of
 * machines per person; it is ordered by user display name and machine name.
 */
@Schema(description = "Filters of the machine list")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class MachineQueryRequest {

    @Schema(description = "Only machines of this user", nullable = true)
    private UUID userId;

    @Schema(description = "Only machines whose latest account is this account", nullable = true)
    private UUID accountId;

    @Schema(description = "Only machines with this status", nullable = true, example = "ACTIVE")
    private MachineStatus status;
}
