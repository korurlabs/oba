package com.korur.oba.apps.kaya.account.controller;

// 1. Framework-level
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.annotation.GetUser;
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.dojo.util.ResponseUtil;
import com.korur.oba.apps.kaya.account.constant.controller.AccountControllerLogConstant;
import com.korur.oba.apps.kaya.account.constant.controller.AccountControllerResponseConstant;
import com.korur.oba.apps.kaya.account.dto.request.AccountQueryRequest;
import com.korur.oba.apps.kaya.account.dto.response.AccountDetailResponse;
import com.korur.oba.apps.kaya.account.dto.response.AccountListResponse;
import com.korur.oba.apps.kaya.account.service.AccountQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Accounts of the organization named by the X-Organization-ID header: the AI provider accounts its
 * users use, with who uses them, their machines and their current usage. Accounts are learned from
 * recorder batches, so this controller only reads.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/accounts")
@Tag(name = "Accounts", description = "AI provider accounts of the organization, their users and usage")
public class AccountController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AccountController.class);

    private final AccountQueryService accountQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the accounts of the organization.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the accounts of the organization",
            description = "Not paged; ordered by account email, provider organization name and id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "422", description = "GEN_VAL_001: a filter value is invalid"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<AccountListResponse>>> getAccounts(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @Valid @ModelAttribute @ParameterObject AccountQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AccountControllerLogConstant.ACCOUNT_LIST_REQUEST, organizationId);
        List<AccountListResponse> response = accountQueryService.getAccounts(organizationId, request);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AccountControllerLogConstant.ACCOUNT_LIST_COMPLETED,
                response.size(), organizationId);
        return ResponseUtil.ok(AccountControllerResponseConstant.RESPONSE_ACCOUNTS_RETRIEVED, response);
    }

    /**
     * Reads one account.
     */
    @GetMapping("/{id}")
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "Read an account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID, or the id is not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "404", description = "KAY_NF_001: no such account in the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<AccountDetailResponse>> getAccount(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId,
            @PathVariable UUID id) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AccountControllerLogConstant.ACCOUNT_GET_REQUEST, id, organizationId);
        AccountDetailResponse response = accountQueryService.getAccount(organizationId, id);
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, AccountControllerLogConstant.ACCOUNT_GET_COMPLETED, id, organizationId);
        return ResponseUtil.ok(AccountControllerResponseConstant.RESPONSE_ACCOUNT_RETRIEVED, response);
    }
}
