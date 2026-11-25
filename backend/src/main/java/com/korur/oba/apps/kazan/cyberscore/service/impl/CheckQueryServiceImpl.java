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
import com.korur.oba.apps.kazan.cyberscore.dto.request.CheckQueryRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CheckResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.mapper.CheckMapper;
import com.korur.oba.apps.kazan.cyberscore.repository.CheckRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CheckQueryService;

/**
 * Implementation of check read operations.
 *
 * All methods run within a read-only transaction and read only the checks of
 * the request's organization.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckQueryServiceImpl implements CheckQueryService {

    private final CheckRepository checkRepository;
    private final CheckMapper checkMapper;

    @Override
    public List<CheckResponse> listChecks(UUID organizationId, CheckQueryRequest query) {
        List<Check> checks = query.getStatus() == null
                ? checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId)
                : checkRepository.findByOrganizationIdAndStatusOrderByCategoryAscSortOrderAscNameAsc(
                        organizationId, query.getStatus());
        return checkMapper.toResponses(checks);
    }
}
