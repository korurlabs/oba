package com.korur.oba.apps.kaya.delivery.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;

/**
 * A release named by its repository and number, as the CLI records it per checkout. No toString,
 * since a raw remote may carry an access token.
 */
@Schema(description = "A release of a checkout's repository by number")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryReleaseQueryRequest {

    @NotBlank(message = DeliveryConstant.REMOTE_URL_REQUIRED_MESSAGE)
    @Size(max = DeliveryConstant.REMOTE_URL_MAX_LENGTH, message = DeliveryConstant.REMOTE_URL_SIZE_MESSAGE)
    @Schema(description = "The checkout's git remote, in any form git accepts; Kaya normalises it",
            example = "git@github.com:korurlabs/village.git")
    private String remoteUrl;

    @NotNull(message = DeliveryConstant.NUMBER_REQUIRED_MESSAGE)
    @Min(value = DeliveryConstant.NUMBER_MIN, message = DeliveryConstant.NUMBER_MIN_MESSAGE)
    @Schema(description = "Release number of the repository", example = "7")
    private Integer number;
}
