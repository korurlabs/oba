package com.korur.oba.dojo.security.token.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenHelperUnitTest {

    private final ApiTokenHelper helper = new ApiTokenHelper();

    @ParameterizedTest
    @EnumSource(ApiTokenKind.class)
    void generate_eachKind_hasItsPrefixAnd43Base64UrlCharacters(ApiTokenKind kind) {
        String token = helper.generate(kind);

        assertTrue(token.startsWith(kind.getPrefix()));
        assertEquals(kind.getPrefix().length() + 43, token.length());
        assertTrue(helper.hasTokenFormat(token, kind));
    }

    @Test
    void generate_twice_drawsDifferentTokens() {
        assertNotEquals(helper.generate(ApiTokenKind.AGENT), helper.generate(ApiTokenKind.AGENT));
    }

    @Test
    void hash_sameToken_givesTheSame64LowerCaseHexCharacters() {
        String token = helper.generate(ApiTokenKind.AGENT);

        String hash = helper.hash(token);

        assertEquals(hash, helper.hash(token));
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void hash_knownValue_isSha256Hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", helper.hash("abc"));
    }

    @Test
    void displayPrefix_isTheFirst12Characters() {
        String token = helper.generate(ApiTokenKind.KAYA_MACHINE);

        assertEquals(token.substring(0, 12), helper.displayPrefix(token));
    }

    @Test
    void hasTokenFormat_refusesNullWrongKindWrongLengthAndForeignCharacters() {
        String agentToken = helper.generate(ApiTokenKind.AGENT);
        String body = agentToken.substring(ApiTokenKind.AGENT.getPrefix().length());

        assertFalse(helper.hasTokenFormat(null, ApiTokenKind.AGENT));
        assertFalse(helper.hasTokenFormat(agentToken, ApiTokenKind.KAYA_MACHINE));
        assertFalse(helper.hasTokenFormat(agentToken + "A", ApiTokenKind.AGENT));
        assertFalse(helper.hasTokenFormat(agentToken.substring(1), ApiTokenKind.AGENT));
        assertFalse(helper.hasTokenFormat("oat_" + body.substring(1) + "=", ApiTokenKind.AGENT));
    }
}
