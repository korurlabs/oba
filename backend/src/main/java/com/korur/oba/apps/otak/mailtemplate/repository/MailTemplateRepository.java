package com.korur.oba.apps.otak.mailtemplate.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Spring Data repository of {@link MailTemplate}, the otak.mail_templates table.
 *
 * Every method that looks up rows takes the organization, because a mail template belongs
 * to exactly one organization and is only read and written within it. The inherited
 * findById is not used for that reason: it would find a mail template of any organization.
 * Lists go through {@link JpaSpecificationExecutor} with
 * {@link com.korur.oba.apps.otak.mailtemplate.specification.MailTemplateSpecification#filterBy},
 * which always adds the organization filter.
 *
 * Only the mail template domain uses this repository. Another domain reads mail templates
 * through {@link com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService}.
 *
 * @see MailTemplate
 */
@Repository
public interface MailTemplateRepository
        extends JpaRepository<MailTemplate, UUID>, JpaSpecificationExecutor<MailTemplate> {

    /**
     * Reads one mail template within an organization, so a mail template of another
     * organization is not found. This is the only lookup of a single mail template.
     *
     * Called by
     * {@link com.korur.oba.apps.otak.mailtemplate.validator.MailTemplateValidator#ensureMailTemplateExists}.
     *
     * @param id             the mail template identifier
     * @param organizationId the organization that owns the mail template
     * @return the mail template, or empty when the organization has no mail template with the id
     */
    Optional<MailTemplate> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Tells whether the organization already has a mail template with the name, case
     * ignored, in the language. Used on create. The unique index on the organization, the
     * lower-case name and the language repeats the rule and catches two creates that race.
     *
     * @param organizationId the organization that owns the mail templates
     * @param name           the name to look for, already trimmed
     * @param language       the language the name must be unique in
     * @return true when a mail template of the organization has the name in the language
     */
    boolean existsByOrganizationIdAndNameIgnoreCaseAndLanguage(UUID organizationId, String name,
                                                               DocumentLanguage language);

    /**
     * Tells whether another mail template of the organization has the name, case ignored,
     * in the language. Used on update, where the mail template being changed may keep its
     * own name.
     *
     * @param organizationId the organization that owns the mail templates
     * @param name           the name to look for, already trimmed
     * @param language       the language the name must be unique in
     * @param id             the mail template left out of the check
     * @return true when a mail template of the organization other than the given one has the name in the language
     */
    boolean existsByOrganizationIdAndNameIgnoreCaseAndLanguageAndIdNot(UUID organizationId, String name,
                                                                       DocumentLanguage language, UUID id);
}
