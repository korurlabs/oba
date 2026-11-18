package com.korur.oba.apps.kaya.lesson.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

@Schema(description = "The lesson a machine added")
public record LessonDeliveryCreatedResponse(
        @Schema(description = "Lesson id", example = "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
        UUID id,

        @Schema(description = "The lesson's page in the web app",
                example = "https://kaya.example.com/lessons/1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e")
        String url
) {
}
