package com.korur.oba.apps.kaya.lesson.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;

/**
 * Filters of the lessons list. Lessons are always ordered newest first, so sortBy and
 * sortDirection are ignored. The list allows pages of up to 100, above the 50 of the base class,
 * so this request keeps its own page size.
 */
@Schema(description = "Paging and filters of the lessons list; sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class LessonQueryRequest extends QueryRequest {

    @Schema(description = "Number of lessons per page", example = "25", nullable = true)
    @Min(value = 1, message = CommonConstant.AMOUNT_MIN_MESSAGE)
    @Max(value = LessonConstant.LIST_MAX_AMOUNT, message = LessonConstant.AMOUNT_MAX_MESSAGE)
    private int amount = LessonConstant.LIST_DEFAULT_AMOUNT;

    @Schema(description = "Only lessons of this repository", nullable = true)
    private UUID repositoryId;

    @Schema(description = "true: only lessons of no repository; false leaves the list unfiltered", nullable = true,
            example = "true")
    private Boolean organizationWide;

    @Schema(description = "Only lessons of this author", nullable = true)
    private UUID userId;

    @Schema(description = "Only lessons recorded by this Claude Code session; a session Kaya has not seen matches none",
            nullable = true, example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
    @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN, message = LessonConstant.SESSION_ID_FILTER_MESSAGE)
    private String sessionId;

    @Schema(description = "Matches the title or the text, case ignored", nullable = true, example = "flyway")
    @Size(max = LessonConstant.SEARCH_MAX_LENGTH, message = LessonConstant.SEARCH_SIZE_MESSAGE)
    private String search;

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }
}
