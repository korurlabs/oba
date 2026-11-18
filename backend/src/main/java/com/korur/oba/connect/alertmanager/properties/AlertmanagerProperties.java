package com.korur.oba.connect.alertmanager.properties;

// 1. Framework-level
import org.springframework.validation.annotation.Validated;

// 2. Third-party
import lombok.Getter;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Configuration of the Alertmanager webhook, bound from the prefix
 * "oba.integrations.alertmanager".
 *
 * The token may be unset so the application still starts without the
 * integration: a blank token refuses every webhook call. A set token must name
 * the organization its alerts belong to, so the organization is configuration
 * bound to the token, never read from a notification.
 */
@Validated
@Getter
@Setter
public class AlertmanagerProperties {

    // Bearer token Alertmanager sends; compared in constant time, never logged.
    private String token;

    // The only organization whose alert routes and boards the token's notifications reach.
    private UUID organizationId;
}
