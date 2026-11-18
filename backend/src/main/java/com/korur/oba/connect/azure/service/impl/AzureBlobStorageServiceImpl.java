package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 3. Java SDK
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

// 4. Internal
import static com.korur.oba.connect.azure.constant.service.AzureBlobStorageServiceLogConstant.*;

import com.korur.oba.connect.azure.client.AzureBlobStorageMultiContainerClient;
import com.korur.oba.connect.azure.service.AzureBlobStorageService;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.retry.annotation.ConnectRetry;
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;
import com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Azure Blob Storage write service implementation.
 *
 * <p>Handles upload and delete operations against Azure Blob Storage
 * via the multi-container client.</p>
 */
@Service
@Transactional
public class AzureBlobStorageServiceImpl implements AzureBlobStorageService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureBlobStorageServiceImpl.class);

    private final AzureBlobStorageMultiContainerClient blobStorageClient;
    private final LoggingService loggingService;

    public AzureBlobStorageServiceImpl(AzureBlobStorageMultiContainerClient blobStorageClient, LoggingService loggingService) {
        this.blobStorageClient = Objects.requireNonNull(blobStorageClient, "Blob storage client cannot be null");
        this.loggingService = loggingService;
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public String uploadFile(String fileName, byte[] fileContent) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, UPLOAD_START, fileName, fileContent.length);

        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            String fileUrl = blobStorageClient.uploadFile(fileName, inputStream, fileContent.length);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, UPLOAD_SUCCESS, fileName, fileUrl);
            return fileUrl;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, UPLOAD_FAILED, e, fileName);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_UPLOAD,
                    Map.of(FIELD_ERROR, e.getMessage()));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public String uploadFile(String containerKey, String fileName, byte[] fileContent) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, UPLOAD_CONTAINER_START,
                containerKey, fileName, fileContent.length);

        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            String fileUrl = blobStorageClient.uploadFile(containerKey, fileName, inputStream, fileContent.length);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, UPLOAD_CONTAINER_SUCCESS,
                    containerKey, fileName, fileUrl);
            return fileUrl;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, UPLOAD_CONTAINER_FAILED, e,
                    containerKey, fileName);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_UPLOAD,
                    Map.of(FIELD_ERROR, e.getMessage(), FIELD_CONTAINER_KEY, containerKey));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public void deleteFile(String fileUrl) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DELETE_START, fileUrl);

        try {
            blobStorageClient.deleteFile(fileUrl);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DELETE_SUCCESS, fileUrl);
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, DELETE_FAILED, e, fileUrl);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_DELETE,
                    Map.of(FIELD_ERROR, e.getMessage()));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public void deleteFile(String containerKey, String fileUrl) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DELETE_CONTAINER_START,
                containerKey, fileUrl);

        try {
            blobStorageClient.deleteFile(containerKey, fileUrl);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DELETE_CONTAINER_SUCCESS,
                    containerKey, fileUrl);
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, DELETE_CONTAINER_FAILED, e,
                    containerKey, fileUrl);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_DELETE,
                    Map.of(FIELD_ERROR, e.getMessage(), FIELD_CONTAINER_KEY, containerKey));
        }
    }
}
