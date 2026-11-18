package com.korur.oba.connect.push.dto.expo;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * One message of a POST /--/api/v2/push/send request to Expo, addressed to one device. Fields that
 * are null are left out, which is how a READ push carries only data.
 *
 * @param to                the device's Expo push token; never logged
 * @param title             the notification title, null for a data-only push
 * @param body              the notification body, null for a data-only push
 * @param data              the routing data the app reads
 * @param sound             "default", null for a data-only push
 * @param priority          "high", or "normal" for a data-only push
 * @param channelId         the Android notification channel
 * @param categoryId        the notification category with its actions, chat pushes only
 * @param interruptionLevel the iOS interruption level, "time-sensitive" for incidents only
 * @param badge             the iOS app badge, null to leave it unchanged
 * @param contentAvailable  true to wake the iOS app in the background, for a data-only push
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpoPushMessage(
        @JsonProperty("to") String to,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("data") ExpoPushData data,
        @JsonProperty("sound") String sound,
        @JsonProperty("priority") String priority,
        @JsonProperty("channelId") String channelId,
        @JsonProperty("categoryId") String categoryId,
        @JsonProperty("interruptionLevel") String interruptionLevel,
        @JsonProperty("badge") Integer badge,
        @JsonProperty("_contentAvailable") Boolean contentAvailable
) {

    /**
     * Keeps the token and the copy out of every log line and exception message that prints this record.
     */
    @Override
    public String toString() {
        return "ExpoPushMessage[type=" + (data == null ? null : data.type()) + "]";
    }
}
