package com.korur.oba.apps.otak.mailsender.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.mailsender.constant.service.MailSenderServiceLogConstant.*;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.mapper.MailSenderMapper;
import com.korur.oba.apps.otak.mailsender.repository.MailSenderRepository;
import com.korur.oba.apps.otak.mailsender.service.MailSenderQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implementation of mail sender read operations.
 *
 * All methods run within a read-only transaction. Every read is filtered by the
 * organization id from the checked request header, so another organization's senders are
 * never returned. Senders are read through {@link MailSenderRepository} and mapped by
 * {@link MailSenderMapper#toListResponse}.
 *
 * @see MailSenderQueryService
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MailSenderQueryServiceImpl implements MailSenderQueryService {

    /**
     * Log prefix of this service, derived from the class name.
     */
    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MailSenderQueryServiceImpl.class);

    /**
     * Reads the senders of an organization.
     */
    private final MailSenderRepository mailSenderRepository;

    /**
     * Maps a sender entity to its list item.
     */
    private final MailSenderMapper mailSenderMapper;

    /**
     * Writes the debug line of each read.
     */
    private final LoggingService loggingService;

    /**
     * Lists every mail sender of the organization, ordered by mail address and then by id,
     * so the order is stable even for addresses that differ only in case.
     *
     * The ordering lives in the derived query
     * {@link MailSenderRepository#findAllByOrganizationIdOrderByMailAddressAscIdAsc}, and no
     * paging is applied, because an organization has a handful of senders.
     *
     * Called by {@link com.korur.oba.apps.otak.mailsender.controller.MailSenderController#listMailSenders}
     * and by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#list}.
     *
     * @param organizationId the organization that owns the senders
     * @return the senders, empty when there are none
     * @see MailSenderQueryService#list
     */
    @Override
    public List<MailSenderListResponse> list(UUID organizationId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_MAIL_SENDERS, organizationId);

        return mailSenderRepository.findAllByOrganizationIdOrderByMailAddressAscIdAsc(organizationId).stream()
                .map(mailSenderMapper::toListResponse)
                .toList();
    }
}
