package com.korur.oba.apps.kaya.machine.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the machine controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MachineControllerLogConstant {

    private MachineControllerLogConstant() {
    }

    public static final String MACHINE_LIST_REQUEST = "Received request to list the machines of organization {}";
    public static final String MACHINE_LIST_COMPLETED = "Listed {} machines of organization {}";
    public static final String MACHINE_GET_REQUEST = "Received request to read machine {} of organization {}";
    public static final String MACHINE_GET_COMPLETED = "Read machine {} of organization {}";
    public static final String MACHINE_CREATE_REQUEST = "Received request to register a machine in organization {}";
    public static final String MACHINE_CREATE_COMPLETED = "Registered machine {} in organization {}";
    public static final String TOKEN_ROTATE_REQUEST = "Received request to rotate the token of machine {} of organization {}";
    public static final String TOKEN_ROTATE_COMPLETED = "Rotated the token of machine {} of organization {}";
    public static final String TOKEN_REVOKE_REQUEST = "Received request to revoke the token of machine {} of organization {}";
    public static final String TOKEN_REVOKE_COMPLETED = "Revoked the token of machine {} of organization {}";
    public static final String MACHINE_UPDATE_REQUEST = "Received request to retire machine {} of organization {}";
    public static final String MACHINE_UPDATE_COMPLETED = "Retired machine {} of organization {}";
}
