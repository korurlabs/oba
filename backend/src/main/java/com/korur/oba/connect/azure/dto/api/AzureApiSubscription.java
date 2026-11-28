package com.korur.oba.connect.azure.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One subscription as GET /subscriptions returns it. Tags, policies and managing tenants are left
 * out on purpose: a connection shows only which subscriptions its identity can read.
 *
 * @param subscriptionId the subscription id
 * @param displayName    the name the subscription's owner gave it
 * @param state          Azure's state, such as Enabled, Warned, PastDue, Disabled or Deleted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzureApiSubscription(
        @JsonProperty("subscriptionId") UUID subscriptionId,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("state") String state
) {
}
