package com.korur.oba.connect.push.properties;

// 1. Framework-level
import org.springframework.util.StringUtils;

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK

// 4. Internal

/**
 * Configuration of the Expo Push Service, bound from the prefix "oba.integrations.expo".
 *
 * The token defaults to empty so the application starts without push: while it is blank no push is
 * sent and no Expo call is made.
 */
@Getter
@Setter
public class ExpoProperties {

    // Access token of the Expo project; sent as a bearer token, never logged. No toString on purpose.
    private String accessToken;

    /**
     * Tells whether push sending is on.
     *
     * @return true when the access token is set
     */
    public boolean isConfigured() {
        return StringUtils.hasText(accessToken);
    }
}
