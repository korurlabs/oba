package com.korur.oba.apps.kaya.ingest.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the ingest controller.
 */
public final class IngestControllerLogConstant {

    private IngestControllerLogConstant() {
    }

    public static final String BATCH_REQUEST = "Received batch of session {} from machine {}";
    public static final String BATCH_COMPLETED = "Acknowledged batch of session {} up to seq {}";
}
