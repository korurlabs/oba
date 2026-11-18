package com.korur.oba.apps.kaya.delivery.dto.derived;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.delivery.dto.response.DeliveryCurrentResponse;

/**
 * The current release with the ETag that stands for it, so the controller can answer 304 to a
 * CLI that already holds the same release.
 *
 * @param response the response body
 * @param etag     the unquoted ETag value
 */
public record DeliveryCurrent(DeliveryCurrentResponse response, String etag) {
}
