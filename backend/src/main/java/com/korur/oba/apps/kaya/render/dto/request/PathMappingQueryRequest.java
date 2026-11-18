package com.korur.oba.apps.kaya.render.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
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
import com.korur.oba.apps.kaya.render.constant.RenderConstant;

/**
 * The scope of the path mapping page: the versions whose files the rows count. No version is a
 * valid scope, since the rows still show where each kind lives; the counts are then null.
 */
@Schema(description = "The versions whose files the path mapping rows count")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PathMappingQueryRequest {

    @Schema(description = "The versions of the scope, repeated as versionId=...&versionId=...; none leaves the counts "
            + "null, and an id of another organization counts 0", example = "[\"5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f\"]")
    @Size(max = RenderConstant.PATH_MAPPING_VERSION_IDS_MAX, message = RenderConstant.PATH_MAPPING_VERSION_IDS_SIZE_MESSAGE)
    private List<@NotNull UUID> versionId;
}
