package com.korur.oba.apps.kaya.core.security.filter;

// 1. Framework-level
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

// 2. Third-party
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 3. Java SDK
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ErrorResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.core.security.authentication.MachineTokenAuthentication;
import com.korur.oba.apps.kaya.core.security.constant.KayaSecurityConstant;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.apps.kaya.machine.dto.MachinePrincipal;
import com.korur.oba.apps.kaya.machine.service.MachineService;
import com.korur.oba.apps.kaya.machine.service.MachineTokenQueryService;
import com.korur.oba.apps.kaya.identity.service.KayaUserQueryService;
import com.korur.oba.apps.kaya.identity.service.OrganizationAccessQueryService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Authenticates the recorder and the CLI on the machine token paths with the bearer token of an
 * machine. A missing or refused token answers 401 KAY_SEC_003 here, and a body over the limit
 * answers 413 KAY_VAL_012, because an exception thrown from a servlet filter never reaches the
 * MVC exception handler.
 *
 * The token is checked before the body is read, so an unauthenticated caller cannot make the
 * server buffer anything. The body is then read up to the limit even without a Content-Length,
 * so a chunked request cannot exceed it either. The token's use is recorded before the body is
 * checked, so a refused batch still shows its token as used.
 *
 * On every path of the chain except ingest the token's user must also still act in the
 * organization with the Kaya app: a token outlives a user disabled, a membership ended or a Kaya
 * grant revoked after it was issued, and must then neither read agent config nor change it. The check
 * runs here, once, because the request interceptor does not see a machine token. The tenant check
 * runs before the app check, so a user who left the organization still learns that and not only that
 * Kaya is closed. Ingest stays open to every live token: it records sessions and serves no config.
 *
 * The check fails closed: it names the one exempt pattern, not the checked ones, so a path the chain
 * accepts and the exemption does not recognise is checked. The exemption matches the decoded path
 * within the application, the same path the chain's matcher and the MVC routing match, so an encoded
 * form of a delivery path cannot pass as ingest or skip the check.
 *
 * The filter is built by SecurityConfig for the machine token chain only and is not a bean, so the
 * servlet container does not also run it on every other path.
 */
