package com.korur.oba.apps.kazan.dailyupdate.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.LocalDate;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateNudgeRepository;
import com.korur.oba.apps.kazan.dailyupdate.service.DailyUpdateNudgeQueryService;

/**
 * Implementation of daily update nudge read operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyUpdateNudgeQueryServiceImpl implements DailyUpdateNudgeQueryService {

    private final DailyUpdateNudgeRepository dailyUpdateNudgeRepository;

    @Override
    public boolean isNudged(UUID organizationId, UUID recipientId, LocalDate day) {
        return dailyUpdateNudgeRepository.existsByOrganizationIdAndRecipientIdAndDay(organizationId, recipientId, day);
    }
}
