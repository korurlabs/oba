package com.korur.oba.apps.otak.battlefield.controller;

// 1. Framework-level
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.battlefield.constant.controller.BattlefieldControllerLogConstant;
import com.korur.oba.apps.otak.battlefield.constant.controller.BattlefieldControllerResponseConstant;
import com.korur.oba.apps.otak.battlefield.service.BattlefieldQueryService;
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
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
 * REST controller for the battlefield: every lead and customer of an organization in one
 * view, grouped by stage on the client. It owns no data; {@link BattlefieldQueryService}
 * reads them from the relation domain.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/otak/battlefield")
@Tag(name = "Otak battlefield", description = "Every lead and customer of an organization in one view")
public class BattlefieldController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BattlefieldController.class);

    private final BattlefieldQueryService battlefieldQueryService;
    private final LoggingService loggingService;

    /**
     * Lists every lead and customer for the battlefield.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Battlefield",
            description = "Every lead and customer of the organization, every stage, by name.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed X-Organization-ID header"),
            @ApiResponse(responseCode = "403", description = "GUEST role in the organization (ORG_SEC_003), not a "
                    + "member of it, or membership or organization disabled (ORG_SEC_001, ORG_SEC_005)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<RelationListResponse>>> getBattlefield(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, BattlefieldControllerLogConstant.BATTLEFIELD_REQUEST);

        List<RelationListResponse> response = battlefieldQueryService.getBattlefield(organizationId);

        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, BattlefieldControllerLogConstant.BATTLEFIELD_COMPLETED);

        return ResponseUtil.ok(BattlefieldControllerResponseConstant.RESPONSE_BATTLEFIELD_RETRIEVED, response);
    }
}
