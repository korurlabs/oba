package com.korur.oba.connect.push.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.enums.PushPlatform;

/**
 * Response payload for a registered push device. It carries no Expo push token, because anyone who
 * holds one can push to the device; the app keeps the id to unregister on sign-out.
 */
@Schema(description = "Push device response")
public record PushDeviceResponse(

        @Schema(description = "Device id", example = "5f0c7c1e-3b9a-4a4e-9d55-2f1f0c8a7b21")
        UUID id,

        @Schema(description = "Operating system of the device", example = "IOS")
        PushPlatform platform,

        @Schema(description = "Name of the device", nullable = true, example = "Ayşe's iPhone")
        String deviceName,

        @Schema(description = "Version of the mobile app", example = "1.0.3")
        String appVersion,

        @Schema(description = "Last registration of the token", example = "2026-11-06T08:15:00Z")
        Instant lastRegisteredAt
) {
}
