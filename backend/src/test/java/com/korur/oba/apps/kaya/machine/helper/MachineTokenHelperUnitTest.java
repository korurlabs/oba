package com.korur.oba.apps.kaya.machine.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.util.HashSet;
import java.util.Set;

// 4. Internal

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineTokenHelperUnitTest {

    private final MachineTokenHelper helper = new MachineTokenHelper();

    @Test
    void generate_always_returnsKytTokenOf47UrlSafeCharacters() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = helper.generate();

            assertTrue(token.matches("^kyt_[A-Za-z0-9_-]{43}$"), token);
            seen.add(token);
        }
        assertEquals(200, seen.size());
    }

    @Test
    void hash_knownInput_returnsLowerCaseHexSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", helper.hash("abc"));
    }

    @Test
    void hash_generatedToken_is64HexCharactersAndNotTheToken() {
        String token = helper.generate();

        String hash = helper.hash(token);

        assertTrue(hash.matches("^[0-9a-f]{64}$"));
        assertNotEquals(token, hash);
        assertEquals(hash, helper.hash(token));
    }

    @Test
    void displayPrefix_token_returnsFirst12Characters() {
        String token = helper.generate();

        assertEquals(token.substring(0, 12), helper.displayPrefix(token));
        assertTrue(helper.displayPrefix(token).startsWith("kyt_"));
    }

    @Test
    void hasTokenFormat_variants_acceptsOnlyKytAnd47Characters() {
        assertTrue(helper.hasTokenFormat(helper.generate()));
        assertFalse(helper.hasTokenFormat(null));
        assertFalse(helper.hasTokenFormat(""));
        assertFalse(helper.hasTokenFormat("kyt_short"));
        assertFalse(helper.hasTokenFormat("abc_" + "A".repeat(43)));
        assertFalse(helper.hasTokenFormat("kyt_" + "A".repeat(44)));
    }
}
