package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.dto.request.PushPreferenceUpdateRequest;
import com.korur.oba.connect.push.dto.response.PushPreferenceResponse;
import com.korur.oba.connect.push.entity.PushPreference;
import com.korur.oba.connect.push.mapper.PushPreferenceMapperImpl;
import com.korur.oba.connect.push.repository.PushPreferenceRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class PushPreferenceServiceImplUnitTest {

    @Mock
    private PushPreferenceRepository pushPreferenceRepository;
    @Mock
    private LoggingService loggingService;

    private PushPreferenceServiceImpl service;

    private final User user = User.builder().email("user@example.com").build();

    @BeforeEach
    void setUp() {
        user.setId(UUID.randomUUID());
        service = new PushPreferenceServiceImpl(pushPreferenceRepository, new PushPreferenceMapperImpl(),
                loggingService);
    }

    @Test
    void updatePreferences_allFiveSwitches_persistsEveryOne() {
        // Arrange
        PushPreference existing = PushPreference.builder().userId(user.getId()).build();
        when(pushPreferenceRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));
        when(pushPreferenceRepository.save(any(PushPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PushPreferenceUpdateRequest request = new PushPreferenceUpdateRequest(false, false, true, false, false);

        // Act
        PushPreferenceResponse response = service.updatePreferences(user, request);

        // Assert
        assertThat(response).isEqualTo(new PushPreferenceResponse(false, false, true, false, false));
        assertThat(existing.getMentionsAndReplies()).isFalse();
        assertThat(existing.getEveryone()).isFalse();
        assertThat(existing.getHere()).isTrue();
        assertThat(existing.getUrgentIncidents()).isFalse();
        assertThat(existing.getShowPreviews()).isFalse();
        InOrder order = inOrder(pushPreferenceRepository);
        order.verify(pushPreferenceRepository).insertIfAbsent(user.getId());
        order.verify(pushPreferenceRepository).findByUserId(user.getId());
        order.verify(pushPreferenceRepository).save(existing);
    }

    @Test
    void updatePreferences_rowMissingAfterTheInsert_throwsNotFound() {
        // Arrange
        when(pushPreferenceRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        PushPreferenceUpdateRequest request = new PushPreferenceUpdateRequest(true, true, true, true, true);

        // Act and assert
        assertThatThrownBy(() -> service.updatePreferences(user, request))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PSH_NOT_002");
        verify(pushPreferenceRepository, never()).save(any(PushPreference.class));
    }
}
