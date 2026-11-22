package com.korur.oba.shrine.document.repository.document;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.entity.document.Document;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;

/**
 * Spring Data repository of shrine.documents. Every read that serves a request is scoped to
 * an organization; only the cascade of an owner delete reads by owner alone, because the
 * owner's domain has already proved which organization it belongs to.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    /**
     * The single lookup of one document, scoped to the organization, so a document of
     * another organization is a missing one.
     *
     * @param id             the document
     * @param organizationId the organization of the request
     * @return the document, or empty
     */
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * The documents of one owner, for the cascade when the owner is deleted. Served by
     * documents_entity_type_entity_id_idx.
     *
     * @param entityType the kind of owner
     * @param entityId   the owner
     * @return its documents
     */
    List<Document> findByEntityTypeAndEntityId(DocumentEntityType entityType, UUID entityId);

    /**
     * Tells whether a document of the organization names the legal entity, so its delete can
     * be refused with a clear code before the foreign key would refuse it.
     *
     * @param organizationId the organization
     * @param legalEntityId  the legal entity
     * @return true when at least one document names it
     */
    boolean existsByOrganizationIdAndLegalEntityId(UUID organizationId, UUID legalEntityId);
}
