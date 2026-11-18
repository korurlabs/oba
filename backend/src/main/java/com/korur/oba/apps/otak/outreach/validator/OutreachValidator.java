package com.korur.oba.apps.otak.outreach.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.mail.dto.request.MailRequest;
import com.korur.oba.apps.otak.outreach.constant.OutreachConstant;
import com.korur.oba.apps.otak.outreach.constant.OutreachExceptionLogConstant;
import com.korur.oba.apps.otak.outreach.entity.Outreach;
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.apps.otak.outreach.repository.OutreachRepository;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for outreach lookups, the edit lock of a sent outreach, the mail fields that fit
 * each channel, the channel an action needs and the preconditions of a send or of recording
 * an outreach as done. Whether a mail's attachments may still change is checked by
 * {@link com.korur.oba.apps.otak.mail.validator.MailValidator#ensureMailUnsent}.
 *
 * Each check logs a warning with the ids involved and throws an exception that carries an
 * {@link ExceptionDomainOtakError} code (or {@link ExceptionValidationError#VALIDATION} for
 * a request-shape error), which the global exception handler turns into the HTTP status and
 * the error code the client sees. Checks that read data take the organization id, so a
 * row of another organization fails the same way as a missing one.
 *
 * Used by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl} for every write and by
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#get}. Reads
 * outreaches only through {@link OutreachRepository#findByIdAndOrganizationId}.
 *
 * @see OutreachRepository
 * @see OutreachStatus
 */
@Component
@RequiredArgsConstructor
public class OutreachValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(OutreachValidator.class);

    private final OutreachRepository outreachRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the outreach and returns it. An outreach of another
     * organization answers the same not found as a missing one, so its id reveals nothing
     * to another tenant. Takes no row lock; the writes that need one lock the row through
     * {@link OutreachRepository#findByIdAndOrganizationIdForUpdate} and fall back to this
     * method only to answer the not found error.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#get}, by
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} and by the row-lock
     * helper of {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl} that serves update,
     * complete and delete.
     *
     * @param organizationId the organization the caller acts in
     * @param outreachId     the outreach identifier
     * @return the outreach
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no outreach with the id
     */
    public Outreach ensureOutreachExists(UUID organizationId, UUID outreachId) {
        return outreachRepository.findByIdAndOrganizationId(outreachId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            OutreachExceptionLogConstant.OUTREACH_NOT_FOUND,
                            outreachId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_OUTREACH_FOUND,
                            Map.of(OutreachConstant.FIELD_OUTREACH_ID, outreachId));
                });
    }

    /**
     * Ensures the outreach may still change. A SENT outreach is the record of what the
     * contact received, and a send claims the row as SENT before it calls the mail
     * service, so a running send also counts as sent.
     *
     * Only {@link OutreachStatus#SENT} is refused: {@link OutreachStatus#DRAFT} has not gone
     * out yet, and {@link OutreachStatus#FAILED} did not reach the contact and may be fixed
     * and sent again. For an edit or delete the outreach must have been read with the row
     * lock, so that the check still holds when the write runs.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#update},
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#delete} and, before the claim,
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} and
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete}.
     *
     * @param outreach the stored outreach
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE} if the
     *         outreach is SENT
     */
    public void ensureEditable(Outreach outreach) {
        if (outreach.getStatus() == OutreachStatus.SENT) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.OUTREACH_NOT_EDITABLE,
                    outreach.getId(), outreach.getOrganizationId(), outreach.getStatus());
            throw new ConflictException(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE,
                    Map.of(OutreachConstant.FIELD_OUTREACH_ID, outreach.getId()));
        }
    }

    /**
     * Ensures the mail fields of a create request fit the channel. A MAIL outreach needs
     * exactly one source for its mail: a mail template or a mail written by hand; with both
     * or neither the outreach's mail would be ambiguous or missing. A WHATSAPP or CALL
     * outreach happens outside Oba and has no mail, so each of a mail template, a mail and a
     * non-empty document template list is refused, every one given named as its own field. The
     * request record cannot express this with field annotations alone, so it is checked
     * here, before anything is written.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#create}.
     *
     * @param type           the channel of the new outreach
     * @param mailTemplateId the mail template to copy, or null
     * @param mail           the mail written by hand, or null
     * @param documentTemplateIds the document templates to copy onto the mail, or null
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} with the field mail
     *         when a MAIL outreach has both or neither sources, or with the fields mailTemplateId,
     *         mail and documentTemplateIds that a WHATSAPP or CALL outreach was given
     */
    public void ensureMailFitsType(OutreachType type, UUID mailTemplateId, MailRequest mail,
                                   List<UUID> documentTemplateIds) {
        if (type == OutreachType.MAIL) {
            boolean templateGiven = mailTemplateId != null;
            boolean mailGiven = mail != null;
            // Equal means both or neither.
            if (templateGiven == mailGiven) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.MAIL_SOURCE_INVALID,
                        mailTemplateId, mailGiven);
                throw new ValidationException(ExceptionValidationError.VALIDATION,
                        Map.of(OutreachConstant.FIELD_MAIL, OutreachConstant.ONE_MAIL_SOURCE_MESSAGE));
            }
            return;
        }
        // Every refused field is named at once, so the client can fix them in one go.
        Map<String, Object> refused = new LinkedHashMap<>();
        if (mailTemplateId != null) {
            refused.put(OutreachConstant.FIELD_MAIL_TEMPLATE_ID, OutreachConstant.MAIL_TEMPLATE_NOT_FOR_TYPE_MESSAGE);
        }
        if (mail != null) {
            refused.put(OutreachConstant.FIELD_MAIL, OutreachConstant.MAIL_NOT_FOR_TYPE_MESSAGE);
        }
        if (documentTemplateIds != null && !documentTemplateIds.isEmpty()) {
            refused.put(OutreachConstant.FIELD_DOCUMENT_TEMPLATE_IDS,
                    OutreachConstant.DOCUMENT_TEMPLATES_NOT_FOR_TYPE_MESSAGE);
        }
        refuseMailFields(type, refused);
    }

    /**
     * Ensures the mail of an update request fits the stored channel: required for a MAIL
     * outreach, whose own mail it replaces, and absent for a WHATSAPP or CALL outreach, which
     * has no mail. The update request does not carry the type, which never changes, so the
     * stored outreach decides.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#update}.
     *
     * @param type the stored channel of the outreach
     * @param mail the mail of the update request, or null
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} with the field mail
     *         when a MAIL outreach gets no mail or a WHATSAPP or CALL outreach gets one
     */
    public void ensureUpdateMailFitsType(OutreachType type, MailRequest mail) {
        if (type == OutreachType.MAIL) {
            if (mail == null) {
                loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.MAIL_SOURCE_INVALID,
                        null, false);
                throw new ValidationException(ExceptionValidationError.VALIDATION,
                        Map.of(OutreachConstant.FIELD_MAIL, OutreachConstant.MAIL_REQUIRED_MESSAGE));
            }
            return;
        }
        Map<String, Object> refused = new LinkedHashMap<>();
        if (mail != null) {
            refused.put(OutreachConstant.FIELD_MAIL, OutreachConstant.MAIL_NOT_FOR_TYPE_MESSAGE);
        }
        refuseMailFields(type, refused);
    }

    /**
     * Ensures an action fits the outreach's channel. Sending through Microsoft Graph exists
     * only for a MAIL outreach, while recording an outreach as done exists
     * only for one that happens outside Oba, so each action names the channel it needs.
     *
     * Called with {@link OutreachType#MAIL} by
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send}, right after
     * {@link #ensureEditable}, so the outreach stays unchanged.
     *
     * @param outreach the stored outreach
     * @param expected the channel the action needs
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE} when the
     *         outreach has another channel
     */
    public void ensureType(Outreach outreach, OutreachType expected) {
        if (outreach.getType() != expected) {
            refuseAction(outreach);
        }
    }

    /**
     * Ensures an action does not run on a channel it does not fit: the reverse of
     * {@link #ensureType}. Recording an outreach as done fits every channel but MAIL, whose
     * SENT status only a real send may set.
     *
     * Called with {@link OutreachType#MAIL} by
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete}, right after
     * {@link #ensureEditable}.
     *
     * @param outreach the stored outreach
     * @param refused  the channel the action does not fit
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE} when the
     *         outreach has that channel
     */
    public void ensureNotType(Outreach outreach, OutreachType refused) {
        if (outreach.getType() == refused) {
            refuseAction(outreach);
        }
    }

    /**
     * Ensures the outreach still has a contact. Deleting a contact clears it on its
     * outreaches, so a DRAFT or FAILED one needs a new contact before it can be sent or
     * recorded as done.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} and
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete}, before the
     * claim, so the outreach stays unchanged.
     *
     * @param outreach the outreach to send
     * @return the contact identifier
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_CONTACT_FOUND} when the
     *         outreach has no contact
     */
    public UUID ensureHasContact(Outreach outreach) {
        if (outreach.getContactId() == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.OUTREACH_WITHOUT_CONTACT,
                    outreach.getId(), outreach.getOrganizationId());
            throw new NotFoundException(ExceptionDomainOtakError.NOT_CONTACT_FOUND,
                    Map.of(OutreachConstant.FIELD_OUTREACH_ID, outreach.getId()));
        }
        return outreach.getContactId();
    }

    /**
     * Ensures the contact of the outreach has a mail address and returns it trimmed. The
     * address is read at send time, so a contact whose address changed after the outreach
     * was prepared gets the mail at the new one. The trimmed address is what the claim
     * stores as sentTo.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send}, before the
     * claim, so the outreach stays unchanged.
     *
     * @param outreach the outreach to send
     * @param contact  the contact of the outreach
     * @return the trimmed mail address
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CONTACT_WITHOUT_ADDRESS} when
     *         the contact has no address
     */
    public String ensureContactAddress(Outreach outreach, Contact contact) {
        // Trimmed, so an address of only whitespace counts as none.
        String address = contact.getEmail() == null ? null : contact.getEmail().trim();
        if (address == null || address.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.CONTACT_WITHOUT_ADDRESS,
                    outreach.getId(), contact.getId());
            throw new ValidationException(ExceptionDomainOtakError.VAL_CONTACT_WITHOUT_ADDRESS,
                    Map.of(OutreachConstant.FIELD_CONTACT_ID, contact.getId()));
        }
        return address;
    }

    /**
     * Ensures the contact of a WHATSAPP or CALL outreach has a phone number and returns it
     * trimmed. The phone is read when the outreach is recorded as done, so it is the number
     * the contact had then; the trimmed phone is what the claim stores as sentTo.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete},
     * before the claim, so the outreach stays unchanged.
     *
     * @param outreach the outreach to record as done
     * @param contact  the contact of the outreach
     * @return the trimmed phone number
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CONTACT_WITHOUT_PHONE} when
     *         the contact has no phone number
     */
    public String ensureContactPhone(Outreach outreach, Contact contact) {
        // Trimmed, so a phone of only whitespace counts as none.
        String phone = contact.getPhone() == null ? null : contact.getPhone().trim();
        if (phone == null || phone.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.CONTACT_WITHOUT_PHONE,
                    outreach.getId(), contact.getId());
            throw new ValidationException(ExceptionDomainOtakError.VAL_CONTACT_WITHOUT_PHONE,
                    Map.of(OutreachConstant.FIELD_CONTACT_ID, contact.getId()));
        }
        return phone;
    }

    /**
     * Ensures the attachments sent with an outreach fit in one Microsoft Graph sendMail
     * request. Graph takes a file attachment inline in the request only up to a few
     * megabytes, so the attachments together are capped at
     * {@link OutreachConstant#ATTACHMENTS_MAX_BYTES}. The size is known only after the
     * attachments are read from blob storage.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} after the
     * claim, so a failure here leaves the
     * outreach FAILED, and the caller can remove an attachment and send again.
     *
     * @param outreach   the outreach to send
     * @param totalBytes the size of all attachments together
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_ATTACHMENTS_TOO_LARGE} when
     *         the attachments together are larger than {@link OutreachConstant#ATTACHMENTS_MAX_BYTES}
     */
    public void ensureAttachmentsFit(Outreach outreach, long totalBytes) {
        if (totalBytes > OutreachConstant.ATTACHMENTS_MAX_BYTES) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.ATTACHMENTS_TOO_LARGE,
                    outreach.getId(), totalBytes, OutreachConstant.ATTACHMENTS_MAX_BYTES);
            throw new ValidationException(ExceptionDomainOtakError.VAL_ATTACHMENTS_TOO_LARGE,
                    Map.of(OutreachConstant.FIELD_SIZE_BYTES, totalBytes,
                            OutreachConstant.FIELD_MAX_SIZE_BYTES, OutreachConstant.ATTACHMENTS_MAX_BYTES));
        }
    }

    /**
     * Ensures the send claimed the outreach. No claimed row means another send claimed it
     * first, between the edit check and the claim, so this send must not mail it again.
     * It answers the same conflict as {@link #ensureEditable}, because for the caller the
     * outreach is sent or being sent either way. Zero rows can also mean the outreach was
     * deleted in between; that too must not be mailed.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} and
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete} right after
     * {@link OutreachRepository#claimForSending}.
     *
     * @param outreach    the outreach as read before the claim
     * @param claimedRows the number of rows the claim updated
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_OUTREACH_NOT_EDITABLE} when
     *         no row was claimed
     */
    public void ensureClaimed(Outreach outreach, int claimedRows) {
        if (claimedRows == 0) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.OUTREACH_CLAIM_LOST,
                    outreach.getId(), outreach.getOrganizationId());
            throw new ConflictException(ExceptionDomainOtakError.STA_OUTREACH_NOT_EDITABLE,
                    Map.of(OutreachConstant.FIELD_OUTREACH_ID, outreach.getId()));
        }
    }

    /**
     * Throws the validation error for mail fields that a WHATSAPP or CALL outreach was
     * given; does nothing when none were.
     *
     * Called by {@link #ensureMailFitsType} and {@link #ensureUpdateMailFitsType}.
     *
     * @param type    the channel of the outreach
     * @param refused the refused fields, each with its message; empty when none
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} with the refused fields
     */
    private void refuseMailFields(OutreachType type, Map<String, Object> refused) {
        if (!refused.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.MAIL_NOT_FOR_TYPE,
                    type, refused.keySet());
            throw new ValidationException(ExceptionValidationError.VALIDATION, refused);
        }
    }

    /**
     * Throws the error of an action that does not fit the outreach's channel.
     *
     * Called by {@link #ensureType} and {@link #ensureNotType}.
     *
     * @param outreach the stored outreach
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_ACTION_NOT_FOR_TYPE} always
     */
    private void refuseAction(Outreach outreach) {
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, OutreachExceptionLogConstant.ACTION_NOT_FOR_TYPE,
                outreach.getId(), outreach.getOrganizationId(), outreach.getType());
        throw new ValidationException(ExceptionDomainOtakError.VAL_ACTION_NOT_FOR_TYPE,
                Map.of(OutreachConstant.FIELD_OUTREACH_ID, outreach.getId(),
                        OutreachConstant.FIELD_TYPE, outreach.getType()));
    }
}