public class MachineTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MachineTokenAuthenticationFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final MachineTokenQueryService machineTokenQueryService;
    private final MachineService machineService;
    private final KayaUserQueryService kayaUserQueryService;
    private final OrganizationAccessQueryService organizationAccessQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final ObjectMapper objectMapper;
    private final LoggingService loggingService;

    public MachineTokenAuthenticationFilter(
            MachineTokenQueryService machineTokenQueryService,
            MachineService machineService,
            KayaUserQueryService kayaUserQueryService,
            OrganizationAccessQueryService organizationAccessQueryService,
            AppAccessQueryService appAccessQueryService,
            ObjectMapper objectMapper,
            LoggingService loggingService) {
        this.machineTokenQueryService = machineTokenQueryService;
        this.machineService = machineService;
        this.kayaUserQueryService = kayaUserQueryService;
        this.organizationAccessQueryService = organizationAccessQueryService;
        this.appAccessQueryService = appAccessQueryService;
        this.objectMapper = objectMapper;
        this.loggingService = loggingService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(KayaSecurityConstant.AUTHORIZATION_BEARER_PREFIX)) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.MACHINE_TOKEN_MISSING,
                    request.getMethod(), request.getRequestURI());
            writeError(request, response, HttpStatus.UNAUTHORIZED,
                    ExceptionDomainKayaError.SEC_MACHINE_TOKEN_INVALID.getCode(),
                    ExceptionDomainKayaError.SEC_MACHINE_TOKEN_INVALID.getMessage(), null);
            return;
        }

        MachinePrincipal principal;
        try {
            principal = machineTokenQueryService.authenticate(
                    header.substring(KayaSecurityConstant.AUTHORIZATION_BEARER_PREFIX.length()).trim());
        } catch (ApplicationException e) {
            // The token lookup has logged why; the refusal is answered to the caller here.
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.MACHINE_TOKEN_REFUSED,
                    request.getMethod(), request.getRequestURI(), e.getErrorCode());
            writeError(request, response, e.getStatusCode(), e.getErrorCode(), e.getErrorMessage(), e.getErrorValues());
            return;
        }
        machineService.markTokenUsed(principal.tokenId());
        loggingService.debug(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.MACHINE_TOKEN_AUTHENTICATED,
                principal.tokenId(), principal.machineId(), request.getMethod(), request.getRequestURI());

        if (!isIngestPath(request)) {
            try {
                verifyDeliveryAccess(principal);
            } catch (ApplicationException e) {
                // The access check has logged why; the refusal is answered to the caller here.
                loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.MACHINE_TOKEN_REFUSED,
                        request.getMethod(), request.getRequestURI(), e.getErrorCode());
                writeError(request, response, e.getStatusCode(), e.getErrorCode(), e.getErrorMessage(),
                        e.getErrorValues());
                return;
            }
        }

        byte[] body = readBodyWithinLimit(request);
        if (body == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.MACHINE_BODY_TOO_LARGE,
                    request.getMethod(), request.getRequestURI(), principal.machineId(),
                    KayaSecurityConstant.MACHINE_TOKEN_MAX_BODY_BYTES);
            writeError(request, response, HttpStatus.PAYLOAD_TOO_LARGE,
                    ExceptionDomainKayaError.VAL_BODY_TOO_LARGE.getCode(),
                    ExceptionDomainKayaError.VAL_BODY_TOO_LARGE.getMessage(),
                    Map.of(KayaSecurityConstant.FIELD_LIMIT_BYTES, KayaSecurityConstant.MACHINE_TOKEN_MAX_BODY_BYTES));
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new MachineTokenAuthentication(principal));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /**
     * Tells whether the request is on an ingest path. The path is decoded and taken without the
     * context path, as the security chain and the MVC routing take it, because the raw request URI
     * keeps percent-encoded letters that both of them decode.
     */
    private static boolean isIngestPath(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return PATH_MATCHER.match(KayaSecurityConstant.MACHINE_TOKEN_INGEST_PATH, path);
    }

    /**
     * Refuses a token whose user no longer acts in the organization with the Kaya app.
     *
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException GEN_SEC_002 when the user
     *         is not ACTIVE or not a member, KAY_SEC_002 when the membership or the organization is
     *         not ACTIVE, GEN_SEC_003 for a GUEST, APP_SEC_001 when the user may not open the Kaya app
     */
    private void verifyDeliveryAccess(MachinePrincipal principal) {
        User user = kayaUserQueryService.getEntityById(principal.userId());
        organizationAccessQueryService.verifyTenantAccess(user, principal.organizationId());
        appAccessQueryService.verifyAppAccess(principal.organizationId(), user.getId(), App.KAYA);
    }

    /**
     * Reads the body when it fits the limit.
     *
     * @return the body, or null when it is larger than the limit
     */
    private byte[] readBodyWithinLimit(HttpServletRequest request) throws IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > KayaSecurityConstant.MACHINE_TOKEN_MAX_BODY_BYTES) {
            return null;
        }
        try (InputStream input = request.getInputStream()) {
            byte[] body = input.readNBytes(KayaSecurityConstant.MACHINE_TOKEN_MAX_BODY_BYTES + 1);
            return body.length > KayaSecurityConstant.MACHINE_TOKEN_MAX_BODY_BYTES ? null : body;
        }
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> errorValues) throws IOException {
        CustomResponse<Void> body = new CustomResponse<>(false, message, null, null,
                new ErrorResponse(code, message, errorValues));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            objectMapper.writeValue(response.getWriter(), body);
        } catch (IOException e) {
            loggingService.error(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.MACHINE_ERROR_WRITE_FAILED, e,
                    request.getMethod(), request.getRequestURI());
            throw e;
        }
    }
}
