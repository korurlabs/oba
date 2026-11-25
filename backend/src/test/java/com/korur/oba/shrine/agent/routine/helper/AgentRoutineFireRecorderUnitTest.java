package com.korur.oba.shrine.agent.routine.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.agent.routine.entity.AgentRoutine;
import com.korur.oba.shrine.agent.routine.repository.AgentRoutineRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRoutineFireRecorderUnitTest {

    private static final Instant FIRED_AT = Instant.parse("2026-11-25T09:00:00Z");

    @Mock
    private AgentRoutineRepository agentRoutineRepository;
    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private AgentRoutineFireRecorder agentRoutineFireRecorder;

    @Test
    void record_existingRoutine_storesStatusTimeHttpStatusAndSession() {
        AgentRoutine routine = AgentRoutine.builder().id(UUID.randomUUID()).routineId("trig_01").build();
        when(agentRoutineRepository.findById(routine.getId())).thenReturn(Optional.of(routine));

        agentRoutineFireRecorder.record(routine.getId(),
                new RoutineFireResult(RoutineFireStatus.FIRED, 200, "https://claude.ai/code/session_01"), FIRED_AT);

        assertEquals(FIRED_AT, routine.getLastFiredAt());
        assertEquals(RoutineFireStatus.FIRED, routine.getLastFireStatus());
        assertEquals(200, routine.getLastHttpStatus());
        assertEquals("https://claude.ai/code/session_01", routine.getLastSessionUrl());
        verify(agentRoutineRepository).save(routine);
    }

    @Test
    void record_routineRemovedMeanwhile_savesNothing() {
        UUID agentRoutineId = UUID.randomUUID();
        when(agentRoutineRepository.findById(agentRoutineId)).thenReturn(Optional.empty());

        agentRoutineFireRecorder.record(agentRoutineId, RoutineFireResult.failedWithoutAnswer(), FIRED_AT);

        verify(agentRoutineRepository, never()).save(any());
    }
}
