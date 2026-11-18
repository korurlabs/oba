package com.korur.oba.connect.push.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read service for the push switches of users. A user who never saved them has every switch on.
 */
public interface PushPreferenceQueryService {

    /**
     * Returns the caller's push switches.
     *
     * @param user the authenticated, ACTIVE user
     * @return the switches, every one on when the user never saved them
     */
    PushPreferenceResponse getPreferences(User user);

    /**
     * Returns the push switches of the given users, for deciding which recipients get a push.
     * Performs no access check.
     *
     * @param userIds IDs of the users
     * @return one entry per given user, every switch on for a user who never saved them
     */
    Map<UUID, PushPreferenceResponse> getPreferencesByUserIds(Collection<UUID> userIds);
}
