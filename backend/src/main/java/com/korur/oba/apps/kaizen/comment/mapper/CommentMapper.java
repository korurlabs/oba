package com.korur.oba.apps.kaizen.comment.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.comment.dto.request.CommentCreateRequest;
import com.korur.oba.apps.kaizen.comment.dto.response.CommentDetailResponse;
import com.korur.oba.apps.kaizen.comment.entity.Comment;
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;

/**
 * Maps comment entities to DTOs and applies request payloads to entities.
 *
 * Author profile fields (authorEmail, authorFirstName, authorLastName) are mapped
 * from the nested author entity and its embedded profile.
 *
 * The content field is rich-text HTML that may embed blob URLs; the
 * BlobUrlRewriterMapper qualifier rewrites each embedded blob URL into a
 * SAS-signed URL before the response leaves the backend.
 */
@Mapper(
        componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        uses = { BlobUrlRewriterMapper.class })
public interface CommentMapper {

    /**
     * Maps a comment creation request to a new entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "ticket", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Comment toEntity(CommentCreateRequest request);

    /**
     * Maps a comment entity to a detail response DTO.
     *
     * Maps author profile data from the nested User entity.
     */
    @Mapping(source = "ticket.id", target = "ticketId")
    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "author.email", target = "authorEmail")
    @Mapping(source = "author.profile.firstName", target = "authorFirstName")
    @Mapping(source = "author.profile.lastName", target = "authorLastName")
    @Mapping(source = "content", target = "content", qualifiedByName = "rewriteHtmlBlobUrls")
    CommentDetailResponse toDetailResponse(Comment comment);
}
