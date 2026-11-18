package com.korur.oba.connect.push.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.push.dto.request.PushPreferenceUpdateRequest;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service for the push switches of a user.
 */
public interface PushPreferenceService {

    /**
     * Replaces all of the caller's push switches; they apply to every device of the caller.
     *
     * @param user    the authenticated, ACTIVE user
     * @param request every switch
     * @return the switches after the change
     */
    PushPreferenceResponse updatePreferences(User user, PushPreferenceUpdateRequest request);
}
