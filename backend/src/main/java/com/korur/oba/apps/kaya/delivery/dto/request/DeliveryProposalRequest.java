package com.korur.oba.apps.kaya.delivery.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;
import com.korur.oba.apps.kaya.version.constant.VersionConstant;
import com.korur.oba.apps.kaya.version.dto.request.ProposalFileRequest;

/**
 * A change proposed from a checkout: the files edited on top of the release the checkout runs.
 */
@Schema(description = "A change proposed from a checkout: the files edited on top of the release it runs")
public record DeliveryProposalRequest(
        @Schema(description = "The checkout's git remote, in any form git accepts; Kaya normalises it",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "git@github.com:korurlabs/village.git")
        @NotBlank(message = DeliveryConstant.REMOTE_URL_REQUIRED_MESSAGE)
        @Size(max = DeliveryConstant.REMOTE_URL_MAX_LENGTH, message = DeliveryConstant.REMOTE_URL_SIZE_MESSAGE)
        String remoteUrl,

        @Schema(description = "Number of the release the checkout runs and the files were edited on",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "7")
        @NotNull(message = DeliveryConstant.NUMBER_REQUIRED_MESSAGE)
        @Min(value = DeliveryConstant.NUMBER_MIN, message = DeliveryConstant.NUMBER_MIN_MESSAGE)
        Integer releaseNumber,

        @Schema(description = "Why the change is needed, 10 to 1000 characters; the reviewer reads it",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Allow the kaizen CLI in every repository")
        @NotBlank(message = VersionConstant.REASON_REQUIRED_MESSAGE)
        @Size(min = VersionConstant.REASON_MIN_LENGTH, max = VersionConstant.REASON_MAX_LENGTH,
                message = VersionConstant.REASON_SIZE_MESSAGE)
        String reason,

        @Schema(description = "The changed files, 1 to 200, each path once; all from one pack",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = DeliveryConstant.PROPOSAL_FILES_SIZE_MESSAGE)
        @Size(min = DeliveryConstant.PROPOSAL_FILES_MIN, max = DeliveryConstant.PROPOSAL_FILES_MAX,
                message = DeliveryConstant.PROPOSAL_FILES_SIZE_MESSAGE)
        List<@NotNull(message = DeliveryConstant.PROPOSAL_FILE_REQUIRED_MESSAGE) @Valid ProposalFileRequest> files
) {
}
