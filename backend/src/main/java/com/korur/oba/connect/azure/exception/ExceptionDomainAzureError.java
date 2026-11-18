package com.korur.oba.connect.azure.exception;

import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;

/**
 * Domain-specific error codes for the Azure Connect domain (AZR).
 *
 * <p>Covers Azure Blob Storage operations: file upload, download, listing,
 * and deletion.</p>
 */
public enum ExceptionDomainAzureError implements ExceptionDomainError {

    // ── Upstream (AZR_UPS) ──────────────────────────────────────────────────
    UPS_AZURE_BLOB_UPLOAD("AZR_UPS_001", "Failed to upload file to Azure Blob Storage.", ExceptionCategory.UPS),
    UPS_AZURE_BLOB_DOWNLOAD("AZR_UPS_002", "Failed to download file from Azure Blob Storage.", ExceptionCategory.UPS),
    UPS_AZURE_BLOB_LIST("AZR_UPS_003", "Failed to list files in Azure Blob Storage.", ExceptionCategory.UPS),
    UPS_AZURE_BLOB_DELETE("AZR_UPS_004", "Failed to delete file from Azure Blob Storage.", ExceptionCategory.UPS);

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
