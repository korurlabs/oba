package com.korur.oba.apps.kaya.convention.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.convention.constant.ConventionConstant;

/**
 * Filters of the convention list: the versions whose conventions a page shows. The list is not paged,
 * because a version holds a few dozen conventions; it is ordered by version, path and id.
 */
@Schema(description = "Filters of the convention list")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ConventionQueryRequest {

    @Schema(description = "The versions to list, repeated as versionId=...&versionId=...; an id of another "
            + "organization matches nothing", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[\"5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f\"]")
    @NotEmpty(message = ConventionConstant.VERSION_IDS_SIZE_MESSAGE)
    @Size(max = ConventionConstant.VERSION_IDS_MAX, message = ConventionConstant.VERSION_IDS_SIZE_MESSAGE)
    private List<@NotNull UUID> versionId;
}
