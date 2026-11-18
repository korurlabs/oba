package com.korur.oba.connect.azure.constant.service;

/**
 * Log message constants for Azure Blob Storage query service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureBlobStorageQueryServiceLogConstant {

    private AzureBlobStorageQueryServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    // Field names
    public static final String FIELD_ERROR = "error";
    public static final String FIELD_CONTAINER_KEY = "containerKey";

    // Download operations
    public static final String DOWNLOAD_START = "Starting Azure blob file download. FileName: {}";
    public static final String DOWNLOAD_SUCCESS = "Successfully downloaded file from Azure blob. FileName: {}, FileSize: {} bytes";
    public static final String DOWNLOAD_FAILED = "Failed to download file from Azure blob. FileName: {}";
    public static final String DOWNLOAD_CONTAINER_START = "Starting Azure blob file download with container. ContainerKey: {}, FileName: {}";
    public static final String DOWNLOAD_CONTAINER_SUCCESS = "Successfully downloaded file from Azure blob with container. ContainerKey: {}, FileName: {}, FileSize: {} bytes";
    public static final String DOWNLOAD_CONTAINER_FAILED = "Failed to download file from Azure blob with container. ContainerKey: {}, FileName: {}";

    // List operations
    public static final String LIST_START = "Starting Azure blob file listing. Prefix: {}";
    public static final String LIST_SUCCESS = "Successfully listed files from Azure blob. Prefix: {}, FileCount: {}";
    public static final String LIST_FAILED = "Failed to list files from Azure blob. Prefix: {}";
    public static final String LIST_CONTAINER_START = "Starting Azure blob file listing with container. ContainerKey: {}, Prefix: {}";
    public static final String LIST_CONTAINER_SUCCESS = "Successfully listed files from Azure blob with container. ContainerKey: {}, Prefix: {}, FileCount: {}";
    public static final String LIST_CONTAINER_FAILED = "Failed to list files from Azure blob with container. ContainerKey: {}, Prefix: {}";

    // Private download operations
    public static final String PRIVATE_DOWNLOAD_START = "Starting Azure private blob download. BlobURL: {}";
    public static final String PRIVATE_DOWNLOAD_SUCCESS = "Successfully downloaded private blob from Azure. BlobURL: {}, BlobSize: {} bytes";
    public static final String PRIVATE_DOWNLOAD_FAILED = "Failed to download private blob from Azure. BlobURL: {}";
}
