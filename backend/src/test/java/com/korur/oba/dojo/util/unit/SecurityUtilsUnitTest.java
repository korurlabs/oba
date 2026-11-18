package com.korur.oba.dojo.util.unit;

// 1. Framework-level
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

// 3. Java SDK
import java.util.Optional;

// 4. Internal
import com.korur.oba.dojo.exception.internal.auth.UnauthorizedException;
import com.korur.oba.dojo.util.SecurityUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsUnitTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpSecurityContextWithJwt(Jwt jwt) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private void setUpSecurityContextWithPrincipal(Object principal) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private void setUpEmptySecurityContext() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);
    }

    private Jwt buildJwtWithSub(String sub) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", sub)
                .build();
    }

    private Jwt buildJwtWithoutSub() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("iss", "test-issuer")
                .build();
    }

    @Nested
    class GetKeycloakIdFromToken {

        @Test
        void validJwt_returnsKeycloakId() {
            // Arrange
            String expectedKeycloakId = "kc-user-123";
            setUpSecurityContextWithJwt(buildJwtWithSub(expectedKeycloakId));

            // Act
            String result = SecurityUtils.getKeycloakIdFromToken();

            // Assert
            assertEquals(expectedKeycloakId, result);
        }

        @Test
        void noAuthentication_throwsUnauthorizedException() {
            // Arrange
            setUpEmptySecurityContext();

            // Act & Assert
            assertThrows(UnauthorizedException.class, SecurityUtils::getKeycloakIdFromToken);
        }

        @Test
        void nonJwtPrincipal_throwsUnauthorizedException() {
            // Arrange
            setUpSecurityContextWithPrincipal("not-a-jwt");

            // Act & Assert
            assertThrows(UnauthorizedException.class, SecurityUtils::getKeycloakIdFromToken);
        }

        @Test
        void nullSubClaim_throwsUnauthorizedException() {
            // Arrange
            setUpSecurityContextWithJwt(buildJwtWithoutSub());

            // Act & Assert
            assertThrows(UnauthorizedException.class, SecurityUtils::getKeycloakIdFromToken);
        }

        @Test
        void blankSubClaim_throwsUnauthorizedException() {
            // Arrange
            setUpSecurityContextWithJwt(buildJwtWithSub("   "));

            // Act & Assert
            assertThrows(UnauthorizedException.class, SecurityUtils::getKeycloakIdFromToken);
        }
    }

    @Nested
    class FindKeycloakIdFromToken {

        @Test
        void validJwt_returnsOptionalWithKeycloakId() {
            // Arrange
            String expectedKeycloakId = "kc-user-123";
            setUpSecurityContextWithJwt(buildJwtWithSub(expectedKeycloakId));

            // Act
            Optional<String> result = SecurityUtils.findKeycloakIdFromToken();

            // Assert
            assertTrue(result.isPresent());
            assertEquals(expectedKeycloakId, result.get());
        }

        @Test
        void noAuthentication_returnsEmpty() {
            // Arrange
            setUpEmptySecurityContext();

            // Act
            Optional<String> result = SecurityUtils.findKeycloakIdFromToken();

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        void nonJwtPrincipal_returnsEmpty() {
            // Arrange
            setUpSecurityContextWithPrincipal("not-a-jwt");

            // Act
            Optional<String> result = SecurityUtils.findKeycloakIdFromToken();

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        void nullSubClaim_returnsEmpty() {
            // Arrange
            setUpSecurityContextWithJwt(buildJwtWithoutSub());

            // Act
            Optional<String> result = SecurityUtils.findKeycloakIdFromToken();

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        void blankSubClaim_returnsEmpty() {
            // Arrange
            setUpSecurityContextWithJwt(buildJwtWithSub("   "));

            // Act
            Optional<String> result = SecurityUtils.findKeycloakIdFromToken();

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class SanitizeForLog {

        @Test
        void normalString_returnsSameString() {
            // Arrange
            String input = "normal-string-123";

            // Act
            String result = SecurityUtils.sanitizeForLog(input);

            // Assert
            assertEquals("normal-string-123", result);
        }

        @Test
        void nullInput_returnsNullPlaceholder() {
            // Act
            String result = SecurityUtils.sanitizeForLog(null);

            // Assert
            assertEquals("[NULL]", result);
        }

        @Test
        void inputWithNewlines_stripsControlCharacters() {
            // Arrange
            String input = "line1\nline2\nline3";

            // Act
            String result = SecurityUtils.sanitizeForLog(input);

            // Assert
            assertEquals("line1_line2_line3", result);
        }

        @Test
        void inputWithTabs_stripsControlCharacters() {
            // Arrange
            String input = "col1\tcol2";

            // Act
            String result = SecurityUtils.sanitizeForLog(input);

            // Assert
            assertEquals("col1_col2", result);
        }

        @Test
        void inputWithCarriageReturns_stripsControlCharacters() {
            // Arrange
            String input = "line1\r\nline2";

            // Act
            String result = SecurityUtils.sanitizeForLog(input);

            // Assert
            assertEquals("line1__line2", result);
        }
    }
}
