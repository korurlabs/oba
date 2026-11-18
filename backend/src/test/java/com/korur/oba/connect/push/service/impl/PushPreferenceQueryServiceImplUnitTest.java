package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.entity.PushPreference;
import com.korur.oba.connect.push.mapper.PushPreferenceMapperImpl;
import com.korur.oba.connect.push.repository.PushPreferenceRepository;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class PushPreferenceQueryServiceImplUnitTest {

    private static final PushPreferenceResponse ALL_ON = new PushPreferenceResponse(true, true, true, true, true);

    @Mock
    private PushPreferenceRepository pushPreferenceRepository;

    private PushPreferenceQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PushPreferenceQueryServiceImpl(pushPreferenceRepository, new PushPreferenceMapperImpl());
    }

    private User user() {
        User user = User.builder().email("user@example.com").build();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void getPreferences_noRow_readsEveryRegisteredSwitchAsOnWithoutSaving() {
        // Arrange
        User user = user();
        when(pushPreferenceRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        // Act
        PushPreferenceResponse response = service.getPreferences(user);

        // Assert
        assertThat(response).isEqualTo(ALL_ON);
        verify(pushPreferenceRepository, never()).save(any(PushPreference.class));
    }

    @Test
    void getPreferences_savedRow_returnsTheSavedSwitches() {
        // Arrange
        User user = user();
        PushPreference saved = PushPreference.builder().userId(user.getId()).here(false).showPreviews(false).build();
        when(pushPreferenceRepository.findByUserId(user.getId())).thenReturn(Optional.of(saved));

        // Act
        PushPreferenceResponse response = service.getPreferences(user);

        // Assert
        assertThat(response).isEqualTo(new PushPreferenceResponse(true, true, false, true, false));
    }

    @Test
    void getPreferencesByUserIds_mixOfSavedAndMissing_fillsMissingWithAllOn() {
        // Arrange
        UUID savedUserId = UUID.randomUUID();
        UUID missingUserId = UUID.randomUUID();
        PushPreference saved = PushPreference.builder().userId(savedUserId).everyone(false).build();
        when(pushPreferenceRepository.findAllByUserIdIn(Set.of(savedUserId, missingUserId)))
                .thenReturn(List.of(saved));

        // Act
        Map<UUID, PushPreferenceResponse> result = service.getPreferencesByUserIds(Set.of(savedUserId, missingUserId));

        // Assert
        assertThat(result).containsOnlyKeys(savedUserId, missingUserId);
        assertThat(result.get(savedUserId).everyone()).isFalse();
        assertThat(result.get(missingUserId)).isEqualTo(ALL_ON);
    }

    @Test
    void getPreferencesByUserIds_emptyOrNull_returnsEmptyWithoutAQuery() {
        // Act and assert
        assertThat(service.getPreferencesByUserIds(Set.of())).isEmpty();
        assertThat(service.getPreferencesByUserIds(null)).isEmpty();
        verify(pushPreferenceRepository, never()).findAllByUserIdIn(any());
    }
}
