package com.korur.oba.apps.kaya.signing.controller;

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
import com.korur.oba.apps.kaya.signing.constant.controller.SigningKeyControllerLogConstant;
import com.korur.oba.apps.kaya.signing.constant.controller.SigningKeyControllerResponseConstant;
import com.korur.oba.apps.kaya.signing.dto.embedded.SigningKey;
import com.korur.oba.apps.kaya.signing.dto.response.SigningKeyResponse;
import com.korur.oba.apps.kaya.signing.service.SigningQueryService;

/**
 * The public keys that release signatures verify against. The keys are the server's, the same for
 * every organization; the organization header is still required, so only a member reads them here.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/kaya/signing-keys")
@Tag(name = "Signing keys", description = "Public keys of release signing")
public class SigningKeyController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SigningKeyController.class);

    private final SigningQueryService signingQueryService;
    private final LoggingService loggingService;

    /**
     * Lists the trusted signing keys.
     */
    @GetMapping
    @RequiredUserAccess(OrganizationMemberRole.MEMBER)
    @Operation(summary = "List the release signing keys",
            description = "The current key first, then a previous key during a rotation; publicKey is the raw 32-byte "
                    + "Ed25519 key in base64")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "400", description = "X-Organization-ID missing or not a UUID"),
            @ApiResponse(responseCode = "401", description = "No valid JWT"),
            @ApiResponse(responseCode = "403", description = "Not a member with access to the organization"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CustomResponse<List<SigningKeyResponse>>> getSigningKeys(
            @Parameter(hidden = true) @GetUser User user,
            @RequestHeader(CommonConstant.HEADER_ORGANIZATION_ID) UUID organizationId) {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SigningKeyControllerLogConstant.SIGNING_KEYS_REQUEST);
        List<SigningKeyResponse> response = signingQueryService.getSigningKeys();
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, SigningKeyControllerLogConstant.SIGNING_KEYS_COMPLETED, response.size());
        return ResponseUtil.ok(SigningKeyControllerResponseConstant.RESPONSE_SIGNING_KEYS_RETRIEVED, response);
    }
}
