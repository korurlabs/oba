package com.korur.oba.apps.kaizen.alertroute.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteListProjection;
import com.korur.oba.apps.kaizen.alertroute.dto.request.AlertRouteCreateRequest;
import com.korur.oba.apps.kaizen.alertroute.dto.response.AlertRouteResponse;
import com.korur.oba.apps.kaizen.alertroute.entity.AlertRoute;

/**
 * Maps alert route entities to DTOs and create requests to entities.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AlertRouteMapper {

    /**
     * Maps an alert route creation request to a new entity.
     *
     * The board comes from the board access check, the organization from the
     * checked request header and the default flag from the default handling, all
     * in the service, so none is mapped here.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "isDefault", ignore = true)
    AlertRoute toEntity(AlertRouteCreateRequest request);

    /**
     * Maps an alert route entity to a response DTO.
     */
    @Mapping(source = "board.id", target = "boardId")
    @Mapping(source = "board.name", target = "boardName")
    @Mapping(source = "board.prefix", target = "boardPrefix")
    @Mapping(source = "board.color", target = "boardColor")
    AlertRouteResponse toResponse(AlertRoute alertRoute);

    /**
     * Maps a listed alert route row, board fields included, to a response DTO.
     */
    AlertRouteResponse toResponse(AlertRouteListProjection route);
}
