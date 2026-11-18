package com.korur.oba.connect.azure.client;

// 1. Framework-level

// 2. Third-party
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.sas.SasProtocol;

// 3. Java SDK
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

// 4. Internal
import com.korur.oba.connect.azure.constant.client.AzureBlobStorageClientConstant;

/**
 * Client for Azure Blob Storage operations on a single container.
 * Handles basic blob operations such as upload, download, list, and delete.
 */
public class AzureBlobStorageClient {

    private final BlobContainerClient containerClient;
    private final int sasTtlMinutes;
    private final SasProtocol sasProtocol;

    /**
     * Creates a new client for a specific blob container.
     *
     * @param containerClient The Azure BlobContainerClient
     * @param sasTtlMinutes   The SAS token time-to-live in minutes
     */
    public AzureBlobStorageClient(BlobContainerClient containerClient, int sasTtlMinutes) {
        this.containerClient = Objects.requireNonNull(containerClient, "Container client cannot be null");
        this.sasTtlMinutes = sasTtlMinutes;
        this.sasProtocol = resolveSasProtocol(containerClient.getBlobContainerUrl());
    }

    /**
     * Uploads a file to Azure Blob Storage.
     *
     * @param fileName The name of the file to be uploaded
     * @param fileData An InputStream containing the file data
     * @param fileSize The size of the file in bytes
     * @return The URL of the uploaded blob
     */
    public String uploadFile(String fileName, InputStream fileData, long fileSize) {
        Objects.requireNonNull(fileName, "File name cannot be null");
        Objects.requireNonNull(fileData, "File data cannot be null");

        BlobClient blobClient = containerClient.getBlobClient(fileName);
        blobClient.upload(fileData, fileSize, true);
        return blobClient.getBlobUrl();
    }

    /**
     * Downloads a file from Azure Blob Storage.
     *
     * @param fileName The name of the file to be downloaded
     * @return A byte array containing the downloaded file content
     */
    public byte[] downloadFile(String fileName) {
        Objects.requireNonNull(fileName, "File name cannot be null");

        BlobClient blobClient = containerClient.getBlobClient(fileName);
        return blobClient.downloadContent().toBytes();
    }

    /**
     * Lists all files in Azure Blob Storage with a specific prefix.
     *
     * @param prefix The prefix to filter files
     * @return A list of file URLs matching the prefix
     */
    public List<String> listFiles(String prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");

        List<String> fileUrls = new ArrayList<>();
        for (BlobItem blobItem : containerClient.listBlobsByHierarchy(prefix)) {
            fileUrls.add(containerClient.getBlobClient(blobItem.getName()).getBlobUrl());
        }
        return fileUrls;
    }

    /**
     * Deletes a file from Azure Blob Storage.
     *
     * @param fileUrl The URL of the file to be deleted
     */
    public void deleteFile(String fileUrl) {
        Objects.requireNonNull(fileUrl, "File URL cannot be null");

        String blobName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.delete();
    }

    /**
     * Generates a full URL to the specified blob with an appended Shared Access Signature (SAS) token.
     *
     * The returned URL enables secure access to the blob for clients (e.g., frontend applications)
     * without exposing Azure Storage account credentials. The SAS token is appended as query parameters
     * using a '?' separator, which is required by standard URL syntax to distinguish the base
     * URL path from the query string.
     *
     * The token is restricted to HTTPS when the container is served over HTTPS. A plain-HTTP endpoint,
     * such as the local Azurite emulator, rejects an HTTPS-only token, so there the token allows both.
     *
     * Example return format:
     * https://<account>.blob.core.windows.net/<container>/<blob>?sv=...&sig=...
     *
     * @param fileName The name of the blob inside this container
     * @return The full blob URL including the SAS token as query parameters
     */
    public String generateBlobSasUrl(String fileName) {
        // Create permissions - By default allow reading always.
        BlobSasPermission permissions = new BlobSasPermission()
                .setReadPermission(true).setWritePermission(false);

        // Set expiry time using the configured TTL.
        OffsetDateTime expiryTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(sasTtlMinutes);

        // Build the SAS values - with time zone scew
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permissions)
                .setStartTime(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .setProtocol(sasProtocol);

        BlobClient blobClient = containerClient.getBlobClient(fileName);

        // Generate the SAS token
        String sasToken = blobClient.generateSas(sasValues);

        // Return the full URL with SAS
        return blobClient.getBlobUrl() + "?" + sasToken;
    }

    public BlobContainerClient getContainerClient() {
        return containerClient;
    }

    /**
     * Chooses the SAS protocol that the container endpoint can honour.
     *
     * An HTTPS endpoint keeps tokens HTTPS-only so a signed URL is never usable in clear text.
     * Any other endpoint can only be reached over HTTP, where an HTTPS-only token fails with
     * AuthorizationProtocolMismatch, so it gets a token valid for both protocols.
     *
     * @param containerUrl The URL of the blob container
     * @return {@link SasProtocol#HTTPS_ONLY} for an HTTPS endpoint, otherwise {@link SasProtocol#HTTPS_HTTP}
     */
    private static SasProtocol resolveSasProtocol(String containerUrl) {
        boolean isHttpsEndpoint = containerUrl.toLowerCase(Locale.ROOT)
                .startsWith(AzureBlobStorageClientConstant.HTTPS_URL_PREFIX);
        return isHttpsEndpoint ? SasProtocol.HTTPS_ONLY : SasProtocol.HTTPS_HTTP;
    }
}
