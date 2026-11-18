package com.korur.oba.apps.kaya.session.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * One Claude organization the sessions of an organization ran on, with the billing type and seat
 * tier of its newest session.
 */
public interface SessionSubscriptionProjection {

    String getUuid();

    String getName();

    String getBillingType();

    String getSeatTier();
}
