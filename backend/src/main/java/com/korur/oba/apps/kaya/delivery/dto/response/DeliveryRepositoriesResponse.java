package com.korur.oba.apps.kaya.delivery.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.dto.response.embedded.DeliveryRepositoryResponse;

/**
 * The repositories of the machine token's organization that a checkout can be delivered from. The
 * CLI keeps this list and compares a checkout's remotes with it, so it needs no file in the
 * repository to know that the checkout belongs to Kaya.
 */
@Schema(description = "The active repositories of the machine token's organization, by normalised remote")
public record DeliveryRepositoriesResponse(
        @Schema(description = "The machine token's organization", example = "7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d")
        UUID organizationId,

        @Schema(description = "Every ACTIVE repository of the organization, sorted by remote URL; empty when it has none")
        List<DeliveryRepositoryResponse> repositories
) {
}
