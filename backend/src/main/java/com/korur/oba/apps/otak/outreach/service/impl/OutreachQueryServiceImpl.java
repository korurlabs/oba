package com.korur.oba.apps.otak.outreach.service.impl;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import static com.korur.oba.apps.otak.outreach.constant.service.OutreachServiceLogConstant.*;

import com.korur.oba.apps.otak.contact.dto.response.ContactListResponse;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.service.ContactQueryService;
import com.korur.oba.apps.otak.contact.validator.ContactValidator;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.mailsender.dto.response.MailSenderListResponse;
import com.korur.oba.apps.otak.mailsender.service.MailSenderQueryService;
import com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachQueryRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachContactResponse;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachListResponse;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachMailResponse;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.mapper.OutreachMapper;
import com.korur.oba.apps.otak.outreach.repository.OutreachRepository;
import com.korur.oba.apps.otak.outreach.service.OutreachQueryService;
import com.korur.oba.apps.otak.outreach.specification.OutreachSpecification;
import com.korur.oba.apps.otak.outreach.validator.OutreachValidator;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Implements the reads of an organization's outreaches declared by
 * {@link OutreachQueryService}.
 *
 * An outreach row holds only ids, so its contact, the address of its mail sender and its mail
 * are read through their own domains: contacts through {@link ContactQueryService} and
 * {@link ContactValidator}, senders through {@link MailSenderQueryService} and
 * {@link MailSenderValidator}, the mail through {@link MailQueryService}, and the relation of
 * a list filter through {@link RelationQueryService}. Only the outreach rows are read from
 * this domain's own {@link OutreachRepository}. Every read takes the organization id, so
 * nothing of another organization can enter a response. The mail's attachments are not part
 * of the detail: a client lists them through /storage/attachments, where storage checks the
 * reader's access to the mail.
 *
 * The list reads its related data once per page, not once per row, so a page of outreaches
 * costs a fixed number of queries per relation on the page.
 *
 * All methods run within a read-only transaction.
 *
 * @see OutreachQueryService
 * @see OutreachRepository
 * @see OutreachSpecification
 * @see OutreachMapper
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutreachQueryServiceImpl implements OutreachQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutreachQueryServiceImpl.class);

    /**
     * Order of the outreach list: newest first, with the id as tie-breaker. A fixed order
     * keeps pages stable when two outreaches share a creation time, and keeps arbitrary
     * sortBy values of the request from reaching the query.
     */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, OutreachConstant.FIELD_CREATED_AT)
            .and(Sort.by(Sort.Direction.DESC, OutreachConstant.FIELD_ID));

    private final OutreachRepository outreachRepository;
    private final OutreachMapper outreachMapper;
    private final OutreachValidator outreachValidator;
    private final RelationQueryService relationQueryService;
    private final ContactQueryService contactQueryService;
    private final ContactValidator contactValidator;
    private final MailSenderQueryService mailSenderQueryService;
    private final MailSenderValidator mailSenderValidator;
    private final MailQueryService mailQueryService;
    private final LoggingService loggingService;

    /**
     * Lists one page of the organization's outreaches.
     *
     * Steps: check the optional relation filter, read the page of outreach rows through
     * {@link OutreachRepository} with {@link OutreachSpecification#filterBy}, then read the
     * contacts and the sender addresses of the whole page in bulk with {@link #contacts} and
     * {@link #senderAddresses}, and map each row with {@link OutreachMapper#toListResponse}.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#listOutreaches}.
     *
     * @param organizationId the organization that owns the outreaches
     * @param request        the one-based page, the page size and the optional filters of {@link OutreachQueryRequest}
     * @return the page of outreaches, newest first
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND} if the
     *         relation filter names a relation the organization does not have
     */
    @Override
    public Page<OutreachListResponse> list(UUID organizationId, OutreachQueryRequest request) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, LISTING_OUTREACHES, organizationId, request);

        if (request.getRelationId() != null) {
            // An unknown relation answers not found instead of an empty page.
            relationQueryService.getEntity(organizationId, request.getRelationId());
        }

        // The request counts pages from 1, Spring Data from 0.
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getAmount(), NEWEST_FIRST);
        Page<Outreach> page = outreachRepository.findAll(OutreachSpecification.filterBy(organizationId, request),
                pageable);

        // Related data for the whole page at once, looked up per row from these maps below.
        Map<UUID, OutreachContactResponse> contacts = contacts(organizationId, page.getContent());
        Map<UUID, String> senderAddresses = senderAddresses(organizationId, page.getContent());

        // A deleted contact or sender leaves a null id on the row (ON DELETE SET NULL), which
        // the response shows as no contact or no sender address.
        return page.map(outreach -> outreachMapper.toListResponse(
                outreach,
                outreach.getContactId() == null ? null : contacts.get(outreach.getContactId()),
                outreach.getMailSenderId() == null ? null : senderAddresses.get(outreach.getMailSenderId())));
    }

    /**
     * Reads one outreach of the organization with everything its detail view shows.
     *
     * Steps: read the outreach within the organization through
     * {@link OutreachValidator#ensureOutreachExists}; read its contact within its relation
     * through {@link ContactValidator#ensureContactExists} and its sender through
     * {@link MailSenderValidator#ensureMailSenderExists}, both only when the row still has
     * their ids; read its mail through {@link MailQueryService}; then assemble the response with
     * {@link OutreachMapper#toDetailResponse}.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#getOutreach},
     * and by every write method of
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl} to answer with the
     * stored outreach.
     *
     * @param organizationId the organization that owns the outreach
     * @param outreachId     the outreach identifier
     * @return the outreach with its contact, sender address and mail
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach;
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} only if
     *         a stored id points outside the organization, which the foreign keys prevent
     */
    @Override
    public OutreachDetailResponse get(UUID organizationId, UUID outreachId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, READING_OUTREACH, outreachId, organizationId);

        Outreach outreach = outreachValidator.ensureOutreachExists(organizationId, outreachId);

        // No contact id means the contact was deleted after the outreach was made.
        OutreachContactResponse contact = null;
        if (outreach.getContactId() != null) {
            Contact stored = contactValidator.ensureContactExists(organizationId, outreach.getRelationId(),
                    outreach.getContactId());
            contact = outreachMapper.toContactResponse(stored);
        }

        // No sender id means the outreach was never sent, or its sender was deleted since.
        String mailSenderAddress = outreach.getMailSenderId() == null
                ? null
                : mailSenderValidator.ensureMailSenderExists(organizationId, outreach.getMailSenderId())
                        .getMailAddress();

        // Only a MAIL outreach has a mail.
        OutreachMailResponse mail = null;
        if (outreach.getMailId() != null) {
            Mail stored = mailQueryService.getEntity(organizationId, outreach.getMailId());
            mail = outreachMapper.toMailResponse(stored);
        }

        return outreachMapper.toDetailResponse(outreach, contact, mailSenderAddress, mail);
    }

    /**
     * Reads the contacts of the outreaches on a page, keyed by contact id.
     *
     * Contacts are read as one {@link ContactQueryService#list} per distinct relation on the
     * page rather than one lookup per row, because a page usually holds several outreaches
     * to the same relation. Rows without a contact add no relation to read.
     *
     * Called by {@link #list}.
     *
     * @param organizationId the organization that owns the relations
     * @param outreaches     the outreaches on the page
     * @return the contacts of those relations by contact id, mapped with
     *         {@link OutreachMapper#toContactResponse(ContactListResponse)}
     */
    private Map<UUID, OutreachContactResponse> contacts(UUID organizationId, List<Outreach> outreaches) {
        Set<UUID> relationIds = outreaches.stream()
                .filter(outreach -> outreach.getContactId() != null)
                .map(Outreach::getRelationId)
                .collect(Collectors.toSet());
        // Every contact of each relation goes into the map; the list picks the ones it needs.
        Map<UUID, OutreachContactResponse> contacts = new HashMap<>();
        for (UUID relationId : relationIds) {
            for (ContactListResponse contact : contactQueryService.list(organizationId, relationId)) {
                contacts.put(contact.id(), outreachMapper.toContactResponse(contact));
            }
        }
        return contacts;
    }

    /**
     * Reads the addresses of the organization's mail senders, keyed by sender id, for the
     * outreaches on a page.
     *
     * An organization has a handful of senders, so one {@link MailSenderQueryService#list}
     * covers every row; it is skipped when no row on the page has a sender, which is the
     * common case of a page of drafts.
     *
     * Called by {@link #list}.
     *
     * @param organizationId the organization that owns the senders
     * @param outreaches     the outreaches on the page
     * @return the sender addresses by sender id, or an empty map when no row was sent
     */
    private Map<UUID, String> senderAddresses(UUID organizationId, List<Outreach> outreaches) {
        boolean anySender = outreaches.stream().map(Outreach::getMailSenderId).anyMatch(Objects::nonNull);
        if (!anySender) {
            return Map.of();
        }
        return mailSenderQueryService.list(organizationId).stream()
                .collect(Collectors.toMap(MailSenderListResponse::id, MailSenderListResponse::mailAddress));
    }
}
