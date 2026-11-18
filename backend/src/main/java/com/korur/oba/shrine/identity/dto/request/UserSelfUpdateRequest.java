package com.korur.oba.shrine.identity.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.shrine.identity.dto.request.embedded.UserProfileEmbeddedUpdateRequest;

/**
 * Update of the caller's own account. It has no roles field, so an account holder
 * cannot grant themself a platform role; roles change only through the
 * administrator update. An unknown roles value in the JSON body is ignored.
 */
@Schema(description = "Own user update request")
public record UserSelfUpdateRequest(

        @Schema(description = "Email address", nullable = true, example = "jane.doe@example.com")
        @Email(message = CommonConstant.NOT_VALID_EMAIL_MESSAGE)
        @Size(max = 254, message = CommonConstant.SIZE_MESSAGE_254)
        String email,

        @Schema(description = "User profile", nullable = true)
        @Valid
        UserProfileEmbeddedUpdateRequest profile,

        @Schema(description = "Default organization ID. Null or absent keeps the current default; "
                + "DELETE /users/me/default-organization clears it", nullable = true,
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID defaultOrganizationId,

        @Schema(description = "User preferences (opaque JSON)", nullable = true)
        Map<String, Object> userPreferences
) {
}
