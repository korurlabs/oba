package com.korur.oba.apps.kazan.cyberscore.enums;

/**
 * Layer of the organization's IT a check belongs to, after the ten layers of a
 * cyberscan from the top of the stack down.
 *
 * The declaration order is the display order. Mapped to the PostgreSQL enum
 * kazan_check_category_enum, whose values must stay in the same order.
 */
public enum CheckCategory {

    // Identity.
    IDENTITY,

    // Office.
    OFFICE,

    // Network and edge.
    NETWORK_EDGE,

    // Cloud platform.
    CLOUD_PLATFORM,

    // Applications.
    APPLICATIONS,

    // Data.
    DATA,

    // Devices.
    DEVICES,

    // Development and supply chain.
    DEVELOPMENT_SUPPLY_CHAIN,

    // Detection and response.
    DETECTION_RESPONSE,

    // Compliance.
    COMPLIANCE
}
