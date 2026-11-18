package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 4. Internal
import static com.korur.oba.connect.azure.constant.service.AzureBlobStorageQueryServiceLogConstant.*;

import com.korur.oba.connect.azure.client.AzureBlobStorageMultiContainerClient;
import com.korur.oba.connect.azure.service.AzureBlobStorageQueryService;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.retry.annotation.ConnectRetry;
import com.korur.oba.connect.retry.enums.ConnectRetryPolicy;
import com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Azure Blob Storage read/query service implementation.
 *
 * <p>Handles download, list, and SAS URL generation operations against Azure Blob Storage
 * via the multi-container client.</p>
 */
@Service
@Transactional(readOnly = true)
public class AzureBlobStorageQueryServiceImpl implements AzureBlobStorageQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureBlobStorageQueryServiceImpl.class);

    private final AzureBlobStorageMultiContainerClient blobStorageClient;
    private final LoggingService loggingService;

    public AzureBlobStorageQueryServiceImpl(AzureBlobStorageMultiContainerClient blobStorageClient, LoggingService loggingService) {
        this.blobStorageClient = Objects.requireNonNull(blobStorageClient, "Blob storage client cannot be null");
        this.loggingService = loggingService;
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public byte[] downloadFile(String fileName) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DOWNLOAD_START, fileName);

        try {
            byte[] fileContent = blobStorageClient.downloadFile(fileName);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DOWNLOAD_SUCCESS,
                    fileName, fileContent.length);
            return fileContent;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, DOWNLOAD_FAILED, e, fileName);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_DOWNLOAD,
                    Map.of(FIELD_ERROR, e.getMessage()));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public byte[] downloadFile(String containerKey, String fileName) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DOWNLOAD_CONTAINER_START,
                containerKey, fileName);

        try {
            byte[] fileContent = blobStorageClient.downloadFile(containerKey, fileName);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, DOWNLOAD_CONTAINER_SUCCESS,
                    containerKey, fileName, fileContent.length);
            return fileContent;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, DOWNLOAD_CONTAINER_FAILED, e,
                    containerKey, fileName);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_DOWNLOAD,
                    Map.of(FIELD_ERROR, e.getMessage(), FIELD_CONTAINER_KEY, containerKey));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public List<String> listFiles(String prefix) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LIST_START, prefix);

        try {
            List<String> files = blobStorageClient.listFiles(prefix);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LIST_SUCCESS, prefix, files.size());
            return files;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, LIST_FAILED, e, prefix);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_LIST,
                    Map.of(FIELD_ERROR, e.getMessage()));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public List<String> listFiles(String containerKey, String prefix) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LIST_CONTAINER_START,
                containerKey, prefix);

        try {
            List<String> files = blobStorageClient.listFiles(containerKey, prefix);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LIST_CONTAINER_SUCCESS,
                    containerKey, prefix, files.size());
            return files;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, LIST_CONTAINER_FAILED, e,
                    containerKey, prefix);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_LIST,
                    Map.of(FIELD_ERROR, e.getMessage(), FIELD_CONTAINER_KEY, containerKey));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public byte[] downloadPrivateBlob(String fileName) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PRIVATE_DOWNLOAD_START, fileName);

        try {
            byte[] blobContent = blobStorageClient.downloadPrivateBlob(fileName);
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, PRIVATE_DOWNLOAD_SUCCESS,
                    fileName, blobContent.length);
            return blobContent;
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, PRIVATE_DOWNLOAD_FAILED, e, fileName);
            throw new IntegrationErrorException(ExceptionDomainAzureError.UPS_AZURE_BLOB_DOWNLOAD,
                    Map.of(FIELD_ERROR, e.getMessage()));
        }
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public String generateSasUrl(String filename) {
        return blobStorageClient.getDefaultClient().generateBlobSasUrl(filename);
    }

    @Override
    @ConnectRetry(ConnectRetryPolicy.AZURE_BLOB_STORAGE)
    public String generateSasUrl(String containerKey, String blobName) {
        return blobStorageClient.getClientForContainer(containerKey).generateBlobSasUrl(blobName);
    }
}
