package com.korur.oba.apps.otak.outreach.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.dto.request.OutreachCreateRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachSendRequest;
import com.korur.oba.apps.otak.outreach.dto.request.OutreachUpdateRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write side of the outreaches of an organization: create, change, send, record as done and
 * delete an outreach. The read side is {@link OutreachQueryService}, and every
 * write method here answers with the outreach as that service reads it after the write.
 *
 * An outreach is one reach-out to one contact of one relation, through the channel its
 * {@link com.korur.oba.apps.otak.outreach.enums.OutreachType} names. It holds no content of its
 * own. A MAIL outreach has its subject and body in a mail that belongs to the outreach alone,
 * and the files that mail carries are shrine attachments with the mail as parent, written
 * through /storage/attachments; writing an outreach through this service also writes the mail
 * through {@link com.korur.oba.apps.otak.mail.service.MailService}. A WHATSAPP or CALL outreach
 * happens outside Oba, has no mail and is recorded as done instead of sent.
 *
 * Organization scoping: the caller's role in the organization is checked once per request
 * by the access interceptor, before these methods run, from the X-Organization-ID header.
 * Every method then reads the outreach, the relation, the contact, the mail sender and the
 * document templates within that organization only, so a row of another organization answers the
 * same not found as a missing one and reveals nothing to another tenant.
 *
 * Status rule: an outreach is {@link com.korur.oba.apps.otak.outreach.enums.OutreachStatus#DRAFT}
 * until it is sent, {@link com.korur.oba.apps.otak.outreach.enums.OutreachStatus#SENT} from the
 * moment a send claims it, and
 * {@link com.korur.oba.apps.otak.outreach.enums.OutreachStatus#FAILED} when the send failed; a
 * WHATSAPP or CALL outreach becomes SENT when it is recorded as done and never FAILED.
 * A SENT outreach is the record of what the contact received, so every write here refuses
 * it; a FAILED one may be changed and sent again.
 *
 * Called only by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController}.
 *
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl
 * @see OutreachQueryService
 */
public interface OutreachService {

    /**
     * Creates a DRAFT outreach to a contact of a relation in one transaction; a MAIL outreach
     * with its own mail and a copy of each requested document template's PDF as an attachment
     * of that mail, a WHATSAPP or CALL outreach without any.
     *
     * The mail of a MAIL outreach comes from exactly one source: a mail template, copied through
     * {@link com.korur.oba.apps.otak.mail.service.MailService#createFromTemplate}, or a subject
     * and body written by hand, stored through
     * {@link com.korur.oba.apps.otak.mail.service.MailService#create}. The mail is a copy and not
     * the template itself, so a later change of the template never changes an outreach, and a
     * sent outreach keeps the exact text that went out. The type comes from the request and
     * never changes afterwards. Each template is copied through
     * {@link com.korur.oba.shrine.document.service.template.DocumentTemplateService#copyToAttachment},
     * in the order of the request, a repeated id once.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#createOutreach}.
     *
     * @param organizationId the organization that owns the relation, from the checked request header
     * @param user           the authenticated user, recorded in the audit log and checked for write access to the mail
     * @param request        the relation, the contact, the title, the mail source and the document template ids
     * @return the created outreach, read back through {@link OutreachQueryService#get}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND} if the
     *         organization has no such relation,
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND} if the
     *         contact is not of the relation,
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_TEMPLATE_FOUND}
     *         if the mail template is missing, or
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#NOT_DOCUMENT_TEMPLATE_FOUND} if
     *         a document template is missing
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.dojo.exception.enums.ExceptionValidationError#VALIDATION} if a MAIL
     *         outreach has both or neither mail sources, or a WHATSAPP or CALL outreach has any mail
     *         field, or
     *         {@link com.korur.oba.shrine.document.exception.ExceptionDomainDocumentError#VAL_DOCUMENT_NOT_USABLE}
     *         if a document template has no PDF
     * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#create
     */
    OutreachDetailResponse create(UUID organizationId, User user, OutreachCreateRequest request);

    /**
     * Changes the contact, the title and, for MAIL, the mail of an outreach that was not sent.
     *
     * The outreach row is read with a row lock held until the transaction ends, through
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#findByIdAndOrganizationIdForUpdate},
     * so the editable check and the write run wholly before or wholly after a send's claim and never write
     * over a mail that is going out. For MAIL the subject, the body and the language, which the request must
     * give, are written to the outreach's mail through
     * {@link com.korur.oba.apps.otak.mail.service.MailService#update}; the mail keeps its template link.
     * A WHATSAPP or CALL outreach takes no mail. The relation, the type, the mail link and the send columns
     * never change.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#updateOutreach}.
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
     * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#update
     */
    OutreachDetailResponse update(UUID organizationId, UUID outreachId, OutreachUpdateRequest request);

    /**
     * Sends a DRAFT or FAILED MAIL outreach through Microsoft Graph from one of the organization's
     * mail senders to the contact's current address: its mail as an HTML body and every
     * attachment of the mail, in upload order, read from blob storage with its own file name
     * and content type.
     *
     * The send is not one transaction, because a call to Microsoft Graph cannot be rolled
     * back. Instead the outreach is claimed as SENT in its own short transaction by
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#claimForSending}
     * before {@link com.korur.oba.connect.notification.client.MsGraphClient#sendEmail} is
     * called. A second send of the same outreach meanwhile finds nothing to claim and is
     * refused, so the contact never gets the mail twice, and an edit or delete, which hold
     * the same row lock, see SENT and are refused too. The mail and its attachments
     * are read only after the claim, so what goes out is exactly what the claim froze. When
     * building the mail or the Graph call fails, the claim is released with
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#markSendFailed}
     * and the outreach becomes FAILED, so it can be sent again.
     *
     * A PERSONAL sender sends from its own address; a SHARED sender sends from the shared
     * mailbox, addressed by its Graph object id, which is looked up at send time. The mail
     * never goes out from the user who clicked send.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#sendOutreach}.
     *
     * @param organizationId the organization that owns the outreach, from the checked request header
     * @param outreachId     the outreach identifier
     * @param request        the mail sender of the organization to send from
     * @return the outreach with its new status, read back through {@link OutreachQueryService#get}
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND},
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_MAIL_SENDER_FOUND} or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_CONTACT_FOUND} (the
     *         outreach has no contact or the contact is gone) before the claim, outreach unchanged
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE}
     *         if the outreach is SENT, or another send claimed it first
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE}
     *         (not a MAIL outreach),
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_CONTACT_WITHOUT_ADDRESS}
     *         or
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_SHARED_MAILBOX_NOT_FOUND}
     *         before the claim, outreach unchanged;
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#VAL_ATTACHMENTS_TOO_LARGE}
     *         after the claim, outreach FAILED
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.IntegrationErrorException
     *         {@link com.korur.oba.connect.azure.exception.ExceptionDomainAzureError#UPS_AZURE_BLOB_DOWNLOAD}
     *         if an attachment cannot be read from blob storage, outreach FAILED
     * @throws com.korur.oba.dojo.exception.internal.infrastructure.BadGatewayException
     *         {@link com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError#UPS_MS_GRAPH_COMMUNICATION}
     *         if Microsoft Graph fails the shared mailbox lookup (outreach unchanged) or the send (outreach
     *         FAILED)
     * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send
     */
    OutreachDetailResponse send(UUID organizationId, UUID outreachId, OutreachSendRequest request);

    /**
     * Records a DRAFT WHATSAPP or CALL outreach as done: status SENT, sentAt now and sentTo the
     * contact's trimmed phone, with no mail sender. The outreach happened outside Oba, so
     * nothing external is called. The outreach is locked like in {@link #update}.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#completeOutreach}.
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
     * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete
     */
    OutreachDetailResponse complete(UUID organizationId, UUID outreachId);

    /**
     * Deletes an outreach that was not sent, together with its mail, which belongs to this
     * outreach alone, and the mail's attachments.
     *
     * The outreach is locked like in {@link #update}, so a delete cannot remove an outreach
     * that a send is mailing. The outreach row is removed and flushed before
     * {@link com.korur.oba.apps.otak.mail.service.MailService#delete} removes the mail, because
     * the row's foreign key to the mail has no cascade.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#deleteOutreach}.
     *
     * @param organizationId the organization that owns the outreach, from the checked request header
     * @param outreachId     the outreach identifier
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE}
     *         if the outreach is SENT or being sent
     * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#delete
     */
    void delete(UUID organizationId, UUID outreachId);
}
