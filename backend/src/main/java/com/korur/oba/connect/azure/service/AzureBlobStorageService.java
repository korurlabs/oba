package com.korur.oba.connect.azure.service;

/**
 * Write service for Azure Blob Storage operations.
 *
 * <p>Handles upload and delete operations against Azure Blob Storage.</p>
 */
public interface AzureBlobStorageService {

    String uploadFile(String fileName, byte[] fileContent);

    String uploadFile(String containerKey, String fileName, byte[] fileContent);

    void deleteFile(String fileUrl);

    void deleteFile(String containerKey, String fileUrl);
}
