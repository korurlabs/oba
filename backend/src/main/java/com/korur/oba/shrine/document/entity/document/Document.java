package com.korur.oba.shrine.document.entity.document;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;
import com.korur.oba.shrine.document.enums.common.DocumentType;
import com.korur.oba.shrine.document.enums.document.DocumentEntityType;

/**
 * Entity representing a document, a row of shrine.documents: a living business document (a
 * proposal, an NDA, a report, a contract) of one owner, issued by a legal entity of the
 * organization. Like a template it holds a designed HTML file and the PDF rendered from it,
 * which people replace as often as they like.
 *
 * Owner. entityType and entityId name the owner without a foreign key, as on attachments;
 * access to the document is access to the owner (see {@link DocumentEntityType}).
 *
 * Organization scoping. Every lookup goes through
 * {@link com.korur.oba.shrine.document.repository.document.DocumentRepository#findByIdAndOrganizationId}
 * or the organization filter of
 * {@link com.korur.oba.shrine.document.specification.document.DocumentSpecification}. The
 * composite foreign key on (legal_entity_id, organization_id) keeps a document from naming
 * another organization's company and refuses the delete of a legal entity it names.
 *
 * Snapshots. Attaching a document to a mail stores a copy of its PDF as an attachment with
 * document_id set. The copy has its own blob, so replacing the files here never changes what
 * a relation received; deleting the document sets document_id of its snapshots to null.
 *
 * Blob lifecycle. The two blobs belong to this row alone and are removed by
 * {@link com.korur.oba.shrine.document.service.document.impl.DocumentServiceImpl} once the
 * row no longer points at them.
 *
 * @see com.korur.oba.shrine.document.service.document.DocumentQueryService
 */
@Entity
@Table(name = "documents", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Document extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Organization that owns the document, set once on create from the checked request header.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Legal entity of the organization that issues the document; checked on every write.
     */
    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    /**
     * Owner of the document, of the kind entityType names; set once on create.
     */
    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    /**
     * Template the document was created from, for provenance only; null for a document made
     * from a local file, and set to null by the database when the template is deleted.
     */
    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    /**
     * Title shown in lists, stored trimmed; also the file name of its PDF snapshots.
     */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /**
     * Unsigned URL of the HTML blob; null until the files are stored.
     */
    @Column(name = "html_blob_url", length = 500)
    private String htmlBlobUrl;

    /**
     * Unsigned URL of the PDF blob; null until the files are stored.
     */
    @Column(name = "pdf_blob_url", length = 500)
    private String pdfBlobUrl;

    /**
     * User who created the document, set once on create.
     */
    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;

    /**
     * User who changed the fields or files last; with updatedAt it tells who edited it when.
     */
    @Column(name = "updated_by_id", nullable = false)
    private UUID updatedById;

    // -------------Enums-----------//

    /**
     * Kind of owner; set once on create.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private DocumentEntityType entityType;

    /**
     * Kind of document; may change on update.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false)
    private DocumentType type;

    /**
     * Language the content is written in; may change on update.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "language", nullable = false)
    private DocumentLanguage language;

    // -------------Derived-----------//

    /**
     * Tells whether both files are stored. Derived, not a column; both files are always
     * uploaded together, as on a template.
     *
     * @return true when the HTML and the PDF are stored
     */
    public boolean isReady() {
        return htmlBlobUrl != null && pdfBlobUrl != null;
    }
}
