package com.korur.oba.apps.kaya.review.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;
import com.korur.oba.apps.kaya.review.constant.ReviewConstant;
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;

/**
 * The file and the paging of the history of one path of a pack.
 */
@Schema(description = "The history of one file: any version of its pack and the path; newest number first, "
        + "sortBy and sortDirection are ignored")
@Getter
@Setter
@NoArgsConstructor
public class FileHistoryQueryRequest extends QueryRequest {

    @Schema(description = "Any version of the pack whose file history is read",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "6b0e2f4a-1c3d-4e5f-8a9b-0c1d2e3f4a5b")
    @NotNull(message = ReviewConstant.VERSION_ID_REQUIRED_MESSAGE)
    private UUID versionId;

    @Schema(description = "Path of the file relative to the repository root", requiredMode = Schema.RequiredMode.REQUIRED,
            example = ".agents/rules/core.md")
    @NotBlank(message = ReviewConstant.PATH_REQUIRED_MESSAGE)
    @Size(max = ValidationConstant.PATH_MAX_LENGTH)
    private String path;
}
