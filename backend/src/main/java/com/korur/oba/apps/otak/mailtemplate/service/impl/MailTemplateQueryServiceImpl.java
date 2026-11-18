package com.korur.oba.apps.otak.mailtemplate.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.mailtemplate.constant.service.MailTemplateServiceLogConstant.*;

import com.korur.oba.apps.otak.mailtemplate.constant.MailTemplateConstant;
import com.korur.oba.apps.otak.mailtemplate.dto.request.MailTemplateQueryRequest;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateDetailResponse;
import com.korur.oba.apps.otak.mailtemplate.dto.response.MailTemplateListResponse;
import com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate;
import com.korur.oba.apps.otak.mailtemplate.mapper.MailTemplateMapper;
import com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository;
import com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService;
import com.korur.oba.apps.otak.mailtemplate.specification.MailTemplateSpecification;
import com.korur.oba.apps.otak.mailtemplate.validator.MailTemplateValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implementation of the mail template reads declared by {@link MailTemplateQueryService}.
 *
 * Every method runs in a read-only transaction (the class-level {@link Transactional}); a
 * caller that already holds a read-write transaction, such as the outreach that copies a
 * mail template, joins it. The single lookup by id and organization is
 * {@link MailTemplateValidator#ensureMailTemplateExists}, shared with the command side, so
 * both answer a foreign or missing id with the same not found.
 *
 * @see MailTemplateServiceImpl
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailTemplateQueryServiceImpl implements MailTemplateQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailTemplateQueryServiceImpl.class);

    // A fixed order, with the id as tie-breaker, keeps pages stable and keeps arbitrary sortBy
    // values from reaching the query. The language groups the translations of one name.
    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, MailTemplateConstant.FIELD_NAME)
            .and(Sort.by(Sort.Direction.ASC, MailTemplateConstant.FIELD_LANGUAGE))
            .and(Sort.by(Sort.Direction.ASC, MailTemplateConstant.FIELD_ID));

    private final MailTemplateRepository mailTemplateRepository;
    private final MailTemplateMapper mailTemplateMapper;
    private final MailTemplateValidator mailTemplateValidator;
    private final LoggingService loggingService;

    /**
     * {@inheritDoc}
     *
     * Builds a page request from the one-based page of the request and the fixed BY_NAME
     * order, runs {@link MailTemplateRepository#findAll} with
     * {@link MailTemplateSpecification#filterBy}, and maps each row with
     * {@link MailTemplateMapper#toListResponse}.
     */
    @Override
    public Page<MailTemplateListResponse> list(UUID organizationId, MailTemplateQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_MAIL_TEMPLATES, organizationId, request);

        // The request counts pages from 1, Spring Data from 0.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), BY_NAME);
        return mailTemplateRepository
                .findAll(MailTemplateSpecification.filterBy(organizationId, request), pageable)
                .map(mailTemplateMapper::toListResponse);
    }

    /**
     * {@inheritDoc}
     *
     * Looks the mail template up through {@link MailTemplateValidator#ensureMailTemplateExists}
     * and maps it with {@link MailTemplateMapper#toDetailResponse}.
     */
    @Override
    public MailTemplateDetailResponse get(UUID organizationId, UUID mailTemplateId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_MAIL_TEMPLATE, mailTemplateId, organizationId);

        return mailTemplateMapper.toDetailResponse(
                mailTemplateValidator.ensureMailTemplateExists(organizationId, mailTemplateId));
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link MailTemplateValidator#ensureMailTemplateExists}. No debug line is
     * written, because the caller logs the copy it makes.
     */
    @Override
    public MailTemplate getEntity(UUID organizationId, UUID mailTemplateId) {
        return mailTemplateValidator.ensureMailTemplateExists(organizationId, mailTemplateId);
    }
}
