package com.korur.oba.apps.kaya.repository.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.BadRequestException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.apps.kaya.repository.entity.Repository;
import com.korur.oba.apps.kaya.repository.enums.RepositoryStatus;
import com.korur.oba.apps.kaya.repository.service.RepositoryService;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository connect is called by ingest inside its own transaction; the native
 * ON CONFLICT DO NOTHING inserts are what let two recorders report the same repository at
 * once, so the race test fires the calls through a barrier.
 */
class RepositoryConnectIT extends ObaIntegrationTest {

    private static final int TIMEOUT_SECONDS = 30;

    @Autowired
    private RepositoryService repositoryService;

    private String remote() {
        return "github.com/o" + uniqueSuffix() + "/village";
    }

    private long repositoryCount(UUID organizationId, String remoteUrl) {
        return count("SELECT count(*) FROM kaya.repositories WHERE organization_id = ? AND remote_url = ?",
                organizationId, remoteUrl);
    }

    @Test
    void connectRepository_newRemote_createsActiveRepository() {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        String remote = remote();

        Repository result = repositoryService.connectRepository(organization.getId(), "https://" + remote, " Village ");

        assertEquals(remote, result.getRemoteUrl());
        assertEquals("Village", result.getName());
        assertEquals(RepositoryStatus.ACTIVE, result.getStatus());
    }

    @Test
    void connectRepository_noName_usesLastPathSegment() {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        String remote = remote();

        Repository result = repositoryService.connectRepository(organization.getId(), "https://" + remote, null);

        assertEquals("village", result.getName());
    }

    @Test
    void connectRepository_sameRepositoryInDifferentForms_returnsOneRow() {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        String owner = "o" + uniqueSuffix();

        Repository https = repositoryService.connectRepository(organization.getId(),
                "https://github.com/" + owner + "/Village.git", "Village");
        Repository ssh = repositoryService.connectRepository(organization.getId(),
                "git@GitHub.com:" + owner + "/village", "Renamed by the recorder");

        assertEquals(https.getId(), ssh.getId());
        assertEquals("Village", ssh.getName());
        assertEquals(1L, repositoryCount(organization.getId(), "github.com/" + owner + "/village"));
    }

    @Test
    void connectRepository_sameRemoteInTwoOrganizations_createsTwoRepositories() {
        Organization first = createOrganization(OrganizationStatus.ACTIVE);
        Organization second = createOrganization(OrganizationStatus.ACTIVE);
        String remote = remote();

        Repository a = repositoryService.connectRepository(first.getId(), "https://" + remote, null);
        Repository b = repositoryService.connectRepository(second.getId(), "https://" + remote, null);

        assertFalse(a.getId().equals(b.getId()));
    }

    @Test
    void connectRepository_credentialsInRemote_areNeverStored() {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        String remote = remote();

        Repository result = repositoryService.connectRepository(organization.getId(),
                "https://user:s3cretToken@" + remote, null);

        assertEquals(remote, result.getRemoteUrl());
        assertEquals(0L, count("SELECT count(*) FROM kaya.repositories WHERE remote_url LIKE '%s3cretToken%'"));
    }

    @Test
    void connectRepository_archivedRepository_throwsValidationAndKeepsStatus() {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        String remote = remote();
        Repository connected = repositoryService.connectRepository(organization.getId(), "https://" + remote, null);
        jdbcTemplate.update("UPDATE kaya.repositories SET status = 'ARCHIVED' WHERE id = ?", connected.getId());

        ValidationException exception = assertThrows(ValidationException.class, () ->
                repositoryService.connectRepository(organization.getId(), "https://" + remote, null));

        assertEquals("KAY_VAL_002", exception.getErrorCode());
        assertEquals(connected.getId().toString(), exception.getErrorValues().get("repositoryId"));
    }

    @Test
    void connectRepository_invalidRemote_throwsBadRequestAndStoresNothing() {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);

        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                repositoryService.connectRepository(organization.getId(), "https://tok3n@github.com/solo", null));

        assertEquals("GEN_API_001", exception.getErrorCode());
        assertFalse(exception.getErrorValues().toString().contains("tok3n"));
        assertEquals(0L, count("SELECT count(*) FROM kaya.repositories WHERE organization_id = ?",
                organization.getId()));
    }

    @Test
    void connectRepository_sixRecordersAtOnce_oneRepositoryNoFailure() throws Exception {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        String remote = remote();
        int callers = 6;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            CyclicBarrier barrier = new CyclicBarrier(callers);
            List<Future<Repository>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                Callable<Repository> task = () -> {
                    barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return repositoryService.connectRepository(organization.getId(), "https://" + remote, null);
                };
                futures.add(executor.submit(task));
            }
            UUID id = null;
            for (Future<Repository> future : futures) {
                try {
                    Repository result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    id = id == null ? result.getId() : id;
                    assertEquals(id, result.getId());
                } catch (ExecutionException e) {
                    throw new AssertionError("a concurrent connect failed", e.getCause());
                }
            }
            assertTrue(id != null);
            assertEquals(1L, repositoryCount(organization.getId(), remote));
        } finally {
            executor.shutdownNow();
        }
    }
}
