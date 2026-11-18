package com.korur.oba.apps.kaya.link.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
import com.korur.oba.apps.kaya.link.constant.LinkConstant;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;

/**
 * The scope and the file whose dependencies are read: the versions a page shows, for example a
 * repository's root project and its packs, and one path among their files.
 */
@Schema(description = "The versions of a scope and one path among their files")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DependencyQueryRequest {

    @Schema(description = "The versions of the scope, repeated as versionId=...&versionId=...; a path two of them "
            + "deliver is read from the first; an id of another organization matches nothing",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f\"]")
    @NotEmpty(message = LinkConstant.VERSION_IDS_SIZE_MESSAGE)
    @Size(max = LinkConstant.VERSION_IDS_MAX, message = LinkConstant.VERSION_IDS_SIZE_MESSAGE)
    private List<@NotNull UUID> versionId;

    @Schema(description = "Path of the file relative to the repository root", requiredMode = Schema.RequiredMode.REQUIRED,
            example = ".agents/rules/core.md")
    @NotBlank(message = LinkConstant.PATH_REQUIRED_MESSAGE)
    @Size(max = ValidationConstant.PATH_MAX_LENGTH)
    private String path;
}
