package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.time.Instant;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.ingest.constant.IngestConstant;
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;

@Schema(description = "A plan as the session last captured it: a spec file it wrote, or its plan-mode or "
        + "plan-format message")
public record IngestPlanRequest(
        @Schema(description = "SPEC for a spec file, SESSION for the plan of the session",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "SPEC")
        @NotNull(message = IngestConstant.PLAN_KIND_REQUIRED_MESSAGE)
        PlanKind kind,

        @Schema(description = "FILE for a SPEC; PLAN_MODE or MESSAGE for a SESSION plan",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "FILE")
        @NotNull(message = IngestConstant.PLAN_SOURCE_REQUIRED_MESSAGE)
        PlanVersionSource source,

        @Schema(description = "Repository-relative path of a SPEC, worktree prefix removed, matching "
                + "^docs/(.+/)?plans/.+\\.md$; null for a SESSION plan", nullable = true,
                example = "docs/private/plans/kaya/04_KAYA_PLANS.md")
        @Size(max = IngestConstant.PLAN_PATH_MAX_LENGTH, message = IngestConstant.PLAN_PATH_SIZE_MESSAGE)
        String path,

        @Schema(description = "The markdown of the plan", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "# Kaya plans\n\n## 1. Summary\n")
        @NotNull(message = IngestConstant.PLAN_CONTENT_REQUIRED_MESSAGE)
        @Size(min = IngestConstant.PLAN_CONTENT_MIN_LENGTH, max = IngestConstant.PLAN_CONTENT_MAX_LENGTH,
                message = IngestConstant.PLAN_CONTENT_SIZE_MESSAGE)
        String content,

        @Schema(description = "When the recorder captured the plan", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2026-10-25T09:15:00Z")
        @NotNull(message = IngestConstant.PLAN_CAPTURED_AT_REQUIRED_MESSAGE)
        Instant capturedAt
) {

    private static final Pattern PLAN_PATH = Pattern.compile(IngestConstant.PLAN_PATH_PATTERN);

    /**
     * A SPEC is identified by its path, so the path must name a spec file inside the repository; a
     * "." or ".." segment or an empty one would let two spellings name the same file. A SESSION
     * plan is identified by its session and carries no path.
     */
    @Schema(hidden = true)
    @AssertTrue(message = IngestConstant.PLAN_PATH_FOR_KIND_MESSAGE)
    public boolean isPathValidForKind() {
        if (kind == null) {
            return true;
        }
        if (kind == PlanKind.SESSION) {
            return path == null;
        }
        if (path == null || !PLAN_PATH.matcher(path).matches()) {
            return false;
        }
        for (String segment : path.split(IngestConstant.PATH_SEPARATOR, -1)) {
            if (segment.isEmpty() || segment.equals(IngestConstant.PATH_SEGMENT_CURRENT)
                    || segment.equals(IngestConstant.PATH_SEGMENT_PARENT)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A spec is always read from its file; a session plan comes from plan mode or a message.
     */
    @Schema(hidden = true)
    @AssertTrue(message = IngestConstant.PLAN_SOURCE_FOR_KIND_MESSAGE)
    public boolean isSourceValidForKind() {
        if (kind == null || source == null) {
            return true;
        }
        return kind == PlanKind.SPEC ? source == PlanVersionSource.FILE : source != PlanVersionSource.FILE;
    }
}
