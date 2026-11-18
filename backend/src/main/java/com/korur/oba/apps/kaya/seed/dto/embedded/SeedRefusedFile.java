package com.korur.oba.apps.kaya.seed.dto.embedded;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.seed.enums.SeedSetKind;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;

/**
 * A file the seed named but did not import: it breaks a file rule (a path off the allow-list, a
 * symlink that leaves the repository, a file outside its project's namespace, and so on), or it
 * cannot be stored as its kind (NOT_DELIVERABLE, INVALID_FILE). The rest of its set is imported
 * without it.
 */
@Schema(description = "A file the seed refused, with the rules it breaks or the reason it cannot be stored")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeedRefusedFile(
        @Schema(description = "Kind of the set the file was named in", example = "PACK")
        SeedSetKind setKind,

        @Schema(description = "Name of that set", example = "org")
        String setName,

        @Schema(description = "Path relative to the repository root", example = ".agents/skills/shadcn/shadcn")
        String path,

        @Schema(description = "KAY_CFG_027 INVALID_FILE or KAY_CFG_028 NOT_DELIVERABLE when the file cannot be stored; "
                + "null when it breaks file rules", nullable = true, example = "KAY_CFG_028")
        String errorCode,

        @Schema(description = "Why the file cannot be stored; null when it breaks file rules", nullable = true,
                example = "no kind is delivered at this path")
        String reason,

        @Schema(description = "The file rules the file breaks; empty when it cannot be stored")
        List<Violation> violations
) {
}
