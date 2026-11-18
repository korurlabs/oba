package com.korur.oba.connect.push.dto.expo;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK

// 4. Internal

/**
 * The "details" of a ticket or receipt in status "error". Only the error code is read; the token Expo
 * may echo is ignored, since the dispatcher already knows which device each ticket belongs to.
 *
 * @param error the error code, "DeviceNotRegistered" when the token will never be delivered to again
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushErrorDetails(
        @JsonProperty("error") String error
) {
}
