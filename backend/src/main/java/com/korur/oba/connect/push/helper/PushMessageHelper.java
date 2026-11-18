package com.korur.oba.connect.push.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.dto.expo.ExpoPushData;
import com.korur.oba.connect.push.dto.expo.ExpoPushMessage;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.enums.PushKind;
import com.korur.oba.connect.push.mapper.ExpoPushMapper;

/**
 * Decides what a push looks like for one kind of recipient: whether the recipient's switches allow
 * it, its title and body, the Android channel, the notification category and the iOS interruption
 * level.
 *
 * - Chat kinds (MENTION, REPLY, EVERYONE, HERE, REVIEW): the conversation as title, "Sender: text"
 *   as body, "Sender sent an attachment in #general" for a message with files and no text, or a line
 *   without the sender and the text when previews are off or the message has no text; category
 *   chat_message, so the notification offers Reply and Mark as read.
 * - INCIDENT: "P1 incident on Board", "KZN-311 Title", time-sensitive so it passes a Focus mode.
 * - NUDGE: "Daily update", "Sender reminds you to post today's update. Deadline 10:30."
 * - READ: data only, nothing shown.
 *
 * Previews apply to chat kinds only: they keep message text out of Apple's and Google's services,
 * and the other kinds carry no message text.
 */
@Component
@RequiredArgsConstructor
public class PushMessageHelper {

    private final ExpoPushMapper expoPushMapper;

    /**
     * Tells whether the recipient's switches let this kind of push through. READ always passes, as
     * it only withdraws notifications. Missing preferences count as every switch on.
     *
     * @param kind        the kind of push
     * @param preferences the recipient's switches, or null
     * @return true when the push is sent
     */
    public boolean isAllowed(PushKind kind, PushPreferenceResponse preferences) {
        if (preferences == null) {
            return true;
        }
        return switch (kind) {
            case MENTION, REPLY, REVIEW, NUDGE -> isOn(preferences.mentionsAndReplies());
            case EVERYONE -> isOn(preferences.everyone());
            case HERE -> isOn(preferences.here());
            case INCIDENT -> isOn(preferences.urgentIncidents());
            case READ -> true;
        };
    }

    /**
     * Builds the message for one device of one recipient.
     *
     * @param request     the dispatched request
     * @param userId      the recipient
     * @param kind        the kind of push the recipient gets
     * @param preferences the recipient's switches, or null for every switch on
     * @param token       the device's Expo push token
     * @param sentAt      when the push was built
     * @return the message
     */
    public ExpoPushMessage toMessage(PushRequest request, UUID userId, PushKind kind,
                                     PushPreferenceResponse preferences, String token, Instant sentAt) {
        ExpoPushData data = expoPushMapper.toData(request, kind, sentAt);
        if (kind == PushKind.READ) {
            return expoPushMapper.toDataOnlyMessage(token, data);
        }
        boolean showPreviews = preferences == null || isOn(preferences.showPreviews());
        Integer badge = request.badges() == null ? null : request.badges().get(userId);
        return expoPushMapper.toNotificationMessage(token, title(request, kind), body(request, kind, showPreviews),
                data, channelId(kind), categoryId(kind), interruptionLevel(kind), badge);
    }

    private String title(PushRequest request, PushKind kind) {
        return switch (kind) {
            case INCIDENT -> PushConstant.TITLE_INCIDENT_FORMAT.formatted(request.priority(), request.conversation());
            case NUDGE -> PushConstant.TITLE_NUDGE;
            default -> request.conversation();
        };
    }

    private String body(PushRequest request, PushKind kind, boolean showPreviews) {
        return switch (kind) {
            case INCIDENT -> cut(PushConstant.BODY_INCIDENT_FORMAT.formatted(request.ticketKey(), request.text()));
            case NUDGE -> PushConstant.BODY_NUDGE_FORMAT.formatted(request.senderName(), request.text());
            default -> chatBody(request, kind, showPreviews);
        };
    }

    /**
     * The body of a chat push. With previews off it names neither the sender nor the text, so an
     * attachment-only message keeps the same line as any other.
     */
    private String chatBody(PushRequest request, PushKind kind, boolean showPreviews) {
        if (showPreviews && StringUtils.hasText(request.text())) {
            return cut(PushConstant.BODY_CHAT_FORMAT.formatted(request.senderName(), request.text().strip()));
        }
        if (showPreviews && request.attachmentsOnly()) {
            return cut(PushConstant.BODY_ATTACHMENT_FORMAT.formatted(request.senderName(), request.conversation()));
        }
        return hiddenBody(kind).formatted(request.conversation());
    }

    private String hiddenBody(PushKind kind) {
        return switch (kind) {
            case MENTION -> PushConstant.BODY_MENTION_HIDDEN_FORMAT;
            case REPLY -> PushConstant.BODY_REPLY_HIDDEN_FORMAT;
            case EVERYONE -> PushConstant.BODY_EVERYONE_HIDDEN_FORMAT;
            case HERE -> PushConstant.BODY_HERE_HIDDEN_FORMAT;
            default -> PushConstant.BODY_REVIEW_HIDDEN_FORMAT;
        };
    }

    private String channelId(PushKind kind) {
        return switch (kind) {
            case EVERYONE, HERE -> PushConstant.ANDROID_CHANNEL_EVERYONE_HERE;
            case INCIDENT -> PushConstant.ANDROID_CHANNEL_URGENT_INCIDENTS;
            default -> PushConstant.ANDROID_CHANNEL_MENTIONS_REPLIES;
        };
    }

    private String categoryId(PushKind kind) {
        return switch (kind) {
            case MENTION, REPLY, EVERYONE, HERE, REVIEW -> PushConstant.CATEGORY_CHAT_MESSAGE;
            default -> null;
        };
    }

    private String interruptionLevel(PushKind kind) {
        return kind == PushKind.INCIDENT ? PushConstant.INTERRUPTION_LEVEL_TIME_SENSITIVE : null;
    }

    /**
     * Cuts a body to the maximum length in code points, so an emoji is never split in half.
     */
    private String cut(String body) {
        if (body.codePointCount(0, body.length()) <= PushConstant.BODY_MAX_CODE_POINTS) {
            return body;
        }
        int end = body.offsetByCodePoints(0, PushConstant.BODY_MAX_CODE_POINTS - 1);
        return body.substring(0, end).stripTrailing() + PushConstant.BODY_ELLIPSIS;
    }

    private boolean isOn(Boolean value) {
        return !Boolean.FALSE.equals(value);
    }
}
