package com.korur.oba.shrine.storage.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 2. Third-party
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.storage.entity.attachment.Attachment;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.repository.attachment.AttachmentRepository;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the organization-scoped attachment reads against the Flyway-migrated PostgreSQL schema.
 * An attachment of another organization must never come back from a list, a lookup by id or the
 * duplicate check, even when it hangs on the same owner id or carries the same file hash, because
 * those reads serve requests and a hit would reveal a file of another organization.
 *
 * Each test creates two organizations and removes them afterwards, which removes their
 * attachments through the foreign key, so a database shared with a running backend is not
 * disturbed.
 */
class AttachmentRepositoryIT extends ObaIntegrationTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID organizationA;
    private UUID organizationB;
    private UUID sharedOwnerId;

    @BeforeEach
    void setUp() {
        organizationA = organization();
        organizationB = organization();
        sharedOwnerId = UUID.randomUUID();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shrine.organizations WHERE id IN (?, ?)", organizationA, organizationB);
    }

    private UUID organization() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                id, "itest org " + id);
        return id;
    }

    private UUID attachment(UUID organization, String entityType, UUID entityId, String fileHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shrine.attachments (id, url, file_name, entity_type, entity_id, file_hash, organization_id)
                VALUES (?, ?, 'file.pdf', ?::shrine_storage_entity_type_enum, ?, ?, ?)
                """, id, "https://blob.example.test/" + id, entityType, entityId, fileHash, organization);
        return id;
    }

    private static List<UUID> ids(List<Attachment> attachments) {
        return attachments.stream().map(Attachment::getId).toList();
    }

    @Test
    void findByIdAndOrganizationId_attachmentOfAnotherOrganization_returnsEmpty() {
        UUID ofA = attachment(organizationA, "TICKET", sharedOwnerId, HASH);

        assertThat(attachmentRepository.findByIdAndOrganizationId(ofA, organizationB)).isEmpty();
        assertThat(attachmentRepository.findByIdAndOrganizationId(ofA, organizationA))
                .get().extracting(Attachment::getOrganizationId).isEqualTo(organizationA);
    }

    @Test
    void findByEntityTypeAndEntityIdAndOrganizationId_sameOwnerIdInTwoOrganizations_returnsOnlyOwn() {
        UUID ofA = attachment(organizationA, "TICKET", sharedOwnerId, HASH);
        UUID ofB = attachment(organizationB, "TICKET", sharedOwnerId, HASH);

        assertThat(ids(attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationId(
                StorageEntityType.TICKET, sharedOwnerId, organizationA))).containsExactly(ofA);
        assertThat(ids(attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationId(
                StorageEntityType.TICKET, sharedOwnerId, organizationB))).containsExactly(ofB);
    }

    @Test
    void listByEntityOrdered_ownerOfAnotherOrganization_returnsNothingAndOwnRowsInOrder() {
        UUID first = attachment(organizationA, "MAIL", sharedOwnerId, HASH);
        UUID second = attachment(organizationA, "MAIL", sharedOwnerId, "b".repeat(64));
        jdbc.update("UPDATE shrine.attachments SET created_at = created_at - INTERVAL '1 minute' WHERE id = ?", first);
        attachment(organizationB, "MAIL", sharedOwnerId, HASH);

        assertThat(attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                StorageEntityType.MAIL, sharedOwnerId, UUID.randomUUID())).isEmpty();
        assertThat(ids(attachmentRepository.findByEntityTypeAndEntityIdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                StorageEntityType.MAIL, sharedOwnerId, organizationA))).containsExactly(first, second);
    }

    @Test
    void listByEntitiesOrdered_ownersOfAnotherOrganization_returnsNothing() {
        UUID otherOwner = UUID.randomUUID();
        UUID ofA = attachment(organizationA, "RELATION", sharedOwnerId, HASH);
        attachment(organizationB, "RELATION", otherOwner, HASH);

        List<UUID> found = ids(attachmentRepository
                .findByEntityTypeAndEntityIdInAndOrganizationIdOrderByCreatedAtAscIdAsc(
                        StorageEntityType.RELATION, List.of(sharedOwnerId, otherOwner), organizationA));

        assertThat(found).containsExactly(ofA);
        assertThat(attachmentRepository.findByEntityTypeAndEntityIdInAndOrganizationIdOrderByCreatedAtAscIdAsc(
                StorageEntityType.RELATION, List.of(otherOwner), organizationA)).isEmpty();
    }

    @Test
    void existsByEntityTypeAndFileHashAndOrganizationId_hashOnlyInAnotherOrganization_returnsFalse() {
        attachment(organizationB, "TICKET", sharedOwnerId, HASH);

        assertThat(attachmentRepository.existsByEntityTypeAndFileHashAndOrganizationId(
                StorageEntityType.TICKET, HASH, organizationA)).isFalse();
        assertThat(attachmentRepository.existsByEntityTypeAndFileHashAndOrganizationId(
                StorageEntityType.TICKET, HASH, organizationB)).isTrue();
    }
}
