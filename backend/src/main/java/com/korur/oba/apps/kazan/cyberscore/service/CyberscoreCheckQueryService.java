package com.korur.oba.apps.kazan.cyberscore.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.request.CyberscoreCheckQueryRequest;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreCheckResponse;

/**
 * Read service of an organization's checks.
 */
public interface CyberscoreCheckQueryService {

    /**
     * Lists the organization's checks in display order: category in declaration
     * order with uncategorized checks last, then sort order, then name. The list
     * is not paged, because an organization has tens of checks.
     *
     * @param organizationId the organization of the request
     * @param query          the optional status filter
     * @return the matching checks
     */
    List<CyberscoreCheckResponse> listChecks(UUID organizationId, CyberscoreCheckQueryRequest query);
}
