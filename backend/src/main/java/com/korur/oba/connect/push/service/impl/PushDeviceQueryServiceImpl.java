package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.entity.PushDevice;
import com.korur.oba.connect.push.repository.PushDeviceRepository;
import com.korur.oba.connect.push.service.PushDeviceQueryService;

/**
 * Implements reads of the registry of phones that receive push notifications.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushDeviceQueryServiceImpl implements PushDeviceQueryService {

    private final PushDeviceRepository pushDeviceRepository;

    @Override
    public List<PushDevice> getDevicesByUserIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return pushDeviceRepository.findAllByUserIdIn(userIds);
    }
}
