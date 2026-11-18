package com.korur.oba.shrine.identity.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.identity.enums.UserGender;
import com.korur.oba.shrine.identity.enums.UserPosition;
import com.korur.oba.shrine.identity.enums.UserPreferredLanguage;

@Schema(description = "User profile detail response")
public record UserProfileEmbeddedDetailResponse(

        @Schema(description = "First name", nullable = true)
        String firstName,

        @Schema(description = "Last name", nullable = true)
        String lastName,

        @Schema(description = "Phone number", nullable = true)
        String phoneNumber,

        @Schema(description = "User position", nullable = true)
        UserPosition userPosition,

        @Schema(description = "User preferred language", nullable = true)
        UserPreferredLanguage userPreferredLanguage,

        @Schema(description = "User gender", nullable = true)
        UserGender userGender
) {
}
