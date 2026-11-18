package com.korur.oba.apps.kazan.chat.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kazan.chat.entity.ChatChannel;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelType;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;

class ChatChannelMapperUnitTest {

    private final ChatChannelMapper mapper = new ChatChannelMapperImpl();

    @Test
    void toEntity_createRequest_trimsTheNameAndKeepsTheTextType() {
        // Arrange
        ChatChannelCreateRequest request = new ChatChannelCreateRequest("  release  ", ChatChannelVisibility.PRIVATE,
                ChatChannelPurpose.GENERAL, List.of(UUID.randomUUID()));

        // Act
        ChatChannel channel = mapper.toEntity(request);

        // Assert
        assertThat(channel.getName()).isEqualTo("release");
        assertThat(channel.getType()).isEqualTo(ChatChannelType.TEXT);
        assertThat(channel.getVisibility()).isEqualTo(ChatChannelVisibility.PRIVATE);
        assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.GENERAL);
        assertThat(channel.getOrganizationId()).isNull();
        assertThat(channel.getCreatedBy()).isNull();
    }

    @Test
    void updateEntity_fullUpdate_replacesNameVisibilityAndPurposeOnly() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        ChatChannel channel = ChatChannel.builder()
                .name("General")
                .organizationId(organizationId)
                .createdBy(creatorId)
                .visibility(ChatChannelVisibility.PUBLIC)
                .purpose(ChatChannelPurpose.GENERAL)
                .build();
        ChatChannelUpdateRequest request = new ChatChannelUpdateRequest(" Updates ", ChatChannelVisibility.PUBLIC,
                ChatChannelPurpose.DAILY_UPDATES, null);

        // Act
        mapper.updateEntity(channel, request);

        // Assert
        assertThat(channel.getName()).isEqualTo("Updates");
        assertThat(channel.getPurpose()).isEqualTo(ChatChannelPurpose.DAILY_UPDATES);
        assertThat(channel.getType()).isEqualTo(ChatChannelType.TEXT);
        assertThat(channel.getOrganizationId()).isEqualTo(organizationId);
        assertThat(channel.getCreatedBy()).isEqualTo(creatorId);
    }

    @Test
    void toResponse_channelWithReaderParts_mapsEveryField() {
        // Arrange
        ChatChannel channel = ChatChannel.builder()
                .name("Release")
                .slug("release")
                .visibility(ChatChannelVisibility.PRIVATE)
                .purpose(ChatChannelPurpose.GENERAL)
                .build();
        channel.setId(UUID.randomUUID());
        channel.setCreatedAt(Instant.parse("2026-10-20T08:15:00Z"));

        // Act
        ChatChannelResponse response = mapper.toResponse(channel, false, 5);

        // Assert
        assertThat(response).isEqualTo(new ChatChannelResponse(channel.getId(), "Release", "release", ChatChannelType.TEXT,
                ChatChannelPurpose.GENERAL, ChatChannelVisibility.PRIVATE, false, 5, channel.getCreatedAt()));
    }
}
