package com.korur.oba.shrine.identity.service.impl;

// 1. Framework-level
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.constant.UserConstant;
import com.korur.oba.shrine.identity.constant.service.UserServiceLogConstant;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.exception.ExceptionDomainIdentityError;
import com.korur.oba.shrine.identity.mapper.UserMapper;
import com.korur.oba.shrine.identity.repository.UserRepository;
import com.korur.oba.shrine.identity.service.UserProfileQueryService;
import com.korur.oba.shrine.identity.validator.UserValidator;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;
import com.korur.oba.shrine.invitation.service.InvitationQueryService;

/**
 * Purpose:
 * Handles read-only user profile retrieval from the JWT SecurityContext.
 *
 * Bigger picture:
 * Acts as the read side of the user profile CQRS split. Resolves the
 * authenticated user's keycloakId from the JWT and fetches their profile
 * from the database without any side effects. Consumed by
 * UserProfileController to serve the GET /users/me endpoint.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-04-09
 */
@Service
@RequiredArgsConstructor
public class UserProfileQueryServiceImpl implements UserProfileQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(UserProfileQueryServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final LoggingService loggingService;
    private final InvitationQueryService invitationQueryService;
    private final UserValidator userValidator;
    private final AppAccessQueryService appAccessQueryService;

    /**
     * Purpose:
     * Retrieves the current authenticated user's profile in a read-only fashion,
     * including any pending invitations.
     *
     * Bigger picture:
     * Serves the GET /users/me endpoint. Unlike syncUserFromJwt in
     * UserProfileService, this method never creates or modifies data. If the
     * user has not been synced yet (POST /users/me was never called), it
     * throws a NotFoundException to signal the client to call the sync
     * endpoint first, unless the subject belongs to a soft-deleted user, which
     * is refused so the client does not sync and recreate the account. Enriches the response with pending invitations
     * so the frontend can show accept/decline actions.
     *
     * @docType STANDARD
     * @author Claude
     * @since 2026-03-04
     * @updated 2026-04-09
     * @return the current user's detail response with pending invitations
     * @throws NotFoundException if no user exists for the JWT's keycloakId
     * @throws ForbiddenException if the keycloakId belongs to a soft-deleted user
     */
    @Transactional(readOnly = true)
    @Override
    public UserDetailResponse getCurrentUser() {
        loggingService.observe(
                LOG_PREFIX,
                LogLayer.SERVICE,
                UserServiceLogConstant.USER_GET_CURRENT_USER_START);

        Jwt jwt = extractJwt();
        String keycloakId = jwt.getSubject();

        Optional<User> optionalUser = userRepository.findByKeycloakId(keycloakId);
        if (optionalUser.isEmpty()) {
            if (keycloakId != null && !keycloakId.isBlank()) {
                userValidator.ensureKeycloakIdNotDeleted(keycloakId);
            }
            throw new NotFoundException(ExceptionDomainIdentityError.NOT_USER_NOT_SYNCED,
                    Map.of(UserConstant.FIELD_USER_KEYCLOAK_ID, keycloakId != null ? keycloakId : "unknown"));
        }
        User user = optionalUser.get();

        List<InvitationResponse> pendingInvitations = invitationQueryService
                .listPendingInvitationsByEmail(user.getEmail());

        return userMapper.toDetailResponse(user, pendingInvitations,
                appAccessQueryService.listAccessibleAppsByOrganization(user));
    }

    /**
     * Purpose:
     * Extracts the JWT token from the current SecurityContext.
     *
     * Bigger picture:
     * Isolates SecurityContext access so the public method stays focused
     * on business logic.
     *
     * @docType STANDARD
     * @author Claude
     * @since 2026-03-04
     * @updated 2026-03-04
     * @return the JWT token from the current authentication
     * @throws UnauthorizedException if no JWT authentication is present
     */
    private Jwt extractJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof JwtAuthenticationToken jwtToken)) {
            throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
        }
        return jwtToken.getToken();
    }
}
