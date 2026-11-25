package com.korur.oba.dojo.security.token.handler;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.service.MachineQueryService;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityConstant;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.dojo.security.token.dto.ApiTokenBodyLimit;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KayaMachineTokenKindHandlerUnitTest {

    @Mock
    private MachineQueryService machineQueryService;

    @Mock
    private LoggingService loggingService;

    private KayaMachineTokenKindHandler handler;
    private ApiToken token;

    @BeforeEach
    void setUp() {
        handler = new KayaMachineTokenKindHandler(machineQueryService, loggingService);
        token = ApiToken.builder()
                .id(UUID.randomUUID())
                .kind(ApiTokenKind.KAYA_MACHINE)
                .subjectId(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .build();
    }

    @Test
    void resolvePrincipal_activeMachine_returnsTheMachinePrincipal() {
        MachinePrincipal principal = new MachinePrincipal(token.getOrganizationId(), UUID.randomUUID(),
                token.getSubjectId(), token.getId(), "laptop");
        when(machineQueryService.findActivePrincipal(token.getOrganizationId(), token.getSubjectId(), token.getId()))
                .thenReturn(Optional.of(principal));

        assertSame(principal, handler.resolvePrincipal(token));
    }

    @Test
    void resolvePrincipal_machineNotActive_throwsUnauthorizedAndLogs() {
        when(machineQueryService.findActivePrincipal(token.getOrganizationId(), token.getSubjectId(), token.getId()))
                .thenReturn(Optional.empty());

        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> handler.resolvePrincipal(token));

        assertEquals("KAY_SEC_003", exception.getErrorCode());
        verify(loggingService).warn(any(LogPrefix.class), eq(LogLayer.HANDLER), eq(SecurityLogConstant.MACHINE_NOT_ACTIVE),
                eq(token.getId()), eq(token.getSubjectId()), eq(token.getOrganizationId()));
    }

    @Test
    void refusedPaths_machineToken_refusesNothingBeyondItsReservedPaths() {
        assertTrue(handler.refusedPaths().isEmpty());
    }

    @Test
    void exemptEndpoints_onlyIngestPost_isExempt() {
        assertEquals(1, handler.exemptEndpoints().size());
        ApiTokenEndpoint endpoint = handler.exemptEndpoints().get(0);
        assertEquals(HttpMethod.POST, endpoint.method());
        assertEquals(SecurityConstant.KAYA_MACHINE_TOKEN_INGEST_PATH, endpoint.pathPattern());
    }

    @Test
    void bodyLimit_kayaPaths_capsTheBodyWithTheKayaError() {
        Optional<ApiTokenBodyLimit> limit = handler.bodyLimit();

        assertTrue(limit.isPresent());
        assertEquals(SecurityConstant.KAYA_MACHINE_TOKEN_MAX_BODY_BYTES, limit.get().maxBytes());
        assertEquals("KAY_VAL_012", limit.get().tooLargeError().getCode());
    }
}
