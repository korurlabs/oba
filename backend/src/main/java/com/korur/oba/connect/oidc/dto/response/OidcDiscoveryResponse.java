package com.korur.oba.connect.oidc.dto.response;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * The OpenID Connect discovery document, in the snake case names of OpenID Connect Discovery 1.0,
 * because a provider reads that shape and not the response envelope.
 */
@Schema(description = "OpenID Connect discovery document of the issuer")
public record OidcDiscoveryResponse(
        @Schema(description = "Issuer URL, the iss of every assertion")
        @JsonProperty("issuer") String issuer,
        @Schema(description = "URL of the issuer key set")
        @JsonProperty("jwks_uri") String jwksUri,
        @Schema(description = "Response types the issuer supports")
        @JsonProperty("response_types_supported") List<String> responseTypesSupported,
        @Schema(description = "Subject types the issuer supports")
        @JsonProperty("subject_types_supported") List<String> subjectTypesSupported,
        @Schema(description = "Algorithms the issuer signs with")
        @JsonProperty("id_token_signing_alg_values_supported") List<String> idTokenSigningAlgValuesSupported
) {
}
