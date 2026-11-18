package com.korur.oba.apps.kaya.seed.dto.embedded;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.seed.enums.SeedSetKind;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;

/**
 * A size limit a file of today's repository breaks, which the seed imports anyway: a seed mirrors
 * the repository as it is, and the limits refuse only the changes made through Kaya. Each warning
 * names the set the file was named in.
 */
@Schema(description = "A size limit the seeded files break; imported, reported as a warning")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeedWarning(
        @Schema(description = "Kind of the set the file was named in", example = "PACK")
        SeedSetKind setKind,

        @Schema(description = "Name of that set", example = "org")
        String setName,

        @Schema(description = "The size limit and by how much it is broken")
        Violation violation
) {
}
