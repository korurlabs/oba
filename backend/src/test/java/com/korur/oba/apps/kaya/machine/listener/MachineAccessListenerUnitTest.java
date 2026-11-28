package com.korur.oba.apps.kaya.machine.listener;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.lang.reflect.Method;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.machine.constant.listener.MachineAccessListenerLogConstant;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;

@ExtendWith(MockitoExtension.class)
class MachineAccessListenerUnitTest {

    @Mock
    private KayaUserQueryService kayaUserQueryService;
    @Mock
    private MachineService machineService;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private MachineAccessListener listener;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void onAppAccessRevoked_agentLosesKaya_retiresItsMachines() {
        when(kayaUserQueryService.getEntityById(userId)).thenReturn(user(UserKind.AGENT));
        when(machineService.retireAllOfUser(organizationId, userId)).thenReturn(2);

        listener.onAppAccessRevoked(new AppAccessRevokedEvent(userId, organizationId, App.KAYA));

        verify(machineService).retireAllOfUser(organizationId, userId);
        verify(loggingService).info(any(), eq(LogLayer.HANDLER),
                eq(MachineAccessListenerLogConstant.AGENT_KAYA_LOST_MACHINES_RETIRED),
                eq(organizationId), eq(userId), eq(2));
    }

    @Test
    void onAppAccessRevoked_personLosesKaya_keepsTheMachines() {
        when(kayaUserQueryService.getEntityById(userId)).thenReturn(user(UserKind.HUMAN));

        listener.onAppAccessRevoked(new AppAccessRevokedEvent(userId, organizationId, App.KAYA));

        verifyNoInteractions(machineService);
    }

    @ParameterizedTest
    @EnumSource(value = App.class, names = "KAYA", mode = EnumSource.Mode.EXCLUDE)
    void onAppAccessRevoked_anotherApp_readsAndRetiresNothing(App app) {
        listener.onAppAccessRevoked(new AppAccessRevokedEvent(userId, organizationId, app));

        verifyNoInteractions(kayaUserQueryService, machineService);
    }

    @Test
    void onAppAccessRevoked_retirementFails_rethrowsSoTheRevokeRollsBack() {
        IllegalStateException failure = new IllegalStateException("lock wait failed");
        when(kayaUserQueryService.getEntityById(userId)).thenReturn(user(UserKind.AGENT));
        when(machineService.retireAllOfUser(organizationId, userId)).thenThrow(failure);

        assertThatThrownBy(() -> listener.onAppAccessRevoked(
                new AppAccessRevokedEvent(userId, organizationId, App.KAYA))).isSameAs(failure);
    }

    @Test
    void onAppAccessRevoked_runsSynchronouslyInsideTheRevokingTransaction() throws NoSuchMethodException {
        Method handler = MachineAccessListener.class.getMethod("onAppAccessRevoked", AppAccessRevokedEvent.class);

        assertThat(handler.isAnnotationPresent(EventListener.class)).isTrue();
        assertThat(handler.isAnnotationPresent(TransactionalEventListener.class)).isFalse();
        assertThat(handler.isAnnotationPresent(Async.class)).isFalse();
    }

    private User user(UserKind kind) {
        return User.builder().id(userId).kind(kind).build();
    }
}
