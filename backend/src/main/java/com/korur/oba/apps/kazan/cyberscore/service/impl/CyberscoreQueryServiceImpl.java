package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreConstant;
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreCalculation;
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreTrendQueryRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreResponse;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreTrendPointResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.mapper.CyberscoreMapper;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreCheckRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCalculateService;
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
    private final CyberscoreCheckRepository checkRepository;
    private final CyberscoreCalculateService cyberscoreCalculateService;
    private final CyberscoreTrendService cyberscoreTrendService;
    private final CyberscoreMapper cyberscoreMapper;

    @Override
    public CyberscoreResponse getCurrent(UUID organizationId) {
        Optional<Cyberscore> row = cyberscoreRepository.findByOrganizationId(organizationId);
        // The row appears with the first scoring change, so an organization
        // without it has no check yet and gets the not-yet-scored shape.
        if (row.isEmpty()) {
            return new CyberscoreResponse(0, CyberscoreLevel.UNKNOWN, Map.of(), 0, null);
        }

        // The percentage per category is computed from the checks on every read
        // instead of being stored, so it always matches them.
        List<CyberscoreCheck> checks =
                checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId);
        CyberscoreCalculation calculation = cyberscoreCalculateService.calculate(checks);

        return cyberscoreMapper.toResponse(row.get(), calculation, countScorableChecks(checks));
    }

    @Override
    public List<CyberscoreTrendPointResponse> getTrend(UUID organizationId, CyberscoreTrendQueryRequest query) {
        int days = query.getDays() != null ? query.getDays() : CyberscoreConstant.DEFAULT_TREND_DAYS;
        return cyberscoreMapper.toTrendPointResponses(cyberscoreTrendService.buildTrend(organizationId, days));
    }

    /**
     * Counts the checks whose outcome takes part in the score.
     */
    private static int countScorableChecks(List<CyberscoreCheck> checks) {
        int scorable = 0;
        for (CyberscoreCheck check : checks) {
            if (check.getOutcome().scoreFactor().isPresent()) {
                scorable++;
            }
        }
        return scorable;
    }
}
