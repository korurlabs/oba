package com.korur.oba.apps.kazan.chat.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.service.ChatChannelQueryService;
import com.korur.oba.connect.realtime.dto.RealtimePrincipal;
import com.korur.oba.connect.realtime.util.RealtimeDestinationUtil;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;

@ExtendWith(MockitoExtension.class)
class ChatRealtimeDestinationAuthorizerUnitTest {

    @Mock
    private ChatChannelQueryService chatChannelQueryService;

    @Mock
    private AppAccessQueryService appAccessQueryService;

    private ChatRealtimeDestinationAuthorizer authorizer;
    private final UUID organizationId = UUID.randomUUID();
    private final UUID channelId = UUID.randomUUID();
    private final RealtimePrincipal principal =
            new RealtimePrincipal(UUID.randomUUID(), organizationId, Instant.now().plusSeconds(300));

    @BeforeEach
    void setUp() {
        authorizer = new ChatRealtimeDestinationAuthorizer(chatChannelQueryService, appAccessQueryService);
        lenient().when(appAccessQueryService.hasAppAccess(organizationId, principal.userId(), App.KAZAN))
                .thenReturn(true);
    }

    private String channelTopic(UUID organization, String rawChannelId) {
        return RealtimeDestinationUtil.organizationTopic(organization,
                ChatConstant.CHAT_CHANNEL_TOPIC_SUFFIX_PREFIX + rawChannelId);
    }

    @Test
    void chatDestinations_keepTheirWireValues() {
        assertThat(RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX))
                .isEqualTo("/topic/organizations/" + organizationId + "/chat");
        assertThat(channelTopic(organizationId, channelId.toString()))
                .isEqualTo("/topic/organizations/" + organizationId + "/chat/channels/" + channelId);
        assertThat(ChatConstant.CHAT_USER_QUEUE).isEqualTo("/queue/chat");
        assertThat(ChatConstant.CHAT_USER_QUEUE_SUBSCRIPTION).isEqualTo("/user/queue/chat");
        assertThat(ChatConstant.CHAT_TYPING_DESTINATION).isEqualTo("/app/chat/typing");
    }

    @Test
    void supportsSubscribe_chatTopicAndUserQueue_areClaimedAndAllowedWithoutQuery() {
        String chatTopic = RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);

        assertThat(authorizer.supportsSubscribe(chatTopic)).isTrue();
        assertThat(authorizer.supportsSubscribe(ChatConstant.CHAT_USER_QUEUE_SUBSCRIPTION)).isTrue();
        assertThat(authorizer.authorizeSubscribe(principal, chatTopic)).isTrue();
        assertThat(authorizer.authorizeSubscribe(principal, ChatConstant.CHAT_USER_QUEUE_SUBSCRIPTION)).isTrue();
        verifyNoInteractions(chatChannelQueryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/organizations/not-an-id/chat", "/user/queue/chat-extra", "/queue/chat",
            "/topic/chat", "/app/chat/typing", "/topic/organizations/not-an-id/chat/channels/x"})
    void supportsSubscribe_otherDestinations_areNotClaimed(String destination) {
        assertThat(authorizer.supportsSubscribe(destination)).isFalse();
    }

    @Test
    void supportsSubscribe_deeperPathUnderTheChatTopic_isNotClaimed() {
        String deeper = RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX) + "/x";

        assertThat(authorizer.supportsSubscribe(deeper)).isFalse();
    }

    @Test
    void channelTopic_readableChannel_isAllowed() {
        // Arrange
        String topic = channelTopic(organizationId, channelId.toString());
        when(chatChannelQueryService.canRead(organizationId, principal.userId(), channelId)).thenReturn(true);

        // Act and Assert
        assertThat(authorizer.supportsSubscribe(topic)).isTrue();
        assertThat(authorizer.authorizeSubscribe(principal, topic)).isTrue();
    }

    @Test
    void channelTopic_channelTheUserCannotRead_isRefused() {
        // Arrange
        String topic = channelTopic(organizationId, channelId.toString());
        when(chatChannelQueryService.canRead(organizationId, principal.userId(), channelId)).thenReturn(false);

        // Act and Assert
        assertThat(authorizer.authorizeSubscribe(principal, topic)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "", "ABCDEF00-0000-0000-0000-000000000000", "1-2-3-4-5"})
    void channelTopic_nonCanonicalChannelId_isClaimedAndRefusedWithoutQuery(String rawChannelId) {
        String topic = channelTopic(organizationId, rawChannelId);

        assertThat(authorizer.supportsSubscribe(topic)).isTrue();
        assertThat(authorizer.authorizeSubscribe(principal, topic)).isFalse();
        verifyNoInteractions(chatChannelQueryService);
    }

    @Test
    void channelTopic_deeperPathAfterTheChannelId_isRefused() {
        String topic = channelTopic(organizationId, channelId + "/x");

        assertThat(authorizer.authorizeSubscribe(principal, topic)).isFalse();
        verifyNoInteractions(chatChannelQueryService);
    }

    @Test
    void channelTopic_ofAnotherOrganization_isRefusedWithoutQuery() {
        String topic = channelTopic(UUID.randomUUID(), channelId.toString());

        assertThat(authorizer.authorizeSubscribe(principal, topic)).isFalse();
        verifyNoInteractions(chatChannelQueryService);
    }

    @Test
    void supportsSend_onlyTheTypingDestination() {
        assertThat(authorizer.supportsSend(ChatConstant.CHAT_TYPING_DESTINATION)).isTrue();
        assertThat(authorizer.supportsSend("/app/chat/messages")).isFalse();
        assertThat(authorizer.authorizeSend(principal, ChatConstant.CHAT_TYPING_DESTINATION)).isTrue();
    }

    @Test
    void everyChatDestination_userWithoutKazan_isRefusedBeforeTheChannelQuery() {
        // Arrange
        when(appAccessQueryService.hasAppAccess(organizationId, principal.userId(), App.KAZAN)).thenReturn(false);
        String chatTopic = RealtimeDestinationUtil.organizationTopic(organizationId, ChatConstant.CHAT_TOPIC_SUFFIX);
        String topic = channelTopic(organizationId, channelId.toString());

        // Act and Assert
        assertThat(authorizer.authorizeSubscribe(principal, chatTopic)).isFalse();
        assertThat(authorizer.authorizeSubscribe(principal, ChatConstant.CHAT_USER_QUEUE_SUBSCRIPTION)).isFalse();
        assertThat(authorizer.authorizeSubscribe(principal, topic)).isFalse();
        assertThat(authorizer.authorizeSend(principal, ChatConstant.CHAT_TYPING_DESTINATION)).isFalse();
        verifyNoInteractions(chatChannelQueryService);
    }
}
