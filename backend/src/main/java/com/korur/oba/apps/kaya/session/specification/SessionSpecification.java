package com.korur.oba.apps.kaya.session.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// 3. Java SDK
import java.util.Locale;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;
import com.korur.oba.apps.kaya.session.dto.request.SessionQueryRequest;
import com.korur.oba.apps.kaya.session.entity.Session;
import com.korur.oba.apps.kaya.session.entity.SessionHandoffSnapshot;
import com.korur.oba.apps.kaya.session.entity.SessionLink;
import com.korur.oba.apps.kaya.session.enums.SessionLinkKind;

/**
 * Filters of the wall, and the handoff snapshots of a feature. Every query is limited to one
 * organization.
 */
public final class SessionSpecification {

    private SessionSpecification() {
    }

    private static Specification<Session> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Session> byOrganization(UUID organizationId) {
        if (organizationId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SessionConstant.FIELD_ORGANIZATION_ID), organizationId);
    }

    private static Specification<Session> byRepository(UUID repositoryId) {
        if (repositoryId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SessionConstant.FIELD_REPOSITORY_ID), repositoryId);
    }

    private static Specification<Session> byUser(UUID userId) {
        if (userId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SessionConstant.FIELD_USER_ID), userId);
    }

    private static Specification<Session> byMachine(UUID machineId) {
        if (machineId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SessionConstant.FIELD_MACHINE_ID), machineId);
    }

    private static Specification<Session> byModel(String model) {
        if (model == null || model.isBlank()) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SessionConstant.FIELD_MODEL), model);
    }

    private static Specification<Session> byAccountOrganizationUuid(String accountOrganizationUuid) {
        if (accountOrganizationUuid == null || accountOrganizationUuid.isBlank()) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SessionConstant.FIELD_ACCOUNT_ORGANIZATION_UUID),
                accountOrganizationUuid);
    }

    private static Specification<Session> byEnded(Boolean ended) {
        if (ended == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get(SessionConstant.FIELD_ENDED), ended);
    }

    /**
     * Matches the title, the branch or the key of a linked Kaizen ticket, case ignored. The
     * wildcards of LIKE are escaped, so a search for "a_b" matches only that text.
     */
    public static Specification<Session> searchSpecification(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replaceAll(SessionConstant.LIKE_SPECIAL_CHARACTERS_PATTERN, SessionConstant.LIKE_ESCAPED_REPLACEMENT);
        String pattern = SessionConstant.LIKE_WILDCARD + escaped + SessionConstant.LIKE_WILDCARD;
        return (root, query, cb) -> {
            Subquery<UUID> ticketMatch = query.subquery(UUID.class);
            Root<SessionLink> link = ticketMatch.from(SessionLink.class);
            ticketMatch.select(link.get(CommonConstant.ID)).where(
                    cb.equal(link.get(SessionConstant.FIELD_SESSION), root),
                    cb.equal(link.get(SessionConstant.FIELD_KIND), SessionLinkKind.KAIZEN_TICKET),
                    cb.like(cb.lower(link.get(SessionConstant.FIELD_REFERENCE)), pattern, SessionConstant.LIKE_ESCAPE));
            return cb.or(
                    cb.like(cb.lower(root.get(SessionConstant.FIELD_TITLE)), pattern, SessionConstant.LIKE_ESCAPE),
                    cb.like(cb.lower(root.get(SessionConstant.FIELD_BRANCH)), pattern, SessionConstant.LIKE_ESCAPE),
                    cb.exists(ticketMatch));
        };
    }

    public static Specification<Session> filterBy(UUID organizationId, SessionQueryRequest request) {
        return byOrganization(organizationId)
                .and(byRepository(request.getRepositoryId()))
                .and(byUser(request.getUserId()))
                .and(byMachine(request.getMachineId()))
                .and(byModel(request.getModel()))
                .and(byAccountOrganizationUuid(request.getAccountOrganizationUuid()))
                .and(byEnded(request.getEnded()))
                .and(searchSpecification(request.getSearch()));
    }

    /**
     * The handoff snapshots below handoffs/{feature}/ of the repository's sessions. The wildcards of
     * LIKE in the feature are escaped, so a feature "a_b" never matches "axb".
     */
    public static Specification<SessionHandoffSnapshot> featureHandoffSnapshots(UUID organizationId, UUID repositoryId,
            String feature) {
        String prefix = feature.replaceAll(SessionConstant.LIKE_SPECIAL_CHARACTERS_PATTERN, SessionConstant.LIKE_ESCAPED_REPLACEMENT)
                + SessionConstant.HANDOFF_DIRECTORY_SEPARATOR + SessionConstant.LIKE_WILDCARD;
        return (root, query, cb) -> {
            Join<SessionHandoffSnapshot, Session> session = root.join(SessionConstant.FIELD_SESSION);
            return cb.and(
                    cb.equal(session.get(SessionConstant.FIELD_ORGANIZATION_ID), organizationId),
                    cb.equal(session.get(SessionConstant.FIELD_REPOSITORY_ID), repositoryId),
                    cb.like(root.get(SessionConstant.FIELD_PATH), prefix, SessionConstant.LIKE_ESCAPE));
        };
    }
}
