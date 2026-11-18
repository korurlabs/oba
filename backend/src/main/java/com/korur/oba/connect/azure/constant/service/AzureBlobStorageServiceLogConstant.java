package com.korur.oba.connect.azure.constant.service;

/**
 * Log message constants for Azure Blob Storage write service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AzureBlobStorageServiceLogConstant {

    private AzureBlobStorageServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    // Field names
    public static final String FIELD_ERROR = "error";
    public static final String FIELD_CONTAINER_KEY = "containerKey";

    // Upload operations
    public static final String UPLOAD_START = "Starting Azure blob file upload. FileName: {}, FileSize: {} bytes";
    public static final String UPLOAD_SUCCESS = "Successfully uploaded file to Azure blob. FileName: {}, FileURL: {}";
    public static final String UPLOAD_FAILED = "Failed to upload file to Azure blob. FileName: {}";
    public static final String UPLOAD_CONTAINER_START = "Starting Azure blob file upload with container. ContainerKey: {}, FileName: {}, FileSize: {} bytes";
    public static final String UPLOAD_CONTAINER_SUCCESS = "Successfully uploaded file to Azure blob with container. ContainerKey: {}, FileName: {}, FileURL: {}";
    public static final String UPLOAD_CONTAINER_FAILED = "Failed to upload file to Azure blob with container. ContainerKey: {}, FileName: {}";

    // Delete operations
    public static final String DELETE_START = "Starting Azure blob file deletion. FileURL: {}";
    public static final String DELETE_SUCCESS = "Successfully deleted file from Azure blob. FileURL: {}";
    public static final String DELETE_FAILED = "Failed to delete file from Azure blob. FileURL: {}";
    public static final String DELETE_CONTAINER_START = "Starting Azure blob file deletion with container. ContainerKey: {}, FileURL: {}";
    public static final String DELETE_CONTAINER_SUCCESS = "Successfully deleted file from Azure blob with container. ContainerKey: {}, FileURL: {}";
    public static final String DELETE_CONTAINER_FAILED = "Failed to delete file from Azure blob with container. ContainerKey: {}, FileURL: {}";
}
