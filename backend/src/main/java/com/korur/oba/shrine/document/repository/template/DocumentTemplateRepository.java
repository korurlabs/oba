package com.korur.oba.shrine.document.repository.template;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.document.entity.template.DocumentTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Spring Data repository of {@link DocumentTemplate}, the shrine.document_templates table.
 *
 * Every method that looks up rows takes the organization, because a template belongs to
 * exactly one organization and is only read and written within it. The inherited
 * findById is not used for that reason: it would find a template of any organization.
 * Lists go through {@link JpaSpecificationExecutor} with
 * {@link com.korur.oba.shrine.document.specification.template.DocumentTemplateSpecification#filterBy},
 * which always adds the organization filter.
 *
 * Only the document template domain uses this repository:
 * {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateQueryServiceImpl}
 * for the list, {@link com.korur.oba.shrine.document.service.template.impl.DocumentTemplateServiceImpl}
 * for save and delete, and
 * {@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator} for the
 * lookup by id and the name check. Another domain reads templates through
 * {@link com.korur.oba.shrine.document.service.template.DocumentTemplateQueryService}.
 *
 * @see DocumentTemplate
 * @see com.korur.oba.shrine.document.specification.template.DocumentTemplateSpecification
 */
@Repository
public interface DocumentTemplateRepository
        extends JpaRepository<DocumentTemplate, UUID>, JpaSpecificationExecutor<DocumentTemplate> {

    /**
     * Reads one template within an organization, so a template of another organization is
     * not found. This is the only lookup of a single template, and it is what keeps a
     * foreign id from reaching any read or write.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator#ensureTemplateExists},
     * which turns an empty result into the not found the client sees.
     *
     * @param id             the template identifier
     * @param organizationId the organization that owns the template
     * @return the template, or empty when the organization has no template with the id
     */
    Optional<DocumentTemplate> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Tells whether the organization already has a template with the name, case ignored,
     * in the language. Used on create, where no template is excluded.
     *
     * The same rule is enforced by the unique index on the organization, the lower-case
     * name and the language. This check runs first so the client gets a clear conflict
     * instead of a database error; the index still catches two creates that race.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator#ensureNameFree}
     * when it gets no template to exclude.
     *
     * @param organizationId the organization that owns the templates
     * @param name           the name to look for, already trimmed
     * @param language       the language the name must be unique in
     * @return true when a template of the organization has the name in the language
     */
    boolean existsByOrganizationIdAndNameIgnoreCaseAndLanguage(UUID organizationId, String name,
                                                               DocumentLanguage language);

    /**
     * Tells whether another template of the organization has the name, case ignored, in
     * the language. Used on update, where the template being changed may keep its own name.
     *
     * Called by
     * {@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator#ensureNameFree}
     * when it gets the id of the template being updated.
     *
     * @param organizationId the organization that owns the templates
     * @param name           the name to look for, already trimmed
     * @param language       the language the name must be unique in
     * @param id             the template left out of the check
     * @return true when a template of the organization other than the given one has the name in the language
     */
    boolean existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(UUID organizationId, String name,
                                                                       DocumentLanguage language, UUID id);
}
