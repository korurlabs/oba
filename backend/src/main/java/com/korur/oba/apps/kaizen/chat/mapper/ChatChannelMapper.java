package com.korur.oba.apps.kaizen.chat.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelCreateRequest;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatChannelUpdateRequest;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatChannel;

/**
 * Maps chat channels between requests, entities and responses.
 *
 * The organization and the creator of a new channel are set by the channel
 * service; the reader's membership and the member count of a response are
 * computed there and passed in. Names are stored trimmed, so two names that
 * differ only in surrounding spaces read the same.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ChatChannelMapper {

    /**
     * Maps a create request to a new text channel without organization,
     * creator and slug. The type keeps its entity default, TEXT. The slug is
     * left for the channel service, which derives it from the name and the
     * slugs already taken in the organization.
     *
     * @param request the create request
     * @return the new channel
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimName")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "purpose", source = "purpose")
    ChatChannel toEntity(ChatChannelCreateRequest request);

    /**
     * Applies a full update of name, visibility and purpose; the type never
     * changes, and the member list and the slug are managed by the channel
     * service.
     *
     * @param channel the stored channel
     * @param request the update request
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimName")
    @Mapping(target = "visibility", source = "visibility")
    @Mapping(target = "purpose", source = "purpose")
    void updateEntity(@MappingTarget ChatChannel channel, ChatChannelUpdateRequest request);

    /**
     * Maps a channel for one reader.
     *
     * @param channel     the channel
     * @param member      true when the reader reads the channel: always for a public channel
     * @param memberCount the member list size for a private channel, the ACTIVE organization members for a public one
     * @return the response
     */
    @Mapping(target = "id", source = "channel.id")
    @Mapping(target = "name", source = "channel.name")
    @Mapping(target = "slug", source = "channel.slug")
    @Mapping(target = "type", source = "channel.type")
    @Mapping(target = "purpose", source = "channel.purpose")
    @Mapping(target = "visibility", source = "channel.visibility")
    @Mapping(target = "member", source = "member")
    @Mapping(target = "memberCount", source = "memberCount")
    @Mapping(target = "createdAt", source = "channel.createdAt")
    ChatChannelResponse toResponse(ChatChannel channel, boolean member, int memberCount);

    /**
     * Trims a channel name; the request already refuses a name without
     * visible characters.
     */
    @Named("trimName")
    default String trimName(String name) {
        return name == null ? null : name.trim();
    }
}
