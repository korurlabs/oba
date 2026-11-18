package com.korur.oba.apps.kaizen.alertroute.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteBoardProjection;
import com.korur.oba.apps.kaizen.alertroute.dto.response.AlertRouteResponse;
import com.korur.oba.apps.kaizen.alertroute.mapper.AlertRouteMapper;
import com.korur.oba.apps.kaizen.alertroute.repository.AlertRouteRepository;
import com.korur.oba.apps.kaizen.alertroute.service.AlertRouteQueryService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of alert map read operations.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertRouteQueryServiceImpl implements AlertRouteQueryService {

    private final AlertRouteRepository alertRouteRepository;
    private final AlertRouteMapper alertRouteMapper;

    @Override
    public List<AlertRouteResponse> listAlertRoutes(User user, UUID organizationId) {
        return alertRouteRepository.findAllWithBoard(organizationId).stream()
                .map(alertRouteMapper::toResponse)
                .toList();
    }

    @Override
    public Map<String, UUID> getBoardIdsBySystem(UUID organizationId) {
        Map<String, UUID> boardIdsBySystem = new HashMap<>();
        for (AlertRouteBoardProjection route : alertRouteRepository.findBoardIdsBySystem(organizationId)) {
            boardIdsBySystem.put(route.getSystemKey(), route.getBoardId());
        }
        return boardIdsBySystem;
    }

    @Override
    public Optional<UUID> getDefaultBoardId(UUID organizationId) {
        return alertRouteRepository.findDefaultBoardId(organizationId);
    }
}
