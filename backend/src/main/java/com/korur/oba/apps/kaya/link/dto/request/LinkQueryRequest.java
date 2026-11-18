package com.korur.oba.apps.kaya.link.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * The scope whose links are read: a version's own files, a repository's latest release, or the
 * repository composed with the version in place of its owner's current one. At least one of the
 * two ids is given; {@link com.korur.oba.apps.kaya.link.service.LinkQueryService} refuses neither.
 */
@Getter
@Setter
@Schema(description = "Scope of the links: a version, a repository, or a repository with a version in place")
public class LinkQueryRequest {

    @Schema(description = "A version; alone, the links within its own files; with repositoryId, a DRAFT or IN_REVIEW version "
            + "composed in place of its owner's current one", nullable = true, example = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f")
    private UUID versionId;

    @Schema(description = "A repository; alone, the links its latest release delivers", nullable = true,
            example = "4f5a6b7c-8d9e-4f0a-1b2c-3d4e5f6a7b8c")
    private UUID repositoryId;
}
