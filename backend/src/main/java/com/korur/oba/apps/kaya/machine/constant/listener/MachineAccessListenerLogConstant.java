package com.korur.oba.apps.kaya.machine.constant.listener;

/**
 * Log messages of the listener that ends an agent's Kaya machines when the agent loses Kaya. They carry
 * ids and counts only, never a token.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MachineAccessListenerLogConstant {

    private MachineAccessListenerLogConstant() {
    }

    public static final String AGENT_KAYA_LOST_MACHINES_RETIRED =
            "Agent lost Kaya, its machines are retired: organizationId={}, userId={}, retiredMachines={}";
}
