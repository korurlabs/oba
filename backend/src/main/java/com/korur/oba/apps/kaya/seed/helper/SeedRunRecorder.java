package com.korur.oba.apps.kaya.seed.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.seed.entity.SeedRun;
import com.korur.oba.apps.kaya.seed.repository.SeedRunRepository;

/**
 * Stores a seed run in its own transaction. A run whose composition was refused rolls back every
 * write it made, but the OWNER still needs the stored run and its report to see why.
 */
@Component
@RequiredArgsConstructor
public class SeedRunRecorder {

    private final SeedRunRepository seedRunRepository;

    /**
     * @param run the run to store
     * @return the stored run, committed whatever happens to the caller's transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeedRun recordSeparately(SeedRun run) {
        return seedRunRepository.saveAndFlush(run);
    }
}
