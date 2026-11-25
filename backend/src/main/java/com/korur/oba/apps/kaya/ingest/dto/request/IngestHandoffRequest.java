package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;

@Schema(description = "A handoff file as the session last wrote it")
public record IngestHandoffRequest(
        @Schema(description = "Path below handoffs/, starting with the feature directory",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "kaya/backend/ingest.md")
        @NotBlank(message = IngestConstant.HANDOFF_PATH_REQUIRED_MESSAGE)
        @Size(max = IngestConstant.HANDOFF_PATH_MAX_LENGTH, message = IngestConstant.HANDOFF_PATH_SIZE_MESSAGE)
        String path,

        @Schema(description = "The markdown of the file, 1 to 200000 characters. Content that holds U+0000 is a "
                + "binary file: it is accepted at any length and not stored", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = IngestConstant.HANDOFF_CONTENT_REQUIRED_MESSAGE)
        @Size(min = IngestConstant.HANDOFF_CONTENT_MIN_LENGTH, message = IngestConstant.HANDOFF_CONTENT_SIZE_MESSAGE)
        String content,

        @Schema(description = "When the recorder read the file", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-23T09:15:00Z")
        @NotNull(message = IngestConstant.HANDOFF_CAPTURED_AT_REQUIRED_MESSAGE)
        Instant capturedAt
) {

    /**
     * Text content is stored, so it is held to the stored limit. Content that holds U+0000 is a
     * binary file, which is never stored, so it passes at any length. A recorder cuts a file at
     * 200000 code points, and a binary file decodes partly to characters outside the Basic
     * Multilingual Plane, which count twice here. Without this exception a large image would be
     * refused, and the refusal would stop its session from recording.
     */
    @Schema(hidden = true)
    @AssertTrue(message = IngestConstant.HANDOFF_CONTENT_SIZE_MESSAGE)
    public boolean isContentWithinLimit() {
        return content == null || content.length() <= IngestConstant.HANDOFF_CONTENT_MAX_LENGTH
                || content.indexOf(IngestConstant.NUL) >= 0;
    }
}
