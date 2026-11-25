package com.korur.oba.apps.kazan.cyberscore.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.controller.CyberscoreControllerLogConstant;
import com.korur.oba.apps.kazan.cyberscore.constant.controller.CyberscoreControllerResponseConstant;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreTrendQueryRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreResponse;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreTrendPointResponse;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreQueryService;
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * REST controller for an organization's cyberscore and its trend. Members and
 * above may read them, guests may not; the score changes only through the
 * check endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kazan/cyberscore")
@Tag(name = "Cyberscore", description = "Live security score of the organization")
public class CyberscoreController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CyberscoreController.class);

    private final CyberscoreQueryService cyberscoreQueryService;
    private final LoggingService loggingService;

    /**
     * Returns the current score, its level and the category breakdown.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get cyberscore", description = "Current score, level and category breakdown of the "
            + "organization; 0 and UNKNOWN before the first scorable check")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "A guest of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<CyberscoreResponse>> getCyberscore(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreControllerLogConstant.CYBERSCORE_GET_REQUEST);

        CyberscoreResponse response = cyberscoreQueryService.getCurrent(organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreControllerLogConstant.CYBERSCORE_GET_COMPLETED);

        return ResponseUtil.ok(CyberscoreControllerResponseConstant.RESPONSE_CYBERSCORE_RETRIEVED, response);
    }

    /**
     * Returns the score per business day of the window, oldest first.
     */
    @GetMapping("/trend")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Get cyberscore trend", description = "Score per business day (Europe/Istanbul), "
            + "oldest first, ending with today; empty before the first scorable check")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "A guest of the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "422", description = "days is not a whole number (GEN_VAL_001)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<CyberscoreTrendPointResponse>>> getCyberscoreTrend(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject CyberscoreTrendQueryRequest query) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreControllerLogConstant.CYBERSCORE_TREND_REQUEST);

        List<CyberscoreTrendPointResponse> response = cyberscoreQueryService.getTrend(organizationId, query);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER,
                CyberscoreControllerLogConstant.CYBERSCORE_TREND_COMPLETED);

        return ResponseUtil.ok(CyberscoreControllerResponseConstant.RESPONSE_CYBERSCORE_TREND_RETRIEVED, response);
    }
}
