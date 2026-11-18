package com.korur.oba.connect.azure.client;

// 2. Third-party
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;

// 3. Java SDK
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client for Azure Blob Storage that supports operations on multiple containers.
 * Provides methods for file operations across different containers.
 */
public class AzureBlobStorageMultiContainerClient {

    private final Map<String, BlobContainerClient> containerClients;
    private final String defaultContainerKey;
    private final int sasTtlMinutes;

    /**
     * Creates a new multi-container Azure Blob Storage client.
     *
     * @param containerClients    Map of container keys to their respective BlobContainerClient objects
     * @param defaultContainerKey The key for the default container to use when none is specified
     * @param sasTtlMinutes       The SAS token time-to-live in minutes, passed through to each per-container client
     * @throws IllegalArgumentException if containerClients is null or empty, or if defaultContainerKey is null or not found
     */
    public AzureBlobStorageMultiContainerClient(Map<String, BlobContainerClient> containerClients,
                                                String defaultContainerKey,
                                                int sasTtlMinutes) {
        if (containerClients == null || containerClients.isEmpty()) {
            throw new IllegalArgumentException("Container clients map cannot be null or empty");
        }
        if (defaultContainerKey == null || !containerClients.containsKey(defaultContainerKey)) {
            throw new IllegalArgumentException("Default container key must exist in the container clients map");
        }

        this.containerClients = Map.copyOf(containerClients);
        this.defaultContainerKey = defaultContainerKey;
        this.sasTtlMinutes = sasTtlMinutes;
    }

    /**
     * Gets a client for a specific container.
     *
     * @param containerKey The container key
     * @return A client for the specified container
     * @throws IllegalArgumentException if the specified containerKey doesn't exist
     */
    public AzureBlobStorageClient getClientForContainer(String containerKey) {
        BlobContainerClient containerClient = containerClients.get(containerKey);
        if (containerClient == null) {
            throw new IllegalArgumentException("Container with key '" + containerKey + "' not found");
        }
        return new AzureBlobStorageClient(containerClient, sasTtlMinutes);
    }

    /**
     * Gets a client for the default container.
     *
     * @return A client for the default container
     */
    public AzureBlobStorageClient getDefaultClient() {
        return getClientForContainer(defaultContainerKey);
    }

    /**
     * Uploads a file to a specific container.
     *
     * @param containerKey The container key
     * @param fileName     The name of the file to upload
     * @param fileData     The file data as an InputStream
     * @param fileSize     The size of the file in bytes
     * @return The URL of the uploaded blob
     * @throws IllegalArgumentException if the container key doesn't exist
     */
    public String uploadFile(String containerKey, String fileName, InputStream fileData, long fileSize) {
        Objects.requireNonNull(containerKey, "Container key cannot be null");
        Objects.requireNonNull(fileName, "File name cannot be null");
        Objects.requireNonNull(fileData, "File data cannot be null");

        return getClientForContainer(containerKey).uploadFile(fileName, fileData, fileSize);
    }

    /**
     * Uploads a file to the default container.
     *
     * @param fileName The name of the file to upload
     * @param fileData The file data as an InputStream
     * @param fileSize The size of the file in bytes
     * @return The URL of the uploaded blob
     */
    public String uploadFile(String fileName, InputStream fileData, long fileSize) {
        return getDefaultClient().uploadFile(fileName, fileData, fileSize);
    }

    /**
     * Downloads a file from a specific container.
     *
     * @param containerKey The container key
     * @param fileName     The name of the file to download
     * @return The file content as a byte array
     * @throws IllegalArgumentException if the container key doesn't exist
     */
    public byte[] downloadFile(String containerKey, String fileName) {
        Objects.requireNonNull(containerKey, "Container key cannot be null");
        Objects.requireNonNull(fileName, "File name cannot be null");

        return getClientForContainer(containerKey).downloadFile(fileName);
    }

    /**
     * Downloads a file from the default container.
     *
     * @param fileName The name of the file to download
     * @return The file content as a byte array
     */
    public byte[] downloadFile(String fileName) {
        return getDefaultClient().downloadFile(fileName);
    }

    /**
     * Lists files in a specific container with a given prefix.
     *
     * @param containerKey The container key
     * @param prefix       The prefix to filter files
     * @return A list of file URLs matching the prefix
     * @throws IllegalArgumentException if the container key doesn't exist
     */
    public List<String> listFiles(String containerKey, String prefix) {
        Objects.requireNonNull(containerKey, "Container key cannot be null");
        Objects.requireNonNull(prefix, "Prefix cannot be null");

        return getClientForContainer(containerKey).listFiles(prefix);
    }

    /**
     * Lists files in the default container with a given prefix.
     *
     * @param prefix The prefix to filter files
     * @return A list of file URLs matching the prefix
     */
    public List<String> listFiles(String prefix) {
        return getDefaultClient().listFiles(prefix);
    }

    /**
     * Deletes a file from a specific container.
     *
     * @param containerKey The container key
     * @param fileUrl      The URL of the file to delete
     * @throws IllegalArgumentException if the container key doesn't exist
     */
    public void deleteFile(String containerKey, String fileUrl) {
        Objects.requireNonNull(containerKey, "Container key cannot be null");
        Objects.requireNonNull(fileUrl, "File URL cannot be null");

        getClientForContainer(containerKey).deleteFile(fileUrl);
    }

    /**
     * Deletes a file from the default container.
     *
     * @param fileUrl The URL of the file to delete
     */
    public void deleteFile(String fileUrl) {
        getDefaultClient().deleteFile(fileUrl);
    }


    public byte[] downloadPrivateBlob(String fileName) {

        BlobClient blobClient =  getDefaultClient().getContainerClient().getBlobClient(fileName);
        return blobClient.downloadContent().toBytes();
    }

}