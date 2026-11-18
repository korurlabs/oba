package com.korur.oba.connect.azure.constant.handler;

/**
 * Log message constants for Azure Blob Storage outbox handler.
 */
public final class AzureBlobOutboxHandlerLogConstant {

    private AzureBlobOutboxHandlerLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String OUTBOX_HANDLER_AZURE_UPLOADING = "Uploading file to Azure Blob Storage for event type: {}";
    public static final String OUTBOX_HANDLER_AZURE_UPLOADED = "File uploaded to Azure Blob Storage for event type: {}";
    public static final String OUTBOX_HANDLER_AZURE_DELETING = "Deleting file from Azure Blob Storage for event type: {}";
    public static final String OUTBOX_HANDLER_AZURE_DELETED = "File deleted from Azure Blob Storage for event type: {}";
}
