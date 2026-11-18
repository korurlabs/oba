package com.korur.oba.connect.azure.service;

// 3. Java SDK
import java.util.List;

/**
 * Read/query service for Azure Blob Storage operations.
 *
 * <p>Handles download, list, and SAS URL generation operations against Azure Blob Storage.</p>
 */
public interface AzureBlobStorageQueryService {

    byte[] downloadFile(String fileName);

    byte[] downloadFile(String containerKey, String fileName);

    List<String> listFiles(String prefix);

    List<String> listFiles(String containerKey, String prefix);

    byte[] downloadPrivateBlob(String blobUrl);

    String generateSasUrl(String filename);

    /**
     * Generates a SAS URL for a blob in a specific container.
     *
     * @param containerKey the container key as registered in the multi-container client
     * @param blobName     the blob name relative to the container root
     * @return a full URL including the SAS token query string
     */
    String generateSasUrl(String containerKey, String blobName);
}
