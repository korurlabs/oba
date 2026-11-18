package com.korur.oba.apps.kaya.seed.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the seed controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SeedControllerLogConstant {

    private SeedControllerLogConstant() {
    }

    public static final String SEED_REQUEST = "Received request to seed organization {} with {} sets";
    public static final String SEED_COMPLETED = "Seed run {} of organization {} finished {}";
}
