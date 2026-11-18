package com.korur.oba.apps.otak.event.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.event.dto.request.EventCreateRequest;
import com.korur.oba.apps.otak.event.dto.request.EventUpdateRequest;
import com.korur.oba.apps.otak.event.dto.response.EventDetailResponse;
import com.korur.oba.apps.otak.event.dto.response.EventListResponse;
import com.korur.oba.apps.otak.event.entity.Event;

/**
 * Maps event entities to DTOs and requests to entities. An event has no association, so
 * the mapper needs no helper.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface EventMapper {

    /**
     * Maps a create request to a new entity. The service sets the organization from the
     * checked request header.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    Event toEntity(EventCreateRequest request);

    /**
     * Writes every field of an update request onto the event; null clears an optional
     * field, so nothing stored survives a field the client left out.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    void updateEntity(@MappingTarget Event event, EventUpdateRequest request);

    /**
     * Maps an event to a list item.
     */
    EventListResponse toListResponse(Event event);

    /**
     * Maps an event to the detail response.
     */
    EventDetailResponse toDetailResponse(Event event);
}
