package com.korur.oba.apps.otak.outreach.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.otak.outreach.constant.service.OutreachServiceLogConstant.*;

import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.validator.ContactValidator;
import com.korur.oba.apps.otak.documenttemplate.service.DocumentTemplateService;
import com.korur.oba.apps.otak.mail.entity.Mail;
import com.korur.oba.apps.otak.mail.service.MailQueryService;
import com.korur.oba.apps.otak.mail.service.MailService;
import com.korur.oba.apps.otak.mailsender.entity.MailSender;
import com.korur.oba.apps.otak.mailsender.validator.MailSenderValidator;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachSendRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachUpdateRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.apps.otak.outreach.mapper.OutreachMapper;
import com.korur.oba.apps.otak.outreach.repository.OutreachRepository;
import com.korur.oba.apps.otak.outreach.service.OutreachQueryService;
import com.korur.oba.apps.otak.outreach.service.OutreachService;
import com.korur.oba.apps.otak.outreach.validator.OutreachValidator;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.connect.notification.client.MsGraphClient;
import com.korur.oba.connect.notification.dto.AttachmentEmail;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentContent;
import com.korur.oba.shrine.storage.enums.common.StorageEntityType;
import com.korur.oba.shrine.storage.service.attachment.AttachmentQueryService;

/**
 * Implements writes to an organization's outreaches: create, update, send, record as done
 * and delete an outreach.
 *
 * Organization scoping: the caller's role in the organization is checked once per request
 * by the access interceptor, from the X-Organization-ID header, before any method here
 * runs. Every outreach, relation, contact, mail sender and document template is then read
 * within that organization, through {@link OutreachRepository#findByIdAndOrganizationId},
 * {@link OutreachRepository#findByIdAndOrganizationIdForUpdate},
 * {@link RelationQueryService#getEntity}, {@link ContactValidator#ensureContactExists},
 * {@link MailSenderValidator#ensureMailSenderExists} and
 * {@link DocumentTemplateService#copyToAttachment},
 * so a row of another organization answers the same not found as a missing one and
 * reveals nothing to another tenant.
 *
 * Channels: the {@link OutreachType} is fixed on create. Only a MAIL outreach has content:
 * its subject and body live in its own {@link Mail}, written through {@link MailService} and
 * read through {@link MailQueryService}; the files the mail carries are shrine attachments with
 * the mail as parent, uploaded and deleted through /storage/attachments, where storage checks
 * that the outreach is not SENT. A delete removes the mail and its attachments with the outreach. A WHATSAPP or
 * CALL outreach happens outside Oba: it has no mail, cannot be sent or carry attachments, and
 * is recorded as done by {@link #complete}, which calls nothing external. The Microsoft Graph message has the same simple name
 * as the mail entity, so it is written with its full package name here.
 *
 * Status and row locks: an outreach is {@link OutreachStatus#DRAFT} until it is sent,
 * {@link OutreachStatus#SENT} from the moment a send claims it, and
 * {@link OutreachStatus#FAILED} when building or sending the mail failed. A SENT outreach is
 * the record of what the contact received, so {@link OutreachValidator#ensureEditable}
 * refuses every write to it. Update, complete and delete read the row
 * with a pessimistic write lock, so their editable check and their write run wholly before or
 * wholly after a send's claim, which takes the same row lock.
 *
 * Send: the one write that is not a single transaction, because it calls Microsoft Graph,
 * which cannot be rolled back, between its database writes. See {@link #send} for the
 * claim, the shared mailbox, the attachments read from blob storage and {@link #markFailed}.
 *
 * Called only by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController}
 * through {@link OutreachService}. Every write that returns the outreach reads it back
 * through {@link OutreachQueryService#get}, so the response is exactly what a later read
 * shows.
 *
 * @see OutreachService
 * @see OutreachQueryService
 * @see OutreachValidator
 * @see OutreachRepository
 */
@Service
@RequiredArgsConstructor
public class OutreachServiceImpl implements OutreachService {

