package com.korur.oba.apps.kaya.machine.listener;

// 1. Framework-level
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.machine.constant.listener.MachineAccessListenerLogConstant;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;
import com.korur.oba.shrine.agent.validator.AgentValidator;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Retires an agent's Kaya machines, and revokes their tokens, when the agent loses Kaya.
 *
 * Kaya ingest reads neither the grants nor the member's apps, only the machine and its user, so a
 * machine that outlives the grant keeps recording for the agent. The event comes from every path that
 * takes an app away, the Agents page and the generic app grants page alike, and only for a user who
 * really lost the app, so an agent that keeps Kaya through another grant keeps its machines.
 *
 * The handler runs synchronously inside the revoking transaction, unlike the listeners that act after
 * the commit: the grant and the machines end together through {@link MachineService#retireAllOfUser},
 * and a failure here rolls the revoke back rather than leaving a live machine behind a removed grant.
 * Only an agent's machines are retired; a person who loses Kaya keeps them, as before.
 */
@Component
@RequiredArgsConstructor
public class MachineAccessListener {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MachineAccessListener.class);

    private final KayaUserQueryService kayaUserQueryService;
    private final MachineService machineService;
    private final LoggingService loggingService;

    /**
     * Retires the machines of an agent that lost Kaya in the organization.
     *
     * @param event the app a user lost, published inside the revoking transaction
     */
    @EventListener
    public void onAppAccessRevoked(AppAccessRevokedEvent event) {
        if (event.app() != App.KAYA) {
            return;
        }
        User user = kayaUserQueryService.getEntityById(event.userId());
        if (!AgentValidator.isAgent(user)) {
            return;
        }
        int retired = machineService.retireAllOfUser(event.organizationId(), event.userId());
        loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                MachineAccessListenerLogConstant.AGENT_KAYA_LOST_MACHINES_RETIRED,
                event.organizationId(), event.userId(), retired);
    }
}
