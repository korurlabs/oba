package com.korur.oba.dojo.security.token.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.security.token.TestTokenKinds;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenKindRegistryUnitTest {

    private final ApiTokenKindRegistry registry = TestTokenKinds.registryOf(
            new TestTokenKinds.MachineLikeHandler(), new TestTokenKinds.AgentLikeHandler());

    @Test
    void findByToken_prefixOfAnAcceptedKind_findsItsHandler() {
        assertEquals(ApiTokenKind.AGENT, registry.findByToken("oat_anything").orElseThrow().kind());
        assertEquals(ApiTokenKind.KAYA_MACHINE, registry.findByToken("kyt_anything").orElseThrow().kind());
    }

    @Test
    void findByToken_jwtOrNull_findsNothing() {
        assertTrue(registry.findByToken("eyJhbGciOiJSUzI1NiJ9.e30.sig").isEmpty());
        assertTrue(registry.findByToken(null).isEmpty());
    }

    @Test
    void findByToken_kindWithoutHandler_findsNothing() {
        ApiTokenKindRegistry agentOnly = TestTokenKinds.registryOf(new TestTokenKinds.AgentLikeHandler());

        assertTrue(agentOnly.findByToken("kyt_anything").isEmpty());
    }

    @Test
    void emptyRegistry_acceptsNoKind() {
        ApiTokenKindRegistry empty = TestTokenKinds.registryOf();

        assertTrue(empty.handlers().isEmpty());
        assertTrue(empty.findByToken("oat_anything").isEmpty());
        assertTrue(empty.findByReservedPath("/kaya/ingest/batches").isEmpty());
    }

    @Test
    void findByReservedPath_matchesTheReservingKindOnly() {
        assertEquals(ApiTokenKind.KAYA_MACHINE, registry.findByReservedPath("/kaya/delivery/conventions").orElseThrow().kind());
        assertTrue(registry.findByReservedPath("/kazan/tickets").isEmpty());
    }

    @Test
    void findRefusal_pathTheKindIsRefusedOn_findsTheKindsRefusal() {
        assertEquals("TST_SEC_001", registry.findRefusal(ApiTokenKind.AGENT, "/kaya/lessons/1")
                .orElseThrow().refusedError().getCode());
        assertTrue(registry.findRefusal(ApiTokenKind.AGENT, "/kaya").isPresent());
    }

    @Test
    void findRefusal_otherPathOrKindWithoutRefusals_findsNothing() {
        assertTrue(registry.findRefusal(ApiTokenKind.AGENT, "/kazan/tickets").isEmpty());
        assertTrue(registry.findRefusal(ApiTokenKind.AGENT, "/kayak").isEmpty());
        assertTrue(registry.findRefusal(ApiTokenKind.KAYA_MACHINE, "/kaya/delivery/conventions").isEmpty());
    }

    @Test
    void isExemptEndpoint_matchesMethodAndPathTogether() {
        assertTrue(registry.isExemptEndpoint(ApiTokenKind.AGENT, "GET", "/users/me"));
        assertFalse(registry.isExemptEndpoint(ApiTokenKind.AGENT, "PUT", "/users/me"));
        assertFalse(registry.isExemptEndpoint(ApiTokenKind.AGENT, "DELETE", "/users/me"));
        assertFalse(registry.isExemptEndpoint(ApiTokenKind.AGENT, "GET", "/users/me/avatar"));
        assertFalse(registry.isExemptEndpoint(ApiTokenKind.KAYA_MACHINE, "GET", "/users/me"));
    }

    @Test
    void twoHandlersForOneKind_failsAtStartup() {
        assertThrows(IllegalStateException.class, () -> TestTokenKinds.registryOf(
                new TestTokenKinds.AgentLikeHandler(), new TestTokenKinds.AgentLikeHandler()));
    }
}
