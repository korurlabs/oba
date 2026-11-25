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
import com.korur.oba.apps.kazan.cyberscore.constant.CheckConstant;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CheckResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Check;

/**
 * Maps check requests to entities and checks to their response DTOs.
 *
 * Outcome and status are left to the service, which defaults the outcome and
 * derives the status from it; the organization comes from the checked request
 * header, never from a body.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CheckMapper {

    /**
     * Maps a create request to a new check.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "outcome", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "sortOrder", defaultValue = CheckConstant.DEFAULT_SORT_ORDER)
    Check toEntity(CheckRequest request);

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
    @Mapping(target = "sortOrder", defaultValue = CheckConstant.DEFAULT_SORT_ORDER)
    void updateEntity(@MappingTarget Check check, CheckRequest request);

    /**
     * Maps a check to its response.
     */
    CheckResponse toResponse(Check check);

    /**
     * Maps checks to their responses, keeping their order.
     */
    List<CheckResponse> toResponses(List<Check> checks);
}
