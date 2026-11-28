package com.korur.oba.connect.azure.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Azure Connect domain (AZR).
 *
 * <p>Covers Azure Blob Storage operations: file upload, download, listing,
 * and deletion.</p>
 *
 * Also covers the Azure connections of an organization and the Azure Resource Manager reads made
 * through them. The upstream codes of those reads tell the admin whether to retry
 * ({@link #UPS_AZURE_ARM_UNREACHABLE}), to fix the identity's role or federated credential
 * ({@link #UPS_AZURE_ARM_CREDENTIALS_REFUSED}, {@link #UPS_AZURE_TOKEN_EXCHANGE_REFUSED}) or to wait
 * ({@link #UPS_AZURE_ARM_RATE_LIMITED}).
 */
public enum ExceptionDomainAzureError implements ExceptionDomainError {

    // ── Not found (AZR_NOT) ─────────────────────────────────────────────────
    NOT_AZURE_CONNECTION_FOUND("AZR_NOT_001", "Azure connection not found.", ExceptionCategory.NOT),
    NOT_AZURE_CONNECTIONS_UNAVAILABLE("AZR_NOT_002",
            "Azure connections are not available: this deployment has no OpenID Connect issuer configured.",
            ExceptionCategory.NOT),

    // ── State (AZR_STA) ─────────────────────────────────────────────────────
    STA_AZURE_CONNECTION_NOT_CONNECTED("AZR_STA_001",
            "The Azure connection is not connected. Verify it first.", ExceptionCategory.STA),
    STA_AZURE_CONNECTION_IN_USE("AZR_STA_002",
            "The Azure connection is still in use and cannot be deleted.", ExceptionCategory.STA),
    STA_AZURE_CONNECTION_IDENTITY_DUPLICATE("AZR_STA_003",
            "This organization already has a connection for this tenant and client id.", ExceptionCategory.STA),
    STA_AZURE_CONNECTION_NAME_DUPLICATE("AZR_STA_004",
            "This organization already has an Azure connection with this name.", ExceptionCategory.STA),
    STA_AZURE_CONNECTION_NO_SUBSCRIPTION("AZR_STA_005",
            "The Azure identity can read no subscription. Give it the Reader role on a subscription.",
            ExceptionCategory.STA),

    // ── Validation (AZR_VAL) ────────────────────────────────────────────────
    VAL_AZURE_CONNECTION_CLIENT_ID_MISSING("AZR_VAL_001",
            "Enter the client id of the Azure identity before verifying the connection.", ExceptionCategory.VAL),

    // ── Upstream (AZR_UPS) ──────────────────────────────────────────────────
    UPS_AZURE_BLOB_UPLOAD("AZR_UPS_001", "Failed to upload file to Azure Blob Storage.", ExceptionCategory.UPS),
    UPS_AZURE_BLOB_DOWNLOAD("AZR_UPS_002", "Failed to download file from Azure Blob Storage.", ExceptionCategory.UPS),
    UPS_AZURE_BLOB_LIST("AZR_UPS_003", "Failed to list files in Azure Blob Storage.", ExceptionCategory.UPS),
    UPS_AZURE_BLOB_DELETE("AZR_UPS_004", "Failed to delete file from Azure Blob Storage.", ExceptionCategory.UPS),
    UPS_AZURE_ARM_UNREACHABLE("AZR_UPS_005",
            "Azure Resource Manager could not be reached or could not answer the request. Try again later.",
            ExceptionCategory.UPS),
    UPS_AZURE_ARM_CREDENTIALS_REFUSED("AZR_UPS_006",
            "Azure Resource Manager refused the identity of the connection. Check its Reader role.",
            ExceptionCategory.UPS),
    UPS_AZURE_ARM_RATE_LIMITED("AZR_UPS_007",
            "Azure Resource Manager is limiting requests. Wait a minute and try again.", ExceptionCategory.UPS),
    UPS_AZURE_ARM_UNEXPECTED_RESPONSE("AZR_UPS_008",
            "Azure Resource Manager returned a response that could not be read.", ExceptionCategory.UPS),
    UPS_AZURE_TOKEN_EXCHANGE_REFUSED("AZR_UPS_009",
            "Entra ID refused the token exchange. Check the federated credential of the Azure identity.",
            ExceptionCategory.UPS);

    private final String code;
    private final String message;
    private final ExceptionCategory category;

    ExceptionDomainAzureError(String code, String message, ExceptionCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ExceptionCategory getCategory() {
        return category;
    }
}
