package com.korur.oba.connect.azure.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.constant.AzureConnectionConstant;

/**
 * Request payload to create an Azure connection or to replace its fields.
 *
 * It carries no credential: Azure trusts the connection through a federated credential the
 * organization creates for the subject Oba shows. The client id may stay empty until that identity
 * exists; a check refuses to run without it.
 */
@Schema(description = "Azure connection create or update request")
public record AzureConnectionRequest(
        @Schema(description = "Connection name, unique in the organization", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Production")
        @NotBlank(message = AzureConnectionConstant.MSG_NAME_REQUIRED)
        @Size(max = AzureConnectionConstant.NAME_MAX_LENGTH, message = AzureConnectionConstant.MSG_NAME_SIZE)
        String name,

        @Schema(description = "Directory (tenant) id of the Entra ID tenant that holds the identity",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        @NotNull(message = AzureConnectionConstant.MSG_TENANT_ID_REQUIRED)
        UUID tenantId,

        @Schema(description = "Client id of the managed identity or app registration; null until it exists",
                nullable = true, example = "9a8b7c6d-5e4f-4a3b-2c1d-0e9f8a7b6c5d")
        UUID clientId
) {
}
