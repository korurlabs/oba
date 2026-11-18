package com.korur.oba.connect.push.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.time.Instant;

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.dto.PushRequest;
import com.korur.oba.connect.push.dto.expo.ExpoPushData;
import com.korur.oba.connect.push.dto.expo.ExpoPushMessage;
import com.korur.oba.connect.push.enums.PushKind;

/**
 * Builds the messages sent to the Expo Push Service. The copy itself is decided by
 * {@link com.korur.oba.connect.push.helper.PushMessageHelper}; this mapper only assembles it.
 */
@Mapper(componentModel = "spring")
public interface ExpoPushMapper {

    /**
     * Builds the routing data of a push for one kind of recipient.
     *
     * @param request the dispatched request
     * @param type    the kind of push the recipient gets
     * @param sentAt  when the push was built
     * @return the data object
     */
    @Mapping(target = "type", source = "type")
    @Mapping(target = "organizationId", source = "request.organizationId")
    @Mapping(target = "channelId", source = "request.channelId")
    @Mapping(target = "threadId", source = "request.threadId")
    @Mapping(target = "messageId", source = "request.messageId")
    @Mapping(target = "ticketKey", source = "request.ticketKey")
    @Mapping(target = "conversation", source = "request.conversation")
    @Mapping(target = "sentAt", source = "sentAt")
    ExpoPushData toData(PushRequest request, PushKind type, Instant sentAt);

    /**
     * Builds a visible notification with sound, at high priority.
     *
     * @param to                the device's Expo push token
     * @param title             the title
     * @param body              the body
     * @param data              the routing data
     * @param channelId         the Android channel
     * @param categoryId        the category with actions, or null
     * @param interruptionLevel the iOS interruption level, or null for the default
     * @param badge             the app badge, or null to leave it
     * @return the message
     */
    @Mapping(target = "to", source = "to")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "body", source = "body")
    @Mapping(target = "data", source = "data")
    @Mapping(target = "sound", constant = PushConstant.SOUND_DEFAULT)
    @Mapping(target = "priority", constant = PushConstant.PRIORITY_HIGH)
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "interruptionLevel", source = "interruptionLevel")
    @Mapping(target = "badge", source = "badge")
    @Mapping(target = "contentAvailable", ignore = true)
    ExpoPushMessage toNotificationMessage(String to, String title, String body, ExpoPushData data, String channelId,
                                          String categoryId, String interruptionLevel, Integer badge);

    /**
     * Builds a data-only push that wakes the app without showing anything.
     *
     * @param to   the device's Expo push token
     * @param data the routing data
     * @return the message
     */
    @Mapping(target = "to", source = "to")
    @Mapping(target = "data", source = "data")
    @Mapping(target = "priority", constant = PushConstant.PRIORITY_NORMAL)
    @Mapping(target = "contentAvailable", constant = "true")
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "body", ignore = true)
    @Mapping(target = "sound", ignore = true)
    @Mapping(target = "channelId", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "interruptionLevel", ignore = true)
    @Mapping(target = "badge", ignore = true)
    ExpoPushMessage toDataOnlyMessage(String to, ExpoPushData data);
}
