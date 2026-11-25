package com.korur.oba.apps.kazan.cyberscore.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckStatus;

/**
 * Query parameters for listing the checks of an organization.
 */
@Getter
@Setter
@NoArgsConstructor
public class CyberscoreCheckQueryRequest {

    /**
     * Filter by workflow status. Null means all statuses.
     */
    @Schema(description = "Filter by workflow status", nullable = true, example = "IN_PROGRESS")
    private CyberscoreCheckStatus status;
}
