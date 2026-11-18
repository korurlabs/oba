package com.korur.oba.apps.kaya.blob.integration;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 2. Third-party

// 3. Java SDK
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.support.ObaIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blob reads other domains use: a blob is found by its SHA-256 only within its own organization.
 */
class BlobQueryServiceIT extends ObaIntegrationTest {

    @Autowired
    private BlobQueryService blobQueryService;

    private UUID insertBlob(Organization organization, byte[] content, String sha256) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO kaya.blobs (organization_id, sha256, content, size) VALUES (?, ?, ?, ?)
                RETURNING id
                """, UUID.class, organization.getId(), sha256, content, content.length);
    }

    @Test
    void blobQueryService_readsOnlyTheOrganizationsBlobs() throws Exception {
        Organization organization = createOrganization(OrganizationStatus.ACTIVE);
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        insertBlob(organization, content, sha256);
        String unknown = "0".repeat(64);

        assertArrayEquals(content, blobQueryService.getEntityBySha256(organization.getId(), sha256).getContent());
        assertEquals(Set.of(sha256), blobQueryService.getStoredSha256s(organization.getId(), List.of(sha256, unknown)));
        assertTrue(blobQueryService.getStoredSha256s(organization.getId(), List.of()).isEmpty());

        Organization other = createOrganization(OrganizationStatus.ACTIVE);
        assertThrows(NotFoundException.class, () -> blobQueryService.getEntityBySha256(other.getId(), sha256));
        assertTrue(blobQueryService.getStoredSha256s(other.getId(), List.of(sha256)).isEmpty());
    }
}
