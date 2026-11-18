package com.korur.oba.connect.push.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.entity.PushDevice;

/**
 * Read service for the phones that receive push notifications.
 */
public interface PushDeviceQueryService {

    /**
     * Returns every device of the given users, for sending one push to each device of each recipient.
     * Performs no access check; the caller has already decided who receives the push.
     *
     * @param userIds IDs of the recipients
     * @return the devices, empty when the collection is empty or no recipient has a device
     */
    List<PushDevice> getDevicesByUserIds(Collection<UUID> userIds);
}
