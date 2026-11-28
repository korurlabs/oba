package com.korur.oba.connect.azure.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * The three values a federated credential on the Azure identity must name for Entra ID to accept
 * the connection's assertions.
 */
@Schema(description = "Values of the federated credential to create in Azure")
public record AzureConnectionSetupResponse(
        @Schema(description = "Issuer URL of this deployment; null when no issuer is configured", nullable = true,
                example = "https://api.example.com")
        String issuer,

        @Schema(description = "Subject of the connection, fixed at create",
                example = "oba:organization:0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e:connection:5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b")
        String subject,

        @Schema(description = "Audience Entra ID requires", example = "api://AzureADTokenExchange")
        String audience
) {
}
