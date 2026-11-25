package com.korur.oba.apps.kazan.cyberscore.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreCheckConstant;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreCheckRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreCheckResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;

/**
 * Maps check requests to entities and checks to their response DTOs.
 *
 * Outcome and status are left to the service, which defaults the outcome and
 * derives the status from it; the organization comes from the checked request
 * header, never from a body.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CyberscoreCheckMapper {

    /**
     * Maps a create request to a new check.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "outcome", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "sortOrder", defaultValue = CyberscoreCheckConstant.DEFAULT_SORT_ORDER)
    CyberscoreCheck toEntity(CyberscoreCheckRequest request);

    /**
     * Replaces the descriptive and scoring fields of a check. A null description
     * or category clears it, and a null sort order resets it to 0.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "outcome", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "sortOrder", defaultValue = CyberscoreCheckConstant.DEFAULT_SORT_ORDER)
    void updateEntity(@MappingTarget CyberscoreCheck check, CyberscoreCheckRequest request);

    /**
     * Maps a check to its response.
     */
    CyberscoreCheckResponse toResponse(CyberscoreCheck check);

    /**
     * Maps checks to their responses, keeping their order.
     */
    List<CyberscoreCheckResponse> toResponses(List<CyberscoreCheck> checks);
}
