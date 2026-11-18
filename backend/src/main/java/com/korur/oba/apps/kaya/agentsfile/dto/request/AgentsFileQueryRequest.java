package com.korur.oba.apps.kaya.agentsfile.dto.request;

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
import com.korur.oba.apps.kaya.agentsfile.constant.AgentsFileConstant;

/**
 * Filters of the agents file list: the versions whose agents files a page shows. The list is not paged,
 * because a version holds at most one agents file per placement; it is ordered by version, placement and id.
 */
@Schema(description = "Filters of the agents file list")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class AgentsFileQueryRequest {

    @Schema(description = "The versions to list, repeated as versionId=...&versionId=...; an id of another "
            + "organization matches nothing", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[\"5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f\"]")
    @NotEmpty(message = AgentsFileConstant.VERSION_IDS_SIZE_MESSAGE)
    @Size(max = AgentsFileConstant.VERSION_IDS_MAX, message = AgentsFileConstant.VERSION_IDS_SIZE_MESSAGE)
    private List<@NotNull UUID> versionId;
}
