package com.korur.oba.connect.azure.client.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 2. Third-party
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.SasProtocol;

// 3. Java SDK
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

// 4. Internal
import com.korur.oba.connect.azure.client.AzureBlobStorageClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AzureBlobStorageClient}.
 *
 * The container clients are real SDK clients with a throwaway shared key: SAS signing happens
 * locally, so no storage endpoint is contacted.
 */
class AzureBlobStorageClientUnitTest {

    private static final String ACCOUNT_NAME = "devstoreaccount1";
    private static final String CONTAINER_NAME = "dev-container";
    private static final String BLOB_NAME = "image.png";
    private static final String HTTPS_ENDPOINT = "https://" + ACCOUNT_NAME + ".blob.core.windows.net";
    private static final String HTTP_ENDPOINT = "http://127.0.0.1:10000/" + ACCOUNT_NAME;
    private static final String SIGNED_PROTOCOL_PARAM = "spr";
    private static final int SAS_TTL_MINUTES = 60;

    @Nested
    class GenerateBlobSasUrl {

        @Test
        void httpsEndpoint_signsHttpsOnly() {
            // Arrange
            AzureBlobStorageClient client = new AzureBlobStorageClient(buildContainerClient(HTTPS_ENDPOINT), SAS_TTL_MINUTES);

            // Act
            String sasUrl = client.generateBlobSasUrl(BLOB_NAME);

            // Assert
            assertTrue(sasUrl.startsWith(HTTPS_ENDPOINT + "/" + CONTAINER_NAME + "/" + BLOB_NAME + "?"),
                    "The signed URL should point at the blob on the HTTPS endpoint");
            assertEquals(Optional.of(SasProtocol.HTTPS_ONLY.toString()), queryParam(sasUrl, SIGNED_PROTOCOL_PARAM),
                    "An HTTPS endpoint must only accept the token over HTTPS");
        }

        @Test
        void httpEndpoint_signsHttpsAndHttp() {
            // Arrange
            AzureBlobStorageClient client = new AzureBlobStorageClient(buildContainerClient(HTTP_ENDPOINT), SAS_TTL_MINUTES);

            // Act
            String sasUrl = client.generateBlobSasUrl(BLOB_NAME);

            // Assert
            assertTrue(sasUrl.startsWith(HTTP_ENDPOINT + "/" + CONTAINER_NAME + "/" + BLOB_NAME + "?"),
                    "The signed URL should point at the blob on the HTTP endpoint");
            assertEquals(Optional.of(SasProtocol.HTTPS_HTTP.toString()), queryParam(sasUrl, SIGNED_PROTOCOL_PARAM),
                    "A plain-HTTP endpoint such as Azurite must accept the token over HTTP");
        }
    }

    private static BlobContainerClient buildContainerClient(String endpoint) {
        byte[] accountKey = new byte[64];
        new SecureRandom().nextBytes(accountKey);
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(
                ACCOUNT_NAME, Base64.getEncoder().encodeToString(accountKey));
        return new BlobContainerClientBuilder()
                .endpoint(endpoint)
                .containerName(CONTAINER_NAME)
                .credential(credential)
                .buildClient();
    }

    private static Optional<String> queryParam(String url, String name) {
        String query = URI.create(url).getRawQuery();
        return Arrays.stream(query.split("&"))
                .map((String pair) -> pair.split("=", 2))
                .filter((String[] pair) -> pair.length == 2 && pair[0].equals(name))
                .map((String[] pair) -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .findFirst();
    }
}
