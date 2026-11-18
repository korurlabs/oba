package com.korur.oba.apps.kaya.repository.mock_data;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;

/**
 * Builders for repository entities used by the tests. Every entity gets a random id,
 * because the services compare and log ids.
 */
public final class RepositoryMocks {

    private RepositoryMocks() {
    }

    public static Repository repository(UUID organizationId, String remoteUrl, RepositoryStatus status) {
        return Repository.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .name("Village")
                .remoteUrl(remoteUrl)
                .status(status)
                .build();
    }
}
