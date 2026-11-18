package com.korur.oba.apps.kazan.systemactor.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kazan.systemactor.entity.SystemActor;
import com.korur.oba.apps.kazan.systemactor.enums.SystemActorType;
import com.korur.oba.apps.kazan.systemactor.repository.SystemActorRepository;
import com.korur.oba.apps.kazan.systemactor.service.SystemActorQueryService;

/**
 * Implementation of system actor read operations.
 *
 * All methods run within a read-only transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemActorQueryServiceImpl implements SystemActorQueryService {

    private final SystemActorRepository systemActorRepository;

    @Override
    public Optional<SystemActor> findByType(SystemActorType type) {
        return systemActorRepository.findByType(type);
    }
}
