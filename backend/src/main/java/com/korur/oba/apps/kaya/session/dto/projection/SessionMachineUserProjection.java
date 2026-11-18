package com.korur.oba.apps.kaya.session.dto.projection;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * One machine that recorded sessions of an organization, with the user of those sessions.
 */
public interface SessionMachineUserProjection {

    UUID getMachineId();

    UUID getUserId();
}
