package com.korur.oba.apps.kaya.session.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.session.dto.response.SessionCountSummaryResponse;

/**
 * Counts sessions for the user and machine lists. It depends on the session tables only, so the user
 * and machine query services can use it while the session query service uses them for names.
 */
public interface SessionCountQueryService {

    /**
     * Counts the sessions of each given user.
     *
     * @param organizationId the organization of the users
     * @param userIds        the users; may be empty
     * @return the summary per user id; a user without sessions has no entry
     */
    Map<UUID, SessionCountSummaryResponse> countByUsers(UUID organizationId, Collection<UUID> userIds);

    /**
     * Counts the sessions of each given machine.
     *
     * @param organizationId the organization of the machines
     * @param machineIds       the machines; may be empty
     * @return the summary per machine id; a machine without sessions has no entry
     */
    Map<UUID, SessionCountSummaryResponse> countByMachines(UUID organizationId, Collection<UUID> machineIds);
}
