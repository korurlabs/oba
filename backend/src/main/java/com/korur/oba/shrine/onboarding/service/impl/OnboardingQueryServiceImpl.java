package com.korur.oba.shrine.onboarding.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.onboarding.constant.service.OnboardingServiceLogConstant;
import com.korur.oba.shrine.onboarding.dto.response.OnboardingStatusResponse;
import com.korur.oba.shrine.onboarding.mapper.OnboardingMapper;
import com.korur.oba.shrine.onboarding.service.OnboardingQueryService;

/**
 * Implements read operations for onboarding status queries.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingQueryServiceImpl implements OnboardingQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OnboardingQueryServiceImpl.class);

    private final OnboardingMapper onboardingMapper;
    private final LoggingService loggingService;

    @Override
    public OnboardingStatusResponse getOnboardingStatus(User user) {
        loggingService.info(
                LOG_PREFIX,
                LogLayer.SERVICE,
                OnboardingServiceLogConstant.ONBOARDING_STATUS_QUERIED,
                user.getId());

        return onboardingMapper.toStatusResponse(user);
    }
}
