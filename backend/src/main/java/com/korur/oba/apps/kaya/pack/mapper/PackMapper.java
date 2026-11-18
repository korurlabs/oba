package com.korur.oba.apps.kaya.pack.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.pack.dto.request.PackUpdateRequest;
import com.korur.oba.apps.kaya.pack.dto.response.PackDetailResponse;
import com.korur.oba.apps.kaya.pack.dto.response.PackRepositoryResponse;
import com.korur.oba.apps.kaya.pack.dto.response.PackResponse;
import com.korur.oba.apps.kaya.pack.entity.Pack;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;

/**
 * Maps {@link Pack}s to {@link PackResponse}s and {@link PackDetailResponse}s and applies a
 * {@link PackUpdateRequest}. The mount folders and the repositories that compose a pack live in the
 * release domain, so a mapped pack has no folders until {@link PackResponse#withMountPaths} adds
 * them, and the detail takes both from its caller.
 */
@Mapper(componentModel = "spring")
public interface PackMapper {

    @Mapping(target = "mountPaths", expression = "java(java.util.List.of())")
    PackResponse toResponse(Pack pack);

    List<PackResponse> toResponses(List<Pack> packs);

    @Mapping(target = "mountPaths", source = "mountPaths")
    @Mapping(target = "repositories", source = "repositories")
    PackDetailResponse toDetailResponse(Pack pack, List<String> mountPaths, List<PackRepositoryResponse> repositories);

    /**
     * Writes the name and the description as given, a null description included, so an update
     * that leaves the description out removes it. Whether the name may change is the caller's check.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "parentPackId", ignore = true)
    @Mapping(target = "currentVersionId", ignore = true)
    @Mapping(target = "required", ignore = true)
    void updateEntity(@MappingTarget Pack pack, PackUpdateRequest request);

    @Mapping(target = "rootPath", source = "rootPath")
    @Mapping(target = "latestReleaseNumber", source = "latestReleaseNumber")
    PackRepositoryResponse toRepositoryResponse(RepositoryResponse repository, String rootPath, Integer latestReleaseNumber);
}
