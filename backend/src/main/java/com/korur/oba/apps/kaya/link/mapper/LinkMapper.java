package com.korur.oba.apps.kaya.link.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.link.dto.response.DependencyResponse;
import com.korur.oba.apps.kaya.link.dto.response.LinkResponse;
import com.korur.oba.apps.kaya.link.dto.response.embedded.DependencyEmbeddedResponse;
import com.korur.oba.apps.kaya.link.enums.DependencyRelation;
import com.korur.oba.apps.kaya.link.enums.LinkStatus;
import com.korur.oba.apps.kaya.render.enums.EntityKind;
import com.korur.oba.apps.kaya.render.helper.LinkCandidate;

/**
 * Maps an extracted {@link LinkCandidate} and its {@link LinkStatus} to a {@link LinkResponse}.
 */
@Mapper(componentModel = "spring")
public interface LinkMapper {

    @Mapping(target = "sourcePath", source = "link.sourcePath")
    @Mapping(target = "targetPath", source = "link.targetPath")
    @Mapping(target = "line", source = "link.line")
    @Mapping(target = "status", source = "status")
    LinkResponse toResponse(LinkCandidate link, LinkStatus status);

    DependencyEmbeddedResponse toDependency(String path, EntityKind kind, DependencyRelation relation, LinkStatus status,
                                            UUID versionId);

    DependencyResponse toDependencyResponse(String path, EntityKind kind, List<DependencyEmbeddedResponse> linksTo,
                                            List<DependencyEmbeddedResponse> usedBy);
}
