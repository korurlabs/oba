package com.korur.oba.shrine.document.entity.template;

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

/**
 * Entity representing a document template, a row of shrine.document_templates: a reusable
 * document of an organization, such as a proposal or an NDA, whose PDF an app copies as an
 * attachment. Reusable mails are not document templates: Otak keeps them as mail templates.
 *
 * Content. Every template holds the blob URLs of an HTML file and the PDF rendered from
 * it, whatever its {@link DocumentType}. It is created without files and is not ready for
 * use until both are stored (see {@link #isReady()}).
 *
 * Organization scoping. Every template belongs to one organization and is only read and
 * written within it: every lookup goes through
 * {@link com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository#findByIdAndOrganizationId}
 * or the organization filter of
 * {@link com.korur.oba.shrine.document.specification.template.DocumentTemplateSpecification}.
 * Its name is unique per organization and language with case ignored, which a unique
 * index on the lower-case name enforces. Deleting the organization deletes its templates.
 *
 * Legal entity. A template may name the legal entity of its organization that issues it;
 * null means every legal entity of the organization may use it. A composite foreign key on
 * (legal_entity_id, organization_id) keeps a template from naming another organization's
 * company, and refuses the delete of a legal entity that a template still names.
 *
 * Copies, not references. An attachment made from a template gets its own copy of the
 * PDF blob (see
 * {@link com.korur.oba.shrine.document.service.template.DocumentTemplateService#copyToAttachment}),
 * so a later change to the template, or its delete, does not change the attachment. Rows
 * are deleted outright.
 *
 * Blob lifecycle. The two blobs belong to this row alone. When the files are replaced or
 * the row is deleted,
 * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}
 * removes the old blobs through
 * {@link com.korur.oba.shrine.document.helper.common.DocumentFileStorage#deleteQuietly}
 * once the row no longer points at them.
 *
 * The id and the created and updated times come from {@link BaseEntity}; createdById and
 * updatedById keep who created the template and who changed its fields or files last.
 *
 * @see com.korur.oba.shrine.document.repository.template.DocumentTemplateRepository
 * @see com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService
 */
@Entity
@Table(name = "document_templates", schema = "shrine")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DocumentTemplate extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Name shown in the template list, stored trimmed; at most 100 characters. Unique per
     * organization and language, case ignored.
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * Unsigned URL of the HTML file in the private blob container; null until the files are
     * stored. Signed with a
     * short-lived Shared Access Signature only when a response is built, so the stored
     * value never expires.
     */
    @Column(name = "html_blob_url", length = 500)
    private String htmlBlobUrl;

    /**
     * Unsigned URL of the PDF file in the private blob container; null until the files are
     * stored. Signed like
     * {@link #htmlBlobUrl} on the way out.
     */
    @Column(name = "pdf_blob_url", length = 500)
    private String pdfBlobUrl;

    /**
     * Organization that owns the template, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Legal entity of the organization that issues the template; null when every legal entity
     * of the organization may use it. Checked against the organization on every write, and
     * held to it by the composite foreign key.
     */
    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    /**
     * User who created the template, set once on create.
     */
    @Column(name = "created_by_id", nullable = false, updatable = false)
    private UUID createdById;

    /**
     * User who changed the template's fields or files last; the creator until the first change.
     */
    @Column(name = "updated_by_id", nullable = false)
    private UUID updatedById;

    // -------------Enums-----------//

    /**
     * Kind of document; may change on update, because every type holds the same pair of files.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false)
    private DocumentType type;

    /**
     * Language the content is written in; may change on update, as long as the name stays
     * unique in the new language.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "language", nullable = false)
    private DocumentLanguage language;

    // -------------Derived-----------//

    /**
     * Tells whether the template is complete: both its HTML and its PDF file are stored.
     * Derived, not a column. Both files are always uploaded together, so a ready template is
     * also one whose PDF can be copied onto an attachment.
     *
     * Read by the template responses, as their ready flag. The copy onto an attachment checks
     * only the PDF, in
     * {@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator#ensureTemplateReady}.
     *
     * @return true when the template is ready for use
     */
    public boolean isReady() {
        return htmlBlobUrl != null && pdfBlobUrl != null;
    }
}
