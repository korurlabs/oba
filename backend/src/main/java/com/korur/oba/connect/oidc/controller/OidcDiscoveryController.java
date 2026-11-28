package com.korur.oba.connect.oidc.controller;

// 1. Framework-level
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 2. Third-party
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.oidc.constant.OidcConstant;
import com.korur.oba.connect.oidc.constant.controller.OidcDiscoveryControllerLogConstant;
import com.korur.oba.connect.oidc.dto.response.OidcDiscoveryResponse;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;

/**
 * The two public documents of the OpenID Connect issuer, which a cloud provider fetches without
 * credentials to verify the assertions Oba signs. Both paths are in
 * {@link SecurityConstant#PUBLIC_PATHS}, so the handlers carry no access rule; they publish only
 * the issuer URL and public keys.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "OpenID Connect issuer", description = "Public discovery document and key set of the issuer")
public class OidcDiscoveryController {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OidcDiscoveryController.class);

    private final OidcAssertionService oidcAssertionService;
    private final LoggingService loggingService;

    /**
     * Answers the raw discovery document, not the JSON envelope, because a provider reads the
     * shape OpenID Connect Discovery defines.
     */
    @GetMapping(value = SecurityConstant.OIDC_DISCOVERY_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the OpenID Connect discovery document",
            description = "Anonymous. The raw document, cached for an hour. Errors answer the usual JSON envelope")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The discovery document"),
            @ApiResponse(responseCode = "404", description = "OID_NOT_001: this deployment has no issuer"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OidcDiscoveryResponse> getDiscovery() {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OidcDiscoveryControllerLogConstant.DISCOVERY_REQUEST);
        OidcDiscoveryResponse response = oidcAssertionService.getDiscovery();
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OidcDiscoveryControllerLogConstant.DISCOVERY_COMPLETED,
                response.issuer());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(OidcConstant.DOCUMENT_CACHE_MAX_AGE).cachePublic())
                .body(response);
    }

    /**
     * Answers the raw JSON Web Key Set, not the JSON envelope, because a provider reads the shape
     * RFC 7517 defines.
     */
    @GetMapping(value = SecurityConstant.OIDC_JWKS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the issuer key set",
            description = "Anonymous. The public keys, signing key first, cached for an hour. Errors answer the usual "
                    + "JSON envelope")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The key set"),
            @ApiResponse(responseCode = "404", description = "OID_NOT_001: this deployment has no issuer"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getJwks() {
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OidcDiscoveryControllerLogConstant.JWKS_REQUEST);
        Map<String, Object> response = oidcAssertionService.getJwks();
        loggingService.observe(LOG_PREFIX, LogLayer.CONTROLLER, OidcDiscoveryControllerLogConstant.JWKS_COMPLETED);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(OidcConstant.DOCUMENT_CACHE_MAX_AGE).cachePublic())
                .body(response);
    }
}
