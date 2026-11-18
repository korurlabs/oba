package com.korur.oba.connect.push.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.enums.PushPlatform;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Request payload that registers the caller's phone for push notifications, or refreshes an existing
 * registration of the same token.
 */
@Schema(description = "Push device registration request")
public record PushDeviceRegisterRequest(

        // Token Expo addresses the phone by.
        @Schema(description = "Expo push token of the device", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]")
        @NotBlank(message = PushConstant.MSG_EXPO_PUSH_TOKEN_REQUIRED)
        @Size(max = PushConstant.EXPO_PUSH_TOKEN_MAX_LENGTH, message = PushConstant.MSG_EXPO_PUSH_TOKEN_SIZE)
        @Pattern(regexp = PushConstant.EXPO_PUSH_TOKEN_REGEX, message = PushConstant.MSG_EXPO_PUSH_TOKEN_FORMAT)
        String expoPushToken,

        @Schema(description = "Operating system of the device", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "IOS")
        @NotNull(message = PushConstant.MSG_PLATFORM_REQUIRED)
        PushPlatform platform,

        // Name the user gave the phone; null when the platform reports none.
        @Schema(description = "Name of the device", nullable = true, example = "Ayşe's iPhone")
        @NullOrNotBlank
        @Size(max = PushConstant.DEVICE_NAME_MAX_LENGTH, message = PushConstant.MSG_DEVICE_NAME_SIZE)
        String deviceName,

        @Schema(description = "Version of the mobile app", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "1.0.3")
        @NotBlank(message = PushConstant.MSG_APP_VERSION_REQUIRED)
        @Size(max = PushConstant.APP_VERSION_MAX_LENGTH, message = PushConstant.MSG_APP_VERSION_SIZE)
        String appVersion,

        // Language the push copy for this device is written in.
        @Schema(description = "Language of the push copy: en, nl or tr", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "en")
        @NotBlank(message = PushConstant.MSG_LOCALE_REQUIRED)
        @Pattern(regexp = PushConstant.LOCALE_REGEX, message = PushConstant.MSG_LOCALE_FORMAT)
        String locale
) {
}