    /**
     * Log prefix of this service, derived from the class name.
     */
    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutreachServiceImpl.class);

    /**
     * Reads and writes outreach rows and the send columns; also
     * takes the row lock and runs the claim of a send.
     */
    private final OutreachRepository outreachRepository;

    /**
     * Maps requests to the outreach entity.
     */
    private final OutreachMapper outreachMapper;

    /**
     * Checks existence within the organization, editability, the mail source and the
     * preconditions of a send.
     */
    private final OutreachValidator outreachValidator;

    /**
     * Reads the outreach back after a write, so every write answers with what a read shows.
     */
    private final OutreachQueryService outreachQueryService;

    /**
     * Checks that the relation of a new outreach belongs to the organization.
     */
    private final RelationQueryService relationQueryService;

    /**
     * Checks that the contact belongs to the outreach's relation within the organization.
     */
    private final ContactValidator contactValidator;

    /**
     * Writes the outreach's own mail: copy from a template, create, update and delete.
     */
    private final MailService mailService;

    /**
     * Reads the outreach's mail at send time.
     */
    private final MailQueryService mailQueryService;

    /**
     * Copies the PDF of each document template of a new MAIL outreach onto its mail.
     */
    private final DocumentTemplateService documentTemplateService;

    /**
     * Reads the bytes of the mail's attachments, in upload order, at send time.
     */
    private final AttachmentQueryService attachmentQueryService;

    /**
     * Resolves the mail sender within the organization and, for a shared mailbox, its
     * Microsoft Graph object id at send time.
     */
    private final MailSenderValidator mailSenderValidator;

    /**
     * Sends the built mail through Microsoft Graph.
     */
    private final MsGraphClient msGraphClient;

    /**
     * Runs the claim and the release of a send each in its own short transaction, because
     * {@link #send} itself runs without one.
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * The UTC clock for the send and failure timestamps, replaceable in tests.
     */
    private final Clock clock;

    /**
     * Writes the audit and error log lines of every write.
     */
    private final LoggingService loggingService;

    /**
     * Creates a DRAFT outreach, for MAIL with its own mail and a copy of each requested
     * document template's PDF as an attachment of that mail, all in one transaction, so a
     * failure at any step leaves neither the outreach nor its mail nor an attachment row.
     *
     * Steps, in this order:
     * - the relation is read through {@link RelationQueryService#getEntity} and the contact
     *   checked against it through {@link ContactValidator#ensureContactExists}, both within
     *   the organization;
     * - {@link OutreachValidator#ensureMailFitsType} requires exactly one of a mail template
     *   and a mail written by hand for MAIL, and none of them and no document templates for
     *   WHATSAPP and CALL;
     * - for MAIL only, the mail is written through {@link MailService#createFromTemplate} or
     *   {@link MailService#create};
     * - the outreach row is saved with the requested type, {@link OutreachStatus#DRAFT} and
     *   the mail's id, or no mail id for WHATSAPP and CALL, and flushed;
     * - for MAIL only, each document template is copied onto the mail through
     *   {@link DocumentTemplateService#copyToAttachment}, in the order of the request, a
     *   repeated id once. This runs after the flush, because storage checks WRITE on the mail,
     *   which reads the outreach row with a lock and finds it DRAFT. The blobs of the copies
     *   are outside the transaction, so a later failure leaves them as orphans, as an upload
     *   that rolls back does.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#createOutreach}
     * through {@link OutreachService#create}.
     *
     * @param organizationId the organization that owns the relation, from the checked request header
     * @param user           the authenticated user, recorded in the audit log and checked for write access to the mail
     * @param request        the relation, the contact, the title, the mail source and the document template ids
     * @return the created outreach, read back through {@link OutreachQueryService#get}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         or {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_DOCUMENT_TEMPLATE_FOUND}
     *         if the relation, the contact of the relation, the mail template or a document template
     *         is missing in the organization
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION} if a MAIL
     *         outreach has both or neither mail sources, or a WHATSAPP or CALL outreach has any mail
     *         field, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_DOCUMENT_NOT_USABLE}
     *         if a document template has no PDF
     * @see OutreachService#create
     */
    @Override
    @Transactional
    public OutreachDetailResponse create(UUID organizationId, User user, OutreachCreateRequest request) {
        UUID relationId = request.relationId();
        // Read only for its check: a relation of another organization answers not found.
        relationQueryService.getEntity(organizationId, relationId);
        // The contact must belong to this relation, not only to the organization.
        contactValidator.ensureContactExists(organizationId, relationId, request.contactId());
        outreachValidator.ensureMailFitsType(request.type(), request.mailTemplateId(), request.mail(),
                request.documentTemplateIds());

        // Only a MAIL outreach has a mail. Its row comes first: the outreach's foreign key
        // points at it.
        UUID mailId = null;
        if (request.type() == OutreachType.MAIL) {
            Mail mail = request.mailTemplateId() != null
                    ? mailService.createFromTemplate(organizationId, relationId, request.mailTemplateId())
                    : mailService.create(organizationId, relationId, request.mail());
            mailId = mail.getId();
        }

        // The server owns the organization, the status and the mail link; the mapper
        // ignores them, so a client can never set them.
        Outreach outreach = outreachMapper.toEntity(request);
        outreach.setTitle(request.title().trim());
        outreach.setOrganizationId(organizationId);
        outreach.setStatus(OutreachStatus.DRAFT);
        outreach.setMailId(mailId);
        // Flushed so the read below and the timestamps see the stored row.
        outreach = outreachRepository.saveAndFlush(outreach);

        // After the flush: storage checks WRITE on the mail, which reads this row's status
        // with a lock. A repeated id would copy the same PDF twice, which storage refuses.
        if (mailId != null && request.documentTemplateIds() != null) {
            for (UUID documentTemplateId : new LinkedHashSet<>(request.documentTemplateIds())) {
                documentTemplateService.copyToAttachment(user, organizationId, documentTemplateId,
                        StorageEntityType.MAIL, mailId);
            }
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_CREATED, outreach.getId(), organizationId,
                relationId, outreach.getType(), mailId, user.getId());

        return outreachQueryService.get(organizationId, outreach.getId());
    }

    /**
     * Changes the contact, the title and, for MAIL, the mail of an outreach that was not sent,
     * in one transaction.
     *
     * The outreach is read with a row lock by {@link #lockOutreach}, held until the
     * transaction commits, so {@link OutreachValidator#ensureEditable} and the writes below
     * run wholly before or wholly after a send's claim and never change a mail that is going
     * out. The new contact must belong to the outreach's relation, which never changes.
     * {@link OutreachValidator#ensureUpdateMailFitsType} requires the mail for MAIL and refuses
     * it for WHATSAPP and CALL; for MAIL the subject, the body and the language are written to
     * the outreach's mail through {@link MailService#update}.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#updateOutreach}
     * through {@link OutreachService#update}.
     *
     * @param organizationId the organization that owns the outreach, from the checked request header
     * @param outreachId     the outreach identifier
     * @param request        the new contact, title and, for MAIL, subject, body and language
     * @return the updated outreach, read back through {@link OutreachQueryService#get}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND} if the
     *         contact is not of the outreach's relation
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE}
     *         if the outreach is SENT or being sent
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION} if a MAIL
     *         outreach gets no mail or a WHATSAPP or CALL outreach gets one
     * @see OutreachService#update
     */
    @Override
    @Transactional
    public OutreachDetailResponse update(UUID organizationId, UUID outreachId, OutreachUpdateRequest request) {
        // Locked first: the editable check below must still hold when the writes run.
        Outreach outreach = lockOutreach(organizationId, outreachId);
        outreachValidator.ensureEditable(outreach);
        outreachValidator.ensureUpdateMailFitsType(outreach.getType(), request.mail());
        contactValidator.ensureContactExists(organizationId, outreach.getRelationId(), request.contactId());

        // Only a MAIL outreach has a mail to write.
        if (outreach.getType() == OutreachType.MAIL) {
            mailService.update(organizationId, outreach.getMailId(), request.mail());
        }

        outreachMapper.updateEntity(outreach, request);
        // Stored trimmed, as on create.
        outreach.setTitle(request.title().trim());
        // Flushed here so the response carries the new last-change time.
        outreachRepository.saveAndFlush(outreach);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_UPDATED, outreachId, organizationId);

        return outreachQueryService.get(organizationId, outreachId);
    }

    /**
     * Sends a DRAFT or FAILED outreach through Microsoft Graph from one of the organization's
     * mail senders to the contact's current address. Runs without a surrounding transaction
     * (Propagation.NOT_SUPPORTED), because a Graph call cannot be rolled back and must not
     * hold a database transaction or row lock open while it waits on the network.
     *
     * Three steps:
     *
     * 1. Checks, while the outreach is still unchanged: the outreach exists in the
     *    organization ({@link OutreachValidator#ensureOutreachExists}), is not SENT
     *    ({@link OutreachValidator#ensureEditable}) and is a MAIL outreach
     *    ({@link OutreachValidator#ensureType}); the mail sender exists in the organization
     *    ({@link MailSenderValidator#ensureMailSenderExists}); the outreach still has a contact
     *    ({@link OutreachValidator#ensureHasContact}) that still belongs to the relation
     *    ({@link ContactValidator#ensureContactExists}) and has an address
     *    ({@link OutreachValidator#ensureContactAddress}), read now so a changed address is
     *    used; and {@link #senderId} resolves what Graph sends from, which for a shared
     *    mailbox is a Graph lookup. A failure here leaves the outreach as it was.
     *
     * 2. The claim: {@link OutreachRepository#claimForSending} sets SENT, the sender, the
     *    address and the send time in one conditional update, in its own short transaction
     *    through {@link TransactionTemplate}, and only for a DRAFT or FAILED row of the
     *    organization. The update takes the row lock, so a second send of the same outreach
     *    waits, then finds the row SENT and claims nothing; {@link OutreachValidator#ensureClaimed}
     *    turns that into a conflict, so the contact never gets the mail twice. Update and
     *    delete take the same row lock and refuse a SENT outreach, and so does every
     *    attachment write on the mail (storage's WRITE check reads this row's status with a
     *    lock), so from here the mail and its attachments are frozen.
     *
     * 3. Build and send, by the one call that holds the claim: the outreach's {@link Mail} and
     *    its attachments are read only now, through {@link MailQueryService} and
     *    {@link #mailAttachments}, so what goes out is exactly the content the claim froze. The
     *    plain-text body is escaped into HTML by {@link #htmlBody},
     *    and the Graph message goes to {@link MsGraphClient#sendEmail}. When building the mail
     *    or the Graph call fails, {@link #markFailed} releases the claim by setting FAILED, so
     *    the outreach can be fixed and sent again.
     *
     * The mail never goes out from the user who clicked send, only from a mail sender of the
     * organization: a PERSONAL one by its address, a SHARED one by the object id of the shared
     * mailbox.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#sendOutreach}
     * through {@link OutreachService#send}.
     *
     * @param organizationId the organization that owns the outreach, from the checked request header
     * @param outreachId     the outreach identifier
     * @param request        the mail sender of the organization to send from
     * @return the outreach, now SENT, read back through {@link OutreachQueryService#get}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND} (no
     *         contact, or the contact is gone) before the claim, outreach unchanged
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE}
     *         if the outreach is SENT, or another send claimed it first
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_WITHOUT_ADDRESS}
     *         or {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND}
     *         before the claim, outreach unchanged;
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ATTACHMENTS_TOO_LARGE}
     *         after the claim, outreach FAILED
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException
     *         {@link com.korur.oba.connect.azure.exception.ExceptionDomainAzureError#UPS_AZURE_BLOB_DOWNLOAD}
     *         (AZR_UPS_002) if an attachment cannot be read from blob storage; this happens after the
     *         claim, so the outreach is FAILED
     * @throws BadGatewayException
     *         {@link ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION} if Microsoft
     *         Graph fails the shared mailbox lookup (before the claim, outreach unchanged) or
     *         the send (after the claim, outreach FAILED)
     * @see OutreachService#send
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OutreachDetailResponse send(UUID organizationId, UUID outreachId, OutreachSendRequest request) {
        // Step 1: every check that can fail without touching the outreach. Read without a
        // lock; the claim below decides atomically whether this send may go on.
        Outreach outreach = outreachValidator.ensureOutreachExists(organizationId, outreachId);
        outreachValidator.ensureEditable(outreach);
        // Only a MAIL outreach goes out through Graph; the others are recorded as done.
        outreachValidator.ensureType(outreach, OutreachType.MAIL);
        MailSender mailSender = mailSenderValidator.ensureMailSenderExists(organizationId, request.mailSenderId());
        // A deleted contact clears the outreach's contact id, and the contact must still be
        // of this relation.
        Contact contact = contactValidator.ensureContactExists(organizationId, outreach.getRelationId(),
                outreachValidator.ensureHasContact(outreach));
        // The address is read now, so a contact whose address changed gets the mail there.
        String sentTo = outreachValidator.ensureContactAddress(outreach, contact);
        // For a shared mailbox this calls Graph; done before the claim, so a lookup failure
        // leaves the outreach unchanged.
        String sender = senderId(organizationId, mailSender);

        // Step 2: the claim, committed in its own transaction before Graph is called. Only a
        // DRAFT or FAILED row is updated, so of two concurrent sends exactly one gets a row.
        Instant sentAt = Instant.now(clock);
        int claimedRows = transactionTemplate.execute((TransactionStatus status) ->
                outreachRepository.claimForSending(outreachId, organizationId, mailSender.getId(), sentTo, sentAt));
        outreachValidator.ensureClaimed(outreach, claimedRows);

        // Step 3: build the mail from the content the claim froze. Edits are refused from
        // here on, so this is exactly what the contact receives.
        com.korur.oba.connect.notification.dto.Mail message;
        try {
            Mail mail = mailQueryService.getEntity(organizationId, outreach.getMailId());
            List<AttachmentEmail> attachments = mailAttachments(outreach, mail);
            message = new com.korur.oba.connect.notification.dto.Mail(sender, List.of(sentTo), mail.getSubject(),
                    htmlBody(mail.getBody()), attachments);
        } catch (RuntimeException e) {
            // Attachments over the limit or a storage failure: the claim is released and the
            // caller gets the cause.
            markFailed(organizationId, outreachId, e);
            throw e;
        }
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_SEND_CLAIMED, outreachId, organizationId,
                mailSender.getId(), message.attachments().size());

        try {
            msGraphClient.sendEmail(message);
        } catch (RuntimeException e) {
            // The cause is logged by markFailed; the caller gets one stable upstream code
            // instead of Graph's own error, whatever Graph answered.
            markFailed(organizationId, outreachId, e);
            throw new BadGatewayException(ExceptionDomainNotificationError.UPS_MS_GRAPH_COMMUNICATION);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_SENT, outreachId, organizationId,
                mailSender.getId());

        // The claim already stored SENT, the sender, the address and the time; the read
        // returns them as any later read will.
        return outreachQueryService.get(organizationId, outreachId);
    }

    /**
     * Records a DRAFT WHATSAPP or CALL outreach as done, in one transaction: status SENT,
     * sentAt now and sentTo the contact's trimmed phone, with no mail sender. Such an outreach
     * happened outside Oba, so nothing external is called and it never becomes FAILED.
     *
     * The outreach is read with a row lock by {@link #lockOutreach}, held until the
     * transaction commits, then checked as send checks it before its claim:
     * {@link OutreachValidator#ensureEditable} refuses a SENT one,
     * {@link OutreachValidator#ensureNotType} refuses a MAIL one, whose SENT status only a real
     * send may set, and the contact must still exist on the relation
     * ({@link OutreachValidator#ensureHasContact}, {@link ContactValidator#ensureContactExists})
     * and have a phone ({@link OutreachValidator#ensureContactPhone}). The status and the send
     * columns are written by {@link OutreachRepository#claimForSending}, the one write that
     * sets SENT, with a null mail sender; under the row lock it always claims the row, and
     * {@link OutreachValidator#ensureClaimed} guards that all the same.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#completeOutreach}
     * through {@link OutreachService#complete}.
     *
     * @param organizationId the organization that owns the outreach, from the checked request header
     * @param outreachId     the outreach identifier
     * @return the outreach, now SENT, read back through {@link OutreachQueryService#get}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND} if the
     *         outreach has no contact or the contact is gone
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE}
     *         if the outreach is SENT already
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE}
     *         if the outreach is a MAIL outreach, or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_WITHOUT_PHONE}
     *         if the contact has no phone number
     * @see OutreachService#complete
     */
    @Override
    @Transactional
    public OutreachDetailResponse complete(UUID organizationId, UUID outreachId) {
        // Locked first: the checks below must still hold when the claim runs.
        Outreach outreach = lockOutreach(organizationId, outreachId);
        outreachValidator.ensureEditable(outreach);
        outreachValidator.ensureNotType(outreach, OutreachType.MAIL);
        // A deleted contact clears the outreach's contact id, and the contact must still be
        // of this relation.
        Contact contact = contactValidator.ensureContactExists(organizationId, outreach.getRelationId(),
                outreachValidator.ensureHasContact(outreach));
        String sentTo = outreachValidator.ensureContactPhone(outreach, contact);

        // The claim is the one write that sets SENT; no mail sender for a channel outside Oba.
        int claimedRows = outreachRepository.claimForSending(outreachId, organizationId, null, sentTo,
                Instant.now(clock));
        outreachValidator.ensureClaimed(outreach, claimedRows);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_COMPLETED, outreachId, organizationId,
                outreach.getType());

        // The claim cleared the persistence context, so this reads the stored SENT row.
        return outreachQueryService.get(organizationId, outreachId);
    }

    /**
     * Deletes an outreach that was not sent, together with its mail and the mail's attachments,
     * in one transaction.
     *
     * The outreach is locked by {@link #lockOutreach} like in {@link #update}, so a delete
     * cannot remove an outreach that a send has claimed. The outreach row is deleted and
     * flushed before {@link MailService#delete} removes the mail, because the row's foreign
     * key to the mail has no cascade. {@link MailService#delete} also removes the mail's
     * attachments.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#deleteOutreach}
     * through {@link OutreachService#delete}.
     *
     * @param organizationId the organization that owns the outreach, from the checked request header
     * @param outreachId     the outreach identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE}
     *         if the outreach is SENT or being sent
     * @see OutreachService#delete
     */
    @Override
    @Transactional
    public void delete(UUID organizationId, UUID outreachId) {
        // Locked, so a send cannot claim the outreach between the check and the delete.
        Outreach outreach = lockOutreach(organizationId, outreachId);
        outreachValidator.ensureEditable(outreach);
        UUID mailId = outreach.getMailId();

        // The outreach row goes first and is flushed: its foreign key to the mail has no
        // cascade. Only a MAIL outreach has a mail to delete.
        outreachRepository.delete(outreach);
        outreachRepository.flush();
        if (mailId != null) {
            mailService.delete(organizationId, mailId);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_DELETED, outreachId, organizationId, mailId);
    }

    /**
     * Reads the outreach with a row lock held until the transaction ends, so the editable
     * check and the write cannot interleave with a send's claim. An empty locked read means
     * the organization has no such outreach, and the validator answers that error.
     *
     * The lock is a pessimistic write lock taken by
     * {@link OutreachRepository#findByIdAndOrganizationIdForUpdate}, the same row lock that
     * the conditional update of {@link OutreachRepository#claimForSending} takes. The fallback
     * {@link OutreachValidator#ensureOutreachExists} reads the row again without a lock only to
     * throw the not found error; it never returns an outreach here, because the locked read
     * already found none.
     *
     * Called by {@link #update}, {@link #complete} and {@link #delete}; not by
     * {@link #send}, which runs without a transaction and relies on the claim instead.
     *
     * @param organizationId the organization that owns the outreach
     * @param outreachId     the outreach identifier
     * @return the locked outreach
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach
     */
    private Outreach lockOutreach(UUID organizationId, UUID outreachId) {
        return outreachRepository.findByIdAndOrganizationIdForUpdate(outreachId, organizationId)
                .orElseGet(() -> outreachValidator.ensureOutreachExists(organizationId, outreachId));
    }

    /**
     * Releases the claim of a send that failed after it claimed the outreach: logs the cause
     * and sets the outreach FAILED, so it can be fixed and sent again.
     *
     * Called by {@link #send} in two places: when building the mail fails (a missing mail,
     * attachments over the limit or an attachment that blob storage cannot return) and when {@link MsGraphClient#sendEmail} fails.
     *
     * The write runs in its own transaction through {@link TransactionTemplate}, because
     * {@link #send} has none, and goes through {@link OutreachRepository#markSendFailed}, which
     * updates only a SENT row of the organization and clears the send time. When that write
     * fails too, the outreach stays SENT although the mail may not have gone out; that is
     * logged as an error with both causes, the send failure attached as suppressed, and the
     * caller still gets the original failure, which {@link #send} throws after this returns.
     * This method never throws, so a failing release cannot hide the send's own error.
     *
     * @param organizationId the organization that owns the outreach
     * @param outreachId     the claimed outreach
     * @param sendException  the failure that stopped the send
     */
    private void markFailed(UUID organizationId, UUID outreachId, RuntimeException sendException) {
        loggingService.error(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_SEND_FAILED, sendException, outreachId,
                organizationId);
        try {
            transactionTemplate.executeWithoutResult((TransactionStatus status) ->
                    outreachRepository.markSendFailed(outreachId, organizationId, Instant.now(clock)));
        } catch (RuntimeException markException) {
            // Logged rather than thrown: the send failure is the error the caller must see,
            // and this log line is the trace of an outreach left SENT by mistake.
            markException.addSuppressed(sendException);
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE, OUTREACH_MARK_FAILED_FAILED, markException,
                    outreachId, organizationId);
        }
    }

    /**
     * Reads every attachment of the mail, in upload order, as a mail attachment with its own
     * file name and content type, and checks that they fit in one Graph request.
     *
     * The bytes come from {@link AttachmentQueryService#getEntityAttachmentContents}, which
     * reads the private blob of each attachment row with the mail as parent in the outreach's
     * organization; it checks no access, which the send endpoint and the claim already settled. Graph takes a file
     * attachment inline, as bytes inside the send request, so the total size is known only
     * after every blob is read, and {@link OutreachValidator#ensureAttachmentsFit} runs last.
     *
     * Called by {@link #send} after the claim, so every failure here leaves the outreach
     * FAILED through {@link #markFailed}.
     *
     * @param outreach the claimed outreach
     * @param mail     the outreach's mail
     * @return the attachments, in upload order
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ATTACHMENTS_TOO_LARGE}
     *         if the attachments together exceed {@link OutreachConstant#ATTACHMENTS_MAX_BYTES}
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException
     *         {@link com.korur.oba.connect.azure.exception.ExceptionDomainAzureError#UPS_AZURE_BLOB_DOWNLOAD}
     *         if an attachment cannot be read from blob storage
     */
    private List<AttachmentEmail> mailAttachments(Outreach outreach, Mail mail) {
        List<AttachmentEmail> attachments = new ArrayList<>();
        long totalBytes = 0;
        for (AttachmentContent content : attachmentQueryService.getEntityAttachmentContents(
                outreach.getOrganizationId(), StorageEntityType.MAIL, mail.getId())) {
            totalBytes += content.content().length;
            attachments.add(new AttachmentEmail(content.fileName(), content.content(), content.contentType()));
        }
        // The size is known only after the download, so the Graph request limit is checked last.
        outreachValidator.ensureAttachmentsFit(outreach, totalBytes);
        return attachments;
    }

    /**
     * Returns what Graph sends from: a personal mailbox by its stored address, a shared
     * mailbox by its object id, looked up now, because its address may differ from its user
     * principal name. The mail never goes out from the user who clicked send.
     *
     * A {@link com.korur.oba.apps.otak.mailsender.enums.EmailType#PERSONAL} sender returns its
     * stored address, which Graph accepts as a user principal name. A
     * {@link com.korur.oba.apps.otak.mailsender.enums.EmailType#SHARED} sender is looked up by
     * {@link MailSenderValidator#ensureSharedMailboxInTenant} at send time, so a shared mailbox
     * removed from the tenant since it was added fails here, before the claim.
     *
     * Called by {@link #send} before the claim, so a failure leaves the outreach unchanged.
     *
     * @param organizationId the organization that owns the sender, for the log line of a failed lookup
     * @param mailSender     the mail sender chosen for the send
     * @return the sender id that the Graph message carries to {@link MsGraphClient#sendEmail}
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND}
     *         if the shared mailbox is no longer in the tenant
     * @throws BadGatewayException
     *         {@link ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION} if Microsoft
     *         Graph cannot answer the lookup
     */
    private String senderId(UUID organizationId, MailSender mailSender) {
        return switch (mailSender.getType()) {
            case PERSONAL -> mailSender.getMailAddress();
            case SHARED -> mailSenderValidator.ensureSharedMailboxInTenant(organizationId,
                    mailSender.getMailAddress()).id();
        };
    }

    /**
     * Turns the plain-text body into the HTML body Graph sends: escaped, so no markup of a
     * template reaches the recipient, with each line break as an HTML line break.
     *
     * The body is escaped by {@link HtmlUtils#htmlEscape} first and only then gets the line
     * breaks of {@link OutreachConstant#HTML_LINE_BREAK}, so those are the only tags in the
     * mail. {@link OutreachConstant#LINE_BREAK_PATTERN} matches Windows, old Mac and Unix line
     * ends, so a body typed on any system keeps its lines.
     *
     * Called by {@link #send} when it builds the Graph message.
     *
     * @param body the plain-text body of the outreach's mail
     * @return the HTML body
     */
    private static String htmlBody(String body) {
        return HtmlUtils.htmlEscape(body).replaceAll(OutreachConstant.LINE_BREAK_PATTERN,
                OutreachConstant.HTML_LINE_BREAK);
    }
}
