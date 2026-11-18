package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.entity.PushPreference;
import com.korur.oba.connect.push.mapper.PushPreferenceMapper;
import com.korur.oba.connect.push.repository.PushPreferenceRepository;
import com.korur.oba.connect.push.service.PushPreferenceQueryService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implements reads of the push switches of users.
 *
 * A user without a row gets the switches of an unsaved {@link PushPreference}, whose defaults are the
 * column defaults, so the API and the sender agree on what "never saved" means.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushPreferenceQueryServiceImpl implements PushPreferenceQueryService {

    private final PushPreferenceRepository pushPreferenceRepository;
    private final PushPreferenceMapper pushPreferenceMapper;

    @Override
    public PushPreferenceResponse getPreferences(User user) {
        PushPreference pushPreference = pushPreferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> defaultPreference(user.getId()));
        return pushPreferenceMapper.toResponse(pushPreference);
    }

    @Override
    public Map<UUID, PushPreferenceResponse> getPreferencesByUserIds(Collection<UUID> userIds) {
        Map<UUID, PushPreferenceResponse> preferences = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return preferences;
        }

        for (PushPreference pushPreference : pushPreferenceRepository.findAllByUserIdIn(userIds)) {
            preferences.put(pushPreference.getUserId(), pushPreferenceMapper.toResponse(pushPreference));
        }
        for (UUID userId : userIds) {
            preferences.computeIfAbsent(userId,
                    (UUID missingUserId) -> pushPreferenceMapper.toResponse(defaultPreference(missingUserId)));
        }
        return preferences;
    }

    /**
     * Builds the unsaved preferences of a user who never saved any; it is only mapped, never persisted.
     */
    private PushPreference defaultPreference(UUID userId) {
        return PushPreference.builder().userId(userId).build();
    }
}
