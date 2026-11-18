package com.korur.oba.support;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

// 4. Internal
import com.korur.oba.apps.kaya.machine.constant.MachineConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestJwtDecoderConfigurationUnitTest {

    private final JwtDecoder decoder = new TestJwtDecoderConfiguration().jwtDecoder();

    @Test
    void decode_tokenOfAUser_returnsTheUsersJwt() {
        Jwt jwt = decoder.decode(TestJwtDecoderConfiguration.tokenOf("kc-ada", "ada@example.com"));

        assertEquals("kc-ada", jwt.getSubject());
        assertEquals("ada@example.com", jwt.getClaimAsString("email"));
    }

    @Test
    void decode_machineTokenWhoseBytesHoldOneBar_throwsBadJwt() {
        // A machine token is itself URL-safe base64; with exactly one bar in its decoded bytes it
        // used to pass as keycloakId|email, so the user chain answered 403 instead of 401.
        byte[] bytes = new byte[MachineConstant.TOKEN_RANDOM_BYTES];
        Arrays.fill(bytes, (byte) 'a');
        bytes[10] = (byte) '|';
        String machineToken = MachineConstant.TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertEquals(2, new String(Base64.getUrlDecoder().decode(machineToken), StandardCharsets.UTF_8).split("\\|").length,
                "the token must hold the shape the decoder used to accept");

        assertThrows(BadJwtException.class, () -> decoder.decode(machineToken));
    }
}
