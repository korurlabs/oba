package com.korur.oba.apps.kaya.machine.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the machine controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MachineControllerResponseConstant {

    private MachineControllerResponseConstant() {
    }

    public static final String RESPONSE_MACHINES_RETRIEVED = "Machines retrieved successfully";
    public static final String RESPONSE_MACHINE_RETRIEVED = "Machine retrieved successfully";
    public static final String RESPONSE_MACHINE_CREATED = "Machine registered successfully";
    public static final String RESPONSE_TOKEN_ROTATED = "Token issued successfully";
    public static final String RESPONSE_TOKEN_REVOKED = "Token revoked successfully";
    public static final String RESPONSE_MACHINE_UPDATED = "Machine retired successfully";
}
