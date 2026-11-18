package com.korur.oba.connect.azure.properties;

// 1. Framework-level
import org.springframework.validation.annotation.Validated;

// 2. Third-party
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;

/**
 * Properties class for Azure Blob Storage configuration.
 * Maps properties from application.yml/properties with prefix "azure.storage".
 *
 * The blobBaseUrl field is required. When the underlying property is unset or
 * blank, Spring validation causes a BindValidationException at startup so the
 * application refuses to boot with broken configuration.
 */
@Validated
@Setter
@Getter
public class AzureBlobStorageProperties {
    private String connectionString;
    private Map<String, String> containers = new HashMap<>();
    private String defaultContainer;
    private int sasTtlMinutes = 30;

    @NotBlank
    private String blobBaseUrl;
}
