package com.korur.oba.connect.push.dto.expo;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * Expo's final word on one ticket: "ok" once Apple or Google accepted the push, or "error" with the
 * reason it was not delivered.
 *
 * @param status  "ok" or "error"
 * @param message Expo's description of the error, set when the status is "error"
 * @param details the error code, set when the status is "error"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushReceipt(
        @JsonProperty("status") String status,
        @JsonProperty("message") String message,
        @JsonProperty("details") ExpoPushErrorDetails details
) {

    /**
     * Leaves Expo's message out of every log line that prints this record, because for some errors it
     * quotes the push token.
     */
    @Override
    public String toString() {
        return "ExpoPushReceipt[status=" + status + ", error=" + (details == null ? null : details.error()) + "]";
    }
}
