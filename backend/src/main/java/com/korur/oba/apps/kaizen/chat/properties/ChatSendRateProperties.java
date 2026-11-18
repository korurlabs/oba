package com.korur.oba.apps.kaizen.chat.properties;

// 1. Framework-level
import org.springframework.boot.context.properties.ConfigurationProperties;

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;

/**
 * Configuration properties for the chat send rate.
 * Bound from application.properties with prefix "oba.chat.send-rate".
 *
 * The defaults are the production values, so an environment that sets
 * nothing still stops a client that floods the channel.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = ChatConstant.SEND_RATE_PROPERTIES_PREFIX)
public class ChatSendRateProperties {

    /** Most messages one user may send within one window. */
    private int max = 5;

    /** Length of the sliding window, in milliseconds. */
    private long windowMs = 5_000L;
}
