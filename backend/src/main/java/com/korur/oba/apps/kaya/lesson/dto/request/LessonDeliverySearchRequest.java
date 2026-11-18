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
 * A search over the lessons of a checkout. No toString, since a raw remote may carry an access
 * token.
 */
@Schema(description = "The checkout and the text to find")
@Getter
@Setter
@NoArgsConstructor
public class LessonDeliverySearchRequest {

    @NotBlank(message = LessonConstant.REMOTE_URL_REQUIRED_MESSAGE)
    @Size(max = LessonConstant.REMOTE_URL_MAX_LENGTH, message = LessonConstant.REMOTE_URL_SIZE_MESSAGE)
    @Schema(description = "The checkout's git remote, in any form git accepts; Kaya normalises it",
            example = "git@github.com:korurlabs/village.git")
    private String remoteUrl;

    @NotBlank(message = LessonConstant.SEARCH_REQUIRED_MESSAGE)
    @Size(max = LessonConstant.SEARCH_MAX_LENGTH, message = LessonConstant.SEARCH_SIZE_MESSAGE)
    @Schema(description = "Text to find in the title or the body, case ignored, at most 200 characters", example = "migration")
    private String search;
}
