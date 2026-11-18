package com.korur.oba.apps.kazan.sprint.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.mapper.BoardMapper;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCreateRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUpdateRequest;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintChildBoardResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintListResponse;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintStartConflictResponse;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;

/**
 * Maps sprint entities to DTOs and applies update requests to entities.
 */
@Mapper(componentModel = "spring", uses = {
        BoardMapper.class,
        SprintMapperHelper.class
}, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SprintMapper {

    /**
     * Maps a sprint creation request to a new entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "series", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "boardSprints", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Sprint toEntity(SprintCreateRequest request);

    /**
     * Maps a sprint entity to a list response DTO, with the slug of the sprint's link to
     * the listed board. The slug lives on the link because a shared sprint can have a
     * different slug on each board. The series name is set when closing the sprint would
     * leave its repeating series nothing to run.
     */
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "endsSeriesName", source = "endsSeriesName")
    SprintListResponse toListResponse(Sprint sprint, String slug, String endsSeriesName);

    /**
     * Maps a descendant board to the entry offered when a sprint is created on its ancestor,
     * with its depth below that ancestor, whether the caller may link it, and its active
     * sprint (null when it has none). The caller passes the parent id only when the parent
     * is visible to it, and null otherwise, so that the id of a hidden board is not exposed.
     * The series name is set when closing the active sprint would stop its repeating series.
     */
    @Mapping(target = "id", source = "board.id")
    @Mapping(target = "name", source = "board.name")
    @Mapping(target = "prefix", source = "board.prefix")
    @Mapping(target = "color", source = "board.color")
    @Mapping(target = "parentBoardId", source = "parentBoardId")
    @Mapping(target = "depth", source = "depth")
    @Mapping(target = "canLink", source = "canLink")
    @Mapping(target = "activeSprintId", source = "activeSprint.id")
    @Mapping(target = "activeSprintName", source = "activeSprint.name")
    @Mapping(target = "activeSprintEndsSeriesName", source = "activeSprintEndsSeriesName")
    SprintChildBoardResponse toChildBoardResponse(Board board, UUID parentBoardId, int depth, boolean canLink,
                                                  Sprint activeSprint, String activeSprintEndsSeriesName);

    /**
     * Maps a board and the ACTIVE sprint on it that a start with takeover would close, with
     * the number of its tickets on that board that are not DONE, whether they go to the
     * board's backlog, whether the caller may manage the board, and the name of the repeating
     * series that stops because closing the sprint leaves it nothing to run.
     */
    @Mapping(target = "boardId", source = "board.id")
    @Mapping(target = "boardName", source = "board.name")
    @Mapping(target = "boardPrefix", source = "board.prefix")
    @Mapping(target = "sprintId", source = "sprint.id")
    @Mapping(target = "sprintName", source = "sprint.name")
    @Mapping(target = "unfinishedTicketCount", source = "unfinishedTicketCount")
    @Mapping(target = "movesToBacklog", source = "movesToBacklog")
    @Mapping(target = "canManage", source = "canManage")
    @Mapping(target = "endsSeriesName", source = "endsSeriesName")
    SprintStartConflictResponse toStartConflictResponse(Board board, Sprint sprint, long unfinishedTicketCount,
                                                        boolean movesToBacklog, boolean canManage,
                                                        String endsSeriesName);

    /**
     * Maps a sprint entity to a detail response DTO, with its series built by
     * SprintMapperHelper.
     */
    @Mapping(source = "boardSprints", target = "boards")
    @Mapping(source = ".", target = "series", qualifiedByName = "toSeries")
    SprintDetailResponse toDetailResponse(Sprint sprint);

    /**
     * Maps a board-sprint join entity to a board list response by extracting the board.
     *
     * MapStruct auto-applies this to the List mapping in toDetailResponse.
     * The board-to-BoardListResponse mapping is delegated to BoardMapper via the uses clause.
     */
    default BoardListResponse boardSprintToBoardListResponse(BoardSprint boardSprint) {
        if (boardSprint == null) {
            return null;
        }
        return boardToListResponse(boardSprint.getBoard());
    }

    /**
     * Delegates board-to-list-response mapping to BoardMapper.
     *
     * This abstract method is implemented by MapStruct using the BoardMapper
     * declared in the uses clause.
     */
    BoardListResponse boardToListResponse(Board board);

    /**
     * Updates an existing sprint entity from an update request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "series", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "boardSprints", ignore = true)
    @Mapping(target = "tickets", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Sprint sprint, SprintUpdateRequest request);
}
