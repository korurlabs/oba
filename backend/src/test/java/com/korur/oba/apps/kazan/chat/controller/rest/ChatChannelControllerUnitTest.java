package com.korur.oba.apps.kazan.chat.controller.rest;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelMembersRequest;
import com.korur.oba.apps.kazan.chat.dto.request.ChatChannelUpdateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatChannelResponse;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelPurpose;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelType;
import com.korur.oba.apps.kazan.chat.enums.ChatChannelVisibility;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kazan.chat.service.ChatChannelService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class ChatChannelControllerUnitTest {

    @Mock
    private ChatChannelService chatChannelService;
    @Mock
    private ChatChannelQueryService chatChannelQueryService;

    private ChatChannelController controller;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final User user = ChatMocks.user("Ada", "Lovelace");
    private final ChatChannelResponse channelResponse = new ChatChannelResponse(channelId, "release",
            "release", ChatChannelType.TEXT, ChatChannelPurpose.GENERAL, ChatChannelVisibility.PUBLIC, true, 3,
            Instant.parse("2026-10-20T08:15:00Z"));

    @BeforeEach
    void setUp() {
        controller = new ChatChannelController(chatChannelService, chatChannelQueryService, new LoggingService());
    }

    @Test
    void listChannels_returnsTheQueryServiceListWith200() {
        // Arrange
        when(chatChannelQueryService.listChannels(user, organizationId)).thenReturn(List.of(channelResponse));

        // Act
        ResponseEntity<CustomResponse<List<ChatChannelResponse>>> response =
                controller.listChannels(user, organizationId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsExactly(channelResponse);
    }

    @Test
    void createChannel_returns201WithTheCreatedChannel() {
        // Arrange
        ChatChannelCreateRequest request = new ChatChannelCreateRequest("release", ChatChannelVisibility.PUBLIC,
                ChatChannelPurpose.GENERAL, null);
        when(chatChannelService.createChannel(user, organizationId, request)).thenReturn(channelResponse);

        // Act
        ResponseEntity<CustomResponse<ChatChannelResponse>> response =
                controller.createChannel(user, organizationId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data()).isEqualTo(channelResponse);
    }

    @Test
    void updateChannel_returns200WithTheUpdatedChannel() {
        // Arrange
        ChatChannelUpdateRequest request = new ChatChannelUpdateRequest("release", ChatChannelVisibility.PUBLIC,
                ChatChannelPurpose.GENERAL, null);
        when(chatChannelService.updateChannel(user, organizationId, channelId, request)).thenReturn(channelResponse);

        // Act
        ResponseEntity<CustomResponse<ChatChannelResponse>> response =
                controller.updateChannel(user, organizationId, channelId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEqualTo(channelResponse);
    }

    @Test
    void deleteChannel_delegatesAndReturns200() {
        // Act
        ResponseEntity<CustomResponse<Void>> response = controller.deleteChannel(user, organizationId, channelId);

        // Assert
        verify(chatChannelService).deleteChannel(user, organizationId, channelId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void addMembers_returns200WithTheChannel() {
        // Arrange
        ChatChannelMembersRequest request = new ChatChannelMembersRequest(List.of(UUID.randomUUID()));
        when(chatChannelService.addMembers(user, organizationId, channelId, request)).thenReturn(channelResponse);

        // Act
        ResponseEntity<CustomResponse<ChatChannelResponse>> response =
                controller.addMembers(user, organizationId, channelId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEqualTo(channelResponse);
    }

    @Test
    void removeMember_delegatesAndReturns200() {
        // Arrange
        UUID memberId = UUID.randomUUID();

        // Act
        ResponseEntity<CustomResponse<Void>> response =
                controller.removeMember(user, organizationId, channelId, memberId);

        // Assert
        verify(chatChannelService).removeMember(user, organizationId, channelId, memberId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void serviceRefusal_propagatesToTheExceptionHandler() {
        // Arrange
        when(chatChannelQueryService.listChannels(user, organizationId))
                .thenThrow(new NotFoundException(ExceptionDomainKazanError.NOT_CHAT_CHANNEL_FOUND));

        // Act and Assert
        assertThatThrownBy(() -> controller.listChannels(user, organizationId))
                .isInstanceOf(NotFoundException.class);
    }
}
