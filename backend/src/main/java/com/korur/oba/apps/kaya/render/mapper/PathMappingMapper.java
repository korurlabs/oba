package com.korur.oba.apps.kaya.render.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.render.dto.embedded.PathMappingCount;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolGap;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolMapping;
import com.korur.oba.apps.kaya.render.dto.embedded.ToolPathCount;
import com.korur.oba.apps.kaya.render.dto.response.PathMappingResponse;
import com.korur.oba.apps.kaya.render.dto.response.ToolGapResponse;
import com.korur.oba.apps.kaya.render.dto.response.ToolMappingResponse;
import com.korur.oba.apps.kaya.render.dto.response.ToolPathResponse;
import com.korur.oba.apps.kaya.render.helper.ToolPaths;

/**
 * Maps a {@link PathMappingCount} to the flat {@link PathMappingResponse} of the path mapping page,
 * and a {@link ToolMapping} to the {@link ToolMappingResponse} of its tool view, whose name, summary
 * and not-delivered paths come from {@link ToolPaths}.
 */
@Mapper(componentModel = "spring", imports = ToolPaths.class)
public interface PathMappingMapper {

    @Mapping(target = "kind", source = "mapping.kind")
    @Mapping(target = "part", source = "mapping.part")
    @Mapping(target = "placement", source = "mapping.placement")
    @Mapping(target = "link", source = "mapping.link")
    @Mapping(target = "pathPattern", source = "mapping.pathPattern")
    @Mapping(target = "mountedPattern", source = "mapping.mountedPattern")
    @Mapping(target = "formats", source = "mapping.formats")
    @Mapping(target = "executable", source = "mapping.executable")
    @Mapping(target = "note", source = "mapping.note")
    @Mapping(target = "count", source = "count")
    PathMappingResponse toResponse(PathMappingCount pathMappingCount);

    List<PathMappingResponse> toResponses(List<PathMappingCount> pathMappingCounts);

    @Mapping(target = "tool", source = "tool")
    @Mapping(target = "name", expression = "java(ToolPaths.name(toolMapping.tool()))")
    @Mapping(target = "summary", expression = "java(ToolPaths.summary(toolMapping.tool()))")
    @Mapping(target = "reads", source = "reads")
    @Mapping(target = "notDelivered", expression = "java(toGapResponses(ToolPaths.notDelivered(toolMapping.tool())))")
    ToolMappingResponse toToolResponse(ToolMapping toolMapping);

    List<ToolMappingResponse> toToolResponses(List<ToolMapping> toolMappings);

    @Mapping(target = "readPath", source = "path.readPath")
    @Mapping(target = "loading", source = "path.loading")
    @Mapping(target = "linkPath", source = "path.link.pathPattern")
    @Mapping(target = "linkTarget", expression = "java(ToolPaths.linkTarget(toolPathCount.path().link()))")
    @Mapping(target = "sourcePattern", source = "path.source.pathPattern")
    @Mapping(target = "kind", source = "path.source.kind")
    @Mapping(target = "part", source = "path.source.part")
    @Mapping(target = "placement", source = "path.source.placement")
    @Mapping(target = "note", source = "path.note")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "count", source = "count")
    ToolPathResponse toToolPathResponse(ToolPathCount toolPathCount);

    ToolGapResponse toGapResponse(ToolGap toolGap);

    List<ToolGapResponse> toGapResponses(List<ToolGap> toolGaps);
}
