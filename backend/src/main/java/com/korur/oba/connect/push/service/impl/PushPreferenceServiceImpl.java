package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.push.constant.service.PushPreferenceServiceLogConstant;
import com.korur.oba.connect.push.dto.request.PushPreferenceUpdateRequest;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.entity.PushPreference;
import com.korur.oba.connect.push.exception.ExceptionDomainPushError;
import com.korur.oba.connect.push.mapper.PushPreferenceMapper;
import com.korur.oba.connect.push.repository.PushPreferenceRepository;
import com.korur.oba.connect.push.service.PushPreferenceService;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements writes to the push switches of a user. The rows are always the caller's own, keyed by
 * the authenticated user.
 */
@Service
@RequiredArgsConstructor
public class PushPreferenceServiceImpl implements PushPreferenceService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(PushPreferenceServiceImpl.class);

    private final PushPreferenceRepository pushPreferenceRepository;
    private final PushPreferenceMapper pushPreferenceMapper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public PushPreferenceResponse updatePreferences(User user, PushPreferenceUpdateRequest request) {
        // Creating the row first, in one statement, keeps two first saves from two devices at once
        // from failing on the unique user; both then update the same row.
        pushPreferenceRepository.insertIfAbsent(user.getId());

        PushPreference pushPreference = pushPreferenceRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException(ExceptionDomainPushError.NOT_PUSH_PREFERENCE_FOUND));

        pushPreferenceMapper.updateEntity(pushPreference, request);
        pushPreference = pushPreferenceRepository.save(pushPreference);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, PushPreferenceServiceLogConstant.PREFERENCES_UPDATED,
                user.getId(), pushPreference.getMentionsAndReplies(), pushPreference.getEveryone(),
                pushPreference.getHere(), pushPreference.getUrgentIncidents(), pushPreference.getShowPreviews());

        return pushPreferenceMapper.toResponse(pushPreference);
    }
}
