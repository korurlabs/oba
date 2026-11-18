package com.korur.oba.connect.push.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.dto.expo.ExpoPushMessage;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.mapper.ExpoPushMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the chat copy PushMessageHelper.toMessage builds: the
 * attachment line for a message with files and no text, and the
 * previews-off line that names neither the sender nor the text.
 */
class PushMessageHelperUnitTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RECIPIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID THREAD_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final String TOKEN = "ExponentPushToken[test]";
    private static final String SENDER = "Ayşe Kaya";
    private static final String CHANNEL = "#general";
    private static final String THREAD = "KZN-311 root cause · #general";
    private static final Instant SENT_AT = Instant.parse("2026-11-06T09:00:00Z");

    private static final PushPreferenceResponse PREVIEWS_ON = new PushPreferenceResponse(true, true, true, true, true);
    private static final PushPreferenceResponse PREVIEWS_OFF =
            new PushPreferenceResponse(true, true, true, true, false);

    private final PushMessageHelper pushMessageHelper = new PushMessageHelper(new ExpoPushMapperImpl());

    @Test
    void toMessage_attachmentsOnlyWithPreviews_namesSenderAndChannel() {
        // Arrange
        PushRequest request = chat(PushKind.MENTION, CHANNEL, null, "", true);

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.MENTION, PREVIEWS_ON);

        // Assert
        assertEquals(CHANNEL, message.title());
        assertEquals("Ayşe Kaya sent an attachment in #general", message.body());
    }

    @Test
    void toMessage_attachmentsOnlyInThreadWithPreviews_namesThreadAndChannel() {
        // Arrange
        PushRequest request = chat(PushKind.REPLY, THREAD, THREAD_ID, "", true);

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.REPLY, PREVIEWS_ON);

        // Assert
        assertEquals("Ayşe Kaya sent an attachment in KZN-311 root cause · #general", message.body());
    }

    @Test
    void toMessage_attachmentsOnlyWithoutPreviews_keepsHiddenLine() {
        // Arrange
        PushRequest request = chat(PushKind.REPLY, CHANNEL, null, "", true);

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.REPLY, PREVIEWS_OFF);

        // Assert
        assertEquals("New reply in #general", message.body());
    }

    @Test
    void toMessage_noTextNotAttachmentsOnlyWithPreviews_keepsHiddenLine() {
        // Arrange: a pull request share has no text and is not an attachment
        PushRequest request = chat(PushKind.REVIEW, CHANNEL, null, "", false);

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.REVIEW, PREVIEWS_ON);

        // Assert
        assertEquals("Review requested in #general", message.body());
    }

    @Test
    void toMessage_textWithPreviews_showsSenderAndText() {
        // Arrange
        PushRequest request = chat(PushKind.MENTION, CHANNEL, null, "  Deploy is green  ", false);

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.MENTION, PREVIEWS_ON);

        // Assert
        assertEquals("Ayşe Kaya: Deploy is green", message.body());
    }

    @Test
    void isAllowed_eachKind_obeysItsOwnSwitch() {
        // Arrange
        PushPreferenceResponse allOff = new PushPreferenceResponse(false, false, false, false, true);
        PushPreferenceResponse onlyMentions = new PushPreferenceResponse(true, false, false, false, true);
        PushPreferenceResponse onlyEveryone = new PushPreferenceResponse(false, true, false, false, true);
        PushPreferenceResponse onlyHere = new PushPreferenceResponse(false, false, true, false, true);
        PushPreferenceResponse onlyIncidents = new PushPreferenceResponse(false, false, false, true, true);

        // Assert
        for (PushKind kind : new PushKind[] {PushKind.MENTION, PushKind.REPLY, PushKind.REVIEW, PushKind.NUDGE}) {
            assertTrue(pushMessageHelper.isAllowed(kind, onlyMentions), kind + " follows mentionsAndReplies");
            assertFalse(pushMessageHelper.isAllowed(kind, allOff), kind + " off with mentionsAndReplies off");
        }
        assertTrue(pushMessageHelper.isAllowed(PushKind.EVERYONE, onlyEveryone));
        assertFalse(pushMessageHelper.isAllowed(PushKind.EVERYONE, onlyMentions));
        assertTrue(pushMessageHelper.isAllowed(PushKind.HERE, onlyHere));
        assertFalse(pushMessageHelper.isAllowed(PushKind.HERE, onlyEveryone));
        assertTrue(pushMessageHelper.isAllowed(PushKind.INCIDENT, onlyIncidents));
        assertFalse(pushMessageHelper.isAllowed(PushKind.INCIDENT, onlyHere));
        assertTrue(pushMessageHelper.isAllowed(PushKind.READ, allOff));
    }

    @Test
    void isAllowed_noPreferences_allowsEveryKind() {
        for (PushKind kind : PushKind.values()) {
            assertTrue(pushMessageHelper.isAllowed(kind, null), kind + " allowed without saved switches");
        }
    }

    @Test
    void toMessage_chatKinds_useTheirChannelAndTheChatCategory() {
        // Arrange
        PushRequest request = chat(PushKind.EVERYONE, CHANNEL, null, "hello", false);

        // Act
        ExpoPushMessage everyone = toMessage(request, PushKind.EVERYONE, PREVIEWS_ON);
        ExpoPushMessage mention = toMessage(request, PushKind.MENTION, PREVIEWS_ON);

        // Assert
        assertEquals("everyone_here", everyone.channelId());
        assertEquals("mentions_replies", mention.channelId());
        assertEquals("chat_message", everyone.categoryId());
        assertEquals(PushKind.EVERYONE, everyone.data().type());
    }

    @Test
    void toMessage_everyoneAndHereWithoutPreviews_haveTheirOwnHiddenLines() {
        // Arrange
        PushRequest request = chat(PushKind.EVERYONE, CHANNEL, null, "secret", false);

        // Assert
        assertEquals("New message for everyone in #general",
                toMessage(request, PushKind.EVERYONE, PREVIEWS_OFF).body());
        assertEquals("New message for everyone here in #general",
                toMessage(request, PushKind.HERE, PREVIEWS_OFF).body());
    }

    @Test
    void toMessage_longText_isCutAtOneHundredEightyCodePointsWithAnEllipsis() {
        // Arrange
        PushRequest request = chat(PushKind.MENTION, CHANNEL, null, "\uD83D\uDE00".repeat(300), false);

        // Act
        String body = toMessage(request, PushKind.MENTION, PREVIEWS_ON).body();

        // Assert
        assertEquals(180, body.codePointCount(0, body.length()));
        assertTrue(body.endsWith("\u2026"));
    }

    @Test
    void toMessage_incident_isTimeSensitiveOnTheUrgentChannelAndIgnoresPreviews() {
        // Arrange
        PushRequest request = PushRequest.incident(ORGANIZATION_ID, java.util.List.of(RECIPIENT_ID), AUTHOR_ID,
                "Kaizen Platform", "P1", "KZN-311", "Payment webhooks return 502");

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.INCIDENT, PREVIEWS_OFF);

        // Assert
        assertEquals("P1 incident on Kaizen Platform", message.title());
        assertEquals("KZN-311 Payment webhooks return 502", message.body());
        assertEquals("urgent_incidents", message.channelId());
        assertEquals("time-sensitive", message.interruptionLevel());
        assertNull(message.categoryId());
        assertEquals("KZN-311", message.data().ticketKey());
    }

    @Test
    void toMessage_nudge_namesTheSenderAndTheDeadline() {
        // Arrange
        PushRequest request = PushRequest.nudge(ORGANIZATION_ID, RECIPIENT_ID, AUTHOR_ID, SENDER, "10:30");

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.NUDGE, PREVIEWS_OFF);

        // Assert
        assertEquals("Daily update", message.title());
        assertEquals("Ayşe Kaya reminds you to post today's update. Deadline 10:30.", message.body());
        assertEquals("mentions_replies", message.channelId());
        assertNull(message.interruptionLevel());
    }

    @Test
    void toMessage_read_isDataOnlyWithNoTextOrSound() {
        // Arrange
        PushRequest request = PushRequest.read(ORGANIZATION_ID, RECIPIENT_ID, CHANNEL_ID, THREAD_ID);

        // Act
        ExpoPushMessage message = toMessage(request, PushKind.READ, null);

        // Assert
        assertNull(message.title());
        assertNull(message.body());
        assertNull(message.sound());
        assertEquals(PushKind.READ, message.data().type());
        assertEquals(THREAD_ID, message.data().threadId());
    }

    @Test
    void toMessage_badgeForTheRecipient_isSentAndOthersGetNone() {
        // Arrange
        PushRequest request = PushRequest.chat(ORGANIZATION_ID, Map.of(RECIPIENT_ID, PushKind.MENTION), AUTHOR_ID,
                CHANNEL, SENDER, "hi", false, CHANNEL_ID, null, MESSAGE_ID, Map.of(RECIPIENT_ID, 4));

        // Act and assert
        assertEquals(4, toMessage(request, PushKind.MENTION, PREVIEWS_ON).badge());
        assertNull(pushMessageHelper.toMessage(request, AUTHOR_ID, PushKind.MENTION, PREVIEWS_ON, TOKEN, SENT_AT)
                .badge());
    }

    private static PushRequest chat(PushKind kind, String conversation, UUID threadId, String text,
                                    boolean attachmentsOnly) {
        return PushRequest.chat(ORGANIZATION_ID, Map.of(RECIPIENT_ID, kind), AUTHOR_ID, conversation, SENDER, text,
                attachmentsOnly, CHANNEL_ID, threadId, MESSAGE_ID, null);
    }

    private ExpoPushMessage toMessage(PushRequest request, PushKind kind, PushPreferenceResponse preferences) {
        return pushMessageHelper.toMessage(request, RECIPIENT_ID, kind, preferences, TOKEN, SENT_AT);
    }
}
