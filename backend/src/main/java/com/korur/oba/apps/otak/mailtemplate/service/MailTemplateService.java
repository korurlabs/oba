package com.korur.oba.apps.otak.mailtemplate.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateCreateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateUpdateRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write side of the mail template domain: create, update and delete of an organization's
 * mail templates.
 *
 * Rules every method keeps:
 * - Access. The caller's role is checked before these methods run, by
 *   {@link com.korur.oba.dojo.security.interceptor.RequiredUserAccessInterceptor} on the
 *   controller endpoint. The service does not check roles again.
 * - Organization scoping. The organization comes from the checked X-Organization-ID
 *   header, never from a body, and every existing mail template is read through
 *   {@link com.korur.oba.apps.otak.mailtemplate.validator.MailTemplateValidator#ensureMailTemplateExists}.
 * - Copies, not references. A mail copied from a mail template keeps its own content, so
 *   nothing done here changes a mail already written.
 * - Transactions. Every method runs in one read-write transaction.
 *
 * Callers: {@link com.korur.oba.apps.otak.mailtemplate.controller.MailTemplateController} only.
 *
 * @see MailTemplateQueryService
 * @see com.korur.oba.apps.otak.mailtemplate.service.impl.MailTemplateServiceImpl
 */
public interface MailTemplateService {

    /**
     * Creates a mail template of the organization. It is complete and usable at once.
     *
     * @param organizationId the organization the caller acts in, which owns the new mail template
     * @param user           the authenticated user, recorded in the log line
     * @param request        the name, language, subject and body
     * @return the created mail template
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_MAIL_TEMPLATE_NAME_TAKEN}
     *         (OTK_STA_014) when another mail template of the organization has the name in the language
     */
    MailTemplateDetailResponse create(UUID organizationId, User user, MailTemplateCreateRequest request);

    /**
     * Replaces every field of a mail template. Mails already copied from it keep their own
     * content.
     *
     * @param organizationId the organization the caller acts in, which owns the mail template
     * @param mailTemplateId the mail template to update
     * @param request        the new name, language, subject and body
     * @return the updated mail template, with its new last-change time
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) when the organization has no mail template with the id
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_MAIL_TEMPLATE_NAME_TAKEN}
     *         (OTK_STA_014) when another mail template of the organization has the name in the language
     */
    MailTemplateDetailResponse update(UUID organizationId, UUID mailTemplateId, MailTemplateUpdateRequest request);

    /**
     * Deletes a mail template. Mails copied from it stay with their content; the database
     * sets their mail template id to null (ON DELETE SET NULL).
     *
     * @param organizationId the organization the caller acts in, which owns the mail template
     * @param mailTemplateId the mail template to delete
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) when the organization has no mail template with the id
     */
    void delete(UUID organizationId, UUID mailTemplateId);
}
