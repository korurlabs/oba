package com.korur.oba.apps.kazan.label.mapper;

import java.util.List;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.korur.oba.apps.kazan.label.dto.request.LabelCreateRequest;
import com.korur.oba.apps.kazan.label.dto.request.LabelUpdateRequest;
import com.korur.oba.apps.kazan.label.dto.response.LabelResponse;
import com.korur.oba.apps.kazan.label.entity.Label;
import com.korur.oba.apps.kazan.ticket.entity.TicketLabel;

/**
 * Maps label entities to DTOs and applies request payloads to entities.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface LabelMapper {

    /**
     * Maps a label creation request to a new entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "ticketLabels", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Label toEntity(LabelCreateRequest request);

    /**
     * Updates an existing label entity from an update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "ticketLabels", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Label label, LabelUpdateRequest request);

    /**
     * Maps a label entity to a response DTO.
     */
    @Named("labelToResponse")
    @Mapping(source = "board.id", target = "boardId")
    @Mapping(source = "board.name", target = "boardName")
    LabelResponse toResponse(Label label);

    /**
     * Maps a ticket-label join entity to a label response DTO.
     */
    @Mapping(source = "label.id", target = "id")
    @Mapping(source = "label.name", target = "name")
    @Mapping(source = "label.color", target = "color")
    @Mapping(source = "label.board.id", target = "boardId")
    @Mapping(source = "label.board.name", target = "boardName")
    LabelResponse ticketLabelToLabelResponse(TicketLabel ticketLabel);
}
