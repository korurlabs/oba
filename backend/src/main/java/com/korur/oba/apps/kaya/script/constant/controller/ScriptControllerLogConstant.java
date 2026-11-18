package com.korur.oba.apps.kaya.script.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the script controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ScriptControllerLogConstant {

    private ScriptControllerLogConstant() {
    }

    public static final String SCRIPT_GET_REQUEST = "Received request to read script {} of organization {}";
    public static final String SCRIPT_GET_COMPLETED = "Read script {} of organization {}";
}
