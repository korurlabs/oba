package com.korur.oba.apps.kaya.lesson.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;

/**
 * The checkout whose lessons the CLI asks for, and which page of them. since stays text so that the
 * service refuses an unreadable value with a field message instead of a conversion error.
 *
 * page and amount mean what they mean in {@link com.korur.oba.dojo.dto.QueryRequest}, with pages of
 * 100 by default. The class does not extend it: its toString prints every field, and a raw remote
 * may carry an access token, so this class has no toString.
 *
 * page stays null when the request names none. Such a request reads page 1, but its answer holds
 * only the newest lessons, so it carries an ETag of its own form, which no paged request matches.
 */
@Schema(description = "The checkout, when it last looked, and the page of its lessons")
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

    @Schema(description = "Page number (1-based). Without it the answer is page 1 under an ETag of its own form, "
            + "which only a request without page matches", example = "1", nullable = true)
    @Min(value = 1, message = CommonConstant.PAGE_MIN_MESSAGE)
    private Integer page;

    @Schema(description = "Number of lessons per page", example = "100", nullable = true)
    @Min(value = 1, message = CommonConstant.AMOUNT_MIN_MESSAGE)
    @Max(value = LessonConstant.DELIVERY_MAX_AMOUNT, message = LessonConstant.AMOUNT_MAX_MESSAGE)
    private int amount = LessonConstant.DELIVERY_DEFAULT_AMOUNT;

    @Schema(description = "The ETag header of page 1, quoted as received or bare; required from page 2 on. A set "
            + "that changed since page 1 answers 409 KAY_VAL_017", nullable = true,
            example = "\"v2-9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08\"")
    @Pattern(regexp = LessonConstant.ETAG_PATTERN, message = LessonConstant.ETAG_INVALID_MESSAGE)
    private String etag;
}
