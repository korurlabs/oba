package com.korur.oba.apps.kazan.backlog.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.korur.oba.apps.kazan.backlog.dto.response.BacklogDetailResponse;
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.ticket.mapper.TicketMapper;

/**
 * Maps backlog entities to DTOs.
 * <p>
 * Uses TicketMapper for mapping the tickets collection in the detail response.
 */
@Mapper(componentModel = "spring", uses = {
        TicketMapper.class
}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BacklogMapper {

    /**
     * Maps a backlog entity to a detail response DTO.
     */
    @Mapping(source = "board.id", target = "boardId")
    BacklogDetailResponse toDetailResponse(Backlog backlog);
}
