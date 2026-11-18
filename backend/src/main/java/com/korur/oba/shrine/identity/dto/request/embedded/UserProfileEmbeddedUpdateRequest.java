package com.korur.oba.shrine.identity.dto.request.embedded;

// 1. Framework-level
import jakarta.validation.constraints.Size;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.shrine.identity.enums.UserGender;
import com.korur.oba.shrine.identity.enums.UserPosition;
import com.korur.oba.shrine.identity.enums.UserPreferredLanguage;

@Schema(description = "User profile update request")
public record UserProfileEmbeddedUpdateRequest(

        @Schema(description = "First name", nullable = true)
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String firstName,

        @Schema(description = "Last name", nullable = true)
        @Size(max = 255, message = CommonConstant.SIZE_MESSAGE_255)
        String lastName,

        @Schema(description = "Phone number", nullable = true)
        @Size(max = 30, message = CommonConstant.SIZE_MESSAGE_30)
        String phoneNumber,

        @Schema(description = "User position", nullable = true)
        UserPosition userPosition,

        @Schema(description = "User preferred language", nullable = true)
        UserPreferredLanguage userPreferredLanguage,

        @Schema(description = "User gender", nullable = true)
        UserGender userGender
) {
}
