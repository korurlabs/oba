package com.korur.oba.apps.kaizen.board.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.board.dto.request.BoardMemberAddRequest;
import com.korur.oba.apps.kaizen.board.dto.response.BoardMemberCandidateResponse;
import com.korur.oba.apps.kaizen.board.dto.response.BoardMemberResponse;
import com.korur.oba.apps.kaizen.board.entity.BoardMember;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;

/**
 * Maps board member request payloads to entities and entities to response DTOs.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BoardMemberMapper {

    /**
     * Maps a board member add request to a new entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "board", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "specialties", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    BoardMember toEntity(BoardMemberAddRequest request);

    /**
     * Maps a board member entity to a response DTO with resolved user profile data and whether the member
     * may open Kaizen in the board's organization.
     */
    @Mapping(source = "member.id", target = "id")
    @Mapping(source = "member.user.id", target = "userId")
    @Mapping(source = "member.user.email", target = "email")
    @Mapping(source = "member.user.profile.firstName", target = "firstName")
    @Mapping(source = "member.user.profile.lastName", target = "lastName")
    @Mapping(source = "member.role", target = "role")
    @Mapping(source = "member.createdAt", target = "createdAt")
    @Mapping(source = "kaizenAccess", target = "kaizenAccess")
    BoardMemberResponse toResponse(BoardMember member, boolean kaizenAccess);

    /**
     * Maps an active member of the board's organization to an entry of the add-member picker, with whether
     * the member may open Kaizen there.
     */
    @Mapping(source = "member.userId", target = "userId")
    @Mapping(source = "member.email", target = "email")
    @Mapping(source = "member.firstName", target = "firstName")
    @Mapping(source = "member.lastName", target = "lastName")
    @Mapping(source = "member.role", target = "role")
    @Mapping(source = "kaizenAccess", target = "kaizenAccess")
    BoardMemberCandidateResponse toCandidateResponse(OrganizationMemberSummaryProjection member,
                                                     boolean kaizenAccess);
}
