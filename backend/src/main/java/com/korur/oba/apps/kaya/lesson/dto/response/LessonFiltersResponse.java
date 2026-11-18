package com.korur.oba.apps.kaya.lesson.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.dto.response.embedded.LessonReferenceResponse;

@Schema(description = "The options of the lessons list filters: the values the organization's lessons hold")
public record LessonFiltersResponse(
        @Schema(description = "Repositories that have lessons, by name")
        List<LessonReferenceResponse> repositories,

        @Schema(description = "Authors of lessons, by name")
        List<LessonReferenceResponse> users
) {
}
