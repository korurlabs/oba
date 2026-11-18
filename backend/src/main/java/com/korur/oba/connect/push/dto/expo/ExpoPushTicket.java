package com.korur.oba.connect.push.dto.expo;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * Expo's answer to one sent message, in the order of the request: "ok" with the id of the receipt to
 * read later, or "error" with the reason Expo refused it.
 *
 * @param status  "ok" or "error"
 * @param id      the ticket id, set when the status is "ok"
 * @param message Expo's description of the error, set when the status is "error"
 * @param details the error code, set when the status is "error"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushTicket(
        @JsonProperty("status") String status,
        @JsonProperty("id") String id,
        @JsonProperty("message") String message,
        @JsonProperty("details") ExpoPushErrorDetails details
) {

    /**
     * Leaves Expo's message out of every log line that prints this record, because for some errors it
     * quotes the push token.
     */
    @Override
    public String toString() {
        return "ExpoPushTicket[status=" + status + ", id=" + id + ", error=" + (details == null ? null : details.error()) + "]";
    }
}
