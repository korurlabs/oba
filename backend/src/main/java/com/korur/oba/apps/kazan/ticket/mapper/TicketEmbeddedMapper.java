package com.korur.oba.apps.kazan.ticket.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.comment.entity.Comment;
import com.korur.oba.apps.kazan.systemactor.entity.SystemActor;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketAssigneeResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketCommentResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketCreatedByResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketCreatedBySystemActorResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketReviewerResponse;
import com.korur.oba.apps.kazan.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kazan.ticket.entity.TicketReviewer;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Maps embedded ticket creator and user relations (assignees, reviewers, comments,
 * createdBy, createdBySystemActor) to DTOs.
 * <p>
 * Used by TicketMapper via the {@code uses} mechanism to resolve nested user profile data.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TicketEmbeddedMapper {

    /**
     * Maps a ticket assignee entity to a response DTO with user profile data and whether the assignee is
     * a person or an agent. The agent fields stay null for a person, whose agent detail is null.
     */
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.profile.firstName", target = "firstName")
    @Mapping(source = "user.profile.lastName", target = "lastName")
    @Mapping(source = "user.kind", target = "kind")
    @Mapping(source = "user.agentDetail.role", target = "agentRole")
    @Mapping(source = "user.agentDetail.flow", target = "agentFlow")
    @Mapping(source = "user.agentDetail.provider", target = "agentProvider")
    TicketAssigneeResponse toAssigneeResponse(TicketAssignee assignee);

    /**
     * Maps a ticket reviewer entity to a response DTO with user profile data.
     */
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.profile.firstName", target = "firstName")
    @Mapping(source = "user.profile.lastName", target = "lastName")
    TicketReviewerResponse toReviewerResponse(TicketReviewer reviewer);

    /**
     * Maps a comment entity to a ticket comment response DTO with author profile data.
     */
    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "author.email", target = "authorEmail")
    @Mapping(source = "author.profile.firstName", target = "authorFirstName")
    @Mapping(source = "author.profile.lastName", target = "authorLastName")
    TicketCommentResponse toCommentResponse(Comment comment);

    /**
     * Maps a user entity to a ticket created-by response DTO; null for a ticket
     * created by a system actor.
     */
    @Mapping(source = "id", target = "userId")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "profile.firstName", target = "firstName")
    @Mapping(source = "profile.lastName", target = "lastName")
    TicketCreatedByResponse toCreatedByResponse(User user);

    /**
     * Maps the system actor that created a ticket to a response DTO; null for a
     * ticket created by a user.
     */
    TicketCreatedBySystemActorResponse toCreatedBySystemActorResponse(SystemActor actor);
}
