package com.korur.oba.apps.kaya.lesson.dto.request;

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
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;

/**
 * The checkout whose lessons the CLI asks for. since stays text so that the service refuses an
 * unreadable value with a field message instead of a conversion error. No toString, since a raw
 * remote may carry an access token.
 */
@Schema(description = "The checkout and when it last looked")
@Getter
@Setter
@NoArgsConstructor
public class LessonDeliveryQueryRequest {

    @NotBlank(message = LessonConstant.REMOTE_URL_REQUIRED_MESSAGE)
    @Size(max = LessonConstant.REMOTE_URL_MAX_LENGTH, message = LessonConstant.REMOTE_URL_SIZE_MESSAGE)
    @Schema(description = "The checkout's git remote, in any form git accepts; Kaya normalises it",
            example = "git@github.com:korurlabs/village.git")
    private String remoteUrl;

    @Schema(description = "ISO-8601 instant; a lesson created or updated after it is marked changed", nullable = true,
            example = "2026-10-25T09:00:00Z")
    private String since;
}
