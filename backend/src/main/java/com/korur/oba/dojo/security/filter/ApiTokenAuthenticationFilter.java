package com.korur.oba.dojo.security.filter;

// 1. Framework-level
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

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
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.enums.ExceptionError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ErrorResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.security.constant.SecurityLogConstant;
import com.korur.oba.dojo.security.helper.RequestPathHelper;
import com.korur.oba.dojo.security.token.authentication.ApiTokenAuthentication;
import com.korur.oba.dojo.security.token.constant.ApiTokenConstant;
import com.korur.oba.dojo.security.token.dto.ApiTokenBodyLimit;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.handler.ApiTokenKindHandler;
import com.korur.oba.dojo.security.token.helper.ApiTokenKindRegistry;
import com.korur.oba.dojo.security.token.service.ApiTokenQueryService;
import com.korur.oba.dojo.security.token.service.ApiTokenService;

/**
 * Authenticates the requests of the API token chain. Every refusal answers the 401 of the token's
 * kind, or of the kind that reserves the path, here as JSON, because an exception thrown from a servlet
 * filter never reaches the MVC exception handler.
 *
 * In this order:
 * - a request without a token of an accepted kind reached the chain on a reserved path, so its
 *   credential, a JWT or none, is refused with the error of the kind that reserves the path;
 * - a token on a path another kind reserves is refused with that kind's error;
 * - a token of a kind that reserves paths is refused anywhere else, so its credential opens nothing
 *   beyond them;
 * - the token must be live ({@link ApiTokenQueryService#authenticate}) and its subject may still act
 *   ({@link ApiTokenKindHandler#resolvePrincipal});
 * - its use is recorded, before the body is checked, so a refused upload still shows the token as used;
 * - the body is read up to the kind's limit even without a Content-Length, so a chunked request cannot
 *   exceed it, and only after the token was checked, so an unauthenticated caller cannot make the server
 *   buffer anything.
 *
 * What the token may call is decided afterwards by the RequiredUserAccess check, against its user.
 *
 * Built by {@link com.korur.oba.dojo.security.config.ApiTokenSecurityConfig} for the token chain
 * only and not a bean, so the servlet container does not also run it on every other path.
 */
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ApiTokenAuthenticationFilter.class);

    private final ApiTokenKindRegistry apiTokenKindRegistry;
    private final ApiTokenQueryService apiTokenQueryService;
    private final ApiTokenService apiTokenService;
    private final ObjectMapper objectMapper;
    private final LoggingService loggingService;
    // Kept on the request, as the JWT chain keeps its authentication, so the second dispatch of a
    // streamed response still sees the token's caller.
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    public ApiTokenAuthenticationFilter(
            ApiTokenKindRegistry apiTokenKindRegistry,
            ApiTokenQueryService apiTokenQueryService,
            ApiTokenService apiTokenService,
            ObjectMapper objectMapper,
            LoggingService loggingService) {
        this.apiTokenKindRegistry = apiTokenKindRegistry;
        this.apiTokenQueryService = apiTokenQueryService;
        this.apiTokenService = apiTokenService;
        this.objectMapper = objectMapper;
        this.loggingService = loggingService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = RequestPathHelper.pathWithinApplication(request);
        String bearerToken = ApiTokenRequestMatcher.bearerToken(request);
        Optional<ApiTokenKindHandler> tokenHandler = apiTokenKindRegistry.findByToken(bearerToken);
        Optional<ApiTokenKindHandler> pathHandler = apiTokenKindRegistry.findByReservedPath(path);

        if (tokenHandler.isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_MISSING,
                    request.getMethod(), path);
            writeError(request, response, HttpStatus.UNAUTHORIZED, pathHandler
                    .<ExceptionError>map(ApiTokenKindHandler::invalidTokenError)
                    .orElse(ExceptionSecurityError.UNAUTHORIZED), null);
            return;
        }

        ApiTokenKindHandler handler = tokenHandler.get();
        if (pathHandler.isPresent() && pathHandler.get().kind() != handler.kind()) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_ON_FOREIGN_PATH,
                    handler.kind(), request.getMethod(), path, pathHandler.get().kind());
            writeError(request, response, HttpStatus.UNAUTHORIZED, pathHandler.get().invalidTokenError(), null);
            return;
        }
        if (pathHandler.isEmpty() && !handler.reservedPaths().isEmpty()) {
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_OUTSIDE_RESERVED_PATHS,
                    handler.kind(), request.getMethod(), path);
            writeError(request, response, HttpStatus.UNAUTHORIZED, handler.invalidTokenError(), null);
            return;
        }

        ApiTokenPrincipal principal;
        try {
            ApiToken token = apiTokenQueryService.authenticate(bearerToken);
            principal = handler.resolvePrincipal(token);
        } catch (ApplicationException e) {
            // The lookup or the kind has logged why; every refusal answers the one 401 of the kind.
            loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_REFUSED,
                    handler.kind(), request.getMethod(), path, e.getErrorCode());
            writeError(request, response, HttpStatus.UNAUTHORIZED, handler.invalidTokenError(), null);
            return;
        }
        apiTokenService.markUsed(principal.tokenId());
        loggingService.debug(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_AUTHENTICATED,
                handler.kind(), principal.tokenId(), principal.userId(), request.getMethod(), path);

        HttpServletRequest forwarded = request;
        Optional<ApiTokenBodyLimit> bodyLimit = handler.bodyLimit();
        if (bodyLimit.isPresent()) {
            byte[] body = readBodyWithinLimit(request, bodyLimit.get().maxBytes());
            if (body == null) {
                loggingService.warn(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_BODY_TOO_LARGE,
                        request.getMethod(), path, principal.tokenId(), bodyLimit.get().maxBytes());
                writeError(request, response, HttpStatus.PAYLOAD_TOO_LARGE, bodyLimit.get().tooLargeError(),
                        Map.of(ApiTokenConstant.FIELD_LIMIT_BYTES, bodyLimit.get().maxBytes()));
                return;
            }
            forwarded = new CachedBodyRequest(request, body);
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new ApiTokenAuthentication(handler.kind(), principal));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, forwarded, response);
        filterChain.doFilter(forwarded, response);
    }

    /**
     * Reads the body when it fits the limit.
     *
     * @return the body, or null when it is larger than the limit
     */
    private static byte[] readBodyWithinLimit(HttpServletRequest request, int maxBytes) throws IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBytes) {
            return null;
        }
        try (InputStream input = request.getInputStream()) {
            byte[] body = input.readNBytes(maxBytes + 1);
            return body.length > maxBytes ? null : body;
        }
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ExceptionError error,
            Map<String, Object> errorValues) throws IOException {
        CustomResponse<Void> body = new CustomResponse<>(false, error.getMessage(), null, null,
                new ErrorResponse(error.getCode(), error.getMessage(), errorValues));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            objectMapper.writeValue(response.getWriter(), body);
        } catch (IOException e) {
            loggingService.error(LOG_PREFIX, LogLayer.FILTER, SecurityLogConstant.API_TOKEN_ERROR_WRITE_FAILED, e,
                    request.getMethod(), request.getRequestURI());
            throw e;
        }
    }
}
