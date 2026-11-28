package com.korur.oba.dojo.security.token;

// 1. Framework-level
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionCategory;
import com.korur.oba.dojo.exception.enums.ExceptionDomainError;
import com.korur.oba.dojo.exception.enums.ExceptionSecurityError;
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.security.token.dto.ApiTokenBodyLimit;
import com.korur.oba.dojo.security.token.dto.ApiTokenEndpoint;
import com.korur.oba.dojo.security.token.dto.ApiTokenPrincipal;
import com.korur.oba.dojo.security.token.dto.ApiTokenRefusedPaths;
import com.korur.oba.dojo.security.token.entity.ApiToken;
import com.korur.oba.dojo.security.token.enums.ApiTokenKind;
import com.korur.oba.dojo.security.token.handler.ApiTokenKindHandler;
import com.korur.oba.dojo.security.token.helper.ApiTokenKindRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Token kinds for the unit tests of the token mechanism: a machine-like kind confined to reserved paths
 * with a body limit, and an agent-like kind that may call any path but the refused Kaya paths, and only
 * GET /users/me unannotated.
 */
public final class TestTokenKinds {

    public static final int MACHINE_BODY_LIMIT = 16;

    private TestTokenKinds() {
    }

    public enum TestTokenError implements ExceptionDomainError {
        MACHINE_INVALID("TST_AUT_001", "Machine token refused.", ExceptionCategory.AUT),
        AGENT_INVALID("TST_AUT_002", "Agent token refused.", ExceptionCategory.AUT),
        AGENT_PATH_REFUSED("TST_SEC_001", "Agent token refused on this path.", ExceptionCategory.SEC),
        BODY_TOO_LARGE("TST_API_001", "Body too large.", ExceptionCategory.API);

        private final String code;
        private final String message;
        private final ExceptionCategory category;

        TestTokenError(String code, String message, ExceptionCategory category) {
            this.code = code;
            this.message = message;
            this.category = category;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public ExceptionCategory getCategory() {
            return category;
        }
    }

    public record TestPrincipal(UUID tokenId, UUID userId, UUID organizationId) implements ApiTokenPrincipal {
    }

    /** A machine-like kind; its subject is refused when the token's subject id is {@link #retiredSubjectId}. */
    public static final class MachineLikeHandler implements ApiTokenKindHandler {

        public UUID retiredSubjectId;

        @Override
        public ApiTokenKind kind() {
            return ApiTokenKind.KAYA_MACHINE;
        }

        @Override
        public List<String> reservedPaths() {
            return List.of("/kaya/ingest/**", "/kaya/delivery/**");
        }

        @Override
        public Optional<ApiTokenRefusedPaths> refusedPaths() {
            return Optional.empty();
        }

        @Override
        public List<ApiTokenEndpoint> exemptEndpoints() {
            return List.of(new ApiTokenEndpoint(HttpMethod.POST, "/kaya/ingest/**"));
        }

        @Override
        public Optional<ApiTokenBodyLimit> bodyLimit() {
            return Optional.of(new ApiTokenBodyLimit(MACHINE_BODY_LIMIT, TestTokenError.BODY_TOO_LARGE));
        }

        @Override
        public ApiTokenPrincipal resolvePrincipal(ApiToken token) {
            if (token.getSubjectId().equals(retiredSubjectId)) {
                throw new UnauthorizedException(ExceptionSecurityError.UNAUTHORIZED);
            }
            return new TestPrincipal(token.getId(), token.getUserId(), token.getOrganizationId());
        }

        @Override
        public ExceptionDomainError invalidTokenError() {
            return TestTokenError.MACHINE_INVALID;
        }
    }

    public static final class AgentLikeHandler implements ApiTokenKindHandler {

        @Override
        public ApiTokenKind kind() {
            return ApiTokenKind.AGENT;
        }

        @Override
        public List<String> reservedPaths() {
            return List.of();
        }

        @Override
        public Optional<ApiTokenRefusedPaths> refusedPaths() {
            return Optional.of(new ApiTokenRefusedPaths(List.of("/kaya/**"), TestTokenError.AGENT_PATH_REFUSED,
                    Map.of("action", "KAYA_ACCESS")));
        }

        @Override
        public List<ApiTokenEndpoint> exemptEndpoints() {
            return List.of(new ApiTokenEndpoint(HttpMethod.GET, "/users/me"));
        }

        @Override
        public Optional<ApiTokenBodyLimit> bodyLimit() {
            return Optional.empty();
        }

        @Override
        public ApiTokenPrincipal resolvePrincipal(ApiToken token) {
            return new TestPrincipal(token.getId(), token.getUserId(), token.getOrganizationId());
        }

        @Override
        public ExceptionDomainError invalidTokenError() {
            return TestTokenError.AGENT_INVALID;
        }
    }

    @SuppressWarnings("unchecked")
    public static ApiTokenKindRegistry registryOf(ApiTokenKindHandler... handlers) {
        ObjectProvider<ApiTokenKindHandler> provider = mock(ObjectProvider.class);
        when(provider.iterator()).thenAnswer(invocation -> List.of(handlers).iterator());
        return new ApiTokenKindRegistry(provider);
    }
}
