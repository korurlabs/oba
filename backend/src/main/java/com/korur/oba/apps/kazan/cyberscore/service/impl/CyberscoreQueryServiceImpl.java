package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreResponse;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreTrendPointResponse;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.mapper.CyberscoreMapper;
import com.korur.oba.apps.kazan.cyberscore.repository.CheckRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreQueryService;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreTrendService;

/**
 * Implementation of cyberscore read operations.
 *
 * All methods run within a read-only transaction. The organization role is
 * checked once per request by the access interceptor, and every lookup is
 * scoped by the request's organization.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CyberscoreQueryServiceImpl implements CyberscoreQueryService {

    private final CyberscoreRepository cyberscoreRepository;
    private final CheckRepository checkRepository;
    private final CyberscoreTrendService cyberscoreTrendService;
    private final CyberscoreMapper cyberscoreMapper;

    @Override
    public CyberscoreResponse getCurrent(UUID organizationId) {
        // The row appears with the first scoring change, so an organization
        // without it has no check yet and gets the not-yet-scored shape.
        return cyberscoreRepository.findByOrganizationId(organizationId)
                .map(cyberscore -> cyberscoreMapper.toResponse(cyberscore, countScorableChecks(organizationId)))
                .orElseGet(() -> new CyberscoreResponse(0, CyberscoreLevel.UNKNOWN, Map.of(), 0, null));
    }

    @Override
    public List<CyberscoreTrendPointResponse> getTrend(UUID organizationId, int days) {
        return cyberscoreMapper.toTrendPointResponses(cyberscoreTrendService.buildTrend(organizationId, days));
    }

    /**
     * Counts the organization's checks whose outcome takes part in the score.
     */
    private int countScorableChecks(UUID organizationId) {
        return (int) checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId).stream()
                .filter(check -> check.getOutcome().scoreFactor().isPresent())
                .count();
    }
}
