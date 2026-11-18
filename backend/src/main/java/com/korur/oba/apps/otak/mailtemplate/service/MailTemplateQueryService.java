package com.korur.oba.apps.otak.mailtemplate.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateQueryRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateDetailResponse;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateListResponse;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;

/**
 * Read side of the mail template domain: the list, the detail and the entity that another
 * domain copies a mail from.
 *
 * Every read is scoped to the organization the caller acts in, which comes from the
 * request's checked organization header, and a mail template of another organization is
 * answered exactly like one that does not exist. Writes live in {@link MailTemplateService}.
 *
 * Callers: {@link com.korur.oba.apps.otak.mailtemplate.controller.MailTemplateController} for
 * the list and detail endpoints, open to every organization MEMBER; other domains through
 * {@link #getEntity(UUID, UUID)}, never through
 * {@link com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository}.
 *
 * @see com.korur.oba.apps.otak.mailtemplate.service.impl.MailTemplateQueryServiceImpl
 */
public interface MailTemplateQueryService {

    /**
     * Lists one page of the organization's mail templates, sorted by name, then language,
     * then id. The order is fixed so the translations of one mail sit next to each other
     * and pages stay stable; the sortBy and sortDirection of the request are ignored.
     *
     * @param organizationId the organization the caller acts in; only its mail templates are listed
     * @param request        the one-based page and page size, the optional name search and language filter
     * @return the page of list items; an empty page when nothing matches, never an error
     */
    Page<MailTemplateListResponse> list(UUID organizationId, MailTemplateQueryRequest request);

    /**
     * Reads one mail template of the organization with every field.
     *
     * @param organizationId the organization the caller acts in
     * @param mailTemplateId the mail template identifier
     * @return the mail template detail
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) when the organization has no mail template with the id
     */
    MailTemplateDetailResponse get(UUID organizationId, UUID mailTemplateId);

    /**
     * Reads one mail template entity of the organization, for another domain that copies
     * its subject, body and language into a mail of its own. The caller must not change the
     * entity: the mail is a copy, so a later template edit leaves it as it was.
     *
     * @param organizationId the organization the caller acts in
     * @param mailTemplateId the mail template identifier
     * @return the mail template entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         (OTK_NOT_010) when the organization has no mail template with the id
     */
    MailTemplate getEntity(UUID organizationId, UUID mailTemplateId);
}
