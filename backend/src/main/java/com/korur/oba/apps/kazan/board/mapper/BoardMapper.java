package com.korur.oba.apps.kazan.board.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.request.BoardCreateRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardDetailResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardListWithChildrenResponse;
import com.korur.oba.apps.kazan.board.entity.Board;

/**
 * Maps board entities to DTOs and applies update requests to entities.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BoardMapper {

    /**
     * Maps a board creation request to a new entity. The slug is left for the service,
     * which derives it from the name and the slugs already taken.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parentBoard", ignore = true)
    @Mapping(target = "childBoards", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "boardSprints", ignore = true)
    @Mapping(target = "backlog", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "labels", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Board toEntity(BoardCreateRequest request);

    /**
     * Maps a board entity to a list response DTO.
     */
    BoardListResponse toListResponse(Board board);

    /**
     * Maps a board entity to a list with children response DTO.
     */
    BoardListWithChildrenResponse toListWithChildrenResponse(Board board);

    /**
     * Maps a board entity to a detail response DTO.
     */
    @Mapping(source = "parentBoard.id", target = "parentBoardId")
    BoardDetailResponse toDetailResponse(Board board);

    /**
     * Updates an existing board entity from an update request. The slug is left for the
     * service, which keeps or regenerates it from the new name.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parentBoard", ignore = true)
    @Mapping(target = "childBoards", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "boardSprints", ignore = true)
    @Mapping(target = "backlog", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "labels", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Board board, BoardUpdateRequest request);
}
