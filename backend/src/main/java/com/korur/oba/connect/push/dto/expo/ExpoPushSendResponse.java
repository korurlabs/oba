package com.korur.oba.connect.push.dto.expo;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * Expo's answer to POST /--/api/v2/push/send.
 *
 * @param data one ticket per sent message, in the order of the request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushSendResponse(
        @JsonProperty("data") List<ExpoPushTicket> data
) {
}
