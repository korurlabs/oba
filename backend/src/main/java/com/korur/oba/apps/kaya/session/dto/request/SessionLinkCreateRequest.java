package com.korur.oba.apps.kaya.session.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.enums.SessionLinkKind;

@Schema(description = "A link a member adds to a session by hand")
public record SessionLinkCreateRequest(
        @Schema(description = "What the link points at", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "KAIZEN_TICKET")
        @NotNull(message = SessionConstant.LINK_KIND_REQUIRED_MESSAGE)
        SessionLinkKind kind,

        @Schema(description = "A ticket key such as KAYA-42, or a pull request URL starting with https://github.com/",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "KAYA-42")
        @NotBlank(message = SessionConstant.LINK_REFERENCE_REQUIRED_MESSAGE)
        @Size(max = SessionConstant.LINK_REFERENCE_MAX_LENGTH, message = SessionConstant.LINK_REFERENCE_SIZE_MESSAGE)
        String reference
) {
}
