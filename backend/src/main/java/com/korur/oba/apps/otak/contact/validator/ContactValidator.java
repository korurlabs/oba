package com.korur.oba.apps.otak.contact.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.constant.ContactConstant;
import com.korur.oba.apps.otak.contact.constant.ContactExceptionLogConstant;
import com.korur.oba.apps.otak.contact.entity.Contact;
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;
import com.korur.oba.apps.otak.contact.repository.ContactRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for the contacts of a relation: the organization-scoped contact lookup and
 * the request rules that Bean Validation on a single field can not express, because they
 * compare two fields of a request or a request with the stored contact.
 *
 * - {@link #ensureContactExists}: the contact belongs to the relation and the organization.
 * - {@link #ensureEmailTypeHasEmail}: an email type comes with an email address.
 * - {@link #ensureMainFlagFitsKind}: a true main flag names the contact's own kind.
 * - {@link #ensureMainContactKept}: a full-replace update does not unmark the main contact
 *   of an unchanged kind.
 *
 * Each refusal is logged as a warning with {@link LoggingService#warn} before it is thrown,
 * and answers a {@link NotFoundException} or {@link ValidationException} that the global
 * exception handler turns into 404 or 422 with the error code. Every check runs before the
 * service writes anything, so a refused request leaves the contacts as they were.
 *
 * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl} for every
 * rule, and by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl} and
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl} for the
 * contact lookup only.
 */
@Component
@RequiredArgsConstructor
public class ContactValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ContactValidator.class);

    private final ContactRepository contactRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the relation of the organization has the contact and returns it. A contact
     * of another relation or organization answers the same not found as a missing one,
     * so its id reveals nothing to another tenant.
     *
     * Reads with {@link ContactRepository#findByIdAndRelationIdAndOrganizationId}, which
     * names the organization and the relation in the query itself, so the scope holds even
     * where the relation was not checked first.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update}
     * and {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#delete}, and
     * for the contact an outreach is addressed to by
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#create},
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#update},
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} and
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#get}.
     *
     * @param organizationId the organization the caller acts in
     * @param relationId     the relation the contact must belong to
     * @param contactId      the contact identifier
     * @return the contact
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_CONTACT_FOUND} (OTK_NOT_002)
     *         if the relation has no contact with the id
     */
    public Contact ensureContactExists(UUID organizationId, UUID relationId, UUID contactId) {
        return contactRepository.findByIdAndRelationIdAndOrganizationId(contactId, relationId, organizationId)
                .orElseThrow(() -> {
                    // The log keeps the ids for support; the response names only the contact
                    // id the caller already sent.
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            ContactExceptionLogConstant.CONTACT_NOT_FOUND,
                            contactId,
                            relationId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_CONTACT_FOUND,
                            Map.of(ContactConstant.FIELD_CONTACT_ID, contactId));
                });
    }

    /**
     * Ensures an email type comes with an email address. The type describes the address,
     * so a type alone would be a claim about nothing; a blank address counts as none.
     * No type always passes, with or without an address.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}
     * and {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update}; on an
     * update the request replaces both fields, so a client that clears the address clears the
     * type with it.
     *
     * @param email     the email address of the request, or null
     * @param emailType the kind of the address, or null
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL}
     *         (OTK_VAL_005) when a type is given without an address
     */
    public void ensureEmailTypeHasEmail(String email, ContactEmailType emailType) {
        // Passes when there is no type to justify, or when the type has an address.
        if (emailType == null || (email != null && !email.isBlank())) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                ContactExceptionLogConstant.CONTACT_EMAIL_TYPE_WITHOUT_EMAIL, emailType);
        throw new ValidationException(
                ExceptionDomainOtakError.VAL_CONTACT_EMAIL_TYPE_WITHOUT_EMAIL,
                Map.of(ContactConstant.FIELD_EMAIL_TYPE, emailType));
    }

    /**
     * Ensures a true main flag names the contact's own kind. A person can only be the main
     * person contact and a business contact only the main business contact, so a flag of
     * the other kind is refused instead of ignored, and the caller learns its request did
     * not do what it asked. False flags always pass. The database check constraints on
     * otak.contacts refuse the same combination, so this check turns a constraint error into
     * a clear validation error.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#create}
     * and {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update}, with
     * the kind the contact has after the request.
     *
     * @param kind                the kind the contact has after the request
     * @param mainPersonContact   the main person flag of the request
     * @param mainBusinessContact the main business flag of the request
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND}
     *         (OTK_VAL_013) when a true flag names the other kind
     */
    public void ensureMainFlagFitsKind(ContactKind kind, Boolean mainPersonContact, Boolean mainBusinessContact) {
        // Only the flag of the other kind matters here; the flag of the own kind may be
        // true or false.
        boolean otherKindFlag = kind == ContactKind.PERSON
                ? Boolean.TRUE.equals(mainBusinessContact)
                : Boolean.TRUE.equals(mainPersonContact);
        if (!otherKindFlag) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                ContactExceptionLogConstant.CONTACT_MAIN_FLAG_NOT_FOR_KIND, kind, mainPersonContact,
                mainBusinessContact);
        throw new ValidationException(
                ExceptionDomainOtakError.VAL_CONTACT_MAIN_FLAG_NOT_FOR_KIND,
                Map.of(ContactConstant.FIELD_KIND, kind));
    }

    /**
     * Ensures an update that keeps the kind does not unmark the main contact of that kind.
     * A relation with contacts of a kind always has one main contact of it, and an update
     * replaces the whole contact, so false on the current main contact would ask for a
     * relation without one; the main contact changes only by marking another one. False on
     * a contact that is not main, and true, pass. Called only when the kind is unchanged: a
     * kind change moves the main flag to the oldest remaining contact of the old kind.
     *
     * The refusal is a field error on the flag, not a domain error code, so a form shows it
     * next to the flag like any other field error.
     *
     * Called by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl#update},
     * before any write.
     *
     * @param contact             the stored contact, with its unchanged kind
     * @param mainPersonContact   the main person flag of the request
     * @param mainBusinessContact the main business flag of the request
     * @throws ValidationException {@link ExceptionValidationError#VALIDATION} (GEN_VAL_001) on
     *         the flag of the contact's kind, {@link ContactConstant#FIELD_MAIN_PERSON_CONTACT}
     *         or {@link ContactConstant#FIELD_MAIN_BUSINESS_CONTACT}, with
     *         {@link ContactConstant#MAIN_CONTACT_UNMARK_MESSAGE}, when it is false while the
     *         contact is main of that kind
     */
    public void ensureMainContactKept(Contact contact, Boolean mainPersonContact, Boolean mainBusinessContact) {
        // Compare the stored flag of the contact's own kind with the request's flag of it.
        boolean person = contact.getKind() == ContactKind.PERSON;
        boolean main = person ? contact.isMainPersonContact() : contact.isMainBusinessContact();
        Boolean flag = person ? mainPersonContact : mainBusinessContact;
        if (!main || Boolean.TRUE.equals(flag)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                ContactExceptionLogConstant.CONTACT_MAIN_FLAG_UNMARKED, contact.getKind(), contact.getId());
        // The error names the field the client sent, so the client can show it in place.
        String field = person
                ? ContactConstant.FIELD_MAIN_PERSON_CONTACT
                : ContactConstant.FIELD_MAIN_BUSINESS_CONTACT;
        throw new ValidationException(ExceptionValidationError.VALIDATION,
                Map.of(field, ContactConstant.MAIN_CONTACT_UNMARK_MESSAGE));
    }
}
