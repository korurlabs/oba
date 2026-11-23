package com.korur.oba.apps.kaya.delivery.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

@Schema(description = "A repository the CLI delivers to, named by its normalised remote")
public record DeliveryRepositoryResponse(
        @Schema(description = "Normalised git remote: host/owner/name in lower case", example = "github.com/korurlabs/oba")
        String remoteUrl
) {
}
