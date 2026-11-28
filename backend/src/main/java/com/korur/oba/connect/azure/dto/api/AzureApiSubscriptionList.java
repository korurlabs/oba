package com.korur.oba.connect.azure.dto.api;

// 1. Framework-level

// 2. Third-party
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * One page of GET /subscriptions.
 *
 * @param value    the subscriptions of this page
 * @param nextLink the absolute link of the next page, null on the last one
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AzureApiSubscriptionList(
        @JsonProperty("value") List<AzureApiSubscription> value,
        @JsonProperty("nextLink") String nextLink
) {
}
