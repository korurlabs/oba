package com.korur.oba.connect.azure.config;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 2. Third-party
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.azure.client.AzureBlobStorageClient;
import com.korur.oba.connect.azure.client.AzureBlobStorageMultiContainerClient;
import com.korur.oba.connect.azure.properties.AzureBlobStorageProperties;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Configuration class for Azure Blob Storage.
 * Provides beans for multi-container and single-container clients.
 */
@Configuration
@RequiredArgsConstructor
public class AzureBlobStorageConfig {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AzureBlobStorageConfig.class);

    private final LoggingService loggingService;

    /**
     * Creates the Azure Blob Storage properties bean.
     *
     * @return The configured properties
     */
    @Bean
    @ConfigurationProperties(prefix = "azure.storage")
    public AzureBlobStorageProperties azureBlobStorageProperties() {
        return new AzureBlobStorageProperties();
    }

    /**
     * Creates a client that can work with multiple blob containers.
     *
     * @return A client that supports operations across multiple containers
     */
    @Bean
    public AzureBlobStorageMultiContainerClient multiContainerAzureBlobStorageClient(
            AzureBlobStorageProperties properties) {
        // Log properties to verify correct loading
        if (properties.getConnectionString() == null || properties.getConnectionString().isBlank()) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    "Azure Storage connection string is NULL or empty! Check application properties.");
        } else {
            loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                    "Azure Storage connection string loaded.");
            loggingService.debug(LOG_PREFIX, LogLayer.HANDLER,
                    "Connection String (masked): {}", maskConnectionString(properties.getConnectionString()));
        }

        if (properties.getContainers() == null || properties.getContainers().isEmpty()) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    "Azure Storage containers map is NULL or empty! Check application properties.");
        } else {
            loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                    "Found {} Azure Storage containers in configuration.", properties.getContainers().size());
            properties.getContainers().forEach((key, value) ->
                    loggingService.debug(LOG_PREFIX, LogLayer.HANDLER,
                            "Container mapping: {} -> {}", key, value));
        }

        if (properties.getDefaultContainer() == null || properties.getDefaultContainer().isBlank()) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    "Default container name is NULL or empty! Check application properties.");
        } else {
            loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                    "Default container name loaded: {}", properties.getDefaultContainer());
        }

        Map<String, BlobContainerClient> containerClients = createContainerClients(properties);
        return new AzureBlobStorageMultiContainerClient(
                containerClients,
                properties.getDefaultContainer(),
                properties.getSasTtlMinutes());
    }

    /**
     * Creates a legacy client for backward compatibility.
     * This client works only with the default container.
     *
     * @param multiClient The multi-container client
     * @return A client for the default container only
     */
    @Bean
    public AzureBlobStorageClient azureBlobStorageClient(
            AzureBlobStorageMultiContainerClient multiClient,
            AzureBlobStorageProperties properties) {

        AzureBlobStorageClient client = multiClient.getClientForContainer(properties.getDefaultContainer());

        if (client != null) {
            loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                    "Legacy Azure Blob Storage client successfully initialized for default container: {}",
                    properties.getDefaultContainer());
        } else {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    "Failed to initialize legacy Azure Blob Storage client for default container! Check your configuration.");
        }

        return client;
    }

    private Map<String, BlobContainerClient> createContainerClients(AzureBlobStorageProperties properties) {
        Map<String, BlobContainerClient> containerClients = new HashMap<>();

        for (Map.Entry<String, String> entry : properties.getContainers().entrySet()) {
            String containerKey = entry.getKey();
            String containerName = entry.getValue();

            try {
                BlobContainerClient containerClient = new BlobContainerClientBuilder()
                        .connectionString(properties.getConnectionString())
                        .containerName(containerName)
                        .buildClient();

                if (containerClient.getBlobContainerName() != null) {
                    loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                            "Azure Blob Storage successfully initialized for container: {} (key: {})",
                            containerClient.getBlobContainerName(), containerKey);
                    containerClients.put(containerKey, containerClient);
                } else {
                    loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                            "Failed to initialize Azure Blob Storage for container: {} (key: {})",
                            containerName, containerKey);
                }
            } catch (Exception e) {
                loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                        "Exception while initializing Azure Blob Storage for container: {} (key: {})",
                        e, containerName, containerKey);
            }
        }

        if (containerClients.isEmpty()) {
            loggingService.error(LOG_PREFIX, LogLayer.HANDLER,
                    "No container clients were successfully initialized! Application may not function correctly.");
        } else {
            loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                    "Successfully initialized {} Azure Blob Storage container clients.", containerClients.size());
        }

        return containerClients;
    }

    /**
     * Masks sensitive parts of the connection string for security.
     */
    private String maskConnectionString(String connStr) {
        if (connStr == null || connStr.isEmpty()) {
            return "NULL_OR_EMPTY";
        }
        return connStr.replaceAll("(?<=AccountKey=)[^;]+", "*****");
    }
}
