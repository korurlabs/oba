package com.korur.oba.connect.push.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.dto.request.PushDeviceRegisterRequest;
import com.korur.oba.connect.push.dto.response.PushDeviceResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write service for the phones that receive push notifications.
 */
public interface PushDeviceService {

    /**
     * Registers the caller's phone, or refreshes the registration of its token. A token registered
     * under another user moves to the caller, so a phone handed to someone else never receives the
     * previous user's pushes.
     *
     * @param user    the authenticated, ACTIVE user
     * @param request the token and the device details
     * @return the registered device
     */
    PushDeviceResponse registerDevice(User user, PushDeviceRegisterRequest request);

    /**
     * Removes one of the caller's devices, as the app does on sign-out.
     *
     * @param user     the authenticated, ACTIVE user
     * @param deviceId ID of the device
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         PSH_NOT_001 when the device does not exist or belongs to another user
     */
    void unregisterDevice(User user, UUID deviceId);

    /**
     * Removes the devices of tokens Expo reports as no longer registered, so later pushes skip them.
     *
     * @param expoPushTokens the tokens to remove; tokens that are not registered are ignored
     */
    void removeTokens(Collection<String> expoPushTokens);
}
