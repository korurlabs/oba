package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;
import com.korur.oba.connect.push.constant.exception.PushExceptionLogConstant;
import com.korur.oba.connect.push.constant.service.PushDeviceServiceLogConstant;
import com.korur.oba.connect.push.dto.request.PushDeviceRegisterRequest;
import com.korur.oba.connect.push.dto.response.PushDeviceResponse;
import com.korur.oba.connect.push.entity.PushDevice;
import com.korur.oba.connect.push.exception.ExceptionDomainPushError;
import com.korur.oba.connect.push.mapper.PushDeviceMapper;
import com.korur.oba.connect.push.repository.PushDeviceRepository;
import com.korur.oba.connect.push.service.PushDeviceService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements writes to the registry of phones that receive push notifications.
 *
 * Every lookup is scoped by the caller, so another user's device reads as not found. The one
 * exception is a token itself: whoever presents a token holds the phone, so registering it takes the
 * row over from its previous owner.
 */
@Service
@RequiredArgsConstructor
public class PushDeviceServiceImpl implements PushDeviceService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PushDeviceServiceImpl.class);

    private final PushDeviceRepository pushDeviceRepository;
    private final PushDeviceMapper pushDeviceMapper;
    private final LoggingService loggingService;
    private final Clock clock;

    @Override
    @Transactional
    public PushDeviceResponse registerDevice(User user, PushDeviceRegisterRequest request) {
        Instant registeredAt = Instant.now(clock);

        // Creating the row first, in one statement, keeps two registrations of a new token at once
        // from failing on the unique token; both then update the same row.
        pushDeviceRepository.insertIfAbsent(request.expoPushToken(), request.platform().name(),
                request.deviceName(), request.appVersion(), request.locale(), registeredAt, user.getId());

        PushDevice pushDevice = pushDeviceRepository.findByExpoPushToken(request.expoPushToken())
                .orElseThrow(() -> new NotFoundException(ExceptionDomainPushError.NOT_PUSH_DEVICE_FOUND));

        UUID previousUserId = pushDevice.getUserId();
        if (!Objects.equals(previousUserId, user.getId())) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PushDeviceServiceLogConstant.DEVICE_MOVED,
                    pushDevice.getId(), previousUserId, user.getId());
        }

        pushDeviceMapper.updateEntity(pushDevice, request);
        pushDevice.setUserId(user.getId());
        pushDevice.setLastRegisteredAt(registeredAt);
        pushDevice = pushDeviceRepository.save(pushDevice);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PushDeviceServiceLogConstant.DEVICE_REGISTERED,
                pushDevice.getId(), user.getId(), pushDevice.getPlatform());

        return pushDeviceMapper.toResponse(pushDevice);
    }

    @Override
    @Transactional
    public void unregisterDevice(User user, UUID deviceId) {
        PushDevice pushDevice = pushDeviceRepository.findByIdAndUserId(deviceId, user.getId())
                .orElseThrow(() -> {
                    loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, PushExceptionLogConstant.DEVICE_NOT_FOUND,
                            deviceId, user.getId());
                    return new NotFoundException(ExceptionDomainPushError.NOT_PUSH_DEVICE_FOUND,
                            Map.of(PushConstant.FIELD_DEVICE_ID, deviceId));
                });

        pushDeviceRepository.delete(pushDevice);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PushDeviceServiceLogConstant.DEVICE_UNREGISTERED,
                deviceId, user.getId());
    }

    @Override
    @Transactional
    public void removeTokens(Collection<String> expoPushTokens) {
        if (expoPushTokens == null || expoPushTokens.isEmpty()) {
            return;
        }

        int removed = pushDeviceRepository.deleteAllByExpoPushTokenIn(expoPushTokens);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PushDeviceServiceLogConstant.TOKENS_REMOVED,
                expoPushTokens.size(), removed);
    }
}
