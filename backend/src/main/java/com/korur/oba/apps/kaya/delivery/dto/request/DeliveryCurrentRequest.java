package com.korur.oba.apps.kaya.delivery.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.delivery.constant.DeliveryConstant;

/**
 * The checkout the CLI asks for: its git remote, which names the repository. No toString, since a
 * raw remote may carry an access token.
 */
@Schema(description = "The checkout the CLI syncs")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryCurrentRequest {

    @NotBlank(message = DeliveryConstant.REMOTE_URL_REQUIRED_MESSAGE)
    @Size(max = DeliveryConstant.REMOTE_URL_MAX_LENGTH, message = DeliveryConstant.REMOTE_URL_SIZE_MESSAGE)
    @Schema(description = "The checkout's git remote, in any form git accepts; Kaya normalises it",
            example = "git@github.com:korurlabs/village.git")
    private String remoteUrl;
}
