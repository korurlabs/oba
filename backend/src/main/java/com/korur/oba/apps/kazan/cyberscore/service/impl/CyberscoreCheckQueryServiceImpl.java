package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreCheckQueryRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreCheckResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.mapper.CyberscoreCheckMapper;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreCheckRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreCheckQueryService;

/**
 * Implementation of check read operations.
 *
 * All methods run within a read-only transaction and read only the checks of
 * the request's organization.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CyberscoreCheckQueryServiceImpl implements CyberscoreCheckQueryService {

    private final CyberscoreCheckRepository checkRepository;
    private final CyberscoreCheckMapper checkMapper;

    @Override
    public List<CyberscoreCheckResponse> listChecks(UUID organizationId, CyberscoreCheckQueryRequest query) {
        List<CyberscoreCheck> checks = query.getStatus() == null
                ? checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId)
                : checkRepository.findByOrganizationIdAndStatusOrderByCategoryAscSortOrderAscNameAsc(
                        organizationId, query.getStatus());
        return checkMapper.toResponses(checks);
    }
}
