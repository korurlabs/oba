package com.korur.oba.connect.realtime.dto;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;

// 4. Internal

/**
 * A one-time ticket that opens one realtime socket: the client passes it as the ticket query
 * parameter of the WebSocket handshake, because a browser cannot send an Authorization header
 * there.
 *
 * @param ticket    the opaque ticket value, redeemable once
 * @param expiresAt the instant after which the handshake refuses the ticket
 */
public record RealtimeTicketResponse(
        @Schema(description = "Opaque one-time ticket for GET /ws?ticket=")
        String ticket,

        @Schema(description = "Instant after which the ticket no longer opens a socket")
        Instant expiresAt) {
}
