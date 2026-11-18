package com.korur.oba.shrine.organization.controller;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.lang.reflect.Method;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.security.annotation.RequiredUserAccess;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

class OrganizationControllerUnitTest {

    @Test
    void getOrganization_handler_requiresAdminAccessToThePathOrganization() throws NoSuchMethodException {
        // Arrange
        Method handler = OrganizationController.class.getDeclaredMethod("getOrganization", UUID.class);

        // Act
        RequiredUserAccess access = handler.getAnnotation(RequiredUserAccess.class);

        // Assert: the matrix test reads each handler's own value, so this pins the role itself
        assertThat(handler.isAnnotationPresent(PreAuthorize.class)).isFalse();
        assertThat(access).isNotNull();
        assertThat(access.value()).isEqualTo(OrganizationMemberRole.ADMIN);
    }
}
