package com.korur.oba.apps.otak.outreach.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.dto.request.OutreachQueryRequest;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachDetailResponse;
import com.korur.oba.apps.otak.outreach.dto.response.OutreachListResponse;

/**
 * Read side of the outreaches of an organization: the paged list and the detail of one
 * outreach. The write side is {@link OutreachService}.
 *
 * Organization scoping: the caller's role in the organization is checked by the access
 * interceptor before these methods run. Both methods read only within the given
 * organization, so an outreach or relation of another organization answers the same not
 * found as a missing one.
 *
 * An outreach row holds only ids. Its contact, the address of its mail sender and its mail
 * are read through the query services and validators of
 * their own domains and assembled into the response, so this service never reads another
 * domain's repository.
 *
 * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController} for the read
 * endpoints, and by {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl}
 * to answer every write with the stored outreach.
 *
 * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl
 * @see com.korur.oba.apps.otak.outreach.repository.OutreachRepository
 * @see com.korur.oba.apps.otak.outreach.specification.OutreachSpecification
 */
public interface OutreachQueryService {

    /**
     * Lists one page of the organization's outreaches, newest first, each with its contact
     * and the address it was sent from.
     *
     * The order is fixed (creation time, then id, both descending), so pages stay stable and
     * no client-supplied sort field reaches the query. The rows are filtered by
     * {@link com.korur.oba.apps.otak.outreach.specification.OutreachSpecification#filterBy},
     * which always restricts them to the organization. A relation filter naming a relation
     * the organization does not have answers not found instead of an empty page, so a
     * client cannot mistake a wrong id for a relation without outreaches.
     *
     * The contact is null when it was deleted after the outreach was made, and the sender
     * address is null until the outreach is sent or once that sender is deleted.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#listOutreaches}.
     *
     * @param organizationId the organization that owns the outreaches, from the checked request header
     * @param request        the one-based page, the page size and the optional relation and status filters
     * @return the page of outreaches
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_RELATION_FOUND} if the
     *         relation filter names a relation the organization does not have
     * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#list
     */
    Page<OutreachListResponse> list(UUID organizationId, OutreachQueryRequest request);

    /**
     * Reads one outreach of the organization with its contact, its sender address and its
     * mail. The mail's attachments are listed through /storage/attachments, not here.
     *
     * Called by {@link com.korur.oba.apps.otak.outreach.controller.OutreachController#getOutreach},
     * and by every write method of
     * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl} after its write.
     *
     * @param organizationId the organization that owns the outreach, from the checked request header
     * @param outreachId     the outreach identifier
     * @return the outreach
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError#NOT_OUTREACH_FOUND} if the
     *         organization has no such outreach; the other not-found codes of
     *         {@link com.korur.oba.apps.otak.exception.ExceptionDomainOtakError} only if stored data
     *         broke its own foreign keys
     * @see com.korur.oba.apps.otak.outreach.service.impl.OutreachQueryServiceImpl#get
     */
    OutreachDetailResponse get(UUID organizationId, UUID outreachId);
}
